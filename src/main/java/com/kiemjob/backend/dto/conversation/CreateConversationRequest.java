package com.kiemjob.backend.dto.conversation;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConversationRequest {

    @Positive
    private Long candidateId;

    @Positive
    private Long recruiterId;

    @Positive
    private Long companyId;
}
