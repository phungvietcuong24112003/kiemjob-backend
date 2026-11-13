package com.kiemjob.backend.service;

import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.ApplicationRepository;
import com.kiemjob.backend.repository.CvRepository;
import com.kiemjob.backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CvOwnershipService {

    private final CvRepository cvRepository;
    private final ApplicationRepository applicationRepository;
    private final CurrentUserService currentUserService;

    public void checkOwnership(Long cvId, Long accountId) {
        if (!cvRepository.existsOwnedCv(cvId, accountId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy CV hoặc bạn không có quyền truy cập"
            );
        }
    }

    public void checkOwnershipByCandidate(Long cvId, Long candidateId) {
        if (!cvRepository.existsByIdAndCandidate_Id(cvId, candidateId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy CV thuộc ứng viên này"
            );
        }
    }

    /**
     * Kiểm tra ứng viên hiện tại có sở hữu candidateId trong URL hay không.
     * ADMIN luôn được phép.
     */
    public void checkCandidateOwnership(Long candidateId) {
        if (currentUserService.isAdmin()) {
            return;
        }
        Candidate candidate = currentUserService.requireCandidate();
        if (candidateId != null && !candidate.getId().equals(candidateId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy ứng viên hoặc bạn không có quyền truy cập"
            );
        }
    }

    /**
     * Kiểm tra quyền đọc thông tin ứng viên (cho Candidate chủ sở hữu, Admin hoặc Recruiter nếu ứng viên đã ứng tuyển).
     */
    public void checkCandidateReadAccess(Long candidateId) {
        if (currentUserService.isAdmin()) {
            return;
        }
        if (currentUserService.isCandidate()) {
            Candidate candidate = currentUserService.requireCandidate();
            if (candidateId != null && !candidate.getId().equals(candidateId)) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy ứng viên hoặc bạn không có quyền truy cập"
                );
            }
            return;
        }
        if (currentUserService.isRecruiter()) {
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (candidateId != null && recruiter.getCompanyId() != null
                    && applicationRepository.existsByCv_Candidate_IdAndJobPost_Company_Id(candidateId, recruiter.getCompanyId())) {
                return;
            }
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy ứng viên hoặc bạn không có quyền truy cập"
            );
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
    }

    /**
     * Kiểm tra quyền đọc CV:
     * - ADMIN: đọc được tất cả
     * - CANDIDATE: chỉ đọc CV của chính mình
     * - RECRUITER: chỉ đọc được CV của ứng viên đã nộp đơn vào bài đăng thuộc công ty mình
     *
     * Trả 404 nếu CV không tồn tại, 403 nếu không có quyền truy cập.
     */
    public void checkCvReadAccess(Long cvId, Long candidateId) {
        if (currentUserService.isAdmin()) {
            ensureCvExists(cvId);
            return;
        }

        if (currentUserService.isCandidate()) {
            ensureCvExists(cvId);
            Candidate candidate = currentUserService.requireCandidate();
            if (candidateId != null && !candidate.getId().equals(candidateId)) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền truy cập CV này"
                );
            }
            if (!cvRepository.existsByIdAndCandidate_Id(cvId, candidate.getId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền truy cập CV này"
                );
            }
            return;
        }

        if (currentUserService.isRecruiter()) {
            ensureCvExists(cvId);
            Optional<Cv> cvOpt = cvRepository.findById(cvId);
            if (cvOpt.isPresent() && Boolean.TRUE.equals(cvOpt.get().getIsPublic())) {
                return;
            }
            Recruiter recruiter = currentUserService.requireRecruiter();
            if (recruiter.getCompanyId() != null
                    && applicationRepository.existsByCv_IdAndJobPost_Company_Id(cvId, recruiter.getCompanyId())) {
                return;
            }
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập CV này"
            );
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem CV");
    }

    /**
     * Kiểm tra quyền ghi (tạo, sửa, xóa) CV:
     * - ADMIN: được phép
     * - CANDIDATE: chỉ thao tác trên CV của chính mình
     * - RECRUITER / Khác: bị chặn hoàn toàn
     *
     * Trả 404 nếu CV không tồn tại, 403 nếu không có quyền.
     */
    public void checkCvWriteAccess(Long cvId, Long candidateId) {
        if (currentUserService.isAdmin()) {
            if (cvId != null) {
                ensureCvExists(cvId);
            }
            return;
        }

        Candidate candidate = currentUserService.requireCandidate();
        if (candidateId != null && !candidate.getId().equals(candidateId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn không có quyền thao tác trên CV này"
            );
        }
        if (cvId != null) {
            ensureCvExists(cvId);
            if (!cvRepository.existsByIdAndCandidate_Id(cvId, candidate.getId())) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thao tác trên CV này"
                );
            }
        }
    }

    /**
     * Kiểm tra CV có tồn tại hay không. Trả 404 nếu không tìm thấy.
     */
    private void ensureCvExists(Long cvId) {
        if (cvId != null && !cvRepository.existsById(cvId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy CV"
            );
        }
    }
}
