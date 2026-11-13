package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "cv_section_orders",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_cv_section_orders",
        columnNames = {"cv_id", "section_type"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvSectionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "section_type", nullable = false, length = 50)
    private String sectionType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}