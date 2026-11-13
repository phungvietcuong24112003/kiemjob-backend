package com.kiemjob.backend;

import com.kiemjob.backend.dto.ai.AiLabelResult;
import com.kiemjob.backend.dto.ai.AiModerationResponse;
import com.kiemjob.backend.dto.moderation.JobModerationResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.service.AiModerationService;
import com.kiemjob.backend.service.JobModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class JobModerationIntegrationTest {

    @Autowired
    private JobModerationService jobModerationService;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private AiModerationService aiModerationService;

    @BeforeEach
    void setUpMockAi() {
        when(aiModerationService.moderate(any(), any())).thenReturn(
                new AiModerationResponse("Tuyển dụng CTV", Map.of(
                        "JOB_SCAM", new AiLabelResult(0, 0.05),
                        "UPFRONT_FEE_REQUEST", new AiLabelResult(0, 0.02),
                        "GAMBLING_BETTING", new AiLabelResult(0, 0.01),
                        "PYRAMID_MULTI_LEVEL", new AiLabelResult(0, 0.03),
                        "HIGH_SALARY_LOW_SKILL", new AiLabelResult(0, 0.08),
                        "IDENTITY_DOCUMENT_RETENTION", new AiLabelResult(0, 0.01),
                        "ANONYMOUS_RECRUITER", new AiLabelResult(0, 0.04)
                ))
        );
    }

    @Test
    @DisplayName("1. Kiểm tra bài tuyển dụng không tồn tại (jobId = 1) -> Ném 404 NOT_FOUND")
    void testJobNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            jobModerationService.checkJob(1L, "cuongphung24110017@gmail.com");
        });
        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Không tìm thấy bài tuyển dụng"));
        System.out.println("✅ Test 1 PASSED: 404 Not Found khi job không tồn tại");
    }

    @Test
    @DisplayName("2. Kiểm tra không có token / chưa đăng nhập -> Ném 401 UNAUTHORIZED")
    void testUnauthenticated() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            jobModerationService.checkJob(3L, null);
        });
        assertEquals(401, ex.getStatusCode().value());
        System.out.println("✅ Test 2 PASSED: 401 Unauthorized khi không có token");
    }

    @Test
    @DisplayName("3. Kiểm tra tài khoản Candidate không có quyền kiểm duyệt -> Ném 403 FORBIDDEN")
    void testCandidateForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            jobModerationService.checkJob(3L, "testcandidate2026@gmail.com");
        });
        assertEquals(403, ex.getStatusCode().value());
        System.out.println("✅ Test 3 PASSED: 403 Forbidden đối với ứng viên (Candidate)");
    }

    @Test
    @DisplayName("4. Kiểm tra Recruiter không thể kiểm duyệt bài của Recruiter khác -> Ném 403 FORBIDDEN")
    void testRecruiterCannotModerateOtherJob() {
        // Job 3 thuộc về recruiter cuongphung24110017@gmail.com
        // fqc81956@laoia.com là recruiter sở hữu Job 8
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            jobModerationService.checkJob(3L, "fqc81956@laoia.com");
        });
        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Bạn không có quyền quản lý bài tuyển dụng này"));
        System.out.println("✅ Test 4 PASSED: 403 Forbidden khi recruiter kiểm duyệt bài của người khác");
    }

    @Test
    @DisplayName("5. Kiểm tra Recruiter chính chủ kiểm duyệt bài viết của mình -> Gọi AI, Deserialize, Lưu và Đọc JSONB thành công")
    void testRecruiterModerateOwnJobSuccessfully() {
        String ownerEmail = "cuongphung24110017@gmail.com";
        Long jobId = 3L;

        // Chạy kiểm duyệt
        JobModerationResponse response = jobModerationService.checkJob(jobId, ownerEmail);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(jobId, response.jobId());
        assertNotNull(response.result());
        assertNotNull(response.moderationStatus());

        System.out.println("✅ Kết quả kiểm duyệt Job " + jobId + ":");
        System.out.println(" - ID: " + response.id());
        System.out.println(" - Status: " + response.moderationStatus());
        System.out.println(" - Result JSON: " + response.result().toString());

        // Kiểm tra trạng thái thuộc các giá trị hợp lệ
        assertTrue(List.of("passed", "pending_review", "error").contains(response.moderationStatus()));

        // Kiểm tra đọc lại từ DB (SELECT JSONB)
        JobModerationResponse latest = jobModerationService.getLatest(jobId, ownerEmail);
        assertNotNull(latest);
        assertEquals(response.id(), latest.id());
        assertNotNull(latest.result());
        assertEquals(response.result(), latest.result());

        // Kiểm tra đọc lịch sử
        List<JobModerationResponse> history = jobModerationService.getHistory(jobId, ownerEmail);
        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(h -> h.id().equals(response.id())));

        System.out.println("✅ Test 5 PASSED: Lưu và đọc JSONB từ PostgreSQL thành công");
    }

    @Test
    @DisplayName("6. Kiểm tra Admin có quyền kiểm duyệt bất kỳ bài viết nào")
    void testAdminCanModerateAnyJob() {
        // Tạo hoặc sử dụng tài khoản admin tạm để test
        String adminEmail = "system_admin_test@kiemjob.com";
        Account adminAccount = accountRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> {
            Account acc = new Account();
            acc.setEmail(adminEmail);
            acc.setRole("admin");
            acc.setEmailVerified(true);
            return accountRepository.save(acc);
        });

        try {
            // Admin kiểm duyệt Job 3
            JobModerationResponse response = jobModerationService.checkJob(3L, adminEmail);
            assertNotNull(response);
            assertEquals(3L, response.jobId());
            System.out.println("✅ Test 6 PASSED: Admin kiểm duyệt bài viết thành công");
        } finally {
            accountRepository.delete(adminAccount);
        }
    }
}
