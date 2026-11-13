package com.kiemjob.backend.controller;

import com.kiemjob.backend.dto.auth.*;
import com.kiemjob.backend.security.JwtService;
import com.kiemjob.backend.service.AuthService;
import com.kiemjob.backend.service.AuthService.LoginResult;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            @Value("${app.cookie.secure:false}") boolean cookieSecure
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    // ── Đăng ký chung (email + mật khẩu + vai trò) ─────────────
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthMessageResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    // ── Đăng ký ứng viên (email + mật khẩu) ───────────────────
    @PostMapping("/register/candidate")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthMessageResponse registerCandidate(
            @Valid @RequestBody RegisterCandidateRequest request
    ) {
        return authService.registerCandidate(request);
    }

    // ── Đăng ký nhà tuyển dụng (email + mật khẩu) ─────────────
    @PostMapping("/register/recruiter")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthMessageResponse registerRecruiter(
            @Valid @RequestBody RegisterRecruiterRequest request
    ) {
        return authService.registerRecruiter(request);
    }

    // ── Gửi lại OTP ──────────────────────────────────────────
    @PostMapping("/resend-otp")
    public AuthMessageResponse resendOtp(
            @Valid @RequestBody ResendOtpRequest request
    ) {
        return authService.resendOtp(request);
    }

    // ── Xác minh email và cấp JWT cookie ─────────────────────
    @PostMapping("/verify-email")
    public AccountResponse verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.verifyEmail(request);

        setAuthCookie(response, result.token());

        return result.account();
    }

    // ── Quên mật khẩu: Gửi mã OTP xác minh qua email ────────
    @PostMapping("/forgot-password")
    public AuthMessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return authService.forgotPassword(request);
    }

    // ── Xác thực mã OTP đặt lại mật khẩu ────────────────────
    @PostMapping("/verify-reset-otp")
    public AuthMessageResponse verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequest request
    ) {
        return authService.verifyResetOtp(request);
    }

    // ── Đặt lại mật khẩu mới ────────────────────────────────
    @PostMapping("/reset-password")
    public AuthMessageResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return authService.resetPassword(request);
    }

    // ── Đăng nhập và cấp JWT cookie ─────────────────────────
    @PostMapping("/login")
    public AccountResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request);

        setAuthCookie(response, result.token());

        return result.account();
    }

    // ── Lấy tài khoản hiện tại ──────────────────────────────
    @GetMapping("/me")
    public AccountResponse me(Authentication authentication) {
        return authService.getCurrentAccount(authentication.getName());
    }

    // ── Đăng xuất và xóa Cookie ─────────────────────────────
    @RequestMapping(value = "/logout", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();

        ResponseCookie loggedInCookie = ResponseCookie
                .from("logged_in", "")
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                loggedInCookie.toString()
        );
    }

    // ── Helper: set JWT cookie ──────────────────────────────
    private void setAuthCookie(
            HttpServletResponse response,
            String token
    ) {
        ResponseCookie cookie = ResponseCookie
                .from("access_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(
                        jwtService.getExpirationMs()
                ))
                .build();

        ResponseCookie loggedInCookie = ResponseCookie
                .from("logged_in", "true")
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(
                        jwtService.getExpirationMs()
                ))
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                loggedInCookie.toString()
        );
    }
}