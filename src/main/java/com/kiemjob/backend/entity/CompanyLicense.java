package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_license")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;
}
