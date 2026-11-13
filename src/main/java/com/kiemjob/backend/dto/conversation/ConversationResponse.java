
package com.kiemjob.backend.dto.conversation;

import com.kiemjob.backend.entity.Conversation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private Long id;
    private Long candidateId;
    private Long recruiterId;

    // Rich candidate details for Recruiter view
    private String candidateName;
    private String candidateAvatar;
    private String candidateHeadline;

    // Rich recruiter & company details for Candidate view
    private String recruiterName;
    private String companyName;
    private String companyLogo;

    // Latest message preview
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;

    public static ConversationResponse from(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .candidateId(conversation.getCandidateId())
                .recruiterId(conversation.getRecruiterId())
                .build();
    }
}

