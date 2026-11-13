package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "cv_edudation",
    indexes = {
        @Index(name = "idx_cv_edudation_cvid", columnList = "cvid")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cvid", nullable = false)
    private Cv cv;

    @Column(name = "school_name", nullable = false, length = 255)
    private String schoolName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "rank", length = 50)
    private String rank;

    @Column(name = "achivements", columnDefinition = "TEXT")
    private String achivements;
}