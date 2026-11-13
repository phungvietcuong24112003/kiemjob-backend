package com.kiemjob.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kiemjob.backend.service.FileStorageService;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final String bucket;
    private final String region;

    public FileUploadController(
            FileStorageService fileStorageService,
            @org.springframework.beans.factory.annotation.Value("${aws.s3.bucket}") String bucket,
            @org.springframework.beans.factory.annotation.Value("${aws.s3.region}") String region
    ) {
        this.fileStorageService = fileStorageService;
        this.bucket = bucket;
        this.region = region;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {
        String objectKey = fileStorageService.uploadImage(file);
        String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, objectKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", "Tải ảnh lên thành công",
                        "objectKey", objectKey,
                        "bucket", bucket,
                        "url", url
                )
        );
    }
}
