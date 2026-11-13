package com.kiemjob.backend.entity;

import lombok.Getter;

@Getter
public enum AiScoreStatus {
    NOT_SCORED("NOT_SCORED", "Chưa chấm"),
    PENDING("PENDING", "Đang chấm"),
    SUCCESS("SUCCESS", "Chấm thành công"),
    FAILED("FAILED", "Lỗi - thử lại"),
    NO_TEXT("NO_TEXT", "CV không đọc được text");

    private final String value;
    private final String label;

    AiScoreStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static AiScoreStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return NOT_SCORED;
        }
        String normalized = value.trim().toUpperCase();
        for (AiScoreStatus status : values()) {
            if (status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        return NOT_SCORED;
    }
}

