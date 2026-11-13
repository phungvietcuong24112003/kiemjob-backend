
package com.kiemjob.backend.dto.candidate;

import com.kiemjob.backend.entity.CandidateLink;

public record CandidateLinkResponse(
    Long id,
    String type,
    String link
) {
    public static CandidateLinkResponse from(CandidateLink entity) {
        return new CandidateLinkResponse(
            entity.getId(),
            entity.getType(),
            entity.getLink()
        );
    }
}
