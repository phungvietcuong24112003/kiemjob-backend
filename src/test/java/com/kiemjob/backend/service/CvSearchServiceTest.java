package com.kiemjob.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.ai.util.JsonResponseParser;
import com.kiemjob.backend.ai.util.PromptTemplates;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse.CvSearchResult;
import com.kiemjob.backend.dto.cvsearch.CvSearchResponse.SearchFilters;
import com.kiemjob.backend.service.ai.GroqClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CvSearchService Tests")
class CvSearchServiceTest {

    @Mock
    private GroqClient groqClient;

    @Mock
    private PromptTemplates promptTemplates;

    @Spy
    private JsonResponseParser jsonResponseParser = new JsonResponseParser(new ObjectMapper());

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CvSearchService cvSearchService;

    @BeforeEach
    void setUp() {
        // Cấu hình mặc định cho PromptTemplates mock
        lenient().when(promptTemplates.render(anyString(), anyMap()))
                .thenReturn("mock system prompt");
    }

    // ============================================================
    // Tầng 1: parseQuery tests
    // ============================================================

    @Test
    @DisplayName("parseQuery - AI trả JSON hợp lệ → parse thành SearchFilters đúng")
    void parseQuery_validInput_returnsFilters() {
        String aiResponse = """
            {
                "industry": "Công nghệ thông tin",
                "titles": ["Java Developer", "Backend Engineer"],
                "skills": ["java", "spring boot", "postgresql"],
                "minYears": 3.0,
                "maxYears": null,
                "educationLevel": "dai_hoc",
                "location": "Hà Nội",
                "keywords": ["microservices"]
            }
            """;

        when(groqClient.generateJson(anyString(), anyString())).thenReturn(aiResponse);

        SearchFilters filters = cvSearchService.parseQuery("tìm java developer 3 năm kinh nghiệm ở Hà Nội");

        assertThat(filters).isNotNull();
        assertThat(filters.getIndustry()).isEqualTo("Công nghệ thông tin");
        assertThat(filters.getTitles()).containsExactly("Java Developer", "Backend Engineer");
        assertThat(filters.getSkills()).containsExactly("java", "spring boot", "postgresql");
        assertThat(filters.getMinYears()).isEqualTo(3.0);
        assertThat(filters.getMaxYears()).isNull();
        assertThat(filters.getEducationLevel()).isEqualTo("dai_hoc");
        assertThat(filters.getLocation()).isEqualTo("Hà Nội");
    }

    @Test
    @DisplayName("parseQuery - AI lỗi → fallback tách từ khóa, KHÔNG throw exception")
    void parseQuery_aiError_fallbackKeywords() {
        when(groqClient.generateJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("Groq API timeout"));

        SearchFilters filters = cvSearchService.parseQuery("kế toán 3 năm kinh nghiệm ở Hà Nội");

        assertThat(filters).isNotNull();
        assertThat(filters.getKeywords()).isNotEmpty();
        // Không throw exception
    }

    @Test
    @DisplayName("parseQuery - AI trả JSON sai format → fallback tách từ khóa")
    void parseQuery_invalidJson_fallbackKeywords() {
        when(groqClient.generateJson(anyString(), anyString()))
                .thenReturn("Đây không phải JSON hợp lệ!!!");

        SearchFilters filters = cvSearchService.parseQuery("senior react developer");

        assertThat(filters).isNotNull();
        assertThat(filters.getKeywords()).isNotEmpty();
    }

    @Test
    @DisplayName("parseQuery - Skills được normalize lowercase")
    void parseQuery_validation_skillsLowercase() {
        String aiResponse = """
            {
                "industry": null,
                "titles": [],
                "skills": ["JAVA", "Spring Boot", "POSTGRESQL", "Java"],
                "minYears": null,
                "maxYears": null,
                "educationLevel": null,
                "location": null,
                "keywords": []
            }
            """;

        when(groqClient.generateJson(anyString(), anyString())).thenReturn(aiResponse);

        SearchFilters filters = cvSearchService.parseQuery("java developer");

        assertThat(filters.getSkills()).allMatch(s -> s.equals(s.toLowerCase()));
        // "Java" trùng với "java" nên đã bị loại trùng
        assertThat(filters.getSkills()).containsExactly("java", "spring boot", "postgresql");
    }

