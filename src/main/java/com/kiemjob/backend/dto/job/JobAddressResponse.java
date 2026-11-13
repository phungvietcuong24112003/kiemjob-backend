package com.kiemjob.backend.dto.job;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAddressResponse {

    private Long id;

    private Long jobPostId;

    private String jobTitle;

    private Long provinceId;

    private String provinceName;

    private String addressDetail;
}