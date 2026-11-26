package com.finpilot.transaction;

import com.finpilot.ml.MlClient;
import com.finpilot.ml.MlDtos.CategorizeItem;
import com.finpilot.ml.MlDtos.CategorizeRequest;
import com.finpilot.ml.MlDtos.Prediction;
import com.finpilot.ml.MlUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Categorisation pipeline: (1) the user's own merchant rules learned from corrections win,
 * (2) otherwise the ML model predicts, (3) if the ML service is down rows stay "Uncategorized"
 * so imports never fail because of the model.
 */
@Service
public class CategorizationService {
    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);
    private static final int BATCH = 1000;
    private final MlClient ml;
    private final MerchantRuleRepository rules;

    public CategorizationService(MlClient ml, MerchantRuleRepository rules) {
        this.ml = ml;
        this.rules = rules;
    }

    public void categorise(long userId, List<BankTransaction> txns) {
        Map<String, String> userRules = rules.findByUserId(userId).stream()
                .collect(Collectors.toMap(MerchantRule::getMerchant, MerchantRule::getCategory, (a, b) -> b));
        List<BankTransaction> forModel = new ArrayList<>();
        for (BankTransaction t : txns) {
            String rule = userRules.get(t.getMerchant());
            if (rule != null) {
                t.categorise(rule, CategorySource.RULE, 1.0);
            } else {
                forModel.add(t);
            }
        }
        for (int i = 0; i < forModel.size(); i += BATCH) {
            List<BankTransaction> chunk = forModel.subList(i, Math.min(forModel.size(), i + BATCH));
            try {
                List<CategorizeItem> items = chunk.stream()
                        .map(t -> new CategorizeItem(null, t.getDescription(), t.getAmount().doubleValue()))
                        .toList();
                List<Prediction> preds = ml.categorize(new CategorizeRequest(items)).predictions();
                for (int j = 0; j < chunk.size(); j++) {
                    Prediction p = preds.get(j);
                    String category = Categories.isValid(p.category()) ? p.category() : Categories.UNCATEGORIZED;
                    chunk.get(j).categorise(category, CategorySource.ML, p.confidence());
                }
            } catch (MlUnavailableException e) {
                log.warn("ML unavailable, leaving {} transactions uncategorised", chunk.size());
                chunk.forEach(t -> t.categorise(Categories.UNCATEGORIZED, CategorySource.NONE, null));
            }
        }
    }
}
