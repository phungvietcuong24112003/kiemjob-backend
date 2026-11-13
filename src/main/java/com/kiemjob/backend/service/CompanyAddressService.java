
package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.company.CompanyAddressRequest;
import com.kiemjob.backend.dto.company.CompanyAddressResponse;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.CompanyAddress;
import com.kiemjob.backend.entity.Province;
import com.kiemjob.backend.repository.CompanyAddressRepository;
import com.kiemjob.backend.repository.CompanyRepository;
import com.kiemjob.backend.repository.ProvinceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyAddressService {

    private final CompanyAddressRepository addressRepository;
    private final CompanyRepository companyRepository;
    private final ProvinceRepository provinceRepository;

    public CompanyAddressService(
            CompanyAddressRepository addressRepository,
            CompanyRepository companyRepository,
            ProvinceRepository provinceRepository) {
        this.addressRepository = addressRepository;
        this.companyRepository = companyRepository;
        this.provinceRepository = provinceRepository;
    }

    @Transactional
    public CompanyAddressResponse create(
            Long companyId,
            CompanyAddressRequest request) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy công ty"));

        Province province = getProvince(request.provinceId());

        CompanyAddress address = new CompanyAddress();
        address.setCompany(company);
        address.setProvince(province);
        address.setAddressDetail(request.addressDetail().trim());

        return toResponse(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<CompanyAddressResponse> getAll(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy công ty");
        }

        return addressRepository
                .findAllByCompany_IdOrderByIdAsc(companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyAddressResponse getById(
            Long companyId,
            Long addressId) {

        return toResponse(getAddress(companyId, addressId));
    }

    @Transactional
    public CompanyAddressResponse update(
            Long companyId,
            Long addressId,
            CompanyAddressRequest request) {

        CompanyAddress address = getAddress(companyId, addressId);
        Province province = getProvince(request.provinceId());

        address.setProvince(province);
        address.setAddressDetail(request.addressDetail().trim());

        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public void delete(Long companyId, Long addressId) {
        CompanyAddress address = getAddress(companyId, addressId);
        addressRepository.delete(address);
    }

    private CompanyAddress getAddress(Long companyId, Long addressId) {
        return addressRepository
                .findByIdAndCompany_Id(addressId, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy địa chỉ thuộc công ty này"
                ));
    }

    private Province getProvince(Long provinceId) {
        return provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tỉnh/thành phố"
                ));
    }

    private CompanyAddressResponse toResponse(CompanyAddress address) {
        return new CompanyAddressResponse(
                address.getId(),
                address.getCompany().getId(),
                address.getProvince().getId(),
                address.getProvince().getName(),
                address.getAddressDetail()
        );
    }
}
