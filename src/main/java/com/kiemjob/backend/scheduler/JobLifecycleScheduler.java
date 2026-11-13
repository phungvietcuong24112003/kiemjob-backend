package com.kiemjob.backend.scheduler;

import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class JobLifecycleScheduler {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JobPostRepository jobPostRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;

    /**
     * Chạy mỗi giờ (mặc định cron 0 0 * * * *):
     * Cập nhật hàng loạt (batch update) các tin có status = 'open' và application_deadline < hôm nay thành 'expired'.
     * Sử dụng duy nhất một câu lệnh UPDATE trong DB, không tải entity vào bộ nhớ.
     */
    @Scheduled(cron = "${app.scheduler.expire-jobs-cron:0 0 * * * *}")
    @SchedulerLock(name = "JobLifecycleScheduler_expireOpenJobsPastDeadline", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    @Transactional
    public int expireOpenJobsPastDeadline() {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        int updated = jobPostRepository.expireJobsPastDeadline(today);
        if (updated > 0) {
            log.info("JobLifecycleScheduler: Đã chuyển {} tin tuyển dụng quá hạn nộp hồ sơ sang 'expired' (trước ngày {})", updated, today);
        } else {
            log.debug("JobLifecycleScheduler: Không có tin tuyển dụng nào quá hạn ngày {}", today);
        }
        return updated;
    }

    /**
     * Chạy mỗi giờ (mặc định cron 0 0 * * * *):
     * Tắt cờ is_featured cho các tin có featured_until < now.
     * Sử dụng duy nhất một câu lệnh UPDATE trong DB, không tải entity vào bộ nhớ.
     */
    @Scheduled(cron = "${app.scheduler.clear-featured-cron:0 0 * * * *}")
    @SchedulerLock(name = "JobLifecycleScheduler_clearExpiredFeaturedJobs", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    @Transactional
    public int clearExpiredFeaturedJobs() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        int updated = jobPostRepository.clearExpiredFeaturedStatus(now);
        if (updated > 0) {
            log.info("JobLifecycleScheduler: Đã tắt cờ nổi bật (isFeatured) cho {} tin tuyển dụng hết hạn (trước {})", updated, now);
        } else {
            log.debug("JobLifecycleScheduler: Không có tin nổi bật nào hết hạn trước {}", now);
        }
        return updated;
    }

    /**
     * Chạy mỗi ngày vào lúc 08:00 sáng (múi giờ Asia/Ho_Chi_Minh):
     * 1. Gửi email nhắc nhở nhà tuyển dụng cho các tin tuyển dụng sắp hết hạn trong 3 ngày tới.
     * 2. Gửi email nhắc nhở nhà tuyển dụng cho các gói tin nổi bật sắp hết hạn trong 3 ngày tới.
     * Mỗi tin chỉ gửi thông báo nhắc nhở một lần (được đánh dấu qua các cờ reminder_sent).
     */
    @Scheduled(cron = "${app.scheduler.reminder-cron:0 0 8 * * *}", zone = "${app.scheduler.reminder-zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(name = "JobLifecycleScheduler_sendDailyReminders", lockAtLeastFor = "PT15M", lockAtMostFor = "PT30M")
    public void sendDailyReminders() {
        log.info("JobLifecycleScheduler: Bắt đầu tiến trình gửi thông báo nhắc nhở định kỳ 08:00");
        int jobReminders = sendExpiringJobReminders();
        int featuredReminders = sendExpiringFeaturedReminders();
        log.info("JobLifecycleScheduler: Hoàn tất gửi thông báo: {} tin sắp hết hạn nhận hồ sơ, {} tin sắp hết hạn gói nổi bật",
                jobReminders, featuredReminders);
    }

    @Transactional
    public int sendExpiringJobReminders() {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDate targetDate = today.plusDays(3);

        List<JobPost> expiringJobs = jobPostRepository.findJobsExpiringSoon(today, targetDate);
        if (expiringJobs.isEmpty()) {
            return 0;
        }

        // Tải trước thông tin tài khoản bằng 1 query duy nhất để tránh N+1
        List<Long> accountIds = expiringJobs.stream()
                .map(JobPost::getPostedBy)
                .filter(Objects::nonNull)
                .map(Recruiter::getAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<Long> sentJobIds = new ArrayList<>();
        for (JobPost job : expiringJobs) {
            try {
                Recruiter recruiter = job.getPostedBy();
                String email = (recruiter != null && recruiter.getAccountId() != null && accountMap.containsKey(recruiter.getAccountId()))
                        ? accountMap.get(recruiter.getAccountId()).getEmail()
                        : null;

                if (email != null && !email.isBlank()) {
                    String recruiterName = recruiter != null ? recruiter.getFullName() : null;
                    String companyName = job.getCompany() != null ? job.getCompany().getName() : null;
                    emailService.sendJobExpirationReminder(email, recruiterName, job.getTitle(), companyName, job.getApplicationDeadline());
                    sentJobIds.add(job.getId());
                }
            } catch (Exception ex) {
                log.error("Lỗi khi gửi email nhắc hết hạn tin tuyển dụng #{}", job.getId(), ex);
            }
        }

        if (!sentJobIds.isEmpty()) {
            jobPostRepository.markExpirationRemindersSent(sentJobIds);
        }

        return sentJobIds.size();
    }

    @Transactional
    public int sendExpiringFeaturedReminders() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDateTime targetTime = now.plusDays(3);

        List<JobPost> expiringFeatured = jobPostRepository.findFeaturedExpiringSoon(now, targetTime);
        if (expiringFeatured.isEmpty()) {
            return 0;
        }

        List<Long> accountIds = expiringFeatured.stream()
                .map(JobPost::getPostedBy)
                .filter(Objects::nonNull)
                .map(Recruiter::getAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<Long> sentJobIds = new ArrayList<>();
        for (JobPost job : expiringFeatured) {
            try {
                Recruiter recruiter = job.getPostedBy();
                String email = (recruiter != null && recruiter.getAccountId() != null && accountMap.containsKey(recruiter.getAccountId()))
                        ? accountMap.get(recruiter.getAccountId()).getEmail()
                        : null;

                if (email != null && !email.isBlank()) {
                    String recruiterName = recruiter != null ? recruiter.getFullName() : null;
                    String companyName = job.getCompany() != null ? job.getCompany().getName() : null;
                    emailService.sendFeaturedExpirationReminder(email, recruiterName, job.getTitle(), companyName, job.getFeaturedUntil());
                    sentJobIds.add(job.getId());
                }
            } catch (Exception ex) {
                log.error("Lỗi khi gửi email nhắc hết hạn gói nổi bật cho tin #{}", job.getId(), ex);
            }
        }

        if (!sentJobIds.isEmpty()) {
            jobPostRepository.markFeaturedRemindersSent(sentJobIds);
        }

        return sentJobIds.size();
    }
}

