package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.auth.*;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.*;
import com.kiemjob.backend.security.JwtService;
import com.kiemjob.backend.service.email.EmailService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthService {

    private static final String VERIFY_EMAIL = "verifyemail";
    private static final String RESET_PASSWORD = "resetpassword";

    private final AccountRepository accountRepository;
    private final AuthTokenRepository authTokenRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int otpExpirationMinutes;
    private final String otpPepper;

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long OTP_LOCKOUT_MINUTES = 15;

    private record OtpAttemptState(int failedAttempts, Instant lockedUntil) {}
    private final ConcurrentMap<String, OtpAttemptState> otpAttemptMap = new ConcurrentHashMap<>();

    private void checkOtpLockout(String key) {
        OtpAttemptState state = otpAttemptMap.get(key);
        if (state != null && state.lockedUntil() != null) {
            if (Instant.now().isBefore(state.lockedUntil())) {
                long remainingMinutes = Math.max(1, ChronoUnit.MINUTES.between(Instant.now(), state.lockedUntil()));
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Bạn đã nhập sai mã OTP quá 5 lần. Vui lòng thử lại sau " + remainingMinutes + " phút hoặc yêu cầu mã mới."
                );
            } else {
                otpAttemptMap.remove(key);
            }
        }
    }

    private void recordFailedOtpAttempt(String key, Long accountId, String purpose) {
        otpAttemptMap.compute(key, (k, existing) -> {
            int count = (existing == null) ? 1 : existing.failedAttempts() + 1;
            if (count >= MAX_OTP_ATTEMPTS) {
                if (accountId != null && purpose != null) {
                    for (AuthToken t : authTokenRepository.findAllByAccountIdAndPurposeAndUsedAtIsNull(accountId, purpose)) {
                        t.setUsedAt(LocalDateTime.now());
                        authTokenRepository.save(t);
                    }
                }
                return new OtpAttemptState(count, Instant.now().plus(OTP_LOCKOUT_MINUTES, ChronoUnit.MINUTES));
            }
            return new OtpAttemptState(count, null);
        });
    }

    private void clearOtpAttempts(String key) {
        otpAttemptMap.remove(key);
    }

    public AuthService(
            AccountRepository accountRepository,
            AuthTokenRepository authTokenRepository,
            CandidateRepository candidateRepository,
            RecruiterRepository recruiterRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            @Value("${app.otp.expiration-minutes:10}")
            int otpExpirationMinutes,
            @Value("${app.otp.pepper}") String otpPepper
    ) {
        this.accountRepository = accountRepository;
        this.authTokenRepository = authTokenRepository;
        this.candidateRepository = candidateRepository;
        this.recruiterRepository = recruiterRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.otpPepper = otpPepper;
    }

    // ────────────────────────────────────────────────────────────
    //  ĐĂNG KÝ (CHUNG HOẶC RIÊNG CHO ỨNG VIÊN & NHÀ TUYỂN DỤNG)
    //  Chỉ tạo account (email + mật khẩu + vai trò).
    //  Thông tin chi tiết sẽ được bổ sung sau khi đăng nhập.
    // ────────────────────────────────────────────────────────────

    @Transactional
    public AuthMessageResponse register(RegisterRequest request) {
        return registerAccount(request.email(), request.password(), request.role());
    }

    @Transactional
    public AuthMessageResponse registerCandidate(RegisterCandidateRequest request) {
        return registerAccount(request.email(), request.password(), "candidate");
    }

    @Transactional
    public AuthMessageResponse registerRecruiter(RegisterRecruiterRequest request) {
        return registerAccount(request.email(), request.password(), "recruiter");
    }

    private AuthMessageResponse registerAccount(String rawEmail, String rawPassword, String role) {
        String email = normalizeEmail(rawEmail);

        assertEmailNotTaken(email, role);

        Account account = createAccount(email, rawPassword, role);

        if ("candidate".equalsIgnoreCase(account.getRole())) {
            createDefaultCandidateForAccount(account);
        }

        issueVerificationOtp(account);

        return new AuthMessageResponse(
                "Đăng ký thành công. Vui lòng kiểm tra email để lấy mã OTP xác minh."
        );
    }

    // ────────────────────────────────────────────────────────────
    //  GỬI LẠI OTP
    // ────────────────────────────────────────────────────────────

    @Transactional
    public AuthMessageResponse resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.email());
        clearOtpAttempts(email + ":" + VERIFY_EMAIL);

        Optional<Account> accountOpt = accountRepository.findByEmailIgnoreCase(email);
        if (accountOpt.isEmpty()) {
            // Chống user enumeration: không tiết lộ email có tồn tại trong hệ thống hay không
            return new AuthMessageResponse("Nếu email tồn tại trong hệ thống, mã OTP đã được gửi lại.");
        }

        Account account = accountOpt.get();
        if (account.isEmailVerified()) {
            return new AuthMessageResponse("Email đã được xác minh");
        }

        issueVerificationOtp(account);

        return new AuthMessageResponse(
                "Đã gửi lại mã OTP. Vui lòng kiểm tra email."
        );
    }

    // ────────────────────────────────────────────────────────────
    //  XÁC MINH EMAIL
    // ────────────────────────────────────────────────────────────

    @Transactional
    public LoginResult verifyEmail(VerifyEmailRequest request) {
        String email = normalizeEmail(request.email());
        String attemptKey = email + ":" + VERIFY_EMAIL;

        checkOtpLockout(attemptKey);

        Account account = accountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    recordFailedOtpAttempt(attemptKey, null, null);
                    return new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Email hoặc OTP không hợp lệ"
                    );
                });

        if (account.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được xác minh"
            );
        }

        String expectedHash = hashOtp(account.getId(), request.otp());
        byte[] expectedBytes = expectedHash.getBytes(StandardCharsets.UTF_8);

        List<AuthToken> tokens = authTokenRepository
                .findAllByAccountIdAndPurposeAndUsedAtIsNull(account.getId(), VERIFY_EMAIL);

        AuthToken matchedToken = null;
        for (AuthToken t : tokens) {
            byte[] actualBytes = t.getTokenHash().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedBytes, actualBytes)) {
                matchedToken = t;
                break;
            }
        }

        if (matchedToken == null) {
            recordFailedOtpAttempt(attemptKey, account.getId(), VERIFY_EMAIL);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP không hợp lệ hoặc đã được sử dụng"
            );
        }

        if (!matchedToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP đã hết hạn. Vui lòng yêu cầu mã mới."
            );
        }

        clearOtpAttempts(attemptKey);

        matchedToken.setUsedAt(LocalDateTime.now());
        account.setEmailVerified(true);

        authTokenRepository.save(matchedToken);
        accountRepository.save(account);

        boolean profileCompleted = isProfileCompleted(account);

        // Cấp JWT sau khi xác minh email thành công.
        return new LoginResult(
                AccountResponse.of(account, profileCompleted),
                jwtService.generateToken(account)
        );
    }

    // ────────────────────────────────────────────────────────────
    //  ĐĂNG NHẬP
    // ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        Account account = accountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidCredentials);

        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(
                        request.password(),
                        account.getPasswordHash()
                )) {
            throw invalidCredentials();
        }

        if (!account.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vui lòng xác minh email trước khi đăng nhập"
            );
        }

        boolean profileCompleted = isProfileCompleted(account);

        return new LoginResult(
                AccountResponse.of(account, profileCompleted),
                jwtService.generateToken(account)
        );
    }

    // ────────────────────────────────────────────────────────────
    //  QUÊN MẬT KHẨU: GỬI MÃ OTP ĐẶT LẠI MẬT KHẨU
    // ────────────────────────────────────────────────────────────

    @Transactional
    public AuthMessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        clearOtpAttempts(email + ":" + RESET_PASSWORD);

        Optional<Account> accountOpt = accountRepository.findByEmailIgnoreCase(email);
        if (accountOpt.isEmpty()) {
            // Chống user enumeration: không trả 404, trả thông báo chung
            return new AuthMessageResponse(
                    "Nếu email tồn tại trong hệ thống, mã xác thực OTP đã được gửi đến email của bạn."
            );
        }

        Account account = accountOpt.get();

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

        // Vô hiệu hóa các OTP đặt lại mật khẩu cũ chưa sử dụng
        for (AuthToken oldToken : authTokenRepository.findAllByAccountIdAndPurposeAndUsedAtIsNull(
                account.getId(), RESET_PASSWORD)) {
            oldToken.setUsedAt(LocalDateTime.now());
        }
        authTokenRepository.flush();

        AuthToken token = new AuthToken();
        token.setAccountId(account.getId());
        token.setPurpose(RESET_PASSWORD);
        token.setTokenHash(hashOtp(account.getId(), RESET_PASSWORD, otp));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
        token.setUsedAt(null);

        authTokenRepository.saveAndFlush(token);

        try {
            emailService.sendResetPasswordOtp(account.getEmail(), otp);
        } catch (MailException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể gửi email OTP. Vui lòng thử lại sau."
            );
        }

        return new AuthMessageResponse(
                "Mã xác thực OTP đã được gửi đến email của bạn."
        );
    }

    // ────────────────────────────────────────────────────────────
    //  XÁC THỰC MÃ OTP ĐẶT LẠI MẬT KHẨU
    // ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthMessageResponse verifyResetOtp(VerifyResetOtpRequest request) {
        String email = normalizeEmail(request.email());
        String attemptKey = email + ":" + RESET_PASSWORD;

        checkOtpLockout(attemptKey);

        Account account = accountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    recordFailedOtpAttempt(attemptKey, null, null);
                    return new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Email hoặc OTP không hợp lệ"
                    );
                });

        String expectedHash = hashOtp(account.getId(), RESET_PASSWORD, request.otp());
        byte[] expectedBytes = expectedHash.getBytes(StandardCharsets.UTF_8);

        List<AuthToken> tokens = authTokenRepository
                .findAllByAccountIdAndPurposeAndUsedAtIsNull(account.getId(), RESET_PASSWORD);

        AuthToken matchedToken = null;
        for (AuthToken t : tokens) {
            byte[] actualBytes = t.getTokenHash().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedBytes, actualBytes)) {
                matchedToken = t;
                break;
            }
        }

        if (matchedToken == null) {
            recordFailedOtpAttempt(attemptKey, account.getId(), RESET_PASSWORD);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP không hợp lệ hoặc đã được sử dụng"
            );
        }

        if (!matchedToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới."
            );
        }

        return new AuthMessageResponse("Mã xác thực OTP hợp lệ.");
    }

    // ────────────────────────────────────────────────────────────
    //  ĐẶT LẠI MẬT KHẨU MỚI
    // ────────────────────────────────────────────────────────────

    @Transactional
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        String attemptKey = email + ":" + RESET_PASSWORD;

        checkOtpLockout(attemptKey);

        Account account = accountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Email hoặc OTP không hợp lệ"
                ));

        String expectedHash = hashOtp(account.getId(), RESET_PASSWORD, request.otp());
        byte[] expectedBytes = expectedHash.getBytes(StandardCharsets.UTF_8);

        List<AuthToken> tokens = authTokenRepository
                .findAllByAccountIdAndPurposeAndUsedAtIsNull(account.getId(), RESET_PASSWORD);

        AuthToken matchedToken = null;
        for (AuthToken t : tokens) {
            byte[] actualBytes = t.getTokenHash().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedBytes, actualBytes)) {
                matchedToken = t;
                break;
            }
        }

        if (matchedToken == null) {
            recordFailedOtpAttempt(attemptKey, account.getId(), RESET_PASSWORD);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP không hợp lệ hoặc đã được sử dụng"
            );
        }

        if (!matchedToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới."
            );
        }

        clearOtpAttempts(attemptKey);

        matchedToken.setUsedAt(LocalDateTime.now());
        authTokenRepository.save(matchedToken);

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);

        return new AuthMessageResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
    }

    // ────────────────────────────────────────────────────────────
    //  LẤY THÔNG TIN TÀI KHOẢN HIỆN TẠI
    // ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AccountResponse getCurrentAccount(String email) {
        Account account = accountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Tài khoản không còn tồn tại"
                ));

        boolean profileCompleted = isProfileCompleted(account);

        return AccountResponse.of(account, profileCompleted);
    }

    // ────────────────────────────────────────────────────────────
    //  KIỂM TRA HỒ SƠ ĐÃ BỔ SUNG ĐẦY ĐỦ CHƯA
    // ────────────────────────────────────────────────────────────

    public boolean isProfileCompleted(Account account) {
        if ("candidate".equalsIgnoreCase(account.getRole())) {
            return candidateRepository.existsByAccountId(account.getId());
        } else if ("recruiter".equalsIgnoreCase(account.getRole())) {
            return recruiterRepository.findByAccountId(account.getId())
                    .map(r -> r.getCompanyId() != null && companyRepository.existsById(r.getCompanyId()))
                    .orElse(false);
        }
        return true;
    }

    // ────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ────────────────────────────────────────────────────────────

    private void assertEmailNotTaken(String email, String role) {
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            accountRepository.findByEmailIgnoreCase(email).ifPresent(account -> {
                if ("recruiter".equalsIgnoreCase(account.getRole()) && !"recruiter".equalsIgnoreCase(role)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Email này đã được đăng ký tài khoản Nhà tuyển dụng. Vui lòng chuyển sang cổng Nhà tuyển dụng."
                    );
                }
                if ("candidate".equalsIgnoreCase(account.getRole()) && !"candidate".equalsIgnoreCase(role)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Email này đã được đăng ký tài khoản Ứng viên. Vui lòng chuyển sang cổng Ứng viên."
                    );
                }
            });
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng"
            );
        }
    }

    private Account createAccount(String email, String password, String role) {
        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(role.toLowerCase(Locale.ROOT));
        account.setEmailVerified(false);

        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng hoặc dữ liệu không hợp lệ"
            );
        }
    }

    private void issueVerificationOtp(Account account) {
        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // Vô hiệu hóa các OTP xác minh cũ chưa sử dụng.
        for (AuthToken oldToken :
                authTokenRepository
                        .findAllByAccountIdAndPurposeAndUsedAtIsNull(
                                account.getId(),
                                VERIFY_EMAIL
                        )) {
            oldToken.setUsedAt(LocalDateTime.now());
        }

        authTokenRepository.flush();

        AuthToken token = new AuthToken();
        token.setAccountId(account.getId());
        token.setPurpose(VERIFY_EMAIL);
        token.setTokenHash(hashOtp(account.getId(), otp));
        token.setExpiresAt(
                LocalDateTime.now().plusMinutes(otpExpirationMinutes)
        );
        token.setUsedAt(null);

        authTokenRepository.saveAndFlush(token);

        try {
            emailService.sendVerificationOtp(account.getEmail(), otp);
        } catch (MailException ex) {
            // Không log OTP hoặc thông tin xác thực SMTP.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể gửi email OTP. Vui lòng thử lại sau."
            );
        }
    }

    private String hashOtp(Long accountId, String otp) {
        return hashOtp(accountId, VERIFY_EMAIL, otp);
    }

    private String hashOtp(Long accountId, String purpose, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    otpPepper.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            String input = accountId + ":" + purpose + ":" + otp;

            byte[] hash = mac.doFinal(
                    input.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Không thể xử lý OTP",
                    ex
            );
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Email hoặc mật khẩu không chính xác"
        );
    }

    private void createDefaultCandidateForAccount(Account account) {
        if (!candidateRepository.existsByAccountId(account.getId())) {
            Candidate candidate = new Candidate();
            candidate.setAccountId(account.getId());
            candidate.setFullname(deriveCandidateName(account.getEmail()));
            candidate.setAvatarUrl(null);
            candidate.setHeadline(null);
            candidate.setGender(null);
            candidate.setPhone(null);
            candidate.setDateOfBirth(null);
            candidate.setProvinceId(null);
            candidate.setAddressDetail(null);
            candidateRepository.save(candidate);
        }
    }

    private String deriveCandidateName(String email) {
        if (email == null || email.isBlank()) {
            return "user";
        }
        int atIndex = email.indexOf('@');
        String name;
        if (atIndex > 0) {
            name = email.substring(0, atIndex).trim();
        } else if (atIndex == 0) {
            name = "user";
        } else {
            name = email.trim();
        }
        if (name.isBlank()) {
            return "user";
        }
        if (name.length() > 150) {
            name = name.substring(0, 150);
        }
        return name;
    }

    public record LoginResult(
            AccountResponse account,
            String token
    ) {
    }
}