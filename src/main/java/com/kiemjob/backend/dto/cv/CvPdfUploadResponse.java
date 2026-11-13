package com.kiemjob.backend.dto.cv;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CvPdfUploadResponse {

    private CvPdfResponse file;
    private String downloadUrl;
}