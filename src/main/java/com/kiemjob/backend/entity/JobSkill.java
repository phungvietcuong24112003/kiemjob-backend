package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "job_skills",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_job_skills",
            columnNames = {"jobid", "skill_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_job_skills_skill_id",
            columnList = "skill_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class JobSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jobid", nullable = false)
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}