package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CvCertificateRequest {

    @NotNull
    private LocalDate certificateDate;

    @NotBlank
    @Size(max = 255)
    private String certificateName;

    private String description;
}