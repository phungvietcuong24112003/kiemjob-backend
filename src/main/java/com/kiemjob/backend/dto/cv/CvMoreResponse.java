package com.kiemjob.backend.dto.cv;

import com.kiemjob.backend.entity.CvMore;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CvMoreResponse {

    private Long id;
    private Long cvId;
    private String title;
    private String content;

    public static CvMoreResponse fromEntity(CvMore cvMore) {
        return CvMoreResponse.builder()
                .id(cvMore.getId())
                .cvId(cvMore.getCv().getId())
                .title(cvMore.getTitle())
                .content(cvMore.getContent())
                .build();
    }
}