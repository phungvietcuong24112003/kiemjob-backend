package com.kiemjob.backend;

import com.kiemjob.backend.dto.job.JobPostRequest;
import com.kiemjob.backend.dto.job.JobPostResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Company;
import com.kiemjob.backend.entity.JobPost;
import com.kiemjob.backend.entity.JobsCategory;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.CompanyRepository;
import com.kiemjob.backend.repository.JobPostRepository;
import com.kiemjob.backend.repository.JobsCategoryRepository;
import com.kiemjob.backend.repository.RecruiterRepository;
import com.kiemjob.backend.service.JobPostService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPostServiceTest {

    @Mock
    private JobPostRepository jobPostRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private JobsCategoryRepository jobsCategoryRepository;
    @Mock
    private RecruiterRepository recruiterRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private com.kiemjob.backend.repository.JobPostSpecializationRepository postSpecializationRepository;
    @Mock
    private com.kiemjob.backend.repository.JobSpecializationRepository jobSpecializationRepository;
    @Mock
    private com.kiemjob.backend.repository.JobAddressRepository jobAddressRepository;
    @Mock
    private com.kiemjob.backend.service.JobAddressService jobAddressService;
    @Mock
    private com.kiemjob.backend.repository.JobModerationRepository jobModerationRepository;
    @Mock
    private com.kiemjob.backend.repository.CandidateRepository candidateRepository;
    @Mock
    private com.kiemjob.backend.service.email.EmailService emailService;

    private com.kiemjob.backend.security.CurrentUserService currentUserService;
    private JobPostService jobPostService;

    private Company testCompany;
    private JobsCategory testCategory;
    private Recruiter testRecruiter;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        currentUserService = new com.kiemjob.backend.security.CurrentUserService(
                accountRepository,
                candidateRepository,
                recruiterRepository
        );

        jobPostService = new JobPostService(
                jobPostRepository,
                companyRepository,
                jobsCategoryRepository,
                recruiterRepository,
                accountRepository,
                postSpecializationRepository,
                jobSpecializationRepository,
                jobAddressRepository,
                jobAddressService,
                jobModerationRepository,
                currentUserService,
                emailService
        );

        testCompany = new Company();
        ReflectionTestUtils.setField(testCompany, "id", 10L);
        testCompany.setName("Công ty ABC");

        testCategory = new JobsCategory();
        testCategory.setId(1L);
        testCategory.setName("Công nghệ thông tin");

        testRecruiter = new Recruiter();
        ReflectionTestUtils.setField(testRecruiter, "id", 5L);
        testRecruiter.setAccountId(100L);
        testRecruiter.setCompanyId(10L);
        testRecruiter.setFullName("Nguyen Recruiter");

        testAccount = new Account();
        testAccount.setId(100L);
        testAccount.setEmail("recruiter@example.com");
        testAccount.setRole("recruiter");

        // Giả lập lưu thành công trả về entity đã save
        lenient().when(jobPostRepository.saveAndFlush(any(JobPost.class))).thenAnswer(i -> {
            JobPost jp = i.getArgument(0);
            if (jp.getId() == null) {
                jp.setId(99L);
            }
            return jp;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthenticatedRecruiter() {
        var auth = new UsernamePasswordAuthenticationToken(
                "recruiter@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(accountRepository.findByEmailIgnoreCase("recruiter@example.com"))
                .thenReturn(Optional.of(testAccount));
        when(recruiterRepository.findByAccountId(100L))
                .thenReturn(Optional.of(testRecruiter));
        lenient().when(companyRepository.findById(10L))
                .thenReturn(Optional.of(testCompany));
    }

    @Test
    void create_withDraftStatus_shouldSaveWithDraftAndNoPostedAt() {
        mockAuthenticatedRecruiter();
        when(jobsCategoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        JobPostRequest request = new JobPostRequest();
        request.setTitle("Tuyển dụng Frontend Dev");
        request.setCategoryId(1L);
        request.setVacancies(2);
        request.setWorkingMode("onsite");
        request.setStatus("draft");
        request.setSalaryMin(BigDecimal.valueOf(15000000));
        request.setSalaryMax(BigDecimal.valueOf(25000000));
        request.setApplicationDeadline(LocalDate.now().plusDays(30));

        JobPostResponse response = jobPostService.create(request);

        assertNotNull(response);
        assertEquals("draft", response.getStatus());
        assertNull(response.getPostedAt());

        ArgumentCaptor<JobPost> captor = ArgumentCaptor.forClass(JobPost.class);
        verify(jobPostRepository).saveAndFlush(captor.capture());
        JobPost saved = captor.getValue();
        assertEquals("draft", saved.getStatus());
        assertNull(saved.getPostedAt());
        assertEquals(10L, saved.getCompany().getId());
        assertEquals(5L, saved.getPostedBy().getId());
    }

    @Test
    void create_withOpenStatus_byRecruiter_shouldAlwaysSaveAsDraftAndNoPostedAt() {
        mockAuthenticatedRecruiter();
        when(jobsCategoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        JobPostRequest request = new JobPostRequest();
        request.setTitle("Tuyển dụng Backend Java Engineer");
        request.setCategoryId(1L);
        request.setVacancies(3);
        request.setWorkingMode("hybrid");
        request.setStatus("open");
        request.setSalaryMin(BigDecimal.valueOf(20000000));
        request.setSalaryMax(BigDecimal.valueOf(35000000));
        request.setApplicationDeadline(LocalDate.now().plusDays(15));

        JobPostResponse response = jobPostService.create(request);

        assertNotNull(response);
        assertEquals("draft", response.getStatus());
        assertNull(response.getPostedAt());

        ArgumentCaptor<JobPost> captor = ArgumentCaptor.forClass(JobPost.class);
        verify(jobPostRepository).saveAndFlush(captor.capture());
        JobPost saved = captor.getValue();
        assertEquals("draft", saved.getStatus());
        assertNull(saved.getPostedAt());
    }

    @Test
    void create_withOpenStatus_byAdmin_shouldSaveWithOpenAndPostedAt() {
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        when(jobsCategoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(testCompany));
        when(recruiterRepository.findById(5L)).thenReturn(Optional.of(testRecruiter));

        JobPostRequest request = new JobPostRequest();
        request.setCompanyId(10L);
        request.setPostedById(5L);
        request.setTitle("Tuyển dụng DevOps Lead bởi Admin");
        request.setCategoryId(1L);
        request.setVacancies(1);
        request.setWorkingMode("onsite");
        request.setStatus("open");
        request.setSalaryMin(BigDecimal.valueOf(30000000));
        request.setSalaryMax(BigDecimal.valueOf(45000000));
        request.setApplicationDeadline(LocalDate.now().plusDays(20));

        JobPostResponse response = jobPostService.create(request);

        assertNotNull(response);
        assertEquals("open", response.getStatus());
        assertNotNull(response.getPostedAt());

        ArgumentCaptor<JobPost> captor = ArgumentCaptor.forClass(JobPost.class);
        verify(jobPostRepository).saveAndFlush(captor.capture());
        JobPost saved = captor.getValue();
        assertEquals("open", saved.getStatus());
        assertNotNull(saved.getPostedAt());
    }

    @Test
    void create_withSalaryMinGreaterThanMax_shouldThrowBadRequest() {
        JobPostRequest request = new JobPostRequest();
        request.setTitle("Tin lương lỗi");
        request.setCategoryId(1L);
        request.setVacancies(1);
        request.setWorkingMode("onsite");
        request.setSalaryMin(BigDecimal.valueOf(30000000));
        request.setSalaryMax(BigDecimal.valueOf(20000000));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                jobPostService.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Lương tối thiểu không được lớn hơn lương tối đa"));
    }

    @Test
    void create_withNegativeSalary_shouldThrowBadRequest() {
        JobPostRequest request = new JobPostRequest();
        request.setTitle("Tin lương âm");
        request.setCategoryId(1L);
        request.setVacancies(1);
        request.setWorkingMode("onsite");
        request.setSalaryMin(BigDecimal.valueOf(-1000));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                jobPostService.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Lương tối thiểu không được âm"));
    }

    @Test
    void update_byRecruiterOfSameCompany_shouldSucceed() {
        mockAuthenticatedRecruiter();

        JobPost existing = new JobPost();
        existing.setId(50L);
        existing.setCompany(testCompany); // Company ID: 10
        existing.setCategory(testCategory);
        existing.setPostedBy(testRecruiter);
        existing.setTitle("Tiêu đề cũ");
        existing.setWorkingMode("onsite");
        existing.setStatus("draft");

        when(jobPostRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(jobsCategoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        JobPostRequest updateRequest = new JobPostRequest();
        updateRequest.setTitle("Tiêu đề mới đã chỉnh sửa");
        updateRequest.setCategoryId(1L);
        updateRequest.setVacancies(5);
        updateRequest.setWorkingMode("remote");
        updateRequest.setStatus("open");

        JobPostResponse response = jobPostService.update(50L, updateRequest);

        assertNotNull(response);
        assertEquals("Tiêu đề mới đã chỉnh sửa", response.getTitle());
        // Recruiter sửa tin không thể tự ý kích hoạt 'open', luôn chuyển về draft chờ duyệt
        assertEquals("draft", response.getStatus());
        assertNull(response.getPostedAt());
    }

    @Test
    void updateStatus_byRecruiterToOpen_shouldThrowForbidden() {
        mockAuthenticatedRecruiter();

        JobPost existing = new JobPost();
        existing.setId(50L);
        existing.setCompany(testCompany);
        existing.setStatus("draft");

        when(jobPostRepository.findById(50L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                jobPostService.updateStatus(50L, "open"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Chỉ Quản trị viên"));
    }

    @Test
    void updateStatus_byAdminToOpen_shouldSucceed() {
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        JobPost existing = new JobPost();
        existing.setId(50L);
        existing.setCompany(testCompany);
        existing.setStatus("draft");

        when(jobPostRepository.findById(50L)).thenReturn(Optional.of(existing));

        JobPostResponse response = jobPostService.updateStatus(50L, "open");

        assertNotNull(response);
        assertEquals("open", response.getStatus());
        assertNotNull(response.getPostedAt());
    }

    @Test
    void update_byRecruiterOfDifferentCompany_shouldThrowForbidden() {
        mockAuthenticatedRecruiter(); // Recruiter companyId is 10

        Company otherCompany = new Company();
        ReflectionTestUtils.setField(otherCompany, "id", 999L);
        otherCompany.setName("Công ty khác");

        JobPost otherJob = new JobPost();
        otherJob.setId(77L);
        otherJob.setCompany(otherCompany); // Company ID: 999
        otherJob.setTitle("Tin của công ty khác");

        when(jobPostRepository.findById(77L)).thenReturn(Optional.of(otherJob));

        JobPostRequest updateRequest = new JobPostRequest();
        updateRequest.setTitle("Cố tình chỉnh sửa");
        updateRequest.setCategoryId(1L);
        updateRequest.setVacancies(1);
        updateRequest.setWorkingMode("onsite");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                jobPostService.update(77L, updateRequest));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Bạn không có quyền"));
    }

    @Test
    void delete_byRecruiterOfDifferentCompany_shouldThrowForbidden() {
        mockAuthenticatedRecruiter(); // Recruiter companyId is 10

        Company otherCompany = new Company();
        ReflectionTestUtils.setField(otherCompany, "id", 888L);

        JobPost otherJob = new JobPost();
        otherJob.setId(88L);
        otherJob.setCompany(otherCompany);

        when(jobPostRepository.findById(88L)).thenReturn(Optional.of(otherJob));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                jobPostService.delete(88L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(jobPostRepository, never()).delete(any(JobPost.class));
    }

    @Test
    void getAll_byAnonymous_withoutStatus_shouldFilterByOpen() {
        JobPost openJob = new JobPost();
        openJob.setId(101L);
        openJob.setStatus("open");
        openJob.setCompany(testCompany);

        when(jobPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(openJob), PageRequest.of(0, 10), 1));
        when(jobAddressRepository.findByJobPost_IdIn(List.of(101L)))
                .thenReturn(List.of());
        when(jobModerationRepository.findByJobPost_IdInOrderByCreatedAtDescIdDesc(List.of(101L)))
                .thenReturn(List.of());

        Page<JobPostResponse> result = jobPostService.getAll(null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("open", result.getContent().get(0).getStatus());
        verify(jobPostRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getAll_byAnonymous_withDraftStatus_shouldReturnEmptyListImmediately() {
        Page<JobPostResponse> result = jobPostService.getAll("draft", null, null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(jobPostRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getAll_withKeywordSearch_shouldQueryRepositoryAndReturnPage() {
        JobPost matchedJob = new JobPost();
        matchedJob.setId(102L);
        matchedJob.setTitle("Java Backend Engineer");
        matchedJob.setStatus("open");
        matchedJob.setCompany(testCompany);

        when(jobPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(matchedJob), PageRequest.of(0, 10), 1));
        when(jobAddressRepository.findByJobPost_IdIn(List.of(102L)))
                .thenReturn(List.of());
        when(jobModerationRepository.findByJobPost_IdInOrderByCreatedAtDescIdDesc(List.of(102L)))
                .thenReturn(List.of());

        Page<JobPostResponse> result = jobPostService.getAll(
                "Java", null, null, null, null, null, null, null, null, null, 0, 10, "newest"
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Java Backend Engineer", result.getContent().get(0).getTitle());
        verify(jobPostRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 10)));
    }

    @Test
    void getAll_withSalaryRange_shouldPassParametersAndReturnFilteredPage() {
        JobPost job = new JobPost();
        job.setId(103L);
        job.setTitle("Senior Dev");
        job.setStatus("open");
        job.setCompany(testCompany);
        job.setSalaryMin(new BigDecimal("15000000"));
        job.setSalaryMax(new BigDecimal("30000000"));

        when(jobPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 10), 1));
        when(jobAddressRepository.findByJobPost_IdIn(List.of(103L)))
                .thenReturn(List.of());
        when(jobModerationRepository.findByJobPost_IdInOrderByCreatedAtDescIdDesc(List.of(103L)))
                .thenReturn(List.of());

        Page<JobPostResponse> result = jobPostService.getAll(
                null, null, null, null, null,
                new BigDecimal("10000000"), new BigDecimal("35000000"),
                "hybrid", null, null, 0, 10, "salary_desc"
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(new BigDecimal("15000000"), result.getContent().get(0).getSalaryMin());
        verify(jobPostRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 10)));
    }

    @Test
    void getAll_withPagination_shouldRequestCorrectPageAndSize() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(jobPostRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                .thenReturn(Page.empty());

        Page<JobPostResponse> result = jobPostService.getAll(
                null, "open", null, null, null, null, null, null, null, null, 2, 15, "newest"
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
        Pageable captured = pageableCaptor.getValue();
        assertEquals(2, captured.getPageNumber());
        assertEquals(15, captured.getPageSize());
    }

    @Test
    void getAll_withSortOptions_shouldExecuteSuccessfully() {
        JobPost job1 = new JobPost();
        job1.setId(104L);
        job1.setStatus("open");
        job1.setCompany(testCompany);

        when(jobPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job1), PageRequest.of(0, 10), 1));
        when(jobAddressRepository.findByJobPost_IdIn(List.of(104L)))
                .thenReturn(List.of());
        when(jobModerationRepository.findByJobPost_IdInOrderByCreatedAtDescIdDesc(List.of(104L)))
                .thenReturn(List.of());

        Page<JobPostResponse> resFeatured = jobPostService.getAll(
                null, null, null, null, null, null, null, null, null, null, 0, 10, "featured"
        );
        assertEquals(1, resFeatured.getTotalElements());

        Page<JobPostResponse> resSalary = jobPostService.getAll(
                null, null, null, null, null, null, null, null, null, null, 0, 10, "salary_desc"
        );
        assertEquals(1, resSalary.getTotalElements());
    }

    @Test
    void getById_withDraftStatus_byAnonymous_shouldThrowNotFound() {
        JobPost draftJob = new JobPost();
        draftJob.setId(202L);
        draftJob.setStatus("draft");
        draftJob.setCompany(testCompany);

        when(jobPostRepository.findById(202L)).thenReturn(Optional.of(draftJob));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                jobPostService.getById(202L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("chưa được công khai"));
    }

    @Test
    void getById_withDraftStatus_byOwnerRecruiter_shouldSucceed() {
        mockAuthenticatedRecruiter();

        JobPost draftJob = new JobPost();
        draftJob.setId(203L);
        draftJob.setStatus("draft");
        draftJob.setCompany(testCompany);

        when(jobPostRepository.findById(203L)).thenReturn(Optional.of(draftJob));
        when(jobAddressService.getByJobPostId(203L)).thenReturn(List.of());
        when(jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(203L))
                .thenReturn(Optional.empty());

        JobPostResponse response = jobPostService.getById(203L);

        assertNotNull(response);
        assertEquals("draft", response.getStatus());
    }

    @Test
    void getById_withDraftStatus_byAdmin_shouldSucceed() {
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        JobPost draftJob = new JobPost();
        draftJob.setId(204L);
        draftJob.setStatus("draft");
        draftJob.setCompany(testCompany);

        when(jobPostRepository.findById(204L)).thenReturn(Optional.of(draftJob));
        when(jobAddressService.getByJobPostId(204L)).thenReturn(List.of());
        when(jobModerationRepository.findFirstByJobPost_IdOrderByCreatedAtDescIdDesc(204L))
                .thenReturn(Optional.empty());

        JobPostResponse response = jobPostService.getById(204L);

        assertNotNull(response);
        assertEquals("draft", response.getStatus());
    }
}
