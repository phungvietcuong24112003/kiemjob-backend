package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.featuredorder.FeaturedOrderRequest;
import com.kiemjob.backend.dto.featuredorder.FeaturedOrderResponse;
import com.kiemjob.backend.service.FeaturedOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/featured-orders")
@PreAuthorize("hasRole('RECRUITER')")
public class FeaturedOrderController {

    private final FeaturedOrderService featuredOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeaturedOrderResponse createOrder(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FeaturedOrderRequest request) {

        String email = getEmail(authentication);

        return featuredOrderService.createOrder(
                email,
                idempotencyKey,
                request
        );
    }

    @GetMapping
    public List<FeaturedOrderResponse> getMyOrders(
            Authentication authentication) {

        return featuredOrderService.getMyOrders(
                getEmail(authentication)
        );
    }

    @GetMapping("/{orderId}")
    public FeaturedOrderResponse getMyOrder(
            Authentication authentication,
            @PathVariable Long orderId) {

        return featuredOrderService.getMyOrder(
                getEmail(authentication),
                orderId
        );
    }

    private String getEmail(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Bạn chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"
            );
        }

        return authentication.getName();
    }
}