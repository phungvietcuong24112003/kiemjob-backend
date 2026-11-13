
package com.kiemjob.backend.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class WebSocketSendMessageRequest {

    @NotNull(message = "conversationId không được để trống")
    @Positive(message = "conversationId phải lớn hơn 0")
    private Long conversationId;

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(max = 5000, message = "Tin nhắn không được vượt quá 5000 ký tự")
    private String content;

    public WebSocketSendMessageRequest() {
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
