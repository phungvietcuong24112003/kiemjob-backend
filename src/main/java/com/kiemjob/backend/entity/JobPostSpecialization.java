package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "job_post_specializations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_job_post_specializations",
            columnNames = {"job_post_id", "specialization_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_job_post_specializations_specialization_id",
            columnList = "specialization_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostSpecialization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialization_id", nullable = false)
    private JobSpecialization specialization;
}