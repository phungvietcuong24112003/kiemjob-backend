
package com.kiemjob.backend.config;

import com.kiemjob.backend.service.ConversationService;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/(\\d+)$");

    private final ConversationService conversationService;

    public StompChannelInterceptor(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            Map<String, Object> attributes = accessor.getSessionAttributes();

            if (attributes == null) {
                throw new AccessDeniedException("Phiên WebSocket không hợp lệ");
            }

            Object emailValue = attributes.get("wsEmail");
            Object roleValue = attributes.get("wsRole");

            if (!(emailValue instanceof String email)
                    || email.isBlank()
                    || !(roleValue instanceof String role)
                    || !List.of("candidate", "recruiter", "admin").contains(role)) {
                throw new AccessDeniedException(
                        "Không xác thực được phiên WebSocket");
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    List.of(new SimpleGrantedAuthority(
                            "ROLE_" + role.toUpperCase(Locale.ROOT))));

            accessor.setUser(authentication);
        }

        if (command == StompCommand.SUBSCRIBE) {
            Principal principal = accessor.getUser();
            String destination = accessor.getDestination();

            if (principal == null || destination == null) {
                throw new AccessDeniedException(
                        "Không xác định được người dùng hoặc kênh nhận");
            }

            Matcher matcher = CONVERSATION_TOPIC.matcher(destination);

            // Chỉ cho phép đăng ký kênh của một cuộc hội thoại cụ thể.
            if (!matcher.matches()) {
                throw new AccessDeniedException(
                        "Destination nhận tin không được phép");
            }

            Long conversationId = Long.valueOf(matcher.group(1));

            conversationService.getConversation(
                    conversationId,
                    principal.getName());
        }

        if (command == StompCommand.SEND) {
            String destination = accessor.getDestination();

            if (!"/app/chat.send".equals(destination)) {
                throw new AccessDeniedException(
                        "Destination gửi tin không được phép");
            }

            // Ensure user is set for SEND frames. If not, retrieve from session attributes (set during CONNECT).
            if (accessor.getUser() == null) {
                Map<String, Object> attrs = accessor.getSessionAttributes();
                if (attrs != null) {
                    Object emailValue = attrs.get("wsEmail");
                    Object roleValue = attrs.get("wsRole");
                    if (emailValue instanceof String email && !email.isBlank()
                            && roleValue instanceof String role && List.of("candidate", "recruiter", "admin").contains(role)) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                        );
                        accessor.setUser(auth);
                    } else {
                        throw new AccessDeniedException("Không xác thực được phiên WebSocket");
                    }
                } else {
                    throw new AccessDeniedException("Phiên WebSocket không hợp lệ");
                }
            }
        }

        return message;
    }
}
