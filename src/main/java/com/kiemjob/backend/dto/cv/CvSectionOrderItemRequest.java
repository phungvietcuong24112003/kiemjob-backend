package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CvSectionOrderItemRequest {

    @NotBlank
    @Size(max = 50)
    private String sectionType;

    @NotNull
    @PositiveOrZero
    private Integer displayOrder;
}