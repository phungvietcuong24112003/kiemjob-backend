package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "job_specializations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_job_specializations",
            columnNames = {"id_job_category", "name"}
        )
    },
    indexes = {
        @Index(
            name = "idx_job_specializations_category",
            columnList = "id_job_category"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSpecialization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_job_category", nullable = false)
    private JobsCategory jobCategory;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}