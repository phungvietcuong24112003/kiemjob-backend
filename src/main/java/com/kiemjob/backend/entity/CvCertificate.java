package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "cv_certificate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "certificate_date", nullable = false)
    private LocalDate certificateDate;

    @Column(name = "certificate_name", nullable = false, length = 255)
    private String certificateName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}