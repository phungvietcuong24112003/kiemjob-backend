package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse.CvSearchResult;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse.SearchFilters;
import com.kiemjob.backend.service.ai.GroqClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service tìm kiếm CV 3 tầng cho nhà tuyển dụng:
 * Tầng 1 – AI hiểu câu hỏi tự nhiên → bộ lọc JSON
 * Tầng 2 – SQL lọc JSONB trên cv_analysis.parsed_data
 * Tầng 3 – AI xếp hạng ứng viên theo mức phù hợp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvSearchService {

    private static final Set<String> VALID_EDUCATION_LEVELS = Set.of(
            "dai_hoc", "cao_dang", "thac_si", "tien_si", "khac"
    );
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 phút
    private static final int MAX_SQL_CANDIDATES = 30;
    private static final int MIN_RESULTS_THRESHOLD = 5;

    private final GroqClient groqClient;
    private final PromptTemplates promptTemplates;
    private final JsonResponseParser jsonResponseParser;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    // Cache đơn giản: key = SHA-256(normalizedQuery + "|" + size), value = CacheEntry
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(CvSearchResponse response, long timestamp) {}

    /**
     * Phương thức chính: tìm kiếm CV theo câu hỏi tự nhiên.
     */
    public CvSearchResponse search(String query, int size) {
        long startTime = System.currentTimeMillis();

        // 1. Chuẩn hóa query
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            return CvSearchResponse.builder()
                    .parsedFilters(SearchFilters.builder().build())
                    .results(List.of())
                    .totalFound(0)
                    .searchTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // 2. Kiểm tra cache
        String cacheKey = computeCacheKey(normalizedQuery, size);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            log.info("CV Search cache hit (query length: {})", normalizedQuery.length());
            return cached.response;
        }

        // 3. Tầng 1: AI hiểu câu hỏi
        SearchFilters filters = parseQuery(normalizedQuery);

        // 4. Tầng 2: SQL lọc JSONB
        List<CandidateProfile> candidates = filterCandidates(filters);
        int totalFound = candidates.size();

        CvSearchResponse response;
        if (candidates.isEmpty()) {
            // Không có kết quả → trả rỗng, KHÔNG gọi AI rank
            response = CvSearchResponse.builder()
                    .parsedFilters(filters)
                    .results(List.of())
                    .totalFound(0)
                    .searchTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } else {
            // 5. Tầng 3: AI xếp hạng
            List<CvSearchResult> results = rankCandidates(normalizedQuery, filters, candidates, size);
            response = CvSearchResponse.builder()
                    .parsedFilters(filters)
                    .results(results)
                    .totalFound(totalFound)
                    .searchTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // 6. Lưu cache
        cache.put(cacheKey, new CacheEntry(response, System.currentTimeMillis()));
        log.info("CV Search completed: query length={}, totalFound={}, returned={}, time={}ms",
                normalizedQuery.length(), totalFound, response.getResults().size(),
                response.getSearchTimeMs());

        return response;
    }

    // ============================================================
    // TẦNG 1: Phân tích câu hỏi bằng AI
    // ============================================================

    SearchFilters parseQuery(String query) {
        try {
            String systemPrompt = promptTemplates.render("cv-search-query-system", Map.of());
            String userPrompt = promptTemplates.render("cv-search-query-user", Map.of("query", query));

            String rawJson = groqClient.generateJson(systemPrompt, userPrompt);
            String cleanJson = jsonResponseParser.cleanJson(rawJson);
            JsonNode rootNode = objectMapper.readTree(cleanJson);

            return validateAndBuildFilters(rootNode);
        } catch (Exception ex) {
            log.warn("Tầng 1 - AI phân tích query thất bại, fallback tách từ khóa: {}", ex.getMessage());
            return fallbackParseQuery(query);
        }
    }

    private SearchFilters validateAndBuildFilters(JsonNode node) {
        SearchFilters.SearchFiltersBuilder builder = SearchFilters.builder();

        // industry
        String industry = getTextOrNull(node, "industry");
        if (industry != null) {
            industry = truncate(industry, 100);
        }
        builder.industry(industry);

        // titles (max 10)
        builder.titles(getStringList(node, "titles", 10, 100));

        // skills (lowercase, max 20)
        List<String> skills = getStringList(node, "skills", 20, 50).stream()
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        builder.skills(skills);

        // minYears / maxYears (clamp 0-50)
        builder.minYears(clampDouble(getDoubleOrNull(node, "minYears"), 0.0, 50.0));
        builder.maxYears(clampDouble(getDoubleOrNull(node, "maxYears"), 0.0, 50.0));

        // educationLevel
        String eduLevel = getTextOrNull(node, "educationLevel");
        if (eduLevel != null) {
            eduLevel = eduLevel.trim().toLowerCase();
            if (!VALID_EDUCATION_LEVELS.contains(eduLevel)) {
                eduLevel = null;
            }
        }
        builder.educationLevel(eduLevel);

        // location
        String location = getTextOrNull(node, "location");
        if (location != null) {
            location = truncate(location.trim(), 100);
        }
        builder.location(location);

        // keywords (max 10)
        builder.keywords(getStringList(node, "keywords", 10, 100).stream()
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList()));

        return builder.build();
    }

    /**
     * Fallback khi AI lỗi: tách query thành từ khóa đơn giản.
     */
    SearchFilters fallbackParseQuery(String query) {
        // Bỏ stopwords tiếng Việt cơ bản
        Set<String> stopwords = Set.of(
                "và", "hoặc", "ở", "tại", "của", "cho", "với", "có", "là",
                "được", "để", "này", "đó", "từ", "trong", "trên", "các",
                "một", "những", "không", "cần", "biết", "năm", "kinh", "nghiệm"
        );

        List<String> keywords = Arrays.stream(query.toLowerCase().split("[\\s,;.]+"))
                .map(String::trim)
                .filter(w -> !w.isBlank() && w.length() >= 2 && !stopwords.contains(w))
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        return SearchFilters.builder()
                .keywords(keywords)
                .skills(List.of())
                .titles(List.of())
                .build();
    }

    // ============================================================
    // TẦNG 2: Lọc SQL trên JSONB
    // ============================================================

    /**
     * DTO nội bộ chứa thông tin profile từ native query
     */
    record CandidateProfile(
            Long cvId,
            Long candidateId,
            String fullName,
            String headline,
            String avatarUrl,
            String province,
            String parsedDataJson,
            String summary,
            BigDecimal qualityScore
    ) {}

    List<CandidateProfile> filterCandidates(SearchFilters filters) {
        // Thử lọc chặt trước, nới lỏng dần nếu ít kết quả
        List<CandidateProfile> results = executeFilter(filters, true, true);

        if (results.size() < MIN_RESULTS_THRESHOLD && filters.getLocation() != null) {
            // Nới lỏng: bỏ location
            SearchFilters relaxed1 = SearchFilters.builder()
                    .industry(filters.getIndustry())
                    .titles(filters.getTitles())
                    .skills(filters.getSkills())
                    .minYears(filters.getMinYears())
                    .maxYears(filters.getMaxYears())
                    .educationLevel(filters.getEducationLevel())
                    .location(null) // bỏ location
                    .keywords(filters.getKeywords())
                    .build();
            results = executeFilter(relaxed1, true, false);
        }

        if (results.size() < MIN_RESULTS_THRESHOLD
                && (filters.getMinYears() != null || filters.getMaxYears() != null)) {
            // Nới lỏng thêm: bỏ cả years
            SearchFilters relaxed2 = SearchFilters.builder()
                    .industry(filters.getIndustry())
                    .titles(filters.getTitles())
                    .skills(filters.getSkills())
                    .minYears(null) // bỏ minYears
                    .maxYears(null) // bỏ maxYears
                    .educationLevel(filters.getEducationLevel())
                    .location(null) // đã bỏ location
                    .keywords(filters.getKeywords())
                    .build();
            results = executeFilter(relaxed2, false, false);
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private List<CandidateProfile> executeFilter(SearchFilters filters, boolean useYears, boolean useLocation) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ca.cv_id, c.id as candidate_id, c.fullname, c.headline, c.avatarurl, ");
        sql.append("p.name as province_name, ");
        sql.append("CAST(ca.parsed_data AS text) as parsed_data_json, ");
        sql.append("ca.summary, ca.quality_score ");
        sql.append("FROM cv_analysis ca ");
        sql.append("JOIN cvs cv ON cv.id = ca.cv_id ");
        sql.append("JOIN candidates c ON c.id = cv.candidate_id ");
        sql.append("LEFT JOIN provinces p ON p.id = c.province_id ");
        sql.append("WHERE cv.is_public = true ");
        sql.append("AND ca.analysis_status = 'completed' ");
        sql.append("AND ca.parsed_data IS NOT NULL ");

        Map<String, Object> params = new HashMap<>();
        int paramIdx = 0;

        // Filter by minYears
        if (useYears && filters.getMinYears() != null) {
            String param = "minYears" + (paramIdx++);
            sql.append("AND (ca.parsed_data->'searchProfile'->>'yearsExperience')::numeric >= :")
               .append(param).append(" ");
            params.put(param, filters.getMinYears());
        }

        // Filter by maxYears
        if (useYears && filters.getMaxYears() != null) {
            String param = "maxYears" + (paramIdx++);
            sql.append("AND (ca.parsed_data->'searchProfile'->>'yearsExperience')::numeric <= :")
               .append(param).append(" ");
            params.put(param, filters.getMaxYears());
        }

        // Filter by location (trên searchProfile.location HOẶC provinces.name)
        if (useLocation && filters.getLocation() != null && !filters.getLocation().isBlank()) {
            String param = "location" + (paramIdx++);
            sql.append("AND (LOWER(ca.parsed_data->'searchProfile'->>'location') LIKE LOWER(:")
               .append(param).append(") ")
               .append("OR LOWER(p.name) LIKE LOWER(:").append(param).append(")) ");
            params.put(param, "%" + filters.getLocation().trim() + "%");
        }

        // Filter by industry
        if (filters.getIndustry() != null && !filters.getIndustry().isBlank()) {
            String param = "industry" + (paramIdx++);
            sql.append("AND LOWER(ca.parsed_data->'searchProfile'->>'industry') LIKE LOWER(:")
               .append(param).append(") ");
            params.put(param, "%" + filters.getIndustry().trim() + "%");
        }

        // Filter by skills: dùng unnest + ANY (tránh ?| xung đột JDBC)
        if (filters.getSkills() != null && !filters.getSkills().isEmpty()) {
            List<String> lowerSkills = filters.getSkills().stream()
                    .map(String::toLowerCase)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .limit(20)
                    .toList();
            if (!lowerSkills.isEmpty()) {
                String param = "skills" + (paramIdx++);
                sql.append("AND EXISTS (")
                   .append("SELECT 1 FROM jsonb_array_elements_text(")
                   .append("ca.parsed_data->'searchProfile'->'skills'")
                   .append(") AS skill WHERE skill = ANY(:").append(param).append(")")
                   .append(") ");
                params.put(param, lowerSkills.toArray(new String[0]));
            }
        }

        // Filter by titles
        if (filters.getTitles() != null && !filters.getTitles().isEmpty()) {
            List<String> titles = filters.getTitles().stream()
                    .filter(t -> t != null && !t.isBlank())
                    .limit(10)
                    .toList();
            if (!titles.isEmpty()) {
                StringBuilder titleCondition = new StringBuilder("AND (");
                for (int i = 0; i < titles.size(); i++) {
                    if (i > 0) titleCondition.append(" OR ");
                    String param = "title" + (paramIdx++);
                    titleCondition.append("EXISTS (")
                            .append("SELECT 1 FROM jsonb_array_elements_text(")
                            .append("ca.parsed_data->'searchProfile'->'jobTitles'")
                            .append(") AS jt WHERE LOWER(jt) LIKE LOWER(:").append(param).append(")")
                            .append(")");
                    params.put(param, "%" + titles.get(i).trim() + "%");
                }
                titleCondition.append(") ");
                sql.append(titleCondition);
            }
        }

        sql.append("ORDER BY ca.quality_score DESC NULLS LAST ");
        sql.append("LIMIT :maxLimit");
        params.put("maxLimit", MAX_SQL_CANDIDATES);

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            nativeQuery.setParameter(entry.getKey(), entry.getValue());
        }

        List<Object[]> rows = nativeQuery.getResultList();
        return rows.stream()
                .map(row -> new CandidateProfile(
                        toLong(row[0]),              // cv_id
                        toLong(row[1]),              // candidate_id
                        toStr(row[2]),               // fullname
                        toStr(row[3]),               // headline
                        toStr(row[4]),               // avatarurl
                        toStr(row[5]),               // province_name
                        toStr(row[6]),               // parsed_data_json
                        toStr(row[7]),               // summary
                        toBigDecimal(row[8])         // quality_score
                ))
                .toList();
    }

    // ============================================================
    // TẦNG 3: AI xếp hạng ứng viên
    // ============================================================

    List<CvSearchResult> rankCandidates(
            String query,
            SearchFilters filters,
            List<CandidateProfile> candidates,
            int size
    ) {
        try {
            // Tạo danh sách profile tóm tắt cho AI (KHÔNG gửi toàn bộ CV)
            String candidatesJson = buildCandidatesSummaryForAi(candidates);
            String filtersJson = objectMapper.writeValueAsString(filters);

            String systemPrompt = promptTemplates.render("cv-search-rank-system",
                    Map.of("maxResults", String.valueOf(size)));
            String userPrompt = promptTemplates.render("cv-search-rank-user", Map.of(
                    "query", query,
                    "filters", filtersJson,
                    "candidates", candidatesJson
            ));

            String rawJson = groqClient.generateJson(systemPrompt, userPrompt);
            String cleanJson = jsonResponseParser.cleanJson(rawJson);
            JsonNode rootNode = objectMapper.readTree(cleanJson);

            return parseAndValidateRankings(rootNode, candidates, size);
        } catch (Exception ex) {
            log.warn("Tầng 3 - AI xếp hạng thất bại, fallback sắp xếp theo qualityScore: {}", ex.getMessage());
            return fallbackRanking(candidates, size);
        }
    }

    private String buildCandidatesSummaryForAi(List<CandidateProfile> candidates) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < candidates.size(); i++) {
            CandidateProfile cp = candidates.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"cvId\":").append(cp.cvId);

            try {
                JsonNode parsedData = objectMapper.readTree(cp.parsedDataJson);
                JsonNode sp = parsedData.path("searchProfile");
                if (sp.isObject()) {
                    sb.append(",\"skills\":").append(sp.path("skills").toString());
                    sb.append(",\"jobTitles\":").append(sp.path("jobTitles").toString());
                    appendJsonField(sb, "yearsExperience", sp.path("yearsExperience"));
                    appendJsonField(sb, "seniority", sp.path("seniority"));
                    appendJsonField(sb, "educationLevel", sp.path("educationLevel"));
                    appendJsonField(sb, "location", sp.path("location"));
                    appendJsonField(sb, "industry", sp.path("industry"));
                }
            } catch (Exception ignored) {}

            // summary tóm tắt (cắt 200 chars)
            String summaryShort = cp.summary != null ? truncate(cp.summary, 200) : "";
            sb.append(",\"summary\":\"").append(escapeJsonString(summaryShort)).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private void appendJsonField(StringBuilder sb, String fieldName, JsonNode node) {
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            sb.append(",\"").append(fieldName).append("\":").append(node.toString());
        }
    }

    private List<CvSearchResult> parseAndValidateRankings(
            JsonNode rootNode,
            List<CandidateProfile> candidates,
            int size
    ) {
        // Map cvId -> CandidateProfile để tra cứu nhanh
        Map<Long, CandidateProfile> candidateMap = candidates.stream()
                .collect(Collectors.toMap(CandidateProfile::cvId, cp -> cp, (a, b) -> a));

        JsonNode rankings = rootNode.path("rankings");
        if (!rankings.isArray()) {
            log.warn("AI trả về JSON thiếu trường 'rankings', fallback");
            return fallbackRanking(candidates, size);
        }

        List<CvSearchResult> results = new ArrayList<>();
        for (JsonNode item : rankings) {
            if (results.size() >= size) break;

            Long cvId = item.has("cvId") && item.get("cvId").isNumber()
                    ? item.get("cvId").asLong() : null;

            // Loại cvId bịa (không có trong danh sách candidates)
            if (cvId == null || !candidateMap.containsKey(cvId)) {
                continue;
            }

            CandidateProfile cp = candidateMap.get(cvId);
            int matchScore = Math.max(0, Math.min(100,
                    item.path("matchScore").asInt(0)));
            String reason = truncate(item.path("reason").asText(""), 300);

            // matchedSkills: chỉ giữ skills có trong profile gốc
            List<String> matchedSkills = extractMatchedSkills(item.path("matchedSkills"), cp);

            results.add(buildSearchResult(cp, matchScore, reason, matchedSkills));
        }

        // Sắp xếp theo matchScore giảm dần
        results.sort(Comparator.comparingInt(CvSearchResult::getMatchScore).reversed());
        return results;
    }

    private List<String> extractMatchedSkills(JsonNode skillsNode, CandidateProfile cp) {
        if (!skillsNode.isArray()) return List.of();

        // Lấy skills từ profile gốc
        Set<String> profileSkills = new HashSet<>();
        try {
            JsonNode parsedData = objectMapper.readTree(cp.parsedDataJson);
            JsonNode spSkills = parsedData.path("searchProfile").path("skills");
            if (spSkills.isArray()) {
                for (JsonNode s : spSkills) {
                    if (s.isTextual()) {
                        profileSkills.add(s.asText().toLowerCase());
                    }
                }
            }
        } catch (Exception ignored) {}

        List<String> matched = new ArrayList<>();
        for (JsonNode s : skillsNode) {
            if (s.isTextual()) {
                String skill = s.asText().trim().toLowerCase();
                if (!skill.isBlank() && profileSkills.contains(skill)) {
                    matched.add(skill);
                }
            }
        }
        return matched;
    }

    /**
     * Fallback xếp hạng theo qualityScore khi AI lỗi.
     */
    List<CvSearchResult> fallbackRanking(List<CandidateProfile> candidates, int size) {
        return candidates.stream()
                .sorted(Comparator.comparing(
                        CandidateProfile::qualityScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .map(cp -> {
                    int score = cp.qualityScore != null ? cp.qualityScore.intValue() : 0;
                    return buildSearchResult(cp, score, "Xếp hạng theo điểm chất lượng CV", List.of());
                })
                .toList();
    }

    private CvSearchResult buildSearchResult(
            CandidateProfile cp,
            int matchScore,
            String reason,
            List<String> matchedSkills
    ) {
        // Trích xuất thông tin từ searchProfile
        Double yearsExperience = null;
        String seniority = null;
        List<String> allSkills = List.of();

        try {
            JsonNode parsedData = objectMapper.readTree(cp.parsedDataJson);
            JsonNode sp = parsedData.path("searchProfile");
            if (sp.isObject()) {
                if (sp.has("yearsExperience") && sp.get("yearsExperience").isNumber()) {
                    yearsExperience = sp.get("yearsExperience").asDouble();
                }
                if (sp.has("seniority") && sp.get("seniority").isTextual()) {
                    seniority = sp.get("seniority").asText();
                }
                if (sp.path("skills").isArray()) {
                    List<String> skills = new ArrayList<>();
                    for (JsonNode s : sp.path("skills")) {
                        if (s.isTextual()) skills.add(s.asText());
                    }
                    allSkills = skills;
                }
            }
        } catch (Exception ignored) {}

        return CvSearchResult.builder()
                .cvId(cp.cvId)
                .candidateId(cp.candidateId)
                .fullName(cp.fullName)
                .headline(cp.headline)
                .avatarUrl(cp.avatarUrl)
                .province(cp.province)
                .yearsExperience(yearsExperience)
                .seniority(seniority)
                .qualityScore(cp.qualityScore)
                .matchScore(matchScore)
                .reason(reason)
                .matchedSkills(matchedSkills)
                .allSkills(allSkills)
                .summary(cp.summary)
                .build();
    }

    // ============================================================
    // Cache cleanup
    // ============================================================

    @Scheduled(fixedRate = 60_000)
    void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        int before = cache.size();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp > CACHE_TTL_MS);
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("CV Search cache cleanup: removed {} expired entries", removed);
        }
    }

    // ============================================================
    // Utility methods
    // ============================================================

    String normalizeQuery(String query) {
        if (query == null) return "";
        String trimmed = query.trim();
        if (trimmed.length() > 300) {
            trimmed = trimmed.substring(0, 300);
        }
        return trimmed;
    }

    private String computeCacheKey(String normalizedQuery, int size) {
        String input = normalizedQuery.toLowerCase() + "|" + size;
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input.hashCode() + "_" + size;
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode child = node.get(field);
        if (child.isNull() || !child.isTextual()) return null;
        String text = child.asText().trim();
        return text.isBlank() ? null : text;
    }

    private Double getDoubleOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode child = node.get(field);
        if (child.isNull()) return null;
        if (child.isNumber()) return child.asDouble();
        return null;
    }

    private Double clampDouble(Double value, double min, double max) {
        if (value == null) return null;
        return Math.max(min, Math.min(max, value));
    }

    private List<String> getStringList(JsonNode node, String field, int maxItems, int maxItemLen) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node.get(field)) {
            if (result.size() >= maxItems) break;
            if (item.isTextual() && !item.asText().isBlank()) {
                String text = truncate(item.asText().trim(), maxItemLen);
                if (text != null && !text.isBlank()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen).trim();
    }

    private String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private String toStr(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(obj.toString()); } catch (Exception e) { return null; }
    }
}

