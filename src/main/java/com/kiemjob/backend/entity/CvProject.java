package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "cv_projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "role", length = 255)
    private String role;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Builder.Default
    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "technologies", columnDefinition = "TEXT")
    private String technologies;

    @Column(name = "project_url", length = 500)
    private String projectUrl;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;
}