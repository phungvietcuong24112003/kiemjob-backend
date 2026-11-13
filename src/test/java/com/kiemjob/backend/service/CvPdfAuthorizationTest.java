package com.kiemjob.backend.service;

import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.ApplicationRepository;
import com.kiemjob.backend.repository.CvRepository;
import com.kiemjob.backend.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra quyền truy cập CV PDF thông qua CvOwnershipService.
 * Controller gọi checkCvWriteAccess / checkCvReadAccess trước khi
 * chuyển tiếp sang CvPdfService, nên ta test logic phân quyền ở đây.
 */
@ExtendWith(MockitoExtension.class)
class CvPdfAuthorizationTest {

    @Mock
    private CvRepository cvRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private CvOwnershipService cvOwnershipService;

    private static final Long CV_ID = 1L;
    private static final Long OWNER_CANDIDATE_ID = 10L;
    private static final Long OTHER_CANDIDATE_ID = 99L;
    private static final Long OWNER_COMPANY_ID = 100L;
    private static final Long OTHER_COMPANY_ID = 999L;

    private Candidate ownerCandidate;
    private Candidate otherCandidate;
    private Recruiter relatedRecruiter;
    private Recruiter unrelatedRecruiter;

    @BeforeEach
    void setUp() {
        ownerCandidate = new Candidate();
        ReflectionTestUtils.setField(ownerCandidate, "id", OWNER_CANDIDATE_ID);
        ownerCandidate.setAccountId(20L);

        otherCandidate = new Candidate();
        ReflectionTestUtils.setField(otherCandidate, "id", OTHER_CANDIDATE_ID);
        otherCandidate.setAccountId(21L);

        relatedRecruiter = new Recruiter();
        ReflectionTestUtils.setField(relatedRecruiter, "id", 50L);
        relatedRecruiter.setCompanyId(OWNER_COMPANY_ID);

        unrelatedRecruiter = new Recruiter();
        ReflectionTestUtils.setField(unrelatedRecruiter, "id", 51L);
        unrelatedRecruiter.setCompanyId(OTHER_COMPANY_ID);
    }

    // ========== checkCvWriteAccess (upload, delete) ==========

    @Nested
    @DisplayName("checkCvWriteAccess – Upload / Delete")
    class WriteAccessTests {

        @Test
        @DisplayName("Chủ CV (candidate) upload thành công")
        void ownerCandidate_Upload_ShouldSucceed() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.requireCandidate()).thenReturn(ownerCandidate);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            when(cvRepository.existsByIdAndCandidate_Id(CV_ID, OWNER_CANDIDATE_ID)).thenReturn(true);

