package com.kiemjob.backend.service;

import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompanyMembershipTierTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyService companyService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Công ty Công nghệ KiemJob");
        company.setCompanyType("big");
        company.setMembershipTier("bronze");
        company.setTotalSpending(0L);
    }

    @Test
    @DisplayName("Kiểm tra phân hạng thành viên theo số tiền chi tiêu (calculateMembershipTier)")
    void testCalculateMembershipTier() {
        // Dưới 500.000 -> bronze
        assertEquals("bronze", Company.calculateMembershipTier(null));
        assertEquals("bronze", Company.calculateMembershipTier(0L));
        assertEquals("bronze", Company.calculateMembershipTier(499_999L));

        // Từ 500.000 đến dưới 2.000.000 -> silver
        assertEquals("silver", Company.calculateMembershipTier(500_000L));
        assertEquals("silver", Company.calculateMembershipTier(1_000_000L));
        assertEquals("silver", Company.calculateMembershipTier(1_999_999L));

        // Từ 2.000.000 đến dưới 5.000.000 -> gold
        assertEquals("gold", Company.calculateMembershipTier(2_000_000L));
        assertEquals("gold", Company.calculateMembershipTier(3_500_000L));
        assertEquals("gold", Company.calculateMembershipTier(4_999_999L));

        // Từ 5.000.000 trở lên -> platinum
        assertEquals("platinum", Company.calculateMembershipTier(5_000_000L));
        assertEquals("platinum", Company.calculateMembershipTier(10_000_000L));
    }

    @Test
    @DisplayName("Kiểm tra tích lũy chi tiêu từng lần (addSpending)")
    void testAddSpendingProgression() {
        // Ban đầu 0đ -> bronze
        assertEquals(0L, company.getTotalSpending());
        assertEquals("bronze", company.getMembershipTier());

        // Lần 1: Nạp 300.000đ -> tổng 300.000đ -> bronze
        company.addSpending(300_000L);
        assertEquals(300_000L, company.getTotalSpending());
        assertEquals("bronze", company.getMembershipTier());

        // Lần 2: Nạp thêm 200.000đ -> tổng 500.000đ -> thăng hạng silver
        company.addSpending(200_000L);
        assertEquals(500_000L, company.getTotalSpending());
        assertEquals("silver", company.getMembershipTier());

        // Lần 3: Nạp thêm 1.500.000đ -> tổng 2.000.000đ -> thăng hạng gold
        company.addSpending(1_500_000L);
        assertEquals(2_000_000L, company.getTotalSpending());
        assertEquals("gold", company.getMembershipTier());

        // Lần 4: Nạp thêm 3.000.000đ -> tổng 5.000.000đ -> thăng hạng platinum
        company.addSpending(3_000_000L);
        assertEquals(5_000_000L, company.getTotalSpending());
        assertEquals("platinum", company.getMembershipTier());
    }

    @Test
    @DisplayName("Kiểm tra CompanyService.recordCompanySpending")
    void testRecordCompanySpending() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company updated = companyService.recordCompanySpending(1L, 2_500_000L);

        assertEquals(2_500_000L, updated.getTotalSpending());
        assertEquals("gold", updated.getMembershipTier());
        verify(companyRepository, times(1)).save(company);
    }
}

