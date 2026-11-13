package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "applications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_applications_job_cv",
            columnNames = {"jobpost_id", "cvid"}
        )
    },
    indexes = {
        @Index(name = "idx_applications_cvid", columnList = "cvid"),
        @Index(
            name = "idx_applications_job_status",
            columnList = "jobpost_id, status"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jobpost_id", nullable = false)
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cvid", nullable = false)
    private Cv cv;

    @Column(name = "cover_latter", columnDefinition = "TEXT")
    private String coverLatter;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null || status.isBlank()) {
            status = "pending";
        }
        if (appliedAt == null) {
            appliedAt = LocalDateTime.now();
        }
    }
}
