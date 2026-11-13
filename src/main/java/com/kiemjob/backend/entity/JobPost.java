package com.kiemjob.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_posts")
@Getter
@Setter
@NoArgsConstructor
public class JobPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private JobsCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postedby", nullable = false)
    private Recruiter postedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "educational_requirement", length = 255)
    private String educationalRequirement;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "vacancies", nullable = false)
    private Integer vacancies = 1;

    @Column(name = "workingmode", nullable = false, length = 20)
    private String workingMode;

    @Column(name = "salary_min", precision = 15, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 15, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured = false;

    @Column(name = "featured_until")
    private LocalDateTime featuredUntil;

    @Column(name = "expiration_reminder_sent", nullable = false)
    private Boolean expirationReminderSent = false;

    @Column(name = "featured_reminder_sent", nullable = false)
    private Boolean featuredReminderSent = false;
}
