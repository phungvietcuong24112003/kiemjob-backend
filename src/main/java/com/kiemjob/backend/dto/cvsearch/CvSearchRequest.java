package com.kiemjob.backend.dto.cvsearch;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvSearchRequest {

    @NotBlank(message = "Câu tìm kiếm không được để trống")
    @Size(max = 300, message = "Câu tìm kiếm không được vượt quá 300 ký tự")
    private String query;

    @Min(value = 1, message = "Số lượng kết quả tối thiểu là 1")
    @Max(value = 20, message = "Số lượng kết quả tối đa là 20")
    @Builder.Default
    private Integer size = 10;
}

