package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "job_address",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_job_address",
            columnNames = {"job_post_id", "province_id", "address_detail"}
        )
    },
    indexes = {
        @Index(
            name = "idx_job_address_province_id",
            columnList = "province_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;
}