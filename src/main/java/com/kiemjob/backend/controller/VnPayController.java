package com.kiemjob.backend.controller;

import com.kiemjob.backend.entity.FeaturedOrder;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.PaymentTransaction;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.FeaturedOrderRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.PaymentTransactionRepository;
import com.kiemjob.backend.repository.RecruiterRepository;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.service.CompanyService;
import com.kiemjob.backend.service.VnPayService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class VnPayController {

    private final VnPayService vnPayService;
    private final FeaturedOrderRepository orderRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final JobPostRepository jobPostRepository;
    private final AccountRepository accountRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyService companyService;
    private final com.kiemjob.backend.security.CurrentUserService currentUserService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Tạo URL thanh toán VNPAY cho đơn hàng FeaturedOrder.
     * Hỗ trợ cả 2 endpoint: /api/vnpay/create-payment và /api/payments/vnpay/create-payment
     */
    @PostMapping({"/api/vnpay/create-payment", "/api/payments/vnpay/create-payment"})
    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestBody Map<String, Object> body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        if (!body.containsKey("orderId")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId là bắt buộc");
        }

        Long orderId = Long.valueOf(body.get("orderId").toString());

        FeaturedOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng #" + orderId));

        // Kiểm tra quyền sở hữu đơn hàng: chỉ nhà tuyển dụng sở hữu đơn hàng mới được thanh toán
        Recruiter recruiter = currentUserService.requireRecruiter();
        if (order.getRecruiter() == null || !order.getRecruiter().getId().equals(recruiter.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thanh toán đơn hàng này");
        }

        if (!"pending".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đơn hàng không ở trạng thái chờ thanh toán");
        }

        // Tự động tạo hoặc lấy PaymentTransaction tương ứng
        PaymentTransaction payment = paymentRepository
                .findAllByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .filter(p -> "pending".equals(p.getStatus()) && "vnpay".equalsIgnoreCase(p.getProvider()))
                .findFirst()
                .orElseGet(() -> {
                    PaymentTransaction p = new PaymentTransaction();
                    p.setOrder(order);
                    p.setProvider("vnpay");
                    p.setProviderOrderRef("KJ-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8));
                    p.setProviderRequestId(UUID.randomUUID().toString());
                    p.setIdempotencyKey("vnpay_" + orderId + "_" + System.currentTimeMillis());
                    p.setRequestHash("orderId=" + orderId + "&provider=vnpay");
                    p.setAmount(order.getAmount());
                    p.setCurrency("VND");
                    p.setStatus("pending");
                    return paymentRepository.save(p);
                });

        String ipAddress = getClientIp(request);
        String planName = order.getPlan() != null ? order.getPlan().getName() : "Goi noi bat";
        String orderInfo = "Thanh toan " + planName + " Don hang " + orderId;

        String paymentUrl = vnPayService.createPaymentUrl(
                orderId, order.getAmount(), orderInfo, ipAddress);

        Map<String, Object> response = new HashMap<>();
        response.put("paymentUrl", paymentUrl);
        response.put("orderId", orderId);
        response.put("transactionId", payment.getId());
        response.put("amount", order.getAmount());
        response.put("provider", "vnpay");

        return ResponseEntity.ok(response);
    }

    /**
     * VNPAY Return URL - Người dùng được redirect về sau khi thanh toán.
     * Hỗ trợ cả 2 URL: /api/vnpay/return và /api/payments/vnpay/return
     * Lưu ý bảo mật: KHÔNG cập nhật trạng thái đơn hàng tại return URL vì tham số từ trình duyệt có thể bị giả mạo.
     * Việc cập nhật trạng thái thanh toán chỉ được thực hiện tin cậy tại IPN (Server-to-Server).
     */
    @GetMapping({"/api/vnpay/return", "/api/payments/vnpay/return"})
    public ResponseEntity<Void> vnpayReturn(HttpServletRequest request) {
        Map<String, String> params = extractVnPayParams(request);

        boolean isValid = vnPayService.verifySignature(params);

        String txnRef = params.get("vnp_TxnRef"); // dạng: {orderId}_{timestamp} hoặc {orderId}
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");

        log.info("VNPAY Return: txnRef={}, responseCode={}, transactionNo={}, isValid={}",
                txnRef, responseCode, transactionNo, isValid);

        Long orderId = parseOrderIdFromTxnRef(txnRef);

        StringBuilder sb = new StringBuilder(frontendUrl).append("/payment/result")
                .append("?vnp_ResponseCode=").append(responseCode != null ? responseCode : "99")
                .append("&vnp_TxnRef=").append(orderId != null ? orderId : (txnRef != null ? txnRef : ""))
                .append("&vnp_TransactionNo=").append(transactionNo != null ? transactionNo : "")
                .append("&valid=").append(isValid);

        if (params.get("vnp_Amount") != null) {
            sb.append("&vnp_Amount=").append(params.get("vnp_Amount"));
        }
        if (params.get("vnp_BankCode") != null) {
            sb.append("&vnp_BankCode=").append(params.get("vnp_BankCode"));
        }
        if (params.get("vnp_PayDate") != null) {
            sb.append("&vnp_PayDate=").append(params.get("vnp_PayDate"));
        }
        if (params.get("vnp_OrderInfo") != null) {
            try {
                sb.append("&vnp_OrderInfo=").append(java.net.URLEncoder.encode(params.get("vnp_OrderInfo"), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        if (params.get("vnp_CardType") != null) {
            sb.append("&vnp_CardType=").append(params.get("vnp_CardType"));
        }

        String targetUrl = sb.toString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", targetUrl)
                .build();
    }

    /**
     * VNPAY IPN (Instant Payment Notification) - Server-to-server callback.
     * Hỗ trợ cả 2 URL: /api/vnpay/ipn và /api/payments/vnpay/ipn
     */
    @GetMapping({"/api/vnpay/ipn", "/api/payments/vnpay/ipn"})
    @Transactional
    public ResponseEntity<Map<String, String>> vnpayIpn(HttpServletRequest request) {
        Map<String, String> params = extractVnPayParams(request);
        Map<String, String> result = new HashMap<>();

        boolean isValid = vnPayService.verifySignature(params);

        if (!isValid) {
            result.put("RspCode", "97");
            result.put("Message", "Invalid Checksum");
            return ResponseEntity.ok(result);
        }

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");

        Long orderId = parseOrderIdFromTxnRef(txnRef);

        if (orderId == null) {
            result.put("RspCode", "01");
            result.put("Message", "Order not Found");
            return ResponseEntity.ok(result);
        }

        FeaturedOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            result.put("RspCode", "01");
            result.put("Message", "Order not Found");
            return ResponseEntity.ok(result);
        }

        // 1. Kiểm tra số tiền thanh toán (vnp_Amount đơn vị là VND * 100 theo chuẩn VNPAY)
        String amountStr = params.get("vnp_Amount");
        long expectedAmountVnp = (order.getAmount() != null ? order.getAmount() : 0L) * 100L;
        if (amountStr == null || !amountStr.matches("\\d+")) {
            result.put("RspCode", "04");
            result.put("Message", "Invalid Amount");
            return ResponseEntity.ok(result);
        }

        long receivedAmount = Long.parseLong(amountStr);
        if (receivedAmount != expectedAmountVnp) {
            log.warn("VNPAY IPN amount mismatch for order #{}: expected={}, received={}",
                    orderId, expectedAmountVnp, receivedAmount);
            result.put("RspCode", "04");
            result.put("Message", "Invalid Amount");
            return ResponseEntity.ok(result);
        }

        // 2. Tính Idempotency: Kiểm tra xem đơn hàng đã được xác nhận hoàn tất trước đó chưa
        if ("paid".equalsIgnoreCase(order.getStatus()) || "failed".equalsIgnoreCase(order.getStatus())) {
            result.put("RspCode", "02");
            result.put("Message", "Order already confirmed");
            return ResponseEntity.ok(result);
        }

        if ("00".equals(responseCode)) {
            updateOrderAndPaymentStatus(orderId, transactionNo, "paid", responseCode, "Thanh toán thành công qua VNPAY");
        } else {
            updateOrderAndPaymentStatus(orderId, transactionNo, "failed", responseCode, "Giao dịch thất bại");
        }

        result.put("RspCode", "00");
        result.put("Message", "Confirm Success");
        return ResponseEntity.ok(result);
    }

    private Long parseOrderIdFromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) return null;
        try {
            if (txnRef.contains("_")) {
                return Long.parseLong(txnRef.split("_")[0]);
            }
            return Long.parseLong(txnRef);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateOrderAndPaymentStatus(Long orderId, String transactionNo, String status, String code, String message) {
        FeaturedOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;

        LocalDateTime now = LocalDateTime.now();

        if ("paid".equals(status)) {
            boolean wasAlreadyPaid = "paid".equals(order.getStatus());
            order.setStatus("paid");
            order.setPaidAt(now);
            orderRepository.save(order);

            // Cập nhật total_spending và membership_tier cho doanh nghiệp nếu đơn hàng thanh toán thành công
            if (!wasAlreadyPaid && order.getAmount() != null && order.getAmount() > 0) {
                Long companyId = null;
                if (order.getJob() != null && order.getJob().getCompany() != null) {
                    companyId = order.getJob().getCompany().getId();
                } else if (order.getRecruiter() != null && order.getRecruiter().getCompanyId() != null) {
                    companyId = order.getRecruiter().getCompanyId();
                }

                if (companyId != null) {
                    try {
                        companyService.recordCompanySpending(companyId, order.getAmount());
                        log.info("Updated total spending (+{} VND) for company #{}", order.getAmount(), companyId);
                    } catch (Exception e) {
                        log.error("Failed to update company spending for order #{}, company #{}: {}", orderId, companyId, e.getMessage());
                    }
                }
            }

            // Tự động kích hoạt trạng thái nổi bật cho tin tuyển dụng
            if (order.getJob() != null && order.getPlan() != null) {
                JobPost job = order.getJob();
                int durationDays = order.getPlan().getDurationDays() != null ? order.getPlan().getDurationDays() : 1;
                LocalDateTime currentUntil = job.getFeaturedUntil();
                LocalDateTime newUntil = (currentUntil != null && currentUntil.isAfter(now))
                        ? currentUntil.plusDays(durationDays)
                        : now.plusDays(durationDays);
                job.setIsFeatured(true);
                job.setFeaturedUntil(newUntil);
                job.setFeaturedReminderSent(false);
                jobPostRepository.save(job);
                log.info("Activated featured status for job #{} until {}", job.getId(), newUntil);
            }
        } else if ("failed".equals(status)) {
            order.setStatus("failed");
            orderRepository.save(order);
        }

        var payments = paymentRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId);
        if (!payments.isEmpty()) {
            PaymentTransaction payment = payments.get(0);
            if (transactionNo != null) {
                payment.setProviderTxnId(transactionNo);
            }
            if ("paid".equals(status) || "success".equals(status)) {
                payment.setStatus("success");
                payment.setCompletedAt(now);
            } else {
                payment.setStatus("failed");
                payment.setFailureCode(code);
                payment.setFailureMessage(message);
            }
            paymentRepository.save(payment);
        }
    }

    private Map<String, String> extractVnPayParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        var paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String value = request.getParameter(name);
            if (value != null && !value.isEmpty()) {
                params.put(name, value);
            }
        }
        return params;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return (ip != null && !ip.isBlank() && !"0:0:0:0:0:0:0:1".equals(ip) && !ip.contains(":")) ? ip : "127.0.0.1";
    }
}
