
package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "url", nullable = false, length = 500)
    private String url;
}
