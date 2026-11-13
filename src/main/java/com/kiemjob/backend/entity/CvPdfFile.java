package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cv_pdf_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvPdfFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "bucket_name", nullable = false, length = 255)
    private String bucketName;

    @Column(name = "object_key", nullable = false,
            length = 1024, unique = true)
    private String objectKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (versionNo == null) {
            versionNo = 1;
        }
        if (isCurrent == null) {
            isCurrent = true;
        }
    }
}