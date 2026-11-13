
package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.conversation.MessageResponse;
import com.kiemjob.backend.dto.conversation.SendMessageRequest;
import com.kiemjob.backend.dto.conversation.WebSocketSendMessageRequest;
import com.kiemjob.backend.service.ConversationService;

import jakarta.validation.Valid;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MessageWebSocketController {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageWebSocketController(
            ConversationService conversationService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(
            @Valid @Payload WebSocketSendMessageRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException(
                    "Không xác định được người dùng gửi tin"
            );
        }

        SendMessageRequest serviceRequest = new SendMessageRequest();
        serviceRequest.setContent(request.getContent());

        // Service kiểm tra thành viên, tự xác định senderType
        // và lưu tin nhắn vào PostgreSQL.
        MessageResponse savedMessage = conversationService.sendMessage(
                request.getConversationId(),
                serviceRequest,
                principal.getName()
        );

        // Chỉ phát sau khi service đã lưu tin nhắn thành công.
        messagingTemplate.convertAndSend(
                "/topic/conversations/" + request.getConversationId(),
                savedMessage
        );
    }
}
