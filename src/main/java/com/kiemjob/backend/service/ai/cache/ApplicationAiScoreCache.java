package com.kiemjob.backend.service.ai.cache;

import com.kiemjob.backend.dto.application.ApplicationAiScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ nhớ đệm (In-Memory Cache) lưu kết quả chấm điểm AI của hồ sơ ứng tuyển.
 * - Thread-safe, không dùng dependency ngoài (Caffeine/Redis).
 * - TTL mặc định: 24 giờ.
 * - Giới hạn kích thước: tối đa 5,000 mục (tự động loại bỏ mục hết hạn hoặc cũ nhất).
 * - Chỉ cache kết quả chấm thành công (SUCCESS).
 */
@Slf4j
@Component
public class ApplicationAiScoreCache {

    private static final int MAX_ENTRIES = 5000;
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000; // 24 giờ

    private static class CacheEntry {
        final ApplicationAiScoreResponse data;
        final long expiresAt;
        volatile long lastAccessedAt;

        CacheEntry(ApplicationAiScoreResponse data, long ttlMillis) {
            this.data = data;
            long now = System.currentTimeMillis();
            this.expiresAt = now + ttlMillis;
            this.lastAccessedAt = now;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> map = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCounter = new AtomicInteger(0);

    /**
     * Lấy kết quả từ cache theo cacheKey.
     * @return Optional chứa kết quả đã đánh dấu cached=true, hoặc empty nếu không có/hết hạn.
     */
    public Optional<ApplicationAiScoreResponse> get(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }

        CacheEntry entry = map.get(cacheKey);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            map.remove(cacheKey);
            return Optional.empty();
        }

        entry.lastAccessedAt = System.currentTimeMillis();

        // Trả về bản sao và gắn cờ cached=true
        ApplicationAiScoreResponse original = entry.data;
        ApplicationAiScoreResponse copy = ApplicationAiScoreResponse.builder()
                .applicationId(original.getApplicationId())
                .status(original.getStatus())
                .overallScore(original.getOverallScore())
                .skillScore(original.getSkillScore())
                .experienceScore(original.getExperienceScore())
                .educationScore(original.getEducationScore())
                .matchedSkills(original.getMatchedSkills() != null ? new ArrayList<>(original.getMatchedSkills()) : null)
                .missingSkills(original.getMissingSkills() != null ? new ArrayList<>(original.getMissingSkills()) : null)
                .strengths(original.getStrengths() != null ? new ArrayList<>(original.getStrengths()) : null)
                .gaps(original.getGaps() != null ? new ArrayList<>(original.getGaps()) : null)
                .summary(original.getSummary())
                .model(original.getModel())
                .promptVersion(original.getPromptVersion())
                .scoredAt(original.getScoredAt())
                .cached(true)
                .build();

        return Optional.of(copy);
    }

    /**
     * Lưu kết quả vào cache. CHỈ cache kết quả SUCCESS.
     */
    public void put(String cacheKey, ApplicationAiScoreResponse response) {
        if (cacheKey == null || cacheKey.isBlank() || response == null) {
            return;
        }

        // Chỉ cache kết quả thành công, không cache lỗi hay NO_TEXT
        if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
            return;
        }

        // Dọn dẹp nếu dung lượng vượt ngưỡng
        if (map.size() >= MAX_ENTRIES || cleanupCounter.incrementAndGet() % 500 == 0) {
            evictExpiredOrOldest();
        }

        map.put(cacheKey, new CacheEntry(response, TTL_MILLIS));
        log.debug("Đã lưu kết quả chấm điểm AI vào cache với key: {}", cacheKey);
    }

    /**
     * Xóa cache theo key (ví dụ khi cần ép chấm lại).
     */
    public void invalidate(String cacheKey) {
        if (cacheKey != null) {
            map.remove(cacheKey);
        }
    }

    /**
     * Dọn sạch toàn bộ cache (hỗ trợ test).
     */
    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }

    private synchronized void evictExpiredOrOldest() {
        long now = System.currentTimeMillis();
        // 1. Xóa các mục đã hết hạn
        map.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // 2. Nếu vẫn vượt quá MAX_ENTRIES, xóa 20% mục có thời gian truy cập cũ nhất
        if (map.size() >= MAX_ENTRIES) {
            List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccessedAt));

            int toRemove = Math.max(1, map.size() / 5);
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                map.remove(entries.get(i).getKey());
            }
            log.info("Đã dọn dẹp {} mục cũ khỏi ApplicationAiScoreCache. Kích thước hiện tại: {}", toRemove, map.size());
        }
    }
}

