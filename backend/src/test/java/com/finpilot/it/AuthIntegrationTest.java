package com.finpilot.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.finpilot.audit.AuditRepository;
import com.finpilot.security.Totp;
import com.finpilot.support.IntegrationTest;
import com.finpilot.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthIntegrationTest extends IntegrationTest {
    @Autowired
    UserRepository users;
    @Autowired
    AuditRepository audit;
    @Autowired
    JdbcTemplate jdbc;

    private String email() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode login(String email, String password, int expected) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                        .with(r -> { r.setRemoteAddr("10.0.0." + Math.abs(email.hashCode() % 200)); return r; }))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void registerLoginMeRefreshRotationAndLogout() throws Exception {
        String email = email();
        JsonNode reg = register(email);
        assertThat(reg.path("mfaRequired").asBoolean()).isFalse();
        assertThat(reg.path("expiresIn").asLong()).isEqualTo(900);
        JsonNode login = login(email, "Password1234", 200);
        String access = login.path("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(access)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.currency").value("INR"));

        String refresh = login.path("refreshToken").asText();
        String rotated = json.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(refresh);
        // replaying the old token is detected and revokes the family, including the rotated token
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotated + "\"}")).andExpect(status().isUnauthorized());

        JsonNode again = login(email, "Password1234", 200);
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + again.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + again.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(audit.findByAction("REGISTER")).isNotEmpty();
    }

    @Test
    void registrationValidationAndDuplicates() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        String email = email();
        register(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Password1234\",\"displayName\":\"x\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordLocksAccountAndLoginIsRateLimited() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 5; i++) {
            login(email, "WrongPassword1", 401);
        }
        // 6th attempt from same IP+email within a minute is rate limited
        JsonNode limited = login(email, "Password1234", 429);
        assertThat(limited.path("status").asInt()).isEqualTo(429);
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getLockedUntil()).isAfter(Instant.now());
        login("missing-" + email, "Password1234", 401);
    }

    @Test
    void lockedAccountReturns423() throws Exception {
        String email = email();
        register(email);
        jdbc.update("UPDATE users SET locked_until = now() + interval '10 minutes' WHERE email = ?", email);
        login(email, "Password1234", 423);
    }

    @Test
    void totpTwoFactorFlow() throws Exception {
        String email = email();
        String access = register(email).path("accessToken").asText();
        JsonNode setup = json.readTree(mvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", bearer(access)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String secret = setup.path("secret").asText();
        assertThat(setup.path("otpauthUri").asText()).startsWith("otpauth://totp/");
        // secret is encrypted at rest
        String stored = jdbc.queryForObject("SELECT mfa_secret_enc FROM users WHERE email = ?", String.class, email);
        assertThat(stored).startsWith("v1:").doesNotContain(secret);

        mvc.perform(post("/api/v1/auth/mfa/enable").header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
        String enableCode = code(secret);
        mvc.perform(post("/api/v1/auth/mfa/enable").header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + enableCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(true));
        mvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", bearer(access)))
                .andExpect(status().isConflict());

        JsonNode challenge = login(email, "Password1234", 200);
        assertThat(challenge.path("mfaRequired").asBoolean()).isTrue();
        assertThat(challenge.has("accessToken")).isFalse();
        String mfaToken = challenge.path("mfaToken").asText();
        // an MFA token is not an access token
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(mfaToken))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"000000\"}")).andExpect(status().isUnauthorized());
        // expired: a code from three steps (90 s) ago is outside the +/-1 step drift window
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + codeAt(secret, -90) + "\"}"))
                .andExpect(status().isUnauthorized());
        // replay: the code already used to enable 2FA (same time step) cannot be used again
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + enableCode + "\"}"))
                .andExpect(status().isUnauthorized());
        String next = codeAt(secret, 30);
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + next + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        // and that code can't be replayed for a second login either
        String mfaToken2 = login(email, "Password1234", 200).path("mfaToken").asText();
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken2 + "\",\"code\":\"" + next + "\"}"))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET mfa_last_counter = mfa_last_counter - 5 WHERE email = ?", email);
        mvc.perform(post("/api/v1/auth/mfa/disable").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code(secret) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(false));
        mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"bogus\",\"code\":\"123456\"}")).andExpect(status().isUnauthorized());
    }

    private static String codeAt(String secret, int offsetSeconds) {
        return Totp.codeAt(secret, Instant.now().getEpochSecond() + offsetSeconds);
    }

    private static String code(String secret) {
        return Totp.codeAt(secret, Instant.now().getEpochSecond());
    }

    @Test
    void rbacAndSecurityHeaders() throws Exception {
        String token = registerAndLogin();
        mvc.perform(get("/api/v1/admin/ml/status").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/nope").header("Authorization", bearer(token))).andExpect(status().isNotFound());
    }
}
