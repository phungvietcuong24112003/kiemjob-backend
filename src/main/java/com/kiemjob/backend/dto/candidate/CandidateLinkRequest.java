
package com.kiemjob.backend.dto.candidate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CandidateLinkRequest(

    @NotBlank(message = "Loại liên kết không được để trống")
    @Size(max = 30, message = "Loại liên kết tối đa 30 ký tự")
    String type,

    @NotBlank(message = "Đường dẫn không được để trống")
    @Size(max = 500, message = "Đường dẫn tối đa 500 ký tự")
    @Pattern(
        regexp = "(?i)^https?://\\S+$",
        message = "Đường dẫn phải bắt đầu bằng http:// hoặc https://"
    )
    String link
) {
}
