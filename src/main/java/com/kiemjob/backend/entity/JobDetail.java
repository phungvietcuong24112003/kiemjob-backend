package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "job_details",
    indexes = {
        @Index(
            name = "idx_job_details_jobid",
            columnList = "jobid"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class JobDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jobid", nullable = false)
    private JobPost jobPost;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
}