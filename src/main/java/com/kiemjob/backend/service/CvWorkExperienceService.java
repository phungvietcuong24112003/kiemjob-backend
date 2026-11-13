package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvWorkExperienceRequest;
import com.kiemjob.backend.dto.cv.CvWorkExperienceResponse;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.entity.CvWorkExperience;
import com.kiemjob.backend.repository.CvRepository;
import com.kiemjob.backend.repository.CvWorkExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CvWorkExperienceService {

    private final CvWorkExperienceRepository experienceRepository;
    private final CvRepository cvRepository;

    @Transactional
    public CvWorkExperienceResponse create(
            Long candidateId,
            Long cvId,
            CvWorkExperienceRequest request) {

        Cv cv = getOwnedCv(candidateId, cvId);
        validateDates(request);

        CvWorkExperience experience = CvWorkExperience.builder()
                .cv(cv)
                .companyName(request.getCompanyName().trim())
                .position(request.getPosition().trim())
                .employmentType(normalize(request.getEmploymentType()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isCurrent(Boolean.TRUE.equals(request.getIsCurrent()))
                .description(normalize(request.getDescription()))
                .build();

        return CvWorkExperienceResponse.fromEntity(
                experienceRepository.save(experience)
        );
    }

    @Transactional(readOnly = true)
    public List<CvWorkExperienceResponse> getAll(
            Long candidateId,
            Long cvId) {

        getOwnedCv(candidateId, cvId);

        return experienceRepository
                .findByCv_IdOrderByStartDateDesc(cvId)
                .stream()
                .map(CvWorkExperienceResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CvWorkExperienceResponse getById(
            Long candidateId,
            Long cvId,
            Long experienceId) {

        getOwnedCv(candidateId, cvId);

        CvWorkExperience experience =
                getExperience(experienceId, cvId);

        return CvWorkExperienceResponse.fromEntity(experience);
    }

    @Transactional
    public CvWorkExperienceResponse update(
            Long candidateId,
            Long cvId,
            Long experienceId,
            CvWorkExperienceRequest request) {

        getOwnedCv(candidateId, cvId);
        validateDates(request);

        CvWorkExperience experience =
                getExperience(experienceId, cvId);

        experience.setCompanyName(request.getCompanyName().trim());
        experience.setPosition(request.getPosition().trim());
        experience.setEmploymentType(
                normalize(request.getEmploymentType())
        );
        experience.setStartDate(request.getStartDate());
        experience.setEndDate(request.getEndDate());
        experience.setIsCurrent(
                Boolean.TRUE.equals(request.getIsCurrent())
        );
        experience.setDescription(normalize(request.getDescription()));

        return CvWorkExperienceResponse.fromEntity(
                experienceRepository.save(experience)
        );
    }

    @Transactional
    public void delete(
            Long candidateId,
            Long cvId,
            Long experienceId) {

        getOwnedCv(candidateId, cvId);

        CvWorkExperience experience =
                getExperience(experienceId, cvId);

        experienceRepository.delete(experience);
    }

    private Cv getOwnedCv(Long candidateId, Long cvId) {
        return cvRepository.findByIdAndCandidate_Id(cvId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy CV thuộc ứng viên này"
                ));
    }

    private CvWorkExperience getExperience(
            Long experienceId,
            Long cvId) {

        return experienceRepository
                .findByIdAndCv_Id(experienceId, cvId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kinh nghiệm làm việc trong CV này"
                ));
    }

    private void validateDates(CvWorkExperienceRequest request) {
        if (request.getStartDate() != null
                && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }

        if (Boolean.TRUE.equals(request.getIsCurrent())
                && request.getEndDate() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đang làm việc thì ngày kết thúc phải để trống"
            );
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}