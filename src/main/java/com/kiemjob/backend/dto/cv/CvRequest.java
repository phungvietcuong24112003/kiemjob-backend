package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvRequest {

    @NotBlank(message = "Tiêu đề CV không được để trống")
    @Size(max = 255, message = "Tiêu đề CV tối đa 255 ký tự")
    private String title;

    private Boolean isPublic;
}
