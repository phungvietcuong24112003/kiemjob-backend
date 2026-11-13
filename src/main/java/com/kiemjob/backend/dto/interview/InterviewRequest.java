package com.kiemjob.backend.dto.interview;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.kiemjob.backend.config.FlexibleLocalDateTimeDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record InterviewRequest(

    Long applicationId,

    @NotBlank(message = "Tên buổi phỏng vấn không được để trống")
    @Size(max = 255, message = "Tên buổi phỏng vấn tối đa 255 ký tự")
    String title,

    @NotNull(message = "Ngày giờ phỏng vấn không được để trống")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    LocalDateTime scheduledAt,

    String note

) {}
