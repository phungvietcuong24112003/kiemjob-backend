package com.kiemjob.backend.specification;

import com.kiemjob.backend.entity.JobAddress;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.JobPostSpecialization;
import com.kiemjob.backend.entity.JobSkill;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JobPostSpecification {

    public static Specification<JobPost> filterJobs(
            String keyword,
            String status,
            Long companyId,
            Long categoryId,
            Long provinceId,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String workingMode,
            List<Long> skillIds,
            Long specializationId,
            boolean isAdmin,
            boolean isOwnerRecruiter,
            String sort
    ) {
        return (Root<JobPost> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Phân quyền và trạng thái tin (status & applicationDeadline)
            if (!isAdmin && !isOwnerRecruiter) {
                predicates.add(cb.equal(root.get("status"), "open"));
                predicates.add(cb.or(
                        cb.isNull(root.get("applicationDeadline")),
                        cb.greaterThanOrEqualTo(root.get("applicationDeadline"), LocalDate.now())
                ));
            } else {
                if (status != null && !status.isBlank()) {
                    predicates.add(cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase()));
                }
            }

            // 2. Keyword: tìm theo title, company name, hoặc skill name
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titlePredicate = cb.like(cb.lower(root.get("title")), pattern);
                Predicate companyPredicate = cb.like(cb.lower(root.get("company").get("name")), pattern);

                Subquery<Long> skillSubquery = query.subquery(Long.class);
                Root<JobSkill> jsRoot = skillSubquery.from(JobSkill.class);
                skillSubquery.select(cb.literal(1L));
                skillSubquery.where(
                        cb.equal(jsRoot.get("jobPost"), root),
                        cb.like(cb.lower(jsRoot.get("skill").get("name")), pattern)
                );

                predicates.add(cb.or(titlePredicate, companyPredicate, cb.exists(skillSubquery)));
            }

            // 3. Company & Category
            if (companyId != null) {
                predicates.add(cb.equal(root.get("company").get("id"), companyId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // 4. Province
            if (provinceId != null) {
                Subquery<Long> addrSubquery = query.subquery(Long.class);
                Root<JobAddress> jaRoot = addrSubquery.from(JobAddress.class);
                addrSubquery.select(cb.literal(1L));
                addrSubquery.where(
                        cb.equal(jaRoot.get("jobPost"), root),
                        cb.equal(jaRoot.get("province").get("id"), provinceId)
                );
                predicates.add(cb.exists(addrSubquery));
            }

            // 5. Working Mode (onsite, remote, hybrid)
            if (workingMode != null && !workingMode.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("workingMode")), workingMode.trim().toLowerCase()));
            }

            // 6. Salary Min & Max
            if (salaryMin != null) {
                Expression<BigDecimal> effectiveMax = cb.coalesce(root.get("salaryMax"), root.get("salaryMin"));
                predicates.add(cb.greaterThanOrEqualTo(effectiveMax, salaryMin));
            }
            if (salaryMax != null) {
                Expression<BigDecimal> effectiveMin = cb.coalesce(root.get("salaryMin"), root.get("salaryMax"));
                predicates.add(cb.lessThanOrEqualTo(effectiveMin, salaryMax));
            }

            // 7. Skill IDs
            if (skillIds != null && !skillIds.isEmpty()) {
                Subquery<Long> skillIdsSubquery = query.subquery(Long.class);
                Root<JobSkill> jsRoot = skillIdsSubquery.from(JobSkill.class);
                skillIdsSubquery.select(cb.literal(1L));
                skillIdsSubquery.where(
                        cb.equal(jsRoot.get("jobPost"), root),
                        jsRoot.get("skill").get("id").in(skillIds)
                );
                predicates.add(cb.exists(skillIdsSubquery));
            }

            // 8. Specialization ID
            if (specializationId != null) {
                Subquery<Long> specSubquery = query.subquery(Long.class);
                Root<JobPostSpecialization> jpsRoot = specSubquery.from(JobPostSpecialization.class);
                specSubquery.select(cb.literal(1L));
                specSubquery.where(
                        cb.equal(jpsRoot.get("jobPost"), root),
                        cb.equal(jpsRoot.get("specialization").get("id"), specializationId)
                );
                predicates.add(cb.exists(specSubquery));
            }

            // 9. Sorting & Order:
            // Chỉ áp dụng orderBy cho SELECT query (tránh lỗi trên count query của Spring Data JPA)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Expression<Integer> featuredRank = cb.<Integer>selectCase()
                        .when(
                                cb.and(
                                        cb.isTrue(root.get("isFeatured")),
                                        cb.or(
                                                cb.isNull(root.get("featuredUntil")),
                                                cb.greaterThan(root.get("featuredUntil"), LocalDateTime.now())
                                        )
                                ),
                                1
                        )
                        .otherwise(0);

                String sortOption = (sort != null) ? sort.trim().toLowerCase() : "newest";
                switch (sortOption) {
                    case "salary_desc":
                        Expression<BigDecimal> sortSalary = cb.coalesce(root.get("salaryMax"), root.get("salaryMin"));
                        query.orderBy(
                                cb.desc(featuredRank),
                                cb.desc(sortSalary),
                                cb.desc(root.get("postedAt")),
                                cb.desc(root.get("id"))
                        );
                        break;
                    case "featured":
                        query.orderBy(
                                cb.desc(featuredRank),
                                cb.desc(root.get("featuredUntil")),
                                cb.desc(root.get("postedAt")),
                                cb.desc(root.get("id"))
                        );
                        break;
                    case "newest":
                    case "latest":
                    default:
                        query.orderBy(
                                cb.desc(featuredRank),
                                cb.desc(root.get("postedAt")),
                                cb.desc(root.get("id"))
                        );
                        break;
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

