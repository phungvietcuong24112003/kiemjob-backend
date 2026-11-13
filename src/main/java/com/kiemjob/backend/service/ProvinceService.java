package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.province.ProvinceRequest;
import com.kiemjob.backend.entity.Province;
import com.kiemjob.backend.repository.ProvinceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProvinceService {

    private final ProvinceRepository provinceRepository;

    // Lấy danh sách
    @Transactional(readOnly = true)
    public List<Province> getAllProvinces() {
        return provinceRepository.findAllByOrderByNameAsc();
    }

    // Lấy chi tiết
    @Transactional(readOnly = true)
    public Province getProvinceById(Long id) {
        return findProvince(id);
    }

    // Thêm mới
    @Transactional
    public Province createProvince(ProvinceRequest request) {
        String name = normalizeName(request.getName());

        if (provinceRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên tỉnh/thành đã tồn tại"
            );
        }

        Province province = Province.builder()
                .name(name)
                .build();

        try {
            return provinceRepository.saveAndFlush(province);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên tỉnh/thành đã tồn tại hoặc vi phạm ràng buộc dữ liệu"
            );
        }
    }

    // Cập nhật
    @Transactional
    public Province updateProvince(Long id, ProvinceRequest request) {
        Province province = findProvince(id);
        String name = normalizeName(request.getName());

        if (provinceRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên tỉnh/thành đã được sử dụng"
            );
        }

        province.setName(name);

        try {
            return provinceRepository.saveAndFlush(province);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên tỉnh/thành đã tồn tại hoặc vi phạm ràng buộc dữ liệu"
            );
        }
    }

    // Xóa
    @Transactional
    public void deleteProvince(Long id) {
        Province province = findProvince(id);

        try {
            provinceRepository.delete(province);
            provinceRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể xóa tỉnh/thành đang được dữ liệu khác sử dụng"
            );
        }
    }

    private Province findProvince(Long id) {
        return provinceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tỉnh/thành có ID: " + id
                ));
    }

    private String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}