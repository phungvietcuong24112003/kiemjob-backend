package com.kiemjob.backend.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Getter
public enum ApplicationStatus {
    PENDING("pending", "Chờ xử lý"),
    VIEWED("viewed", "Đã xem"),
    SHORTLISTED("shortlisted", "Qua vòng CV"),
    INTERVIEW("interview", "Phỏng vấn"),
    ACCEPTED("accepted", "Trúng tuyển"),
    REJECTED("rejected", "Từ chối");

    private final String value;
    private final String label;

    ApplicationStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(VIEWED, SHORTLISTED, REJECTED),
            VIEWED, EnumSet.of(SHORTLISTED, INTERVIEW, REJECTED),
            SHORTLISTED, EnumSet.of(INTERVIEW, REJECTED),
            INTERVIEW, EnumSet.of(ACCEPTED, REJECTED),
            ACCEPTED, EnumSet.of(REJECTED),
            REJECTED, EnumSet.of(SHORTLISTED) // Cho phép mở lại hồ sơ khi cần xem xét lại
    );

    public boolean canTransitionTo(ApplicationStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public static ApplicationStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trạng thái đơn ứng tuyển không được để trống");
        }
        String normalized = value.trim().toLowerCase();
        for (ApplicationStatus status : values()) {
            if (status.value.equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Trạng thái không hợp lệ: " + value +
                ". Các trạng thái được chấp nhận: pending, viewed, shortlisted, interview, accepted, rejected");
    }
}

