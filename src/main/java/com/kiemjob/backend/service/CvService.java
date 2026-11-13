package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvRequest;
import com.kiemjob.backend.dto.cv.CvResponse;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.event.CvAnalysisRequestedEvent;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.CvPdfFileRepository;
import com.kiemjob.backend.repository.CvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CvService {

    public static final int MAX_CVS_PER_CANDIDATE = 3;

    private final CvRepository cvRepository;
    private final CandidateRepository candidateRepository;
    private final CvPdfFileRepository cvPdfFileRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<CvResponse> getMyCvs(Long candidateId) {
        ensureCandidateExists(candidateId);

        return cvRepository.findByCandidate_IdOrderByIdDesc(candidateId)
                .stream()
                .map(this::toCvResponse)
                .toList();
    }

    public CvResponse getById(Long candidateId, Long cvId) {
        return toCvResponse(findCv(candidateId, cvId));
    }

    @Transactional
    public CvResponse create(Long candidateId, CvRequest request) {
        Candidate candidate = ensureCandidateExists(candidateId);

        long currentCount = cvRepository.countByCandidate_Id(candidateId);
        if (currentCount >= MAX_CVS_PER_CANDIDATE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mỗi tài khoản chỉ được lưu tối đa 3 CV. Vui lòng xóa bớt CV cũ trước khi tạo mới."
            );
        }

        boolean isPublic = Boolean.TRUE.equals(request.getIsPublic());
        if (isPublic) {
            cvRepository.resetOtherPublicCvs(candidateId, null);
        }

        Cv cv = Cv.builder()
                .candidate(candidate)
                .title(request.getTitle().trim())
                .isPublic(isPublic)
                .build();

        Cv saved = cvRepository.save(cv);
        if (isPublic) {
            eventPublisher.publishEvent(new CvAnalysisRequestedEvent(saved.getId(), "CREATE_PUBLIC"));
        }
        return toCvResponse(saved);
    }

    @Transactional
    public CvResponse update(
            Long candidateId,
            Long cvId,
            CvRequest request) {

        Cv cv = findCv(candidateId, cvId);
        cv.setTitle(request.getTitle().trim());

        boolean becamePublic = false;
        if (request.getIsPublic() != null) {
            if (Boolean.TRUE.equals(request.getIsPublic())) {
                cvRepository.resetOtherPublicCvs(candidateId, cvId);
                cv.setIsPublic(true);
                becamePublic = true;
            } else {
                cv.setIsPublic(false);
            }
        }

        Cv saved = cvRepository.save(cv);
        if (becamePublic) {
            eventPublisher.publishEvent(new CvAnalysisRequestedEvent(cvId, "UPDATE_PUBLIC"));
        }
        return toCvResponse(saved);
    }

    @Transactional
    public CvResponse setPublicStatus(Long candidateId, Long cvId, boolean isPublic) {
        Cv cv = findCv(candidateId, cvId);

        if (isPublic) {
            cvRepository.resetOtherPublicCvs(candidateId, cvId);
            cv.setIsPublic(true);
        } else {
            cv.setIsPublic(false);
        }

        Cv saved = cvRepository.save(cv);
        if (isPublic) {
            eventPublisher.publishEvent(new CvAnalysisRequestedEvent(cvId, "SET_PUBLIC"));
        }
        return toCvResponse(saved);
    }

    @Transactional
    public void delete(Long candidateId, Long cvId) {
        Cv cv = findCv(candidateId, cvId);
        cvRepository.delete(cv);
    }

    private CvResponse toCvResponse(Cv cv) {
        boolean hasPdf = cv.getId() != null && cvPdfFileRepository.existsByCvIdAndIsCurrentTrue(cv.getId());
        return CvResponse.fromEntity(cv, hasPdf);
    }

    private Cv findCv(Long candidateId, Long cvId) {
        return cvRepository.findByIdAndCandidate_Id(cvId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy CV của ứng viên này"
                ));
    }

    private Candidate ensureCandidateExists(Long candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy ứng viên"
                ));
    }
}
