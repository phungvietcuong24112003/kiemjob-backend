package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CvEducationRequest {

    @jakarta.validation.constraints.NotBlank(
        message = "Tên trường không được để trống"
    )
    @Size(max = 255, message = "Tên trường tối đa 255 ký tự")
    private String schoolName;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 50, message = "Xếp loại tối đa 50 ký tự")
    private String rank;

    private String achivements;
}