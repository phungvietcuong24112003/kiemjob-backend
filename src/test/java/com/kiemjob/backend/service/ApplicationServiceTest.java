package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.application.ApplicationResponse;
import com.kiemjob.backend.dto.application.ApplicationStatusRequest;
import com.kiemjob.backend.dto.application.BulkApplicationStatusRequest;
import com.kiemjob.backend.dto.application.BulkApplicationStatusResponse;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.ApplicationRepository;
import com.kiemjob.backend.repository.CvRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.security.AiRateLimiter;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private JobPostRepository jobPostRepository;
    @Mock
    private CvRepository cvRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CvOwnershipService cvOwnershipService;
    @Mock
    private EmailService emailService;
    @Mock
    private CvAnalysisService cvAnalysisService;
    @Mock
    private AiRateLimiter aiRateLimiter;

    private ApplicationService applicationService;

    private Recruiter recruiter;
    private Company company;
    private JobPost jobPost;
    private Application application;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(
                applicationRepository,
                jobPostRepository,
                cvRepository,
                accountRepository,
                currentUserService,
                cvOwnershipService,
                emailService,
                cvAnalysisService,
                aiRateLimiter
        );

        company = new Company();
        ReflectionTestUtils.setField(company, "id", 100L);

        recruiter = new Recruiter();
        ReflectionTestUtils.setField(recruiter, "id", 10L);
        recruiter.setCompanyId(100L);

        jobPost = new JobPost();
        jobPost.setId(50L);
        jobPost.setCompany(company);

        Candidate candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 20L);
        candidate.setAccountId(200L);
        candidate.setFullname("Nguyen Van A");

        Cv cv = new Cv();
        ReflectionTestUtils.setField(cv, "id", 30L);
        cv.setCandidate(candidate);

        application = new Application();
        application.setId(1L);
        application.setJobPost(jobPost);
        application.setCv(cv);
        application.setStatus("pending");
    }

    @Test
    @DisplayName("Cập nhật trạng thái hợp lệ: pending -> viewed")
    void updateStatus_validTransition_success() {
        Account candidateAccount = new Account();
        candidateAccount.setEmail("candidate@test.com");
        when(accountRepository.findById(200L)).thenReturn(Optional.of(candidateAccount));

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationStatusRequest request = new ApplicationStatusRequest();
        request.setStatus("viewed");

        ApplicationResponse response = applicationService.updateStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo("viewed");
        verify(emailService).sendApplicationStatusUpdate(eq("candidate@test.com"), any(), any(), any(), eq("viewed"));
    }

    @Test
    @DisplayName("Cập nhật trạng thái không hợp lệ: pending -> interview ném 400 với message tiếng Việt")
    void updateStatus_invalidTransition_throwsBadRequest() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireRecruiter()).thenReturn(recruiter);

        ApplicationStatusRequest request = new ApplicationStatusRequest();
        request.setStatus("interview");

        assertThatThrownBy(() -> applicationService.updateStatus(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Không thể chuyển trạng thái");
    }

    @Test
    @DisplayName("Recruiter công ty khác không có quyền cập nhật trạng thái đơn (403)")
    void updateStatus_otherRecruiter_throwsForbidden() {
        Company otherCompany = new Company();
        ReflectionTestUtils.setField(otherCompany, "id", 999L);
        Recruiter otherRecruiter = new Recruiter();
        ReflectionTestUtils.setField(otherRecruiter, "id", 99L);
        otherRecruiter.setCompanyId(999L);

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireRecruiter()).thenReturn(otherRecruiter);

        ApplicationStatusRequest request = new ApplicationStatusRequest();
        request.setStatus("viewed");

        assertThatThrownBy(() -> applicationService.updateStatus(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bạn không có quyền");
    }

    @Test
    @DisplayName("Cập nhật trạng thái hàng loạt (bulkUpdateStatus)")
    void bulkUpdateStatus_success() {
        Application app2 = new Application();
        app2.setId(2L);
        app2.setJobPost(jobPost);
        app2.setStatus("pending");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.isRecruiter()).thenReturn(true);
        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(application, app2));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BulkApplicationStatusRequest request = new BulkApplicationStatusRequest();
        request.setIds(List.of(1L, 2L));
        request.setStatus("viewed");

        BulkApplicationStatusResponse response = applicationService.bulkUpdateStatus(request);

        assertThat(response.getUpdatedCount()).isEqualTo(2);
        assertThat(response.getUpdatedIds()).containsExactly(1L, 2L);
        assertThat(response.getStatus()).isEqualTo("viewed");
    }

    @Test
    @DisplayName("Mở CV lần đầu: tự động chuyển pending sang viewed ở backend")
    void getById_recruiterOpenPendingApplication_autoTransitionsToViewed() {
        Account recruiterAccount = new Account();
        recruiterAccount.setRole("recruiter");

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireAccount()).thenReturn(recruiterAccount);
        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.getById(1L);

        assertThat(response.getStatus()).isEqualTo("viewed");
        assertThat(application.getStatus()).isEqualTo("viewed");
    }
}
