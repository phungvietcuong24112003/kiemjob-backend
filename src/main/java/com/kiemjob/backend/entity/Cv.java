
package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "cvs",
    indexes = {
        @Index(name = "idx_cvs_candidate_id", columnList = "candidate_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(nullable = false, length = 255)
    private String title;

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;
}
