package com.kiemjob.backend;

import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.scheduler.JobLifecycleScheduler;
import com.kiemjob.backend.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobLifecycleSchedulerTest {

    @Mock
    private JobPostRepository jobPostRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private JobLifecycleScheduler scheduler;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private Company sampleCompany;
    private Recruiter sampleRecruiter;
    private Account sampleAccount;
    private JobPost sampleJob;

    @BeforeEach
    void setUp() {
        sampleCompany = new Company();
        ReflectionTestUtils.setField(sampleCompany, "id", 10L);
        sampleCompany.setName("Tech Corp Vietnam");

        sampleRecruiter = new Recruiter();
        ReflectionTestUtils.setField(sampleRecruiter, "id", 20L);
        sampleRecruiter.setAccountId(30L);
        sampleRecruiter.setCompanyId(10L);
        sampleRecruiter.setFullName("Nguyen Van A");

        sampleAccount = new Account();
        sampleAccount.setId(30L);
        sampleAccount.setEmail("recruiter@techcorp.vn");

        sampleJob = new JobPost();
        sampleJob.setId(100L);
        sampleJob.setTitle("Senior Java Developer");
        sampleJob.setStatus("open");
        sampleJob.setCompany(sampleCompany);
        sampleJob.setPostedBy(sampleRecruiter);
        sampleJob.setApplicationDeadline(LocalDate.now(VIETNAM_ZONE).plusDays(2));
        sampleJob.setIsFeatured(true);
        sampleJob.setFeaturedUntil(LocalDateTime.now(VIETNAM_ZONE).plusDays(2));
        sampleJob.setExpirationReminderSent(false);
        sampleJob.setFeaturedReminderSent(false);
    }

    @Test
    @DisplayName("expireOpenJobsPastDeadline: Cập nhật các tin open quá hạn nộp hồ sơ thành expired")
    void testExpireOpenJobsPastDeadline() {
        when(jobPostRepository.expireJobsPastDeadline(any(LocalDate.class))).thenReturn(5);

        int updatedCount = scheduler.expireOpenJobsPastDeadline();

        assertEquals(5, updatedCount);
        verify(jobPostRepository, times(1)).expireJobsPastDeadline(any(LocalDate.class));
    }

    @Test
    @DisplayName("clearExpiredFeaturedJobs: Tắt cờ is_featured cho các tin tuyển dụng đã quá hạn featured_until")
    void testClearExpiredFeaturedJobs() {
        when(jobPostRepository.clearExpiredFeaturedStatus(any(LocalDateTime.class))).thenReturn(3);

        int updatedCount = scheduler.clearExpiredFeaturedJobs();

        assertEquals(3, updatedCount);
        verify(jobPostRepository, times(1)).clearExpiredFeaturedStatus(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("sendExpiringJobReminders: Gửi email nhắc nhở cho tin sắp hết hạn trong 3 ngày và đánh dấu đã gửi")
    void testSendExpiringJobReminders_Success() {
        when(jobPostRepository.findJobsExpiringSoon(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sampleJob));
        when(accountRepository.findAllById(List.of(30L)))
                .thenReturn(List.of(sampleAccount));
        when(jobPostRepository.markExpirationRemindersSent(List.of(100L)))
                .thenReturn(1);

        int count = scheduler.sendExpiringJobReminders();

        assertEquals(1, count);
        verify(emailService, times(1)).sendJobExpirationReminder(
                eq("recruiter@techcorp.vn"),
                eq("Nguyen Van A"),
                eq("Senior Java Developer"),
                eq("Tech Corp Vietnam"),
                eq(sampleJob.getApplicationDeadline())
        );
        verify(jobPostRepository, times(1)).markExpirationRemindersSent(List.of(100L));
    }

    @Test
    @DisplayName("sendExpiringJobReminders: Không có tin nào sắp hết hạn thì không gửi email")
    void testSendExpiringJobReminders_EmptyList() {
        when(jobPostRepository.findJobsExpiringSoon(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        int count = scheduler.sendExpiringJobReminders();

        assertEquals(0, count);
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(emailService);
        verify(jobPostRepository, never()).markExpirationRemindersSent(any());
    }

    @Test
    @DisplayName("sendExpiringFeaturedReminders: Gửi email nhắc nhở gói nổi bật sắp hết hạn trong 3 ngày và đánh dấu đã gửi")
    void testSendExpiringFeaturedReminders_Success() {
        when(jobPostRepository.findFeaturedExpiringSoon(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(sampleJob));
        when(accountRepository.findAllById(List.of(30L)))
                .thenReturn(List.of(sampleAccount));
        when(jobPostRepository.markFeaturedRemindersSent(List.of(100L)))
                .thenReturn(1);

        int count = scheduler.sendExpiringFeaturedReminders();

        assertEquals(1, count);
        verify(emailService, times(1)).sendFeaturedExpirationReminder(
                eq("recruiter@techcorp.vn"),
                eq("Nguyen Van A"),
                eq("Senior Java Developer"),
                eq("Tech Corp Vietnam"),
                eq(sampleJob.getFeaturedUntil())
        );
        verify(jobPostRepository, times(1)).markFeaturedRemindersSent(List.of(100L));
    }

    @Test
    @DisplayName("sendExpiringJobReminders: Bỏ qua và không crash khi một tin gửi email thất bại")
    void testSendExpiringJobReminders_ResilienceOnError() {
        JobPost failingJob = new JobPost();
        failingJob.setId(101L);
        failingJob.setTitle("Job with error");
        failingJob.setPostedBy(sampleRecruiter);
        failingJob.setCompany(sampleCompany);
        failingJob.setApplicationDeadline(LocalDate.now(VIETNAM_ZONE).plusDays(1));

        when(jobPostRepository.findJobsExpiringSoon(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(failingJob, sampleJob));
        when(accountRepository.findAllById(any()))
                .thenReturn(List.of(sampleAccount));

        // Ném exception khi gửi email cho job đầu tiên
        doThrow(new RuntimeException("SMTP connection error"))
                .when(emailService).sendJobExpirationReminder(
                        eq("recruiter@techcorp.vn"),
                        eq("Nguyen Van A"),
                        eq("Job with error"),
                        eq("Tech Corp Vietnam"),
                        any()
                );

        int count = scheduler.sendExpiringJobReminders();

        // Job thứ hai vẫn được gửi thành công
        assertEquals(1, count);
        verify(jobPostRepository, times(1)).markExpirationRemindersSent(List.of(100L));
    }

    @Test
    @DisplayName("sendDailyReminders: Gọi cả hai phương thức nhắc nhở hạn đăng tin và hạn nổi bật")
    void testSendDailyReminders() {
        when(jobPostRepository.findJobsExpiringSoon(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(jobPostRepository.findFeaturedExpiringSoon(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> scheduler.sendDailyReminders());

        verify(jobPostRepository, times(1)).findJobsExpiringSoon(any(), any());
        verify(jobPostRepository, times(1)).findFeaturedExpiringSoon(any(), any());
    }
}
