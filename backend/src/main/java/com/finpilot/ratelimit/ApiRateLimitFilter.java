package com.finpilot.ratelimit;

import com.finpilot.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-user (or per-IP for anonymous callers) request budget for the whole /api surface. */
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final int perMinute;

    public ApiRateLimitFilter(RateLimiter limiter, int perMinute) {
        this.limiter = limiter;
        this.perMinute = perMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String key = auth != null && auth.getPrincipal() instanceof AuthenticatedUser u
                ? "api:u:" + u.id() : "api:ip:" + request.getRemoteAddr();
        RateLimiter.Decision d = limiter.hit(key, perMinute, Duration.ofMinutes(1));
        if (!d.allowed()) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(d.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"https://finpilot.dev/problems/429\",\"title\":\"Too Many Requests\","
                    + "\"status\":429,\"detail\":\"Rate limit exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
