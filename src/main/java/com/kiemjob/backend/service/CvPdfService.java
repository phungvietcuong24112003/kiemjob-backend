package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.cv.CvPdfResponse;
import com.kiemjob.backend.dto.cv.CvPdfUploadResponse;
import com.kiemjob.backend.entity.CvPdfFile;
import com.kiemjob.backend.event.CvAnalysisRequestedEvent;
import com.kiemjob.backend.repository.CvPdfFileRepository;
import com.kiemjob.backend.repository.CvRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvPdfService {

        private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

        private final CvPdfFileRepository repository;
        private final S3Client s3Client;
        private final S3Presigner s3Presigner;
        private final CvRepository cvRepository;
        private final ApplicationEventPublisher eventPublisher;

        @Value("${aws.s3.bucket:${app.aws.s3.bucket:kiemjob-media-2026-abc123}}")
        private String bucketName;

        @Transactional
        public CvPdfUploadResponse upload(Long cvId, MultipartFile file) {

                validatePdf(file);

                // Quyền truy cập đã được kiểm tra tại CvPdfController
                // thông qua CvOwnershipService.checkCvWriteAccess().

                int nextVersion = repository
                                .findTopByCvIdOrderByVersionNoDesc(cvId)
                                .map(pdf -> pdf.getVersionNo() + 1)
                                .orElse(1);

                String objectKey = "cvs/" + cvId + "/v"
                                + nextVersion + "-" + UUID.randomUUID() + ".pdf";

                String fileName = sanitizeFileName(file.getOriginalFilename());

                try {
                        PutObjectRequest request = PutObjectRequest.builder()
                                        .bucket(bucketName)
                                        .key(objectKey)
                                        .contentType("application/pdf")
                                        .build();

                        s3Client.putObject(
                                        request,
                                        RequestBody.fromInputStream(
                                                        file.getInputStream(),
                                                        file.getSize()));

                } catch (IOException e) {
                        throw new ResponseStatusException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Không thể đọc file PDF");
                }

                try {
                        // Đánh dấu bản cũ không còn hiện hành.
                        repository.findByCvIdAndIsCurrentTrue(cvId)
                                        .ifPresent(old -> {
                                                old.setIsCurrent(false);
                                                repository.saveAndFlush(old);
                                        });

                        CvPdfFile entity = CvPdfFile.builder()
                                        .cvId(cvId)
                                        .fileName(fileName)
                                        .bucketName(bucketName)
                                        .objectKey(objectKey)
                                        .fileSizeBytes(file.getSize())
                                        .versionNo(nextVersion)
                                        .isCurrent(true)
                                        .build();

                        CvPdfFile saved = repository.saveAndFlush(entity);

                        // Nếu CV đang ở chế độ công khai (is_public = true), phát sự kiện tự động phân tích lại file PDF mới
                        cvRepository.findById(cvId).ifPresent(cv -> {
                                if (Boolean.TRUE.equals(cv.getIsPublic())) {
                                        eventPublisher.publishEvent(new CvAnalysisRequestedEvent(cvId, "UPLOAD_PDF"));
                                }
                        });

                        return CvPdfUploadResponse.builder()
                                        .file(toResponse(saved))
                                        .downloadUrl(createDownloadUrl(saved))
                                        .build();

                } catch (RuntimeException e) {
                        // Nếu ghi DB thất bại, tránh để lại object không được quản lý.
                        try {
                                s3Client.deleteObject(DeleteObjectRequest.builder()
                                                .bucket(bucketName)
                                                .key(objectKey)
                                                .build());
                        } catch (RuntimeException cleanupError) {
                                e.addSuppressed(cleanupError);
                        }
                        throw e;
                }
        }

        @Transactional(readOnly = true)
        public CvPdfResponse getCurrent(Long cvId) {
                return repository.findByCvIdAndIsCurrentTrue(cvId)
                                .map(this::toResponse)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "CV chưa có file PDF"));
        }

        @Transactional(readOnly = true)
        public List<CvPdfResponse> getVersions(Long cvId) {
                return repository.findAllByCvIdOrderByVersionNoDesc(cvId)
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        @Transactional(readOnly = true)
        public String getDownloadUrl(Long cvId) {
                CvPdfFile pdf = repository
                                .findByCvIdAndIsCurrentTrue(cvId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "CV chưa có file PDF"));

                return createDownloadUrl(pdf);
        }

        @Transactional
        public void delete(Long cvId, Long pdfId) {
                CvPdfFile pdf = repository.findByIdAndCvId(pdfId, cvId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Không tìm thấy file PDF"));

                // Xóa object S3 trước, chỉ xóa metadata khi thao tác thành công.
                s3Client.deleteObject(DeleteObjectRequest.builder()
                                .bucket(pdf.getBucketName())
                                .key(pdf.getObjectKey())
                                .build());

                repository.delete(pdf);
        }

        private void validatePdf(MultipartFile file) {
                if (file == null || file.isEmpty()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Vui lòng chọn file PDF");
                }

                if (file.getSize() > MAX_FILE_SIZE) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "File PDF không được vượt quá 10 MB");
                }

                String name = file.getOriginalFilename();

                if (name == null ||
                                !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "File phải có đuôi .pdf");
                }

                try (var inputStream = file.getInputStream()) {
                        // Đọc tối đa 1024 byte đầu tiên.
                        byte[] header = inputStream.readNBytes(1024);

                        String content = new String(
                                        header,
                                        java.nio.charset.StandardCharsets.ISO_8859_1);

                        // Chữ ký PDF phải xuất hiện gần đầu file.
                        if (!content.contains("%PDF-")) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Không tìm thấy chữ ký PDF trong nội dung file");
                        }

                } catch (IOException e) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Không đọc được file PDF",
                                        e);
                }
        }

        private String sanitizeFileName(String originalName) {
                String name = originalName == null ? "cv.pdf" : originalName;
                name = name.replace("\\", "/");
                name = name.substring(name.lastIndexOf('/') + 1);
                name = name.replaceAll("[^a-zA-Z0-9._-]", "_");

                return name.length() > 255 ? name.substring(0, 255) : name;
        }

        private String createDownloadUrl(CvPdfFile pdf) {
                GetObjectRequest getRequest = GetObjectRequest.builder()
                                .bucket(pdf.getBucketName())
                                .key(pdf.getObjectKey())
                                .responseContentType("application/pdf")
                                .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(getRequest)
                                .build();

                return s3Presigner.presignGetObject(presignRequest)
                                .url()
                                .toString();
        }

        private CvPdfResponse toResponse(CvPdfFile pdf) {
                return CvPdfResponse.builder()
                                .id(pdf.getId())
                                .cvId(pdf.getCvId())
                                .fileName(pdf.getFileName())
                                .fileSizeBytes(pdf.getFileSizeBytes())
                                .versionNo(pdf.getVersionNo())
                                .isCurrent(pdf.getIsCurrent())
                                .createdAt(pdf.getCreatedAt())
                                .build();
        }
}