package com.kiemjob.backend;

import com.kiemjob.backend.dto.auth.RegisterCandidateRequest;
import com.kiemjob.backend.dto.auth.RegisterRecruiterRequest;
import com.kiemjob.backend.dto.profile.CandidateProfileRequest;
import com.kiemjob.backend.dto.profile.RecruiterProfileRequest;
import com.kiemjob.backend.entity.*;
import com.kiemjob.backend.repository.*;
import com.kiemjob.backend.security.JwtService;
import com.kiemjob.backend.service.AuthService;
import com.kiemjob.backend.service.ProfileService;
import com.kiemjob.backend.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthAndProfileServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private RecruiterRepository recruiterRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProvinceRepository provinceRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;

    private AuthService authService;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                accountRepository,
                authTokenRepository,
                candidateRepository,
                recruiterRepository,
                companyRepository,
                passwordEncoder,
                jwtService,
                emailService,
                10,
                "Test_Pepper_Secret_Key_12345"
        );

        profileService = new ProfileService(
                accountRepository,
                candidateRepository,
                recruiterRepository,
                companyRepository,
                provinceRepository
        );
    }

    @Test
    void registerCandidate_shouldSaveAccountAndCreateDefaultCandidateRecordWithNullAttributes() {
        when(accountRepository.existsByEmailIgnoreCase("abc@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pass");

        Account savedAccount = new Account();
        savedAccount.setId(10L);
        savedAccount.setEmail("abc@gmail.com");
        savedAccount.setRole("candidate");
        savedAccount.setPasswordHash("hashed_pass");

        when(accountRepository.saveAndFlush(any(Account.class))).thenReturn(savedAccount);
        when(candidateRepository.existsByAccountId(10L)).thenReturn(false);

        var response = authService.registerCandidate(
                new RegisterCandidateRequest("abc@gmail.com", "password123")
        );

        assertNotNull(response);
        assertTrue(response.message().contains("thành công"));

        // Verify account was created with role candidate
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        assertEquals("candidate", accountCaptor.getValue().getRole());
        assertEquals("abc@gmail.com", accountCaptor.getValue().getEmail());
        assertFalse(accountCaptor.getValue().isEmailVerified());

        // Verify candidate record was created with default fullname from email prefix ("abc") and null attributes
        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        Candidate createdCandidate = candidateCaptor.getValue();
        assertEquals(10L, createdCandidate.getAccountId());
        assertEquals("abc", createdCandidate.getFullname());
        assertNull(createdCandidate.getAvatarUrl());
        assertNull(createdCandidate.getHeadline());
        assertNull(createdCandidate.getGender());
        assertNull(createdCandidate.getPhone());
        assertNull(createdCandidate.getDateOfBirth());
        assertNull(createdCandidate.getProvinceId());
        assertNull(createdCandidate.getAddressDetail());

        // Verify NO recruiter or company was touched
        verifyNoInteractions(companyRepository);
        verifyNoInteractions(recruiterRepository);

        // Verify OTP email was sent
        verify(emailService).sendVerificationOtp(eq("abc@gmail.com"), anyString());
    }

    @Test
    void registerCandidate_shouldFallbackToUserIfPrefixEmpty() {
        when(accountRepository.existsByEmailIgnoreCase("@domain.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pass");

        Account savedAccount = new Account();
        savedAccount.setId(11L);
        savedAccount.setEmail("@domain.com");
        savedAccount.setRole("candidate");

        when(accountRepository.saveAndFlush(any(Account.class))).thenReturn(savedAccount);
        when(candidateRepository.existsByAccountId(11L)).thenReturn(false);

        authService.registerCandidate(new RegisterCandidateRequest("@domain.com", "password123"));

        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        assertEquals("user", candidateCaptor.getValue().getFullname());
    }

    @Test
    void registerRecruiter_shouldOnlySaveAccountAndNotRecruiterOrCompanyRecord() {
        when(accountRepository.existsByEmailIgnoreCase("hr@company.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pass");

        Account savedAccount = new Account();
        savedAccount.setId(20L);
        savedAccount.setEmail("hr@company.com");
        savedAccount.setRole("recruiter");

        when(accountRepository.saveAndFlush(any(Account.class))).thenReturn(savedAccount);

        var response = authService.registerRecruiter(
                new RegisterRecruiterRequest("hr@company.com", "password123")
        );

        assertNotNull(response);
        assertTrue(response.message().contains("thành công"));

        // Verify account was created with role recruiter
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        assertEquals("recruiter", accountCaptor.getValue().getRole());

        // Verify NO recruiter or company was created during registration
        verifyNoInteractions(candidateRepository);
        verifyNoInteractions(companyRepository);
        verifyNoInteractions(recruiterRepository);

        // Verify OTP email was sent
        verify(emailService).sendVerificationOtp(eq("hr@company.com"), anyString());
    }

    @Test
    void isProfileCompleted_shouldReflectExistenceOfProfile() {
        Account candidateAccount = new Account();
        candidateAccount.setId(1L);
        candidateAccount.setRole("candidate");

        when(candidateRepository.existsByAccountId(1L)).thenReturn(false);
        assertFalse(authService.isProfileCompleted(candidateAccount));

        when(candidateRepository.existsByAccountId(1L)).thenReturn(true);
        assertTrue(authService.isProfileCompleted(candidateAccount));

        Account recruiterAccount = new Account();
        recruiterAccount.setId(2L);
        recruiterAccount.setRole("recruiter");

        when(recruiterRepository.findByAccountId(2L)).thenReturn(Optional.empty());
        assertFalse(authService.isProfileCompleted(recruiterAccount));

        Recruiter recruiter = new Recruiter();
        recruiter.setAccountId(2L);
        recruiter.setCompanyId(5L);
        when(recruiterRepository.findByAccountId(2L)).thenReturn(Optional.of(recruiter));
        when(companyRepository.existsById(5L)).thenReturn(true);
        assertTrue(authService.isProfileCompleted(recruiterAccount));
    }

    @Test
    void updateCandidateProfile_shouldCreateNewCandidateProfile() {
        Account account = new Account();
        account.setId(10L);
        account.setEmail("candidate@test.com");
        account.setRole("candidate");

        when(accountRepository.findByEmailIgnoreCase("candidate@test.com")).thenReturn(Optional.of(account));
        when(candidateRepository.findByAccountId(10L)).thenReturn(Optional.empty());
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate c = invocation.getArgument(0);
            return c;
        });

        var request = new CandidateProfileRequest(
                "Nguyễn Văn A",
                "https://avatar.com/a.jpg",
                "Fullstack Developer",
                "male",
                "0987654321",
                null,
                null,
                "Hà Nội"
        );

        var result = profileService.updateCandidateProfile("candidate@test.com", request);

        assertNotNull(result);
        assertEquals("Nguyễn Văn A", result.fullname());
        assertEquals("Fullstack Developer", result.headline());
        assertEquals("male", result.gender());
        assertEquals("0987654321", result.phone());
        assertEquals(10L, result.accountId());
    }

    @Test
    void updateCandidateProfile_wrongRole_shouldThrowForbidden() {
        Account account = new Account();
        account.setId(20L);
        account.setEmail("recruiter@test.com");
        account.setRole("recruiter");

        when(accountRepository.findByEmailIgnoreCase("recruiter@test.com")).thenReturn(Optional.of(account));

        var request = new CandidateProfileRequest(
                "Nguyễn Văn A", null, null, null, null, null, null, null
        );

        assertThrows(ResponseStatusException.class, () ->
                profileService.updateCandidateProfile("recruiter@test.com", request)
        );
    }

    @Test
    void updateRecruiterProfile_shouldCreateCompanyAndRecruiter() {
        Account account = new Account();
        account.setId(30L);
        account.setEmail("hr@company.com");
        account.setRole("recruiter");

        when(accountRepository.findByEmailIgnoreCase("hr@company.com")).thenReturn(Optional.of(account));
        when(recruiterRepository.findByAccountId(30L)).thenReturn(Optional.empty());

        Company savedCompany = new Company();
        savedCompany.setName("Tech Corp");
        savedCompany.setCompanyType("big");
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> {
            Company c = invocation.getArgument(0);
            return c;
        });

        when(recruiterRepository.save(any(Recruiter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new RecruiterProfileRequest(
                "Trần Quản Lý",
                "Tech Corp",
                "big",
                "https://logo.com/logo.png",
                null,
                "contact@techcorp.com",
                "0241234567",
                "Top software company"
        );

        var result = profileService.updateRecruiterProfile("hr@company.com", request);

        assertNotNull(result);
        assertEquals("Trần Quản Lý", result.fullName());
        assertEquals("Tech Corp", result.companyName());
        assertEquals("big", result.companyType());
        assertEquals("contact@techcorp.com", result.companyEmail());
    }
}
