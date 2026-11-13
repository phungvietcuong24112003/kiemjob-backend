
package com.kiemjob.backend.dto.conversation;

import com.kiemjob.backend.entity.Message;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponse {

    private Long id;
    private Long conversationId;
    private String senderType;
    private String content;
    private LocalDateTime createdAt;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderType(message.getSenderType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
