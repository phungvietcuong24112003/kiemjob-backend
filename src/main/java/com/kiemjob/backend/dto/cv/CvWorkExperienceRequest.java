package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CvWorkExperienceRequest {

    @NotBlank(message = "Tên công ty không được để trống")
    @Size(max = 255, message = "Tên công ty tối đa 255 ký tự")
    private String companyName;

    @NotBlank(message = "Vị trí công việc không được để trống")
    @Size(max = 255, message = "Vị trí tối đa 255 ký tự")
    private String position;

    @Size(max = 50, message = "Loại hình làm việc tối đa 50 ký tự")
    private String employmentType;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isCurrent;

    private String description;
}