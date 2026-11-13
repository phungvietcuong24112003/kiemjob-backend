package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.province.ProvinceRequest;
import com.kiemjob.backend.entity.Province;
import com.kiemjob.backend.service.ProvinceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provinces")
@RequiredArgsConstructor
public class ProvinceController {

    private final ProvinceService provinceService;

    @GetMapping
    public List<Province> getAllProvinces() {
        return provinceService.getAllProvinces();
    }

    @GetMapping("/{id}")
    public Province getProvinceById(@PathVariable Long id) {
        return provinceService.getProvinceById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Province createProvince(
            @Valid @RequestBody ProvinceRequest request) {
        return provinceService.createProvince(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Province updateProvince(
            @PathVariable Long id,
            @Valid @RequestBody ProvinceRequest request) {
        return provinceService.updateProvince(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteProvince(@PathVariable Long id) {
        provinceService.deleteProvince(id);
    }
}