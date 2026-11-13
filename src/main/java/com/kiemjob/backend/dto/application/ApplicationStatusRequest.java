
package com.kiemjob.backend.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationStatusRequest {

    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(
        regexp = "pending|viewed|shortlisted|interview|rejected|accepted",
        message = "Trạng thái không hợp lệ"
    )
    private String status;
}
