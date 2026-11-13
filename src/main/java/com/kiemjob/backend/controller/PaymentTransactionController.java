package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.payment.PaymentCreateRequest;
import com.kiemjob.backend.dto.payment.PaymentResponse;
import com.kiemjob.backend.service.PaymentTransactionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PaymentTransactionController {

    private final PaymentTransactionService paymentService;

    public PaymentTransactionController(
            PaymentTransactionService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping("/featured-orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECRUITER')")
    public PaymentResponse createPayment(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentCreateRequest request,
            Authentication authentication
    ) {
        return paymentService.createPayment(
                authentication.getName(),
                orderId,
                request,
                idempotencyKey
        );
    }

    @GetMapping("/featured-orders/{orderId}/payments")
    @PreAuthorize("hasRole('RECRUITER')")
    public List<PaymentResponse> getOrderPayments(
            @PathVariable Long orderId,
            Authentication authentication
    ) {
        return paymentService.getOrderPayments(
                authentication.getName(),
                orderId
        );
    }

    @GetMapping("/admin/featured-orders/{orderId}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentResponse> getOrderPaymentsForAdmin(
            @PathVariable Long orderId
    ) {
        return paymentService.getOrderPaymentsForAdmin(orderId);
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public PaymentResponse getPayment(
            @PathVariable Long paymentId,
            Authentication authentication
    ) {
        return paymentService.getPayment(
                authentication.getName(),
                paymentId
        );
    }
}