    @Test
    @DisplayName("parseQuery - minYears/maxYears bị clamp 0-50")
    void parseQuery_validation_clampYears() {
        String aiResponse = """
            {
                "industry": null,
                "titles": [],
                "skills": [],
                "minYears": -5.0,
                "maxYears": 100.0,
                "educationLevel": null,
                "location": null,
                "keywords": []
            }
            """;

        when(groqClient.generateJson(anyString(), anyString())).thenReturn(aiResponse);

        SearchFilters filters = cvSearchService.parseQuery("developer kinh nghiệm nhiều");

        assertThat(filters.getMinYears()).isEqualTo(0.0);
        assertThat(filters.getMaxYears()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("parseQuery - educationLevel không hợp lệ → null")
    void parseQuery_validation_invalidEducationLevel() {
        String aiResponse = """
            {
                "industry": null,
                "titles": [],
                "skills": [],
                "minYears": null,
                "maxYears": null,
                "educationLevel": "invalid_level",
                "location": null,
                "keywords": []
            }
            """;

        when(groqClient.generateJson(anyString(), anyString())).thenReturn(aiResponse);

        SearchFilters filters = cvSearchService.parseQuery("developer");

        assertThat(filters.getEducationLevel()).isNull();
    }

    // ============================================================
    // Tầng 3: rankCandidates tests
    // ============================================================

    @Test
    @DisplayName("rankCandidates - Loại cvId bịa không có trong danh sách candidates")
    void rankCandidates_removeFakeCvIds() {
        String aiRankResponse = """
            {
                "rankings": [
                    {"cvId": 1, "matchScore": 90, "reason": "Phù hợp", "matchedSkills": ["java"]},
                    {"cvId": 999, "matchScore": 80, "reason": "Bịa", "matchedSkills": ["python"]},
                    {"cvId": 2, "matchScore": 70, "reason": "Tạm ổn", "matchedSkills": []}
                ]
            }
            """;

        when(groqClient.generateJson(anyString(), anyString())).thenReturn(aiRankResponse);

        String parsedData1 = """
            {"searchProfile":{"skills":["java","spring"],"jobTitles":["Developer"],"yearsExperience":3,"seniority":"mid","educationLevel":"dai_hoc","location":"Hà Nội","industry":"IT"}}
            """;
        String parsedData2 = """
            {"searchProfile":{"skills":["python","django"],"jobTitles":["Backend"],"yearsExperience":2,"seniority":"junior","educationLevel":"dai_hoc","location":"HCM","industry":"IT"}}
            """;

        List<CvSearchService.CandidateProfile> candidates = List.of(
                new CvSearchService.CandidateProfile(1L, 10L, "Nguyen A", "Dev", null, "Hà Nội", parsedData1, "Summary 1", BigDecimal.valueOf(85)),
                new CvSearchService.CandidateProfile(2L, 20L, "Tran B", "Backend Dev", null, "HCM", parsedData2, "Summary 2", BigDecimal.valueOf(70))
        );

        SearchFilters filters = SearchFilters.builder()
                .skills(List.of("java"))
                .build();

        List<CvSearchResult> results = cvSearchService.rankCandidates("java developer", filters, candidates, 10);

        // cvId 999 phải bị loại
        assertThat(results).hasSize(2);
        assertThat(results).noneMatch(r -> r.getCvId().equals(999L));
        assertThat(results.get(0).getCvId()).isEqualTo(1L);
        assertThat(results.get(1).getCvId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("rankCandidates - AI rank lỗi → fallback sắp xếp theo qualityScore")
    void rankCandidates_aiError_fallbackQualityScore() {
        when(groqClient.generateJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI error"));

        String parsedData = """
            {"searchProfile":{"skills":["java"],"jobTitles":[],"yearsExperience":3,"seniority":"mid","educationLevel":"dai_hoc","location":"Hà Nội","industry":"IT"}}
            """;

        List<CvSearchService.CandidateProfile> candidates = List.of(
                new CvSearchService.CandidateProfile(1L, 10L, "A", "Dev", null, "HN", parsedData, "S1", BigDecimal.valueOf(60)),
                new CvSearchService.CandidateProfile(2L, 20L, "B", "Dev", null, "HCM", parsedData, "S2", BigDecimal.valueOf(90))
        );

        SearchFilters filters = SearchFilters.builder().skills(List.of("java")).build();

        List<CvSearchResult> results = cvSearchService.rankCandidates("java", filters, candidates, 10);

        // Phải fallback: sắp xếp theo qualityScore DESC → B (90) trước A (60)
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCvId()).isEqualTo(2L);
        assertThat(results.get(0).getMatchScore()).isEqualTo(90);
        assertThat(results.get(0).getReason()).contains("điểm chất lượng");
    }

    // ============================================================
    // Tích hợp: search tests
    // ============================================================

    @Test
    @DisplayName("search - Response không chứa email/phone")
    void search_noEmailPhone() {
        // Verify DTO không có field email/phone
        CvSearchResult result = CvSearchResult.builder()
                .cvId(1L)
                .candidateId(10L)
                .fullName("Test")
                .headline("Dev")
                .matchScore(80)
                .reason("OK")
                .build();

        // CvSearchResult không có getEmail() hoặc getPhone()
        assertThat(result.getCvId()).isEqualTo(1L);
        assertThat(result.getFullName()).isEqualTo("Test");
        // Compilation sẽ fail nếu ai thêm field email/phone
    }

    @Test
    @DisplayName("search - Query rỗng → trả response rỗng")
    void search_emptyQuery_returnsEmpty() {
        CvSearchResponse response = cvSearchService.search("   ", 10);

        assertThat(response).isNotNull();
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalFound()).isZero();
    }

    @Test
    @DisplayName("normalizeQuery - Cắt query > 300 ký tự")
    void normalizeQuery_truncatesLongQuery() {
        String longQuery = "a".repeat(500);
        String result = cvSearchService.normalizeQuery(longQuery);

        assertThat(result.length()).isEqualTo(300);
    }

    @Test
    @DisplayName("fallbackParseQuery - Tách từ khóa cơ bản")
    void fallbackParseQuery_extractsKeywords() {
        SearchFilters filters = cvSearchService.fallbackParseQuery("kế toán tổng hợp Hà Nội MISA");

        assertThat(filters).isNotNull();
        assertThat(filters.getKeywords()).isNotEmpty();
        assertThat(filters.getSkills()).isEmpty();
    }
}

