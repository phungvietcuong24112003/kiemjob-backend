package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvRequest;
import com.kiemjob.backend.dto.cv.CvResponse;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Cv;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.CvPdfFileRepository;
import com.kiemjob.backend.repository.CvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvServiceTest {

    @Mock
    private CvRepository cvRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CvPdfFileRepository cvPdfFileRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CvService cvService;

    private Candidate candidate;

    @BeforeEach
    void setUp() {
        candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 1L);
        candidate.setFullname("Nguyen Van A");
    }

    @Test
    @DisplayName("Tạo CV thành công khi ứng viên chưa đạt tối đa 3 CV")
    void create_Success_WhenUnderLimit() {
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(cvRepository.countByCandidate_Id(1L)).thenReturn(2L);
        when(cvRepository.save(any(Cv.class))).thenAnswer(invocation -> {
            Cv saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        CvRequest request = CvRequest.builder()
                .title("CV Java Backend")
                .isPublic(false)
                .build();

        CvResponse response = cvService.create(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTitle()).isEqualTo("CV Java Backend");
        assertThat(response.getIsPublic()).isFalse();
        assertThat(response.getHasPdf()).isFalse();
        assertThat(response.getIsWebCreated()).isTrue();
        verify(cvRepository, never()).resetOtherPublicCvs(anyLong(), any());
    }

    @Test
    @DisplayName("Tạo CV thất bại khi ứng viên đã có 3 CV (giới hạn tối đa 3 CV)")
    void create_ThrowsException_WhenLimitReached() {
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(cvRepository.countByCandidate_Id(1L)).thenReturn(3L);

        CvRequest request = CvRequest.builder()
                .title("CV thứ 4")
                .build();

        assertThatThrownBy(() -> cvService.create(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("Mỗi tài khoản chỉ được lưu tối đa 3 CV");
                });

        verify(cvRepository, never()).save(any(Cv.class));
    }

    @Test
    @DisplayName("Tạo CV với isPublic=true tự động reset các CV khác của ứng viên")
    void create_WithPublicTrue_ResetsOtherCvs() {
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(cvRepository.countByCandidate_Id(1L)).thenReturn(1L);
        when(cvRepository.save(any(Cv.class))).thenAnswer(invocation -> {
            Cv saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        CvRequest request = CvRequest.builder()
                .title("CV Frontend Public")
                .isPublic(true)
                .build();

        CvResponse response = cvService.create(1L, request);

        assertThat(response.getIsPublic()).isTrue();
        verify(cvRepository).resetOtherPublicCvs(1L, null);
    }

    @Test
    @DisplayName("setPublicStatus chuyển sang true cho Web CV thành công")
    void setPublicStatus_True_WebCv_Success() {
        Cv cv = Cv.builder()
                .id(20L)
                .candidate(candidate)
                .title("CV Test")
                .isPublic(false)
                .build();

        when(cvRepository.findByIdAndCandidate_Id(20L, 1L)).thenReturn(Optional.of(cv));
        when(cvPdfFileRepository.existsByCvIdAndIsCurrentTrue(20L)).thenReturn(false);
        when(cvRepository.save(any(Cv.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvResponse response = cvService.setPublicStatus(1L, 20L, true);

        assertThat(response.getIsPublic()).isTrue();
        assertThat(response.getHasPdf()).isFalse();
        assertThat(response.getIsWebCreated()).isTrue();
        verify(cvRepository).resetOtherPublicCvs(1L, 20L);
        verify(cvRepository).save(cv);
    }

    @Test
    @DisplayName("setPublicStatus chuyển sang true cho PDF CV thành công")
    void setPublicStatus_True_PdfCv_Success() {
        Cv cv = Cv.builder()
                .id(20L)
                .candidate(candidate)
                .title("CV PDF Upload")
                .isPublic(false)
                .build();

        when(cvRepository.findByIdAndCandidate_Id(20L, 1L)).thenReturn(Optional.of(cv));
        when(cvPdfFileRepository.existsByCvIdAndIsCurrentTrue(20L)).thenReturn(true);
        when(cvRepository.save(any(Cv.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvResponse response = cvService.setPublicStatus(1L, 20L, true);

        assertThat(response.getIsPublic()).isTrue();
        assertThat(response.getHasPdf()).isTrue();
        verify(cvRepository).resetOtherPublicCvs(1L, 20L);
        verify(cvRepository).save(cv);
    }

    @Test
    @DisplayName("setPublicStatus chuyển sang false thì chỉ tắt CV đó mà không reset các CV khác")
    void setPublicStatus_False_SetsFalseWithoutResettingOthers() {
        Cv cv = Cv.builder()
                .id(20L)
                .candidate(candidate)
                .title("CV Test")
                .isPublic(true)
                .build();

        when(cvRepository.findByIdAndCandidate_Id(20L, 1L)).thenReturn(Optional.of(cv));
        when(cvRepository.save(any(Cv.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvResponse response = cvService.setPublicStatus(1L, 20L, false);

        assertThat(response.getIsPublic()).isFalse();
        verify(cvRepository, never()).resetOtherPublicCvs(anyLong(), any());
        verify(cvRepository).save(cv);
    }
}
