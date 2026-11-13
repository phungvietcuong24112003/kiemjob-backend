package com.kiemjob.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ lọc giới hạn tần suất gọi (Rate Limiting) cho các endpoint nhạy cảm (/api/auth/*) và AI (/api/ai/*).
 * - Auth endpoints: Giới hạn theo IP client (15 req/phút).
 * - AI endpoints: Giới hạn theo User ID / Email của người dùng (cấu hình qua app.ai.rate-limit.*).
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/register/candidate",
            "/api/auth/register/recruiter",
            "/api/auth/verify-email",
            "/api/auth/resend-otp",
            "/api/auth/forgot-password",
            "/api/auth/verify-reset-otp",
            "/api/auth/reset-password",
            "/api/auth/recruiter/send-otp",
            "/api/auth/recruiter/login-otp"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    private final AiRateLimiter aiRateLimiter;

    private record RequestCounter(long windowStartSecond, AtomicInteger count) {}

    public RateLimitingFilter() {
        this.aiRateLimiter = new AiRateLimiter();
    }

    @Autowired
    public RateLimitingFilter(AiRateLimiter aiRateLimiter) {
        this.aiRateLimiter = aiRateLimiter != null ? aiRateLimiter : new AiRateLimiter();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Giới hạn tần suất cho các endpoint AI theo User
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && isAiPath(path)) {
            String userKey = extractUserIdentifier(request);
            if (!aiRateLimiter.tryAcquire(userKey)) {
                log.warn("AI Rate limit exceeded for user: {} on path: {}", userKey, path);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                response.setHeader("Retry-After", String.valueOf(aiRateLimiter.getWindowSeconds()));

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
                body.put("error", "Too Many Requests");
                body.put("message", String.format("Bạn đã vượt quá giới hạn yêu cầu AI (%d lượt/%d giây). Vui lòng thử lại sau.",
                        aiRateLimiter.getMaxRequestsPerMinute(), aiRateLimiter.getWindowSeconds()));

                objectMapper.writeValue(response.getOutputStream(), body);
                return;
            }
        }

        // 2. Giới hạn các endpoint auth nhạy cảm theo Client IP
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && isRateLimitedPath(path)) {
            String clientIp = extractClientIp(request);
            long currentMinute = Instant.now().getEpochSecond() / 60;

            RequestCounter counter = requestCounts.compute(clientIp, (key, existing) -> {
                if (existing == null || existing.windowStartSecond() != currentMinute) {
                    return new RequestCounter(currentMinute, new AtomicInteger(1));
                }
                existing.count().incrementAndGet();
                return existing;
            });

            if (counter.count().get() > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                response.setHeader("Retry-After", "60");

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("timestamp", Instant.now().toString());
                body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
                body.put("error", "Too Many Requests");
                body.put("message", "Quá nhiều yêu cầu. Vui lòng thử lại sau 1 phút.");

                objectMapper.writeValue(response.getOutputStream(), body);
                return;
            }

            // Dọn dẹp cache định kỳ nếu quá lớn
            if (requestCounts.size() > 5000) {
                requestCounts.entrySet().removeIf(e -> e.getValue().windowStartSecond() < currentMinute);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAiPath(String uri) {
        if (uri == null) return false;
        return uri.startsWith("/api/ai/")
                || uri.startsWith("/api/cv-analysis")
                || uri.startsWith("/api/job-moderations/check");
    }

    private String extractUserIdentifier(HttpServletRequest request) {
        if (request.getUserPrincipal() != null && request.getUserPrincipal().getName() != null) {
            return request.getUserPrincipal().getName();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return extractClientIp(request);
    }

    private boolean isRateLimitedPath(String uri) {
        if (uri == null) return false;
        return RATE_LIMITED_PATHS.contains(uri) || (uri.startsWith("/api/auth/") && !uri.equals("/api/auth/me"));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

