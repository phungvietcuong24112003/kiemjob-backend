package com.kiemjob.backend.specification;

import com.kiemjob.backend.entity.Application;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.JobPost;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ApplicationSpecification {

    public static Specification<Application> filterApplications(
            Long jobPostId,
            Long companyId,
            String status,
            String keyword
    ) {
        return (Root<Application> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Lọc theo jobPostId
            if (jobPostId != null) {
                predicates.add(cb.equal(root.get("jobPost").get("id"), jobPostId));
            }

            // 2. Lọc theo companyId (bài đăng thuộc công ty recruiter)
            if (companyId != null) {
                Join<Application, JobPost> jobJoin = root.join("jobPost", JoinType.INNER);
                Join<JobPost, Company> companyJoin = jobJoin.join("company", JoinType.INNER);
                predicates.add(cb.equal(companyJoin.get("id"), companyId));
            }

            // 3. Lọc theo trạng thái đơn
            if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status.trim())) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase()));
            }

            // 4. Tìm kiếm keyword theo tên ứng viên hoặc tiêu đề CV
            if (keyword != null && !keyword.isBlank()) {
                String searchPattern = "%" + keyword.trim().toLowerCase() + "%";
                Join<Application, Cv> cvJoin = root.join("cv", JoinType.LEFT);
                Join<Cv, Candidate> candidateJoin = cvJoin.join("candidate", JoinType.LEFT);

                Predicate nameMatch = cb.like(cb.lower(candidateJoin.get("fullname")), searchPattern);
                Predicate cvTitleMatch = cb.like(cb.lower(cvJoin.get("title")), searchPattern);

                predicates.add(cb.or(nameMatch, cvTitleMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
