
package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "tpye", nullable = false, length = 30)
    private String type;

    @Column(name = "link", nullable = false, length = 500)
    private String link;
}
