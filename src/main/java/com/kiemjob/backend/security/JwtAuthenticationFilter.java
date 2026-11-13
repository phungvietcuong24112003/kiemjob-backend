package com.kiemjob.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Thử xác thực qua Header Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String candidateToken = authHeader.substring(7).trim();
            if (!candidateToken.isBlank() && !"undefined".equalsIgnoreCase(candidateToken) && !"null".equalsIgnoreCase(candidateToken)) {
                tryAuthenticate(candidateToken, request);
            }
        }

        // 2. Nếu chưa xác thực được bằng header, thử xác thực bằng cookie access_token
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("access_token".equals(cookie.getName())) {
                        tryAuthenticate(cookie.getValue(), request);
                        if (SecurityContextHolder.getContext().getAuthentication() != null) {
                            break;
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void tryAuthenticate(String token, HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtService.parseToken(token);

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            Object accountIdObj = claims.get("accountId");
            if (accountIdObj instanceof Number num) {
                request.setAttribute("accountId", num.longValue());
            }

            if (email != null && !email.isBlank()) {
                String roleAuthority = role != null && !role.isBlank()
                        ? "ROLE_" + role.toUpperCase(Locale.ROOT)
                        : "ROLE_CANDIDATE";

                var authentication = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority(roleAuthority))
                );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // Token không hợp lệ hoặc hết hạn
        }
    }
}