package com.kiemjob.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ giới hạn tần suất gọi AI (AI Rate Limiter) theo định danh người dùng (User ID / Email / IP).
 * Áp dụng thuật toán Fixed Window Counter có thể cấu hình thời gian cửa sổ và số lượt gọi qua application.properties.
 */
@Slf4j
@Component
public class AiRateLimiter {

    @Value("${app.ai.rate-limit.max-requests-per-minute:10}")
    private int maxRequestsPerMinute;

    @Value("${app.ai.rate-limit.window-seconds:60}")
    private int windowSeconds;

    private final ConcurrentMap<String, RequestCounter> userCounts = new ConcurrentHashMap<>();

    private record RequestCounter(long windowStartSecond, AtomicInteger count) {}

    public AiRateLimiter() {
    }

    // Constructor phục vụ test hoặc custom cấu hình
    public AiRateLimiter(int maxRequestsPerMinute, int windowSeconds) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Thử lấy quyền gọi AI cho user. Trả về true nếu hợp lệ, false nếu vượt ngưỡng.
     */
    public boolean tryAcquire(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isBlank()) {
            userIdentifier = "anonymous";
        }

        long nowSecond = Instant.now().getEpochSecond();
        long windowSize = Math.max(1, windowSeconds);
        long currentWindow = nowSecond / windowSize;

        String key = userIdentifier.trim().toLowerCase();
        RequestCounter counter = userCounts.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStartSecond() != currentWindow) {
                return new RequestCounter(currentWindow, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        boolean allowed = counter.count().get() <= maxRequestsPerMinute;
        if (!allowed) {
            log.warn("AI Rate limit exceeded for user/identifier: {} (Count: {}/{} in window {}s)",
                    userIdentifier, counter.count().get(), maxRequestsPerMinute, windowSize);
        }

        // Định kỳ dọn dẹp các window cũ để chống rò rỉ bộ nhớ
        if (userCounts.size() > 5000) {
            userCounts.entrySet().removeIf(e -> e.getValue().windowStartSecond() < currentWindow);
        }

        return allowed;
    }

    /**
     * Kiểm tra giới hạn tần suất. Nếu vượt quá, ném ResponseStatusException HTTP 429.
     */
    public void checkRateLimit(String userIdentifier) {
        if (!tryAcquire(userIdentifier)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    String.format("Bạn đã vượt quá giới hạn yêu cầu AI (%d lượt/%d giây). Vui lòng thử lại sau.",
                            maxRequestsPerMinute, windowSeconds)
            );
        }
    }

    /**
     * Reset bộ đếm của user (dùng khi test hoặc admin can thiệp).
     */
    public void reset(String userIdentifier) {
        if (userIdentifier != null) {
            userCounts.remove(userIdentifier.trim().toLowerCase());
        }
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }
}

