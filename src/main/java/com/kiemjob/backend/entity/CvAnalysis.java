package com.kiemjob.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "cv_analysis",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_cv_analysis_cv_id",
            columnNames = {"cv_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_cv_analysis_status",
            columnList = "analysis_status"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cv_id", nullable = false, unique = true)
    private Cv cv;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_data", columnDefinition = "jsonb")
    private JsonNode parsedData;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "suggestions", columnDefinition = "TEXT")
    private String suggestions;

    @Builder.Default
    @Column(name = "analysis_status", nullable = false, length = 20)
    private String analysisStatus = "pending";

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (analysisStatus == null || analysisStatus.isBlank()) {
            analysisStatus = "pending";
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