            assertDoesNotThrow(() -> cvOwnershipService.checkCvWriteAccess(CV_ID, null));
        }

        @Test
        @DisplayName("Candidate khác upload bị 403 Forbidden")
        void otherCandidate_Upload_ShouldThrow403() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.requireCandidate()).thenReturn(otherCandidate);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            when(cvRepository.existsByIdAndCandidate_Id(CV_ID, OTHER_CANDIDATE_ID)).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvWriteAccess(CV_ID, null));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        @DisplayName("CV không tồn tại bị 404 Not Found")
        void cvNotFound_Upload_ShouldThrow404() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.requireCandidate()).thenReturn(ownerCandidate);
            when(cvRepository.existsById(CV_ID)).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvWriteAccess(CV_ID, null));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Không tìm thấy CV"));
        }

        @Test
        @DisplayName("Admin upload CV bất kỳ thành công")
        void admin_Upload_ShouldSucceed() {
            when(currentUserService.isAdmin()).thenReturn(true);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);

            assertDoesNotThrow(() -> cvOwnershipService.checkCvWriteAccess(CV_ID, null));
        }

        @Test
        @DisplayName("Admin upload CV không tồn tại bị 404")
        void admin_Upload_CvNotFound_ShouldThrow404() {
            when(currentUserService.isAdmin()).thenReturn(true);
            when(cvRepository.existsById(CV_ID)).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvWriteAccess(CV_ID, null));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("Recruiter upload bị 403 (requireCandidate ném lỗi)")
        void recruiter_Upload_ShouldThrow403() {
            when(currentUserService.isAdmin()).thenReturn(false);
            // requireCandidate() ném FORBIDDEN cho recruiter
            when(currentUserService.requireCandidate()).thenThrow(
                    new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Chỉ tài khoản ứng viên mới có thể thực hiện thao tác này"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvWriteAccess(CV_ID, null));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }
    }

    // ========== checkCvReadAccess (getCurrent, download, versions) ==========

    @Nested
    @DisplayName("checkCvReadAccess – Xem / Tải CV PDF")
    class ReadAccessTests {

        @Test
        @DisplayName("Chủ CV (candidate) xem thành công")
        void ownerCandidate_Read_ShouldSucceed() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(true);
            when(currentUserService.requireCandidate()).thenReturn(ownerCandidate);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            when(cvRepository.existsByIdAndCandidate_Id(CV_ID, OWNER_CANDIDATE_ID)).thenReturn(true);

            assertDoesNotThrow(() -> cvOwnershipService.checkCvReadAccess(CV_ID, null));
        }

        @Test
        @DisplayName("Candidate khác xem bị 403 Forbidden")
        void otherCandidate_Read_ShouldThrow403() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(true);
            when(currentUserService.requireCandidate()).thenReturn(otherCandidate);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            when(cvRepository.existsByIdAndCandidate_Id(CV_ID, OTHER_CANDIDATE_ID)).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvReadAccess(CV_ID, null));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        @DisplayName("CV không tồn tại – candidate xem bị 404")
        void cvNotFound_CandidateRead_ShouldThrow404() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(true);
            when(cvRepository.existsById(CV_ID)).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvReadAccess(CV_ID, null));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("Recruiter liên quan (có đơn ứng tuyển vào công ty) xem thành công")
        void relatedRecruiter_Read_ShouldSucceed() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(false);
            when(currentUserService.isRecruiter()).thenReturn(true);
            when(currentUserService.requireRecruiter()).thenReturn(relatedRecruiter);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            when(applicationRepository.existsByCv_IdAndJobPost_Company_Id(CV_ID, OWNER_COMPANY_ID))
                    .thenReturn(true);

            assertDoesNotThrow(() -> cvOwnershipService.checkCvReadAccess(CV_ID, null));
        }

        @Test
        @DisplayName("Recruiter không liên quan bị 403 Forbidden")
        void unrelatedRecruiter_Read_ShouldThrow403() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(false);
            when(currentUserService.isRecruiter()).thenReturn(true);
            when(currentUserService.requireRecruiter()).thenReturn(unrelatedRecruiter);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            when(applicationRepository.existsByCv_IdAndJobPost_Company_Id(CV_ID, OTHER_COMPANY_ID))
                    .thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvReadAccess(CV_ID, null));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        @DisplayName("CV không tồn tại – recruiter xem bị 404")
        void cvNotFound_RecruiterRead_ShouldThrow404() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(false);
            when(currentUserService.isRecruiter()).thenReturn(true);
            when(cvRepository.existsById(CV_ID)).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> cvOwnershipService.checkCvReadAccess(CV_ID, null));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("Recruiter xem CV công khai (isPublic = true) thành công kể cả khi chưa nộp đơn")
        void recruiter_ReadPublicCv_ShouldSucceed() {
            when(currentUserService.isAdmin()).thenReturn(false);
            when(currentUserService.isCandidate()).thenReturn(false);
            when(currentUserService.isRecruiter()).thenReturn(true);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);
            com.kiemjob.backend.entity.Cv publicCv = com.kiemjob.backend.entity.Cv.builder()
                    .id(CV_ID)
                    .isPublic(true)
                    .build();
            when(cvRepository.findById(CV_ID)).thenReturn(java.util.Optional.of(publicCv));

            assertDoesNotThrow(() -> cvOwnershipService.checkCvReadAccess(CV_ID, null));
        }

        @Test
        @DisplayName("Admin xem CV bất kỳ thành công")
        void admin_Read_ShouldSucceed() {
            when(currentUserService.isAdmin()).thenReturn(true);
            when(cvRepository.existsById(CV_ID)).thenReturn(true);

            assertDoesNotThrow(() -> cvOwnershipService.checkCvReadAccess(CV_ID, null));
        }
    }
}

