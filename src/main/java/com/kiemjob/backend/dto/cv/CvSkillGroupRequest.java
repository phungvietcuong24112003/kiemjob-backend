package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvSkillGroupRequest {

    @NotBlank(message = "Tên nhóm kỹ năng không được để trống")
    @Size(max = 100, message = "Tên nhóm kỹ năng tối đa 100 ký tự")
    private String title;

    @NotBlank(message = "Nội dung kỹ năng không được để trống")
    private String content;

    @PositiveOrZero(message = "Thứ tự hiển thị không được âm")
    private Integer displayOrder;
}