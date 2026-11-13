package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CvAwardRequest {

    @NotNull
    private LocalDate awardDate;

    @NotBlank
    @Size(max = 255)
    private String awardName;

    private String description;
}