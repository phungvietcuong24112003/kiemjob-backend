package com.kiemjob.backend.dto.cv;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CvPdfResponse {

    private Long id;
    private Long cvId;
    private String fileName;
    private Long fileSizeBytes;
    private Integer versionNo;
    private Boolean isCurrent;
    private LocalDateTime createdAt;
}