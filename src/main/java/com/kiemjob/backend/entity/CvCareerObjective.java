package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cv_career_objectives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvCareerObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false, unique = true)
    private Long cvId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}