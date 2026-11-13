package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.featuredorder.FeaturedOrderRequest;
import com.kiemjob.backend.dto.featuredorder.FeaturedOrderResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.FeaturePackage;
import com.kiemjob.backend.entity.FeaturedOrder;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.PaymentTransaction;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.CompanyRepository;
import com.kiemjob.backend.repository.FeaturedOrderRepository;
import com.kiemjob.backend.repository.FeaturePackageRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.PaymentTransactionRepository;
import com.kiemjob.backend.repository.RecruiterRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeaturedOrderService {

    private final AccountRepository accountRepository;
    private final RecruiterRepository recruiterRepository;
    private final FeaturePackageRepository featurePackageRepository;
    private final FeaturedOrderRepository featuredOrderRepository;
    private final JobPostRepository jobPostRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public FeaturedOrderResponse createOrder(
            String email,
            String idempotencyKey,
            FeaturedOrderRequest request) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key là bắt buộc và tối đa 100 ký tự"
            );
        }

        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản đăng nhập"
                ));

        Recruiter recruiter = recruiterRepository
                .findByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản chưa có hồ sơ recruiter"
                ));

        String requestHash = sha256("planId=" + request.planId() + "&jobId=" + request.jobId());

        // Kiểm tra idempotency trước khi tạo đơn mới.
        var existing = featuredOrderRepository
                .findByRecruiter_IdAndIdempotencyKey(
                        recruiter.getId(), idempotencyKey
                );

        if (existing.isPresent()) {
            FeaturedOrder oldOrder = existing.get();

            if (!oldOrder.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key đã được dùng cho yêu cầu khác"
                );
            }

            return toResponse(oldOrder);
        }

        FeaturePackage plan = featurePackageRepository
                .findById(request.planId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy gói dịch vụ"
                ));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Gói dịch vụ hiện không còn hoạt động"
            );
        }

        JobPost jobPost = null;
        if (request.jobId() != null) {
            jobPost = jobPostRepository.findById(request.jobId()).orElse(null);
            if (jobPost != null && !jobPost.getPostedBy().getId().equals(recruiter.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Bạn không có quyền quảng bá tin tuyển dụng của nhà tuyển dụng khác"
                );
            }
        }

        FeaturedOrder order = new FeaturedOrder();
        order.setRecruiter(recruiter);
        order.setPlan(plan);
        order.setJob(jobPost);
        order.setAmount(plan.getPrice());
        order.setCurrency("VND");
        order.setStatus("pending");
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestHash(requestHash);

        try {
            FeaturedOrder saved = featuredOrderRepository.saveAndFlush(order);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Xung đột khóa idempotency hoặc ràng buộc CSDL.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Xung đột dữ liệu đơn hàng hoặc Idempotency-Key đã tồn tại"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<FeaturedOrderResponse> getMyOrders(String email) {
        Recruiter recruiter = getRecruiterByEmail(email);

        return featuredOrderRepository
                .findAllByRecruiter_IdOrderByCreatedAtDesc(recruiter.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeaturedOrderResponse getMyOrder(String email, Long orderId) {
        Recruiter recruiter = getRecruiterByEmail(email);

        FeaturedOrder order = featuredOrderRepository
                .findByIdAndRecruiter_Id(orderId, recruiter.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng"
                ));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<FeaturedOrderResponse> getAllOrders() {
        return featuredOrderRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeaturedOrderResponse getOrderByIdForAdmin(Long orderId) {
        FeaturedOrder order = featuredOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng #" + orderId
                ));
        return toResponse(order);
    }

    private Recruiter getRecruiterByEmail(String email) {
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản đăng nhập"
                ));

        return recruiterRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản chưa có hồ sơ recruiter"
                ));
    }

    private FeaturedOrderResponse toResponse(FeaturedOrder order) {
        Long jobId = order.getJob() != null ? order.getJob().getId() : null;
        String jobTitle = order.getJob() != null ? order.getJob().getTitle() : null;
        LocalDateTime featuredUntil = order.getJob() != null ? order.getJob().getFeaturedUntil() : null;
        Integer durationDays = order.getPlan() != null ? order.getPlan().getDurationDays() : null;

        Long companyId = null;
        String companyName = null;
        String companyLogo = null;

        if (order.getJob() != null && order.getJob().getCompany() != null) {
            companyId = order.getJob().getCompany().getId();
            companyName = order.getJob().getCompany().getName();
            companyLogo = order.getJob().getCompany().getLogoUrl();
        } else if (order.getRecruiter() != null && order.getRecruiter().getCompanyId() != null) {
            companyId = order.getRecruiter().getCompanyId();
            var compOpt = companyRepository.findById(companyId);
            if (compOpt.isPresent()) {
                companyName = compOpt.get().getName();
                companyLogo = compOpt.get().getLogoUrl();
            }
        }

        String recruiterName = order.getRecruiter() != null ? order.getRecruiter().getFullName() : null;
        String recruiterEmail = null;
        if (order.getRecruiter() != null && order.getRecruiter().getAccountId() != null) {
            var accOpt = accountRepository.findById(order.getRecruiter().getAccountId());
            if (accOpt.isPresent()) {
                recruiterEmail = accOpt.get().getEmail();
            }
        }

        String paymentProvider = null;
        String providerTxnId = null;
        String paymentStatus = null;

        var payments = paymentTransactionRepository.findAllByOrder_IdOrderByCreatedAtDesc(order.getId());
        if (!payments.isEmpty()) {
            PaymentTransaction lastPayment = payments.get(0);
            paymentProvider = lastPayment.getProvider();
            providerTxnId = lastPayment.getProviderTxnId();
            paymentStatus = lastPayment.getStatus();
        }

        return new FeaturedOrderResponse(
                order.getId(),
                order.getRecruiter().getId(),
                recruiterName,
                recruiterEmail,
                companyId,
                companyName,
                companyLogo,
                order.getPlan().getId(),
                order.getPlan().getName(),
                durationDays,
                jobId,
                jobTitle,
                featuredUntil,
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                paymentProvider,
                providerTxnId,
                paymentStatus,
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getCancelledAt(),
                order.getRefundedAt()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không hỗ trợ SHA-256", ex);
        }
    }
}
