package com.kiemjob.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiemjob.backend.security.JwtAuthenticationFilter;
import com.kiemjob.backend.security.RateLimitingFilter;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
            body.put("error", "Unauthorized");
            body.put("message", "Bạn chưa đăng nhập hoặc phiên đăng nhập không hợp lệ");
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", HttpServletResponse.SC_FORBIDDEN);
            body.put("error", "Forbidden");
            body.put("message", "Bạn không có quyền truy cập tài nguyên này");
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            RateLimitingFilter rateLimitingFilter
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())

                // Cookie authentication: CSRF protection disabled (xem lưu ý rủi ro CSRF trong tài liệu)
                .csrf(csrf -> csrf.disable())

                // Cấu hình HTTP Security Headers bảo vệ ứng dụng
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; frame-ancestors 'none';")
                        )
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(
                                jakarta.servlet.DispatcherType.FORWARD,
                                jakarta.servlet.DispatcherType.ERROR
                        ).permitAll()

                        .requestMatchers("/error").permitAll()

                        // AWS ALB Health Check & Info Probe (Actuator)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Endpoints xác thực công khai (đăng ký, đăng nhập, quên MK, OTP)
                        // Lưu ý: /api/auth/me yêu cầu đã đăng nhập (bị chặn bởi rule authenticated bên dưới)
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/register/**",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-otp",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-reset-otp",
                                "/api/auth/reset-password",
                                "/api/auth/logout"
                        ).permitAll()

                        // GET công khai: Tin tuyển dụng và các thông tin liên quan
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/job-posts",
                                "/api/job-posts/featured",
                                "/api/job-posts/{id}",
                                "/api/job-posts/{id}/specializations",
                                "/api/job-posts/{id}/addresses",
                                "/api/job-posts/{id}/details",
                                "/api/job-posts/{id}/details/**",
                                "/api/job-posts/{id}/skills",
                                "/api/job-posts/{id}/skills/**",
                                "/api/job-addresses",
                                "/api/job-addresses/**"
                        ).permitAll()

                        // GET công khai: Công ty, ảnh, liên kết, địa chỉ công ty
                        // Lưu ý: /api/companies/{companyId}/licenses KHÔNG permitAll
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/companies",
                                "/api/companies/search",
                                "/api/companies/{id}",
                                "/api/companies/{companyId}/images",
                                "/api/companies/{companyId}/images/**",
                                "/api/companies/{companyId}/links",
                                "/api/companies/{companyId}/links/**",
                                "/api/companies/{companyId}/addresses",
                                "/api/companies/{companyId}/addresses/**"
                        ).permitAll()

                        // GET công khai: Danh mục kỹ năng, ngành nghề, tỉnh thành, gói tính năng
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/skills",
                                "/api/skills/**",
                                "/api/skill-categories",
                                "/api/skill-categories/**",
                                "/api/jobs-categories",
                                "/api/jobs-categories/**",
                                "/api/job-specializations",
                                "/api/job-specializations/**",
                                "/api/provinces",
                                "/api/provinces/**",
                                "/api/feature-packages"
                        ).permitAll()

                        // Callback VNPay (có kiểm tra chữ ký checksum)
                        .requestMatchers(
                                "/api/vnpay/return",
                                "/api/vnpay/ipn",
                                "/api/payments/vnpay/return",
                                "/api/payments/vnpay/ipn"
                        ).permitAll()

                        // WebSocket handshake (xác thực nằm ở Handshake / Channel Interceptor)
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // Endpoint quản trị viên
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Toàn bộ các endpoint còn lại bắt buộc phải xác thực
                        .anyRequest().authenticated()
                )

                // RateLimitingFilter chạy trước JwtFilter (và trước UsernamePasswordAuthenticationFilter)
                .addFilterBefore(
                        rateLimitingFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                // JwtFilter chạy ngay trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:3000}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-CSRF-TOKEN", "Idempotency-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
