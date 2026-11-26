package com.finpilot.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.finpilot.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class ImportAndTransactionsIntegrationTest extends IntegrationTest {
    static final String MAPPING = "{\"date\":\"Txn Date\",\"time\":\"Time\",\"description\":\"Narration\","
            + "\"debit\":\"Withdrawal Amt\",\"credit\":\"Deposit Amt\",\"dateFormat\":\"dd/MM/yyyy\"}";

    @Autowired
    JdbcTemplate jdbc;

    static MockMultipartFile sample() throws Exception {
        return new MockMultipartFile("file", "sample_statement.csv", "text/csv",
                new ClassPathResource("samples/sample_statement.csv").getInputStream().readAllBytes());
    }

    private JsonNode importFile(String token, MockMultipartFile file, String mapping) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/v1/imports").file(file)
                        .file(new MockMultipartFile("mapping", "", "application/json", mapping.getBytes()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void previewImportDedupAndHistory() throws Exception {
        String token = registerAndLogin();
        mvc.perform(multipart("/api/v1/imports/preview").file(sample()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headers[2]").value("Narration"))
                .andExpect(jsonPath("$.sampleRows.length()").value(10))
                .andExpect(jsonPath("$.suggestedMapping.debit").value("Withdrawal Amt"))
                .andExpect(jsonPath("$.suggestedMapping.dateFormat").value("dd/MM/yyyy"));

        JsonNode first = importFile(token, sample(), MAPPING);
        int total = first.path("totalRows").asInt();
        assertThat(first.path("imported").asInt()).isEqualTo(total).isGreaterThan(100);
        assertThat(first.path("failed").asInt()).isZero();

        JsonNode second = importFile(token, sample(), MAPPING);
        assertThat(second.path("imported").asInt()).isZero();
        assertThat(second.path("duplicates").asInt()).isEqualTo(total);

        mvc.perform(get("/api/v1/imports").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        JsonNode page = json.readTree(mvc.perform(get("/api/v1/transactions?size=5&sort=amount,asc")
                .header("Authorization", bearer(token))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.path("totalElements").asInt()).isEqualTo(total);
        assertThat(page.path("content")).hasSize(5);
        assertThat(page.path("content").get(0).path("amount").asDouble())
                .isLessThanOrEqualTo(page.path("content").get(1).path("amount").asDouble());
        assertThat(page.path("content").get(0).path("categorySource").asText()).isEqualTo("ML");
    }

    @Test
    void identicalRowsInOneFileAreBothKeptAndBadRowsReported() throws Exception {
        String token = registerAndLogin();
        String csv = "Date,Description,Amount\n2026-01-05,STARBUCKS COFFEE,-250\n2026-01-05,STARBUCKS COFFEE,-250\n"
                + "not-a-date,X,-1\n2026-01-06,,-5\n2026-01-07,ZERO,0\n2026-01-08,SALARY CREDIT,\"1,20,000.00\"\n";
        JsonNode r = importFile(token, new MockMultipartFile("file", "s.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)), "{\"date\":\"Date\",\"description\":\"Description\",\"amount\":\"Amount\"}");
        assertThat(r.path("imported").asInt()).isEqualTo(3);
        assertThat(r.path("failed").asInt()).isEqualTo(3);
        assertThat(r.path("errors").get(0).path("row").asInt()).isEqualTo(4);
        // invertSign flips card-style exports
        String card = "Date,Description,Amount\n2026-02-01,UBER RIDE,300\n";
        JsonNode inv = importFile(token, new MockMultipartFile("file", "c.csv", "text/csv", card.getBytes()),
                "{\"date\":\"Date\",\"description\":\"Description\",\"amount\":\"Amount\",\"invertSign\":true}");
        assertThat(inv.path("imported").asInt()).isEqualTo(1);
        mvc.perform(get("/api/v1/transactions?q=uber").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].amount").value(-300.0))
                .andExpect(jsonPath("$.content[0].category").value("Transport"));
    }

    @Test
    void importValidationErrors() throws Exception {
        String token = registerAndLogin();
        mvc.perform(multipart("/api/v1/imports").file(sample())
                        .file(new MockMultipartFile("mapping", "", "application/json", "{\"date\":\"Txn Date\",\"description\":\"Narration\"}".getBytes()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/imports").file(sample())
                        .file(new MockMultipartFile("mapping", "", "application/json", "{\"date\":\"Nope\",\"description\":\"Narration\",\"amount\":\"X\"}".getBytes()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Nope")));
        mvc.perform(multipart("/api/v1/imports").file(sample())
                        .file(new MockMultipartFile("mapping", "", "application/json", "nope".getBytes()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/imports/preview").file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[] {1, 2}))
                .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/imports/preview").file(new MockMultipartFile("file", "x.csv", "text/csv", new byte[0]))
                .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
    }

    @Test
    void correctionCreatesRuleAppliesToSimilarAndIsUsedOnNextImport() throws Exception {
        String token = registerAndLogin();
        String csv = "Date,Description,Amount\n2026-01-05,UPI/ACME GYM/111222333/Payment,-1500\n"
                + "2026-02-05,UPI/ACME GYM/444555666/Payment,-1500\n2026-02-06,UPI/SWIGGY/999888777/Payment,-300\n";
        String mapping = "{\"date\":\"Date\",\"description\":\"Description\",\"amount\":\"Amount\"}";
        importFile(token, new MockMultipartFile("file", "a.csv", "text/csv", csv.getBytes()), mapping);
        JsonNode list = json.readTree(mvc.perform(get("/api/v1/transactions?q=acme").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).path("content");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("merchant").asText()).isEqualTo("ACME GYM");
        long id = list.get(0).path("id").asLong();

        mvc.perform(patch("/api/v1/transactions/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Health\",\"notes\":\"annual plan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("Health"))
                .andExpect(jsonPath("$.categorySource").value("USER")).andExpect(jsonPath("$.notes").value("annual plan"));
        // notes are encrypted at rest
        assertThat(jdbc.queryForObject("SELECT notes_enc FROM transactions WHERE id = ?", String.class, id))
                .startsWith("v1:");
        JsonNode after = json.readTree(mvc.perform(get("/api/v1/transactions?category=Health").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).path("content");
        assertThat(after).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category_corrections WHERE transaction_id = ?", Long.class, id))
                .isEqualTo(1);

        importFile(token, new MockMultipartFile("file", "b.csv", "text/csv",
                "Date,Description,Amount\n2026-03-05,POS ACME GYM PUNE,-1500\n".getBytes()), mapping);
        mvc.perform(get("/api/v1/transactions?q=pune").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].category").value("Health"))
                .andExpect(jsonPath("$.content[0].categorySource").value("RULE"));

        mvc.perform(patch("/api/v1/transactions/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Nonsense\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions?sort=password,asc").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions?from=2026-02-01&to=2026-02-28&size=500").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions?from=2026-02-01&to=2026-02-28").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));

        // another user cannot see or modify
        String other = registerAndLogin();
        mvc.perform(patch("/api/v1/transactions/" + id).header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Food\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/transactions/" + id).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/transactions/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/categories").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(14));
    }

    @Test
    void mlOutageLeavesRowsUncategorised() throws Exception {
        org.mockito.Mockito.doThrow(new com.finpilot.ml.MlUnavailableException("down"))
                .when(ml).categorize(org.mockito.ArgumentMatchers.any());
        String token = registerAndLogin();
        JsonNode r = importFile(token, new MockMultipartFile("file", "a.csv", "text/csv",
                "Date,Description,Amount\n2026-01-05,SOMETHING,-10\n".getBytes()),
                "{\"date\":\"Date\",\"description\":\"Description\",\"amount\":\"Amount\"}");
        assertThat(r.path("imported").asInt()).isEqualTo(1);
        mvc.perform(get("/api/v1/transactions").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].category").value("Uncategorized"))
                .andExpect(jsonPath("$.content[0].categorySource").value("NONE"));
    }
}
