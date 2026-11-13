package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CvProjectRequest {

    @NotBlank
    @Size(max = 255)
    private String projectName;

    @Size(max = 255)
    private String role;

    private LocalDate startDate;
    private LocalDate endDate;

    private Boolean isCurrent = false;

    private String description;
    private String technologies;

    @Size(max = 500)
    private String projectUrl;

    @Size(max = 500)
    private String repositoryUrl;

    @AssertTrue(message = "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu")
    public boolean isDateRangeValid() {
        return startDate == null
            || endDate == null
            || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Dự án đang thực hiện không được có ngày kết thúc")
    public boolean isCurrentProjectValid() {
        return !Boolean.TRUE.equals(isCurrent) || endDate == null;
    }
}