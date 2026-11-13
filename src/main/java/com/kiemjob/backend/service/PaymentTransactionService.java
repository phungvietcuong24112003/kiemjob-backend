package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.payment.PaymentCreateRequest;
import com.kiemjob.backend.dto.payment.PaymentResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.FeaturedOrder;
import com.kiemjob.backend.entity.PaymentTransaction;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.FeaturedOrderRepository;
import com.kiemjob.backend.repository.PaymentTransactionRepository;
import com.kiemjob.backend.repository.RecruiterRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentRepository;
    private final FeaturedOrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final RecruiterRepository recruiterRepository;

    public PaymentTransactionService(
            PaymentTransactionRepository paymentRepository,
            FeaturedOrderRepository orderRepository,
            AccountRepository accountRepository,
            RecruiterRepository recruiterRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.recruiterRepository = recruiterRepository;
    }

    @Transactional
    public PaymentResponse createPayment(
            String email,
            Long orderId,
            PaymentCreateRequest request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key là bắt buộc và tối đa 100 ký tự"
            );
        }

        String provider = request.provider().toLowerCase(Locale.ROOT);

        if (!provider.equals("momo") && !provider.equals("vnpay")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cổng thanh toán không hợp lệ"
            );
        }

        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản"
                ));

        Recruiter recruiter = recruiterRepository
                .findByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản không có hồ sơ nhà tuyển dụng"
                ));

        FeaturedOrder order = orderRepository
                .findByIdAndRecruiter_Id(orderId, recruiter.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng"
                ));

        String requestHash = sha256(
                "orderId=" + orderId + "&provider=" + provider
        );

        // Idempotency: yêu cầu giống nhau phải trả về giao dịch cũ.
        var existing = paymentRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            PaymentTransaction payment = existing.get();

            if (!payment.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key đã được dùng cho yêu cầu khác"
                );
            }

            if (!payment.getOrder().getRecruiter().getId()
                    .equals(recruiter.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key không hợp lệ"
                );
            }

            return toResponse(payment);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể thanh toán đơn hàng đang pending"
                );
        }

        // Nếu đơn hàng đã có giao dịch pending cùng provider, tái sử dụng để tránh lỗi xung đột
        var existingPending = paymentRepository
                .findAllByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .filter(p -> "pending".equals(p.getStatus()))
                .findFirst();

        if (existingPending.isPresent()) {
            PaymentTransaction p = existingPending.get();
            if (p.getProvider().equalsIgnoreCase(provider)) {
                return toResponse(p);
            }
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setOrder(order);
        payment.setProvider(provider);
        payment.setProviderOrderRef(
                "KJ-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8)
        );
        payment.setProviderRequestId(UUID.randomUUID().toString());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setRequestHash(requestHash);
        payment.setAmount(order.getAmount());
        payment.setCurrency("VND");
        payment.setStatus("pending");

        try {
            return toResponse(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException ex) {
            // Có thể do request đồng thời hoặc một giao dịch pending khác.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Giao dịch đã tồn tại hoặc đơn hàng đang có giao dịch pending"
            );
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentResponse> getOrderPayments(
            String email,
            Long orderId
    ) {
        Recruiter recruiter = getRecruiter(email);

        orderRepository.findByIdAndRecruiter_Id(orderId, recruiter.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng"
                ));

        return paymentRepository
                .findAllByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(PaymentTransactionService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentResponse> getOrderPaymentsForAdmin(Long orderId) {
        return paymentRepository
                .findAllByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(PaymentTransactionService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            String email,
            Long paymentId
    ) {
        PaymentTransaction payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch"
                ));

        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản"
                ));

        if (!"admin".equalsIgnoreCase(account.getRole())) {
            Recruiter recruiter = recruiterRepository.findByAccountId(account.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tài khoản không có hồ sơ nhà tuyển dụng"
                    ));

            if (!payment.getOrder().getRecruiter().getId()
                    .equals(recruiter.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch"
                );
            }
        }

        return toResponse(payment);
    }

    private Recruiter getRecruiter(String email) {
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản"
                ));

        return recruiterRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản không có hồ sơ nhà tuyển dụng"
                ));
    }

    private static PaymentResponse toResponse(
            PaymentTransaction payment
    ) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider(),
                payment.getProviderOrderRef(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                payment.getCreatedAt(),
                payment.getCompletedAt(),
                payment.getRefundedAt()
        );
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "Không hỗ trợ thuật toán SHA-256", ex
            );
        }
    }
}
