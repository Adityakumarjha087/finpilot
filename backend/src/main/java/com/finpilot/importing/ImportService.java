package com.finpilot.importing;

import com.finpilot.audit.AuditService;
import com.finpilot.common.ApiException;
import com.finpilot.importing.ImportDtos.ImportBatchDto;
import com.finpilot.importing.ImportDtos.ImportResult;
import com.finpilot.importing.ImportDtos.ParsedRow;
import com.finpilot.importing.ImportDtos.PreviewResponse;
import com.finpilot.importing.ImportDtos.RowError;
import com.finpilot.security.AuthenticatedUser;
import com.finpilot.transaction.BankTransaction;
import com.finpilot.transaction.CategorizationService;
import com.finpilot.transaction.MerchantNormalizer;
import com.finpilot.transaction.TransactionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
    private static final int MAX_ERRORS = 50;
    private final StatementParser parser;
    private final TransactionRepository transactions;
    private final ImportBatchRepository batches;
    private final CategorizationService categorization;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ZoneId zone;

    public ImportService(StatementParser parser, TransactionRepository transactions, ImportBatchRepository batches,
                         CategorizationService categorization, AuditService audit, ApplicationEventPublisher events,
                         ZoneId appZone) {
        this.parser = parser;
        this.transactions = transactions;
        this.batches = batches;
        this.categorization = categorization;
        this.audit = audit;
        this.events = events;
        this.zone = appZone;
    }

    /** Published after a successful import so anomaly detection can run asynchronously. */
    public record TransactionsImported(long userId, int count) {}

    public PreviewResponse preview(MultipartFile file) {
        RawTable table = read(file);
        return new PreviewResponse(safeName(file), table.headers(), table.rows().stream().limit(10).toList(),
                table.rows().size(), MappingSuggester.suggest(table));
    }

    @Transactional
    public ImportResult importFile(AuthenticatedUser user, MultipartFile file, ColumnMapping mapping) {
        try {
            mapping.validate();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        RawTable table = read(file);
        int dateIdx = required(table, mapping.date(), "date");
        int descIdx = required(table, mapping.description(), "description");
        int timeIdx = ColumnMapping.isSet(mapping.time()) ? required(table, mapping.time(), "time") : -1;
        int amountIdx = ColumnMapping.isSet(mapping.amount()) ? required(table, mapping.amount(), "amount") : -1;
        int debitIdx = ColumnMapping.isSet(mapping.debit()) ? required(table, mapping.debit(), "debit") : -1;
        int creditIdx = ColumnMapping.isSet(mapping.credit()) ? required(table, mapping.credit(), "credit") : -1;
        boolean invert = Boolean.TRUE.equals(mapping.invertSign());

        List<ParsedRow> parsed = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int failed = 0;
        for (int i = 0; i < table.rows().size(); i++) {
            List<String> r = table.rows().get(i);
            int rowNumber = i + 2; // 1-based incl. header row, matches spreadsheet row numbers
            try {
                var postedAt = DateParsing.parse(r.get(dateIdx), timeIdx >= 0 ? r.get(timeIdx) : null,
                        mapping.dateFormat());
                String description = r.get(descIdx).trim();
                if (description.isEmpty()) {
                    throw new IllegalArgumentException("missing description");
                }
                BigDecimal amount;
                if (amountIdx >= 0) {
                    amount = Amounts.parse(r.get(amountIdx));
                    if (invert) {
                        amount = amount.negate();
                    }
                } else {
                    BigDecimal debit = debitIdx >= 0 ? Amounts.parseOptional(r.get(debitIdx)) : BigDecimal.ZERO;
                    BigDecimal credit = creditIdx >= 0 ? Amounts.parseOptional(r.get(creditIdx)) : BigDecimal.ZERO;
                    amount = credit.abs().subtract(debit.abs());
                }
                if (amount.signum() == 0) {
                    throw new IllegalArgumentException("amount is zero or missing");
                }
                parsed.add(new ParsedRow(rowNumber, postedAt, truncate(description, 500), amount));
            } catch (IllegalArgumentException e) {
                failed++;
                if (errors.size() < MAX_ERRORS) {
                    errors.add(new RowError(rowNumber, e.getMessage()));
                }
            }
        }
        ImportBatch batch = batches.save(new ImportBatch(user.id(), safeName(file), table.rows().size()));
        int[] counts = persist(user.id(), batch.getId(), parsed);
        batch.complete(counts[0], counts[1], failed);
        batches.save(batch);
        audit.record(user.id(), user.email(), "IMPORT", "IMPORT_BATCH", batch.getId(),
                "file=" + batch.getFileName() + " imported=" + counts[0] + " duplicates=" + counts[1] + " failed="
                        + failed);
        if (counts[0] > 0) {
            events.publishEvent(new TransactionsImported(user.id(), counts[0]));
        }
        return new ImportResult(batch.getId(), batch.getFileName(), table.rows().size(), counts[0], counts[1], failed,
                errors);
    }

    /**
     * Deduplicates against existing rows (hash of date-time, amount, description and the row's occurrence index
     * among identical rows in this upload, so two genuine identical coffees are both kept while re-uploading the
     * same file inserts nothing), categorises and saves. Returns {imported, duplicates}.
     */
    @Transactional
    public int[] persist(long userId, Long batchId, List<ParsedRow> rows) {
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, ParsedRow> byHash = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            String base = row.postedAt() + "|" + row.amount().setScale(2, RoundingMode.HALF_UP).toPlainString() + "|"
                    + row.description().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            int n = occurrences.merge(base, 1, Integer::sum);
            byHash.put(sha256(userId + "|" + base + "|" + n), row);
        }
        Set<String> existing = byHash.isEmpty() ? Set.of() : transactions.findExistingHashes(userId, byHash.keySet());
        List<BankTransaction> fresh = new ArrayList<>();
        byHash.forEach((hash, row) -> {
            if (!existing.contains(hash)) {
                fresh.add(new BankTransaction(userId, batchId, row.postedAt().atZone(zone).toInstant(),
                        row.description(), MerchantNormalizer.normalise(row.description()),
                        row.amount().setScale(2, RoundingMode.HALF_UP), hash));
            }
        });
        categorization.categorise(userId, fresh);
        transactions.saveAll(fresh);
        return new int[] {fresh.size(), byHash.size() - fresh.size()};
    }

    @Transactional(readOnly = true)
    public List<ImportBatchDto> history(long userId) {
        return batches.findTop50ByUserIdOrderByImportedAtDesc(userId).stream().map(ImportBatchDto::of).toList();
    }

    private RawTable read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Upload a non-empty .csv or .xlsx file");
        }
        try (InputStream in = file.getInputStream()) {
            return parser.parse(file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
    }

    private static int required(RawTable table, String header, String field) {
        int idx = table.indexOf(header);
        if (idx < 0) {
            throw ApiException.badRequest("Column '" + header + "' mapped to " + field + " was not found in the file");
        }
        return idx;
    }

    private static String safeName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "statement" : file.getOriginalFilename();
        name = name.replaceAll("[\\\\/]", "_").replaceAll("[^\\w.\\- ]", "");
        return truncate(name.isBlank() ? "statement" : name, 255);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
