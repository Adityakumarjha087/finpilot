package com.finpilot.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.finpilot.auth.AuthService;
import com.finpilot.importing.ImportDtos.ParsedRow;
import com.finpilot.importing.ImportService;
import com.finpilot.ml.MlUnavailableException;
import com.finpilot.support.IntegrationTest;
import com.finpilot.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Budgets, dashboard, forecast, anomalies, recurring, goals, audit and admin over a 6-month history. */
class FeaturesIntegrationTest extends IntegrationTest {
    @Autowired
    ImportService imports;
    @Autowired
    AuthService auth;
    @Autowired
    com.finpilot.user.UserRepository users;

    String token;
    long userId;

    @BeforeEach
    void seedHistory() throws Exception {
        JsonNode reg = register("feat-" + UUID.randomUUID() + "@example.com");
        token = reg.path("accessToken").asText();
        userId = reg.path("user").path("id").asLong();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<ParsedRow> rows = new ArrayList<>();
        int n = 0;
        for (int m = 5; m >= 0; m--) {
            LocalDate first = today.withDayOfMonth(1).minusMonths(m);
            rows.add(row(++n, first.atTime(9, 0), "SALARY CREDIT", "80000"));
            rows.add(row(++n, first.plusDays(1).atTime(10, 0), "RENT PAYMENT", "-20000"));
            rows.add(row(++n, first.plusDays(2).atTime(3, 0), "NETFLIX.COM", "-649"));
            for (int d = 0; d < Math.min(10, today.getDayOfMonth()); d++) {
                rows.add(row(++n, first.plusDays(d).atTime(13, 0), "UPI/SWIGGY/" + (1000 + n) + "/Payment", "-" + (300 + d)));
            }
        }
        rows.add(row(++n, today.atTime(2, 0), "AMAZON BIG TV", "-95000"));
        imports.persist(userId, null, rows);
    }

    private static ParsedRow row(int n, LocalDateTime at, String desc, String amount) {
        return new ParsedRow(n, at, desc, new BigDecimal(amount));
    }

    @Test
    void budgetsCrudAndAlerts() throws Exception {
        String body = json.readTree(mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Food\",\"monthlyLimit\":100}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.alertLevel").value("EXCEEDED"))
                .andReturn().getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Food\",\"monthlyLimit\":100}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Income\",\"monthlyLimit\":100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Food\",\"monthlyLimit\":-5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/budgets/alerts").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].alertLevel").value("EXCEEDED"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("exceeded")));
        mvc.perform(put("/api/v1/budgets/" + body).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Food\",\"monthlyLimit\":1000000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alertLevel").value("OK"));
        mvc.perform(get("/api/v1/budgets?month=2020-01").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].spent").value(0.0));
        mvc.perform(get("/api/v1/budgets?month=bad").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/budgets/" + body).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/budgets/" + body).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dashboardAggregates() throws Exception {
        JsonNode d = json.readTree(mvc.perform(get("/api/v1/dashboard?months=6").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(d.path("monthly")).hasSize(6);
        assertThat(d.path("totals").path("income").asDouble()).isEqualTo(80000.0);
        assertThat(d.path("netWorth")).hasSize(6);
        assertThat(d.path("categoryBreakdown").get(0).path("category").asText()).isEqualTo("Shopping");
        assertThat(d.path("transactionCount").asLong()).isGreaterThan(20);
        mvc.perform(get("/api/v1/dashboard?months=99").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forecastPerCategoryAndTotal() throws Exception {
        JsonNode f = json.readTree(mvc.perform(get("/api/v1/forecast?horizon=3").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(f.path("categories").size()).isGreaterThanOrEqualTo(3);
        assertThat(f.path("categories").get(0).path("forecast")).hasSize(3);
        assertThat(f.path("total").path("history")).hasSize(5);
        Mockito.doThrow(new MlUnavailableException("down")).when(ml).forecast(ArgumentMatchers.any());
        mvc.perform(get("/api/v1/forecast").header("Authorization", bearer(token))).andExpect(status().isServiceUnavailable());
        String empty = registerAndLogin();
        mvc.perform(get("/api/v1/forecast").header("Authorization", bearer(empty)))
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void anomalyScanListDismiss() throws Exception {
        mvc.perform(post("/api/v1/anomalies/scan").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flagged").value(1));
        JsonNode list = json.readTree(mvc.perform(get("/api/v1/anomalies").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("amount").asDouble()).isEqualTo(-95000.0);
        assertThat(list.get(0).path("anomaly").path("reasons").get(0).asText()).contains("typical");
        long id = list.get(0).path("id").asLong();
        mvc.perform(get("/api/v1/transactions?flagged=true").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/v1/anomalies/" + id + "/dismiss").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.anomaly.status").value("DISMISSED"));
        mvc.perform(post("/api/v1/anomalies/scan").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.flagged").value(0));
        mvc.perform(get("/api/v1/anomalies").header("Authorization", bearer(token))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recurringDetectsSubscriptionAndRent() throws Exception {
        JsonNode r = json.readTree(mvc.perform(get("/api/v1/recurring").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> merchants = new ArrayList<>();
        r.forEach(x -> merchants.add(x.path("merchant").asText()));
        assertThat(merchants).contains("NETFLIX", "RENT");
        JsonNode netflix = null;
        for (JsonNode x : r) {
            if (x.path("merchant").asText().equals("NETFLIX")) {
                netflix = x;
            }
        }
        assertThat(netflix.path("cadence").asText()).isEqualTo("MONTHLY");
        assertThat(netflix.path("subscription").asBoolean()).isTrue();
    }

    @Test
    void goalsCrudAndSuggestion() throws Exception {
        String date = LocalDate.now().plusMonths(6).toString();
        long id = json.readTree(mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New car\",\"targetAmount\":900000,\"savedAmount\":100000,\"targetDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        JsonNode s = json.readTree(mvc.perform(get("/api/v1/goals/" + id + "/suggestion").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(s.path("feasible").asBoolean()).isFalse();
        assertThat(s.path("cuts").size()).isGreaterThan(0);
        assertThat(s.path("advice").asText()).contains("New car");
        assertThat(s.path("provider").asText()).isEqualTo("fake");
        mvc.perform(put("/api/v1/goals/" + id).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Car\",\"targetAmount\":110000,\"savedAmount\":100000,\"targetDate\":\"" + date + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progress").value(0.9091));
        JsonNode ok = json.readTree(mvc.perform(get("/api/v1/goals/" + id + "/suggestion").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertThat(ok.path("feasible").asBoolean()).isTrue();
        mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"targetAmount\":10,\"savedAmount\":20,\"targetDate\":\"" + date + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/goals").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"targetAmount\":10,\"savedAmount\":0,\"targetDate\":\"2001-01-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/goals").header("Authorization", bearer(token))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/v1/goals/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/goals/" + id + "/suggestion").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditAndAdminRetrain() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        auth.createUser(email, "AdminPass1234", "Admin", Role.ADMIN);
        String admin = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"AdminPass1234\"}"))
                .andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        // create a correction so retraining has data
        long txId = json.readTree(mvc.perform(get("/api/v1/transactions?q=netflix").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).path("content").get(0).path("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/transactions/" + txId)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Entertainment\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/ml/status").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelVersion").value(1))
                .andExpect(jsonPath("$.pendingCorrections").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        JsonNode r = json.readTree(mvc.perform(post("/api/v1/admin/ml/retrain").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.path("modelVersion").asInt()).isEqualTo(2);
        assertThat(r.path("corrections").asInt()).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/v1/admin/ml/status").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.pendingCorrections").value(0));
        mvc.perform(get("/api/v1/audit?size=100").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").exists());
        JsonNode mine = json.readTree(mvc.perform(get("/api/v1/audit").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        mine.path("content").forEach(e -> assertThat(e.path("userEmail").asText()).startsWith("feat-"));
        assertThat(mine.path("content").toString()).contains("CATEGORY_CORRECTED");
        Mockito.doThrow(new MlUnavailableException("down")).when(ml).modelInfo();
        mvc.perform(get("/api/v1/admin/ml/status").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.modelVersion").doesNotExist());
    }

    @Test
    void budgetAlertsFireAtExactly80And100Percent() throws Exception {
        JsonNode reg = register("edge-" + UUID.randomUUID() + "@example.com");
        String t = reg.path("accessToken").asText();
        long uid = reg.path("user").path("id").asLong();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        imports.persist(uid, null, List.of(row(1, today.atTime(0, 1), "UPI/SWIGGY/1/Payment", "-800.00"),
                row(2, today.atTime(0, 2), "RENT TRANSFER", "-1000.00")));
        mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Food\",\"monthlyLimit\":1000.00}"))
                .andExpect(jsonPath("$.percent").value(0.8)).andExpect(jsonPath("$.alertLevel").value("WARNING"));
        mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Rent\",\"monthlyLimit\":1000.00}"))
                .andExpect(jsonPath("$.percent").value(1.0)).andExpect(jsonPath("$.alertLevel").value("EXCEEDED"));
        mvc.perform(post("/api/v1/budgets").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Shopping\",\"monthlyLimit\":1000.00}"))
                .andExpect(jsonPath("$.alertLevel").value("OK"));
        mvc.perform(get("/api/v1/budgets/alerts").header("Authorization", bearer(t)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void newUserWithLittleHistoryGetsGracefulForecastAndAnomalyResults() throws Exception {
        JsonNode reg = register("new-" + UUID.randomUUID() + "@example.com");
        String t = reg.path("accessToken").asText();
        mvc.perform(post("/api/v1/anomalies/scan").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scanned").value(0)).andExpect(jsonPath("$.flagged").value(0));
        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(t))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.savingsRate").value(0.0));
        mvc.perform(get("/api/v1/recurring").header("Authorization", bearer(t))).andExpect(jsonPath("$.length()").value(0));
        // a single month of history: categories with <2 months are skipped, the total still forecasts
        long uid = reg.path("user").path("id").asLong();
        LocalDate lastMonth = LocalDate.now(ZoneId.of("Asia/Kolkata")).withDayOfMonth(5).minusMonths(1);
        imports.persist(uid, null, List.of(row(1, lastMonth.atTime(12, 0), "UPI/SWIGGY/1/Payment", "-300")));
        mvc.perform(get("/api/v1/forecast").header("Authorization", bearer(t))).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(0))
                .andExpect(jsonPath("$.total.forecast.length()").value(3));
        long goal = json.readTree(mvc.perform(post("/api/v1/goals").header("Authorization", bearer(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bike\",\"targetAmount\":50000,"
                        + "\"savedAmount\":0,\"targetDate\":\"" + LocalDate.now().plusMonths(5) + "\"}"))
                .andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/v1/goals/" + goal + "/suggestion").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feasible").value(false));
    }

    @Test
    void mlServiceDownGivesGraceful503s() throws Exception {
        Mockito.doThrow(new MlUnavailableException("ML service is unavailable, try again shortly"))
                .when(ml).anomalies(ArgumentMatchers.any());
        mvc.perform(post("/api/v1/anomalies/scan").header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("ML service is unavailable, try again shortly"));
        Mockito.doThrow(new MlUnavailableException("down")).when(ml).train(ArgumentMatchers.any());
        String email = "admin2-" + UUID.randomUUID() + "@example.com";
        auth.createUser(email, "AdminPass1234", "Admin", Role.ADMIN);
        String admin = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"AdminPass1234\"}"))
                .andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        mvc.perform(post("/api/v1/admin/ml/retrain").header("Authorization", bearer(admin)))
                .andExpect(status().isServiceUnavailable());
    }
}
