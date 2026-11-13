package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "cv_awards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "award_date", nullable = false)
    private LocalDate awardDate;

    @Column(name = "award_name", nullable = false, length = 255)
    private String awardName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}