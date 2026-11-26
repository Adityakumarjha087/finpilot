package com.finpilot.security;

import com.finpilot.config.AppProperties;
import com.finpilot.user.Role;
import com.finpilot.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies short-lived HS256 access tokens and 5-minute MFA challenge tokens. */
@Service
public class JwtService {
    static final String TYPE = "typ";
    private static final Duration MFA_TTL = Duration.ofMinutes(5);
    private final SecretKey key;
    private final AppProperties.Jwt cfg;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        this.cfg = props.jwt();
        this.clock = clock;
        byte[] secret = cfg.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(User user) {
        return build(user, "access", cfg.accessTtl());
    }

    public String issueMfaToken(User user) {
        return build(user, "mfa", MFA_TTL);
    }

    public long accessTtlSeconds() {
        return cfg.accessTtl().toSeconds();
    }

    private String build(User user, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(cfg.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim(TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        return parse(token, "access");
    }

    public Optional<AuthenticatedUser> parseMfaToken(String token) {
        return parse(token, "mfa");
    }

    private Optional<AuthenticatedUser> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(cfg.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(Long.parseLong(claims.getSubject()),
                    claims.get("email", String.class), Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
