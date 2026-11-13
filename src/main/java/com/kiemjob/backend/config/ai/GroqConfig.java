package com.kiemjob.backend.config.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class GroqConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(name = "groqRestClient")
    public RestClient groqRestClient(GroqProperties groqProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        int readTimeout = Math.max(5, groqProperties.getTimeoutSeconds());
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));

        return RestClient.builder()
                .baseUrl(groqProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    String apiKey = groqProperties.getApiKey();
                    if (apiKey != null && !apiKey.isBlank() && request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                        request.getHeaders().setBearerAuth(apiKey.trim());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
