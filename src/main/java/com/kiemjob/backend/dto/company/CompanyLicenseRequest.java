package com.kiemjob.backend.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyLicenseRequest(
        @NotBlank(message = "File URL không được để trống")
        @Size(max = 500, message = "File URL tối đa 500 ký tự")
        String fileUrl
) {
}
