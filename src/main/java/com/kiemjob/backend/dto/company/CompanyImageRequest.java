
package com.kiemjob.backend.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyImageRequest(

    @NotBlank(message = "URL ảnh không được để trống")
    @Size(max = 500, message = "URL ảnh tối đa 500 ký tự")
    @Pattern(
        regexp = "(?i)^https?://\\S+$",
        message = "URL ảnh phải bắt đầu bằng http:// hoặc https://"
    )
    String url
) {}
