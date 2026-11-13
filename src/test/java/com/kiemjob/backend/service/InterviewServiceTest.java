package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.interview.InterviewRequest;
import com.kiemjob.backend.dto.interview.InterviewResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Application;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.Interview;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.ApplicationRepository;
import com.kiemjob.backend.repository.InterviewRepository;
import com.kiemjob.backend.security.CurrentUserService;
import com.kiemjob.backend.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private InterviewService interviewService;

    private Recruiter recruiter;
    private Company company;
    private JobPost jobPost;
    private Candidate candidate;
    private Cv cv;
    private Application application;
    private Account candidateAccount;

    @BeforeEach
    void setUp() {
        company = new Company();
        ReflectionTestUtils.setField(company, "id", 1L);
        company.setName("Công ty ABC");

        recruiter = new Recruiter();
        ReflectionTestUtils.setField(recruiter, "id", 10L);
        recruiter.setCompanyId(1L);
        recruiter.setFullName("Nguyen Recruiter");

        jobPost = new JobPost();
        jobPost.setId(100L);
        jobPost.setTitle("Java Developer");
        jobPost.setCompany(company);

        candidateAccount = new Account();
        candidateAccount.setId(20L);
        candidateAccount.setEmail("candidate@test.com");
        candidateAccount.setRole("candidate");

        candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 30L);
        candidate.setAccountId(20L);
        candidate.setFullname("Tran Candidate");

        cv = new Cv();
        cv.setId(40L);
        cv.setCandidate(candidate);

        application = new Application();
        application.setId(50L);
        application.setJobPost(jobPost);
        application.setCv(cv);
        application.setStatus("viewed");
    }

    @Test
    @DisplayName("Đặt lịch thành công: tạo interview, đổi trạng thái sang interview và gửi email")
    void create_Success_ValidSchedule_ChangesStatusAndSendsEmail() {
        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(candidateAccount));

        LocalDateTime futureTime = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);
        InterviewRequest request = new InterviewRequest(50L, "Phỏng vấn kỹ thuật", futureTime, "Tầng 5, tòa A, 12 Duy Tân");

        when(interviewRepository.save(any(Interview.class))).thenAnswer(invocation -> {
            Interview i = invocation.getArgument(0);
            i.setId(1L);
            return i;
        });

        InterviewResponse response = interviewService.create(50L, request);

        assertNotNull(response);
        assertEquals("Phỏng vấn kỹ thuật", response.title());
        assertEquals("interview", application.getStatus());

        // Kiểm tra lưu DB
        verify(applicationRepository, times(1)).save(application);
        verify(interviewRepository, times(1)).save(any(Interview.class));

        // Kiểm tra gửi email có ngày giờ và ghi chú nơi gặp
        verify(emailService, times(1)).sendInterviewInvitation(
                eq("candidate@test.com"),
                eq("Tran Candidate"),
                eq("Java Developer"),
                eq("Công ty ABC"),
                eq(futureTime),
                eq("Tầng 5, tòa A, 12 Duy Tân")
        );
    }

    @Test
    @DisplayName("Đặt lịch khi đơn đã ở trạng thái interview: vẫn gửi email nhưng không cần đổi trạng thái lại")
    void create_Success_WhenAlreadyInterviewStatus_SendsEmailWithoutReSavingStatus() {
        application.setStatus("interview");

        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(candidateAccount));

        LocalDateTime futureTime = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3);
        InterviewRequest request = new InterviewRequest(50L, "Phỏng vấn vòng 2", futureTime, "Online qua Meet");

        when(interviewRepository.save(any(Interview.class))).thenAnswer(invocation -> {
            Interview i = invocation.getArgument(0);
            i.setId(2L);
            return i;
        });

        InterviewResponse response = interviewService.create(50L, request);

        assertNotNull(response);
        assertEquals("interview", application.getStatus());
        // Không lưu lại application vì status đã là interview
        verify(applicationRepository, never()).save(application);
        // Vẫn gửi email
        verify(emailService, times(1)).sendInterviewInvitation(
                eq("candidate@test.com"),
                any(),
                any(),
                any(),
                eq(futureTime),
                eq("Online qua Meet")
        );
    }

    @Test
    @DisplayName("Từ chối đặt lịch (409 Conflict) khi hồ sơ đã ở trạng thái rejected")
    void create_ShouldThrowConflict_WhenStatusIsRejected() {
        application.setStatus("rejected");

        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));

        LocalDateTime futureTime = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        InterviewRequest request = new InterviewRequest(50L, "Phỏng vấn", futureTime, "VP Công ty");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                interviewService.create(50L, request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("rejected"));
        verify(interviewRepository, never()).save(any());
        verify(emailService, never()).sendInterviewInvitation(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Từ chối đặt lịch (409 Conflict) khi hồ sơ đã ở trạng thái accepted")
    void create_ShouldThrowConflict_WhenStatusIsAccepted() {
        application.setStatus("accepted");

        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));

        LocalDateTime futureTime = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        InterviewRequest request = new InterviewRequest(50L, "Phỏng vấn", futureTime, "VP Công ty");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                interviewService.create(50L, request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("accepted"));
        verify(interviewRepository, never()).save(any());
        verify(emailService, never()).sendInterviewInvitation(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Từ chối đặt lịch (400 Bad Request) khi scheduledAt ở quá khứ theo giờ VN")
    void create_ShouldThrowBadRequest_WhenScheduledAtIsInPast() {
        when(currentUserService.requireRecruiter()).thenReturn(recruiter);
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));

        LocalDateTime pastTime = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusHours(2);
        InterviewRequest request = new InterviewRequest(50L, "Phỏng vấn", pastTime, "VP Công ty");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                interviewService.create(50L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("tương lai"));
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối đặt lịch (403 Forbidden) khi Recruiter không thuộc cùng công ty với tin tuyển dụng")
    void create_ShouldThrowForbidden_WhenRecruiterDifferentCompany() {
        Recruiter otherRecruiter = new Recruiter();
        ReflectionTestUtils.setField(otherRecruiter, "id", 99L);
        otherRecruiter.setCompanyId(999L); // Khác companyId 1L của jobPost

        when(currentUserService.requireRecruiter()).thenReturn(otherRecruiter);
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));

        LocalDateTime futureTime = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        InterviewRequest request = new InterviewRequest(50L, "Phỏng vấn", futureTime, "VP Công ty");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                interviewService.create(50L, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ứng viên xem lịch của chính mình qua getByApplication: thành công")
    void getByApplication_CandidateOwner_ShouldSucceed() {
        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireAccount()).thenReturn(candidateAccount); // accountId = 20L khớp với candidate.accountId

        when(interviewRepository.findByApplicationIdWithDetails(50L)).thenReturn(Collections.emptyList());

        List<InterviewResponse> result = interviewService.getByApplication(50L);

        assertNotNull(result);
        verify(interviewRepository, times(1)).findByApplicationIdWithDetails(50L);
    }

    @Test
    @DisplayName("Ứng viên xem lịch của đơn khác không thuộc về mình: bị chặn 403 Forbidden")
    void getByApplication_CandidateNotOwner_ShouldThrowForbidden() {
        Account otherCandidateAccount = new Account();
        otherCandidateAccount.setId(999L); // Khác accountId 20L của đơn
        otherCandidateAccount.setRole("candidate");

        when(applicationRepository.findById(50L)).thenReturn(Optional.of(application));
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireAccount()).thenReturn(otherCandidateAccount);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                interviewService.getByApplication(50L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(interviewRepository, never()).findByApplicationIdWithDetails(any());
    }
}
