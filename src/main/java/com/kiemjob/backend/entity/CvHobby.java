package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "cv_hobbies",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_cv_hobbies",
        columnNames = {"cv_id", "hobby_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CvHobby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "hobby_name", nullable = false, length = 255)
    private String hobbyName;
}