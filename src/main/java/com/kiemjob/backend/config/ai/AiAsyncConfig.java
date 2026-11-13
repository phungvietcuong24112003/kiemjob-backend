package com.kiemjob.backend.config.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Cấu hình ThreadPoolTaskExecutor chuyên dụng cho các tác vụ AI bất đồng bộ (@Async).
 *
 * =========================================================================================
 * LƯU Ý QUAN TRỌNG KHI SỬ DỤNG @Async:
 * 1. Cơ chế hoạt động: @Async trong Spring dựa trên Spring AOP Proxy.
 * 2. Tránh Self-Invocation: @Async CHỈ hoạt động khi phương thức được gọi từ một Spring Bean KHÁC.
 *    Nếu gọi trực tiếp nội bộ giữa các phương thức trong cùng một class (thông qua 'this'),
 *    cuộc gọi sẽ bỏ qua Spring Proxy và chạy ĐỒNG BỘ trên thread hiện tại.
 * 3. Chính sách từ chối (Rejection Policy):
 *    - TLU dùng CallerRunsPolicy làm thread gọi (Tomcat request thread) tự chạy khi queue đầy,
 *      dẫn đến nghẽn HTTP request của người dùng khi có nhiều CV nộp cùng lúc.
 *    - Tại KiemJob, chúng ta cấu hình AbortPolicy: khi hàng đợi (100) đầy, hệ thống sẽ ném
 *      RejectedExecutionException ngay lập tức để tầng gọi đánh dấu trạng thái tác vụ là 'failed'
 *      hoặc xếp lịch xử lý lại sau, không làm sập hoặc chậm trễ web server.
 * =========================================================================================
 */
@EnableAsync
@Configuration
public class AiAsyncConfig {

    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");

        // Sử dụng AbortPolicy: Ném RejectedExecutionException khi queue đầy
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        // Cho phép chờ hoàn tất các task đang chạy khi shutdown ứng dụng graceful
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}

