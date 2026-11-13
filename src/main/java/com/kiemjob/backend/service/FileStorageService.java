
package com.kiemjob.backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final S3Client s3Client;
    private final String bucket;

    public FileStorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn ảnh để tải lên"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dung lượng ảnh tối đa là 5 MB"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null
                || !ALLOWED_TYPES.contains(
                        contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP"
            );
        }

        // Đọc toàn bộ bytes để kiểm tra Magic Bytes (chữ ký nhị phân thực của file)
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể đọc file ảnh"
            );
        }

        validateImageMagicBytes(fileBytes, contentType.toLowerCase(Locale.ROOT));

        String extension = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Định dạng ảnh không hợp lệ"
            );
        };

        String objectKey = "images/"
                + LocalDate.now()
                + "/"
                + UUID.randomUUID()
                + extension;

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            inputStream,
                            fileBytes.length
                    )
            );

            return objectKey;

        } catch (S3Exception e) {
            log.error("Lỗi S3 khi tải ảnh lên bucket: status={}, error={}",
                    e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage(),
                    e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể tải ảnh lên dịch vụ lưu trữ đám mây. Vui lòng thử lại sau.",
                    e
            );
        } catch (Exception e) {
            log.error("Lỗi không xác định khi lưu ảnh: ", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Đã xảy ra lỗi khi xử lý tệp ảnh. Vui lòng thử lại sau.",
                    e
            );
        }
    }

    private void validateImageMagicBytes(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length < 12) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tệp ảnh quá nhỏ hoặc không hợp lệ"
            );
        }

        boolean valid = switch (contentType) {
            case "image/jpeg" ->
                // JPEG: FF D8 FF
                (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
            case "image/png" ->
                // PNG: 89 50 4E 47 0D 0A 1A 0A
                (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47
                        && bytes[4] == (byte) 0x0D && bytes[5] == (byte) 0x0A && bytes[6] == (byte) 0x1A && bytes[7] == (byte) 0x0A);
            case "image/webp" ->
                // WebP: 'RIFF' tại 0..3 và 'WEBP' tại 8..11
                (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');
            default -> false;
        };

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chữ ký nhị phân (magic bytes) của file không khớp với định dạng ảnh " + contentType
            );
        }
    }
}

