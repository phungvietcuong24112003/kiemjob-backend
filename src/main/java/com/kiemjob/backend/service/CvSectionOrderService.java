package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.*;
import com.kiemjob.backend.entity.CvSectionOrder;
import com.kiemjob.backend.repository.CvSectionOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class CvSectionOrderService {

    private final CvSectionOrderRepository repository;
    private final CvOwnershipService ownershipService;

    public CvSectionOrderService(CvSectionOrderRepository repository, CvOwnershipService ownershipService) {
        super();
        this.repository = repository;
        this.ownershipService = ownershipService;
    }

    private static final List<String> ALLOWED_SECTIONS = List.of(
            "career_objective",
            "education",
            "skills",
            "skill_groups",
            "work_experiences",
            "projects",
            "certificates",
            "awards",
            "hobbies",
            "mores");

    private static final List<String> DEFAULT_SECTIONS = List.of(
            "career_objective",
            "education",
            "skill_groups",
            "work_experiences",
            "projects",
            "certificates",
            "awards",
            "hobbies",
            "mores");

    @Transactional(readOnly = true)
    public List<CvSectionOrderResponse> getAll(
            Long cvId, Long accountId) {

        ownershipService.checkOwnership(cvId, accountId);

        return repository
                .findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId)
                .stream()
                .map(CvSectionOrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CvSectionOrderResponse> getAllByCandidate(Long cvId, Long candidateId) {
        ownershipService.checkOwnershipByCandidate(cvId, candidateId);

        return repository
                .findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId)
                .stream()
                .map(CvSectionOrderResponse::from)
                .toList();
    }

    public List<CvSectionOrderResponse> saveAll(
            Long cvId,
            Long accountId,
            CvSectionOrderRequest request) {

        ownershipService.checkOwnership(cvId, accountId);

        List<CvSectionOrderItemRequest> items = request.getSectionOrders();

        validateItems(items);

        List<CvSectionOrder> existing = repository.findAllByCvId(cvId);

        Map<String, CvSectionOrder> existingByType = existing.stream().collect(Collectors.toMap(
                CvSectionOrder::getSectionType,
                Function.identity()));

        Set<String> submittedTypes = items.stream()
                .map(CvSectionOrderItemRequest::getSectionType)
                .collect(Collectors.toSet());

        // Xóa các section không còn nằm trong danh sách gửi lên.
        List<CvSectionOrder> removed = existing.stream()
                .filter(item -> !submittedTypes.contains(
                        item.getSectionType()))
                .toList();

        repository.deleteAll(removed);

        // Cập nhật section cũ hoặc thêm section mới.
        List<CvSectionOrder> toSave = new ArrayList<>();

        for (CvSectionOrderItemRequest item : items) {
            CvSectionOrder entity = existingByType.get(item.getSectionType());

            if (entity == null) {
                entity = CvSectionOrder.builder()
                        .cvId(cvId)
                        .sectionType(item.getSectionType())
                        .displayOrder(item.getDisplayOrder())
                        .build();
            } else {
                entity.setDisplayOrder(item.getDisplayOrder());
            }

            toSave.add(entity);
        }

        repository.saveAll(toSave);

        return repository
                .findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId)
                .stream()
                .map(CvSectionOrderResponse::from)
                .toList();
    }

    public List<CvSectionOrderResponse> initialize(
            Long cvId, Long accountId) {

        ownershipService.checkOwnership(cvId, accountId);

        List<CvSectionOrder> existing = repository.findAllByCvId(cvId);

        Set<String> existingTypes = existing.stream()
                .map(CvSectionOrder::getSectionType)
                .collect(Collectors.toSet());

        List<CvSectionOrder> toSave = new ArrayList<>();

        for (int i = 0; i < DEFAULT_SECTIONS.size(); i++) {
            String sectionType = DEFAULT_SECTIONS.get(i);

            if (!existingTypes.contains(sectionType)) {
                toSave.add(CvSectionOrder.builder()
                        .cvId(cvId)
                        .sectionType(sectionType)
                        .displayOrder(i)
                        .build());
            }
        }

        repository.saveAll(toSave);

        return repository
                .findAllByCvIdOrderByDisplayOrderAscIdAsc(cvId)
                .stream()
                .map(CvSectionOrderResponse::from)
                .toList();
    }

    private void validateItems(
            List<CvSectionOrderItemRequest> items) {

        if (items == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Danh sách section không được null");
        }

        Set<String> types = new HashSet<>();
        Set<Integer> orders = new HashSet<>();

        for (CvSectionOrderItemRequest item : items) {
            if (item == null
                    || item.getSectionType() == null
                    || item.getDisplayOrder() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Thông tin section không hợp lệ");
            }

            String type = item.getSectionType();

            if (!ALLOWED_SECTIONS.contains(type)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Loại section không hợp lệ: " + type);
            }

            if (item.getDisplayOrder() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Thứ tự section không được âm");
            }

            if (!types.add(type)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không được có section trùng loại: " + type);
            }

            if (!orders.add(item.getDisplayOrder())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không được có hai section cùng thứ tự");
            }
        }
    }
}