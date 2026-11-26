package com.finpilot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class ContextSmokeIntegrationTest extends IntegrationTest {
    @Test
    void healthAndOpenApiArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FinPilot API"));
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
