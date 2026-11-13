package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.conversation.*;
import com.kiemjob.backend.service.ConversationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class ConversationController {

    private final ConversationService conversationService;

    // Constructor viết thủ công, không cần Lombok
    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    // GET /api/conversations
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                conversationService.getConversations(authentication.getName())
        );
    }

    // GET /api/conversations/{id}
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationResponse> getConversation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                conversationService.getConversation(
                        id, authentication.getName()
                )
        );
    }

    // POST /api/conversations
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            Authentication authentication
    ) {
        ConversationResponse response =
                conversationService.createConversation(
                        request, authentication.getName()
                );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // DELETE /api/conversations/{id}
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        conversationService.deleteConversation(
                id, authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

    // GET /api/conversations/{id}/messages
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                conversationService.getMessages(
                        id, authentication.getName()
                )
        );
    }

    // POST /api/conversations/{id}/messages
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        MessageResponse response = conversationService.sendMessage(
                id, request, authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // DELETE /api/messages/{id}
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long id,
            Authentication authentication
    ) {
        conversationService.deleteMessage(
                id, authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }
}
