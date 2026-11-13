
package com.kiemjob.backend.dto.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationRequest {

    @NotNull(message = "CV ID không được để trống")
    @Positive(message = "CV ID phải lớn hơn 0")
    private Long cvId;

    @Size(max = 100000, message = "Thư ứng tuyển quá dài")
    private String coverLatter;
}
