
package com.kiemjob.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final StompChannelInterceptor stompChannelInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            StompChannelInterceptor stompChannelInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOriginsStr
    ) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Giới hạn kích thước tin nhắn và bộ đệm WebSocket chống cạn kiệt tài nguyên / DoS
        registration.setMessageSizeLimit(64 * 1024); // 64 KB
        registration.setSendBufferSizeLimit(512 * 1024); // 512 KB
        registration.setSendTimeLimit(20 * 1000); // 20 giây
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }
}

