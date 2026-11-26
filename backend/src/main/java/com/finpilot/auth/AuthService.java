package com.finpilot.auth;

import com.finpilot.audit.AuditService;
import com.finpilot.auth.AuthDtos.LoginResponse;
import com.finpilot.auth.AuthDtos.MfaSetupResponse;
import com.finpilot.auth.AuthDtos.UserDto;
import com.finpilot.common.ApiException;
import com.finpilot.common.RateLimitedException;
import com.finpilot.config.AppProperties;
import com.finpilot.ratelimit.RateLimiter;
import com.finpilot.security.JwtService;
import com.finpilot.security.Totp;
import com.finpilot.user.Role;
import com.finpilot.user.User;
import com.finpilot.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter limiter;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, RateLimiter limiter, AuditService audit, AppProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limiter = limiter;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equaliser-" + random.nextLong());
    }

    @Transactional
    public LoginResponse register(AuthDtos.RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(email, encoder.encode(req.password()), req.displayName().trim(), Role.USER));
        audit.record(user.getId(), email, "REGISTER", "USER", user.getId(), null);
        return issueTokens(user);
    }

    public User createUser(String email, String password, String displayName, Role role) {
        return users.save(new User(email.toLowerCase(Locale.ROOT), encoder.encode(password), displayName, role));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(AuthDtos.LoginRequest req, String ip) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String rlKey = "login:" + ip + ":" + email;
        RateLimiter.Decision d = limiter.hit(rlKey, props.rateLimit().loginPerMinute(), Duration.ofMinutes(1));
        if (!d.allowed()) {
            audit.record(null, email, "LOGIN_RATE_LIMITED", "USER", null, "ip=" + ip);
            throw new RateLimitedException(d.retryAfterSeconds());
        }
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash); // equalise timing to avoid user enumeration
            audit.record(null, email, "LOGIN_FAILED", "USER", null, "unknown email");
            throw ApiException.unauthorized("Invalid email or password");
        }
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            audit.record(user.getId(), email, "LOGIN_LOCKED", "USER", user.getId(), null);
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked after repeated failed logins");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            int failed = user.getFailedLogins() + 1;
            user.setFailedLogins(failed);
            if (failed >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(now.plus(LOCKOUT));
                user.setFailedLogins(0);
            }
            users.save(user);
            audit.record(user.getId(), email, "LOGIN_FAILED", "USER", user.getId(), "failed=" + failed);
            throw ApiException.unauthorized("Invalid email or password");
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        users.save(user);
        limiter.reset(rlKey);
        if (user.isMfaEnabled()) {
            audit.record(user.getId(), email, "LOGIN_MFA_CHALLENGE", "USER", user.getId(), null);
            return LoginResponse.mfa(jwt.issueMfaToken(user));
        }
        audit.record(user.getId(), email, "LOGIN_SUCCESS", "USER", user.getId(), null);
        return issueTokens(user);
    }

    @Transactional
    public LoginResponse loginMfa(AuthDtos.MfaLoginRequest req, String ip) {
        var principal = jwt.parseMfaToken(req.mfaToken())
                .orElseThrow(() -> ApiException.unauthorized("MFA session expired, sign in again"));
        RateLimiter.Decision d = limiter.hit("mfa:" + principal.id(), props.rateLimit().loginPerMinute(),
                Duration.ofMinutes(1));
        if (!d.allowed()) {
            throw new RateLimitedException(d.retryAfterSeconds());
        }
        User user = users.findById(principal.id()).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        if (!user.isMfaEnabled() || !acceptTotp(user, req.code())) {
            audit.record(user.getId(), user.getEmail(), "LOGIN_MFA_FAILED", "USER", user.getId(), "ip=" + ip);
            throw ApiException.unauthorized("Invalid authentication code");
        }
        users.save(user);
        audit.record(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "USER", user.getId(), "mfa");
        return issueTokens(user);
    }

    /** Rotates the refresh token; re-use of a revoked token revokes the whole family (theft detection). */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        Instant now = clock.instant();
        if (token.getRevokedAt() != null) {
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            audit.record(token.getUserId(), null, "REFRESH_TOKEN_REUSE", "USER", token.getUserId(), null);
            throw ApiException.unauthorized("Refresh token reuse detected; all sessions revoked");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthorized("Refresh token expired");
        }
        token.revoke(now);
        refreshTokens.save(token);
        User user = users.findById(token.getUserId()).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            t.revoke(clock.instant());
            refreshTokens.save(t);
            audit.record(t.getUserId(), null, "LOGOUT", "USER", t.getUserId(), null);
        });
    }

    @Transactional(readOnly = true)
    public UserDto me(long userId) {
        return UserDto.of(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
    }

    @Transactional
    public MfaSetupResponse setupMfa(long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.isMfaEnabled()) {
            throw ApiException.conflict("Two-factor authentication is already enabled");
        }
        String secret = Totp.newSecret();
        user.setMfaSecret(secret);
        user.setMfaLastCounter(null);
        users.save(user);
        return new MfaSetupResponse(secret, Totp.otpauthUri("FinPilot", user.getEmail(), secret));
    }

    @Transactional
    public UserDto enableMfa(long userId, String code) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getMfaSecret() == null) {
            throw ApiException.badRequest("Call /auth/mfa/setup first");
        }
        if (!acceptTotp(user, code)) {
            throw ApiException.badRequest("Invalid authentication code");
        }
        user.setMfaEnabled(true);
        users.save(user);
        audit.record(userId, user.getEmail(), "MFA_ENABLED", "USER", userId, null);
        return UserDto.of(user);
    }

    @Transactional
    public UserDto disableMfa(long userId, String code) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.isMfaEnabled() || !acceptTotp(user, code)) {
            throw ApiException.badRequest("Invalid authentication code");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaLastCounter(null);
        users.save(user);
        audit.record(userId, user.getEmail(), "MFA_DISABLED", "USER", userId, null);
        return UserDto.of(user);
    }

    /** Accepts a TOTP code at most once: its time step must be newer than the last accepted one. */
    private boolean acceptTotp(User user, String code) {
        var counter = Totp.match(user.getMfaSecret(), code, clock);
        if (counter.isEmpty()) {
            return false;
        }
        Long last = user.getMfaLastCounter();
        if (last != null && counter.getAsLong() <= last) {
            return false;
        }
        user.setMfaLastCounter(counter.getAsLong());
        return true;
    }

    private LoginResponse issueTokens(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), hash(raw), clock.instant().plus(props.jwt().refreshTtl())));
        return LoginResponse.tokens(jwt.issueAccessToken(user), raw, jwt.accessTtlSeconds(), UserDto.of(user));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
