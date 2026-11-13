package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.feedback.FeedbackCreateRequest;
import com.kiemjob.backend.dto.feedback.FeedbackResponse;
import com.kiemjob.backend.dto.feedback.FeedbackStatsResponse;
import com.kiemjob.backend.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Gửi đóng góp ý kiến (Yêu cầu đăng nhập: Ứng viên, nhà tuyển dụng hoặc admin)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submitFeedback(
            Authentication authentication,
            @Valid @RequestBody FeedbackCreateRequest request
    ) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để gửi góp ý");
        }
        String authEmail = authentication.getName();
        FeedbackResponse saved = feedbackService.createFeedback(authEmail, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cảm ơn bạn đã đóng góp ý kiến để hoàn thiện nền tảng KiemJob!",
                "data", saved
        ));
    }

    /**
     * Lấy các góp ý của chính tài khoản đang đăng nhập
     */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FeedbackResponse>> getMyFeedbacks(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem góp ý");
        }
        String authEmail = authentication.getName();
        return ResponseEntity.ok(feedbackService.getMyFeedbacks(authEmail));
    }

    /**
     * Admin lấy danh sách toàn bộ góp ý
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FeedbackResponse>> getAllFeedbacks() {
        return ResponseEntity.ok(feedbackService.getAllFeedbacks());
    }

    /**
     * Admin lấy thống kê góp ý
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeedbackStatsResponse> getStats() {
        return ResponseEntity.ok(feedbackService.getStats());
    }

    /**
     * Xóa góp ý (Admin)
     */
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        feedbackService.deleteFeedback(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa góp ý thành công."
        ));
    }
}
