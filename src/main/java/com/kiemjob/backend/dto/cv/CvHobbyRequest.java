package com.kiemjob.backend.dto.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CvHobbyRequest {

    @NotBlank
    @Size(max = 255)
    private String hobbyName;
}