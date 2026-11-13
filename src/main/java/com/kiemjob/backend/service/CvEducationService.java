package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvEducationRequest;
import com.kiemjob.backend.dto.cv.CvEducationResponse;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.CvEducation;
import com.kiemjob.backend.repository.CvEducationRepository;
import com.kiemjob.backend.repository.CvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CvEducationService {

    private final CvEducationRepository educationRepository;
    private final CvRepository cvRepository;

    public List<CvEducationResponse> getAll(
            Long candidateId, Long cvId) {

        findOwnedCv(candidateId, cvId);

        return educationRepository.findByCv_IdOrderByStartDateDesc(cvId)
                .stream()
                .map(CvEducationResponse::fromEntity)
                .toList();
    }

    public CvEducationResponse getById(
            Long candidateId, Long cvId, Long educationId) {

        findOwnedCv(candidateId, cvId);

        return CvEducationResponse.fromEntity(
                findEducation(cvId, educationId)
        );
    }

    @Transactional
    public CvEducationResponse create(
            Long candidateId,
            Long cvId,
            CvEducationRequest request) {

        Cv cv = findOwnedCv(candidateId, cvId);
        validateDates(request);

        CvEducation education = CvEducation.builder()
                .cv(cv)
                .schoolName(request.getSchoolName().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .rank(normalize(request.getRank()))
                .achivements(normalize(request.getAchivements()))
                .build();

        return CvEducationResponse.fromEntity(
                educationRepository.save(education)
        );
    }

    @Transactional
    public CvEducationResponse update(
            Long candidateId,
            Long cvId,
            Long educationId,
            CvEducationRequest request) {

        findOwnedCv(candidateId, cvId);

        CvEducation education = findEducation(cvId, educationId);
        validateDates(request);

        education.setSchoolName(request.getSchoolName().trim());
        education.setStartDate(request.getStartDate());
        education.setEndDate(request.getEndDate());
        education.setRank(normalize(request.getRank()));
        education.setAchivements(normalize(request.getAchivements()));

        return CvEducationResponse.fromEntity(
                educationRepository.save(education)
        );
    }

    @Transactional
    public void delete(
            Long candidateId, Long cvId, Long educationId) {

        findOwnedCv(candidateId, cvId);

        educationRepository.delete(findEducation(cvId, educationId));
    }

    private Cv findOwnedCv(Long candidateId, Long cvId) {
        return cvRepository.findByIdAndCandidate_Id(cvId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy CV của ứng viên này"
                ));
    }

    private CvEducation findEducation(Long cvId, Long educationId) {
        return educationRepository.findByIdAndCv_Id(educationId, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông tin học vấn"
                ));
    }

    private void validateDates(CvEducationRequest request) {
        if (request.getStartDate() != null
                && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu"
            );
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}