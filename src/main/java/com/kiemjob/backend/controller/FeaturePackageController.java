package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.featuredorder.FeaturedOrderResponse;
import com.kiemjob.backend.dto.featurepackage.FeaturePackageActiveRequest;
import com.kiemjob.backend.dto.featurepackage.FeaturePackageRequest;
import com.kiemjob.backend.dto.featurepackage.FeaturePackageResponse;
import com.kiemjob.backend.service.FeaturePackageService;
import com.kiemjob.backend.service.FeaturedOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FeaturePackageController {

    private final FeaturePackageService featurePackageService;
    private final FeaturedOrderService featuredOrderService;

    // Người dùng xem các gói đang bán
    @GetMapping("/feature-packages")
    public List<FeaturePackageResponse> getActivePackages() {
        return featurePackageService.getActivePackages();
    }

    // Admin xem tất cả gói, kể cả gói đã ngừng bán
    @GetMapping("/admin/feature-packages")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FeaturePackageResponse> getAllPackages() {
        return featurePackageService.getAllPackages();
    }

    @GetMapping("/admin/feature-packages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FeaturePackageResponse getPackageById(
            @PathVariable Long id) {
        return featurePackageService.getPackageById(id);
    }

    @PostMapping("/admin/feature-packages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public FeaturePackageResponse createPackage(
            @Valid @RequestBody FeaturePackageRequest request) {
        return featurePackageService.createPackage(request);
    }

    @PutMapping("/admin/feature-packages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FeaturePackageResponse updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody FeaturePackageRequest request) {
        return featurePackageService.updatePackage(id, request);
    }

    @PatchMapping("/admin/feature-packages/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public FeaturePackageResponse updateActiveStatus(
            @PathVariable Long id,
            @Valid @RequestBody FeaturePackageActiveRequest request) {
        return featurePackageService.updateActiveStatus(id, request);
    }

    // Xóa mềm: ngừng bán, không xóa bản ghi khỏi database
    @DeleteMapping("/admin/feature-packages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivatePackage(@PathVariable Long id) {
        featurePackageService.deactivatePackage(id);
    }

    // Xóa hoàn toàn (hoặc tự động chuyển ngừng bán nếu đã có đơn hàng)
    @DeleteMapping("/admin/feature-packages/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletePackage(@PathVariable Long id) {
        featurePackageService.deletePackage(id);
    }

    // Admin xem danh sách tất cả đơn mua gói từ các nhà tuyển dụng
    @GetMapping("/admin/featured-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FeaturedOrderResponse> getAllOrders() {
        return featuredOrderService.getAllOrders();
    }

    // Admin xem chi tiết một đơn mua gói
    @GetMapping("/admin/featured-orders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FeaturedOrderResponse getOrderById(@PathVariable Long id) {
        return featuredOrderService.getOrderByIdForAdmin(id);
    }
}
