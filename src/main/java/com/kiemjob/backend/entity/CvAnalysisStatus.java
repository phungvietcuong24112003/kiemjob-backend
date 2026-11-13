package com.kiemjob.backend.entity;

import lombok.Getter;

@Getter
public enum CvAnalysisStatus {
    PENDING("pending", "Chờ phân tích"),
    PROCESSING("processing", "Đang phân tích"),
    COMPLETED("completed", "Đã phân tích xong"),
    FAILED("failed", "Phân tích thất bại");

    private final String value;
    private final String label;

    CvAnalysisStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static CvAnalysisStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().toLowerCase();
        for (CvAnalysisStatus status : values()) {
            if (status.value.equalsIgnoreCase(normalized) || status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        return PENDING;
    }
}

