package com.finpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finpilot.config.AppProperties;
import com.finpilot.user.Role;
import com.finpilot.user.User;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SecurityPrimitivesTest {
    public static AppProperties props(String key) {
        return new AppProperties("Asia/Kolkata",
                new AppProperties.Jwt("unit-secret-unit-secret-unit-secret-0123", "finpilot", Duration.ofMinutes(15),
                        Duration.ofDays(14)),
                new AppProperties.Encryption(key),
                new AppProperties.Ml("http://ml", "t", Duration.ofSeconds(5)),
                new AppProperties.Ai("fake", null, null, null, Duration.ofSeconds(5)),
                new AppProperties.ReadonlyDb("jdbc:postgresql://x/y", "ro", "pw"),
                new AppProperties.RateLimit("memory", 5, 100),
                new AppProperties.Cors(List.of()),
                new AppProperties.Retrain(5, "-"),
                new AppProperties.Seed(false));
    }

    static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void totpMatchesRfc6238Vector() {
        // RFC 6238 SHA-1 test secret "12345678901234567890" at T=59s -> 94287082 (8 digits) -> 287082 (6 digits)
        String secret = Totp.base32Encode("12345678901234567890".getBytes());
        assertThat(Totp.codeAt(secret, 59)).isEqualTo("287082");
        assertThat(Totp.codeAt(secret, 1111111109)).isEqualTo("081804");
        Clock clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        assertThat(Totp.verify(secret, "287082", clock)).isTrue();
        assertThat(Totp.verify(secret, "000000", clock)).isFalse();
        assertThat(Totp.verify(secret, "abc", clock)).isFalse();
        assertThat(Totp.verify(null, "123456", clock)).isFalse();
    }

    @Test
    void totpSecretRoundTripAndUri() {
        String s = Totp.newSecret();
        assertThat(s).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32Decode(s)).hasSize(20);
        assertThat(Totp.otpauthUri("FinPilot", "a@b.c", s)).startsWith("otpauth://totp/FinPilot%3Aa%40b.c?secret=" + s);
        assertThatThrownBy(() -> Totp.base32Decode("!!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aesGcmRoundTripIsRandomised() {
        CryptoService crypto = new CryptoService(props(KEY));
        String a = crypto.encrypt("secret note");
        String b = crypto.encrypt("secret note");
        assertThat(a).startsWith("v1:").isNotEqualTo(b);
        assertThat(crypto.decrypt(a)).isEqualTo("secret note");
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThatThrownBy(() -> crypto.decrypt("plain")).isInstanceOf(IllegalStateException.class);
        String tampered = a.substring(0, a.length() - 4) + "AAAA";
        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoService(props("c2hvcnQ="))).isInstanceOf(IllegalStateException.class);
        EncryptedStringConverter conv = new EncryptedStringConverter(crypto);
        assertThat(conv.convertToEntityAttribute(conv.convertToDatabaseColumn("x"))).isEqualTo("x");
    }

    @Test
    void jwtIssueParseAndTypeSeparation() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService jwt = new JwtService(props(KEY), clock);
        User u = new User("a@b.c", "h", "A", Role.ADMIN);
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(u, 42L);
        String access = jwt.issueAccessToken(u);
        assertThat(jwt.parseAccessToken(access)).hasValueSatisfying(p -> {
            assertThat(p.id()).isEqualTo(42L);
            assertThat(p.role()).isEqualTo(Role.ADMIN);
        });
        assertThat(jwt.parseMfaToken(access)).isEmpty();
        assertThat(jwt.parseAccessToken(jwt.issueMfaToken(u))).isEmpty();
        assertThat(jwt.parseAccessToken("garbage")).isEmpty();
        JwtService later = new JwtService(props(KEY), Clock.offset(clock, Duration.ofHours(1)));
        assertThat(later.parseAccessToken(access)).isEmpty();
        assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
    }
}
