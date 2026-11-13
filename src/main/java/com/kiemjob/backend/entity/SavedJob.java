
package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "saved_jobs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_saved_jobs",
            columnNames = {"jobid", "candidated_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_saved_jobs_candidated_id",
            columnList = "candidated_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class SavedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jobid", nullable = false)
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidated_id", nullable = false)
    private Candidate candidate;
}
