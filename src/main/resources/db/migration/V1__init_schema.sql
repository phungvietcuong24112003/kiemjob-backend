CREATE TABLE account (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),                
    role          VARCHAR(20)  NOT NULL,
	email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_account_role CHECK (role IN ('candidate', 'recruiter', 'admin'))
);
CREATE UNIQUE INDEX uq_account_email ON account (LOWER(email));
-----------------------------------------------------------------------------------
CREATE TABLE account_identities (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    accountid        BIGINT       NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    provider         VARCHAR(30)  NOT NULL, 
    provider_userid  VARCHAR(255) NOT NULL,
    CONSTRAINT uq_identity_provider_user UNIQUE (provider, provider_userid)
);
CREATE INDEX idx_account_identities_accountid ON account_identities (accountid);
-----------------------------------------------------------------------------------
CREATE TABLE auth_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT       NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    purpose     VARCHAR(30)  NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,  
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,                 
    CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('verifyemail', 'resetpassword'))
);
CREATE UNIQUE INDEX uq_auth_tokens_token_hash ON auth_tokens (token_hash);
CREATE INDEX idx_auth_tokens_account_purpose ON auth_tokens (account_id, purpose);
CREATE INDEX idx_auth_tokens_expires_at ON auth_tokens (expires_at); 
-----------------------------------------------------------------------------------
CREATE TABLE provinces (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE     
);
-----------------------------------------------------------------------------------
CREATE TABLE candidates (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    accountid       BIGINT       NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE, 
    fullname        VARCHAR(150) NOT NULL,
    avatarurl       VARCHAR(500),
    headline        VARCHAR(255),
    gender          VARCHAR(10),
    phone           VARCHAR(20),
    date_of_birth   DATE,
    province_id     BIGINT REFERENCES provinces(id),
    address_detail  VARCHAR(255),
    CONSTRAINT ck_candidates_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'other')),
    CONSTRAINT ck_candidates_dob CHECK (date_of_birth IS NULL OR date_of_birth <= CURRENT_DATE)
);
CREATE INDEX idx_candidates_province_id ON candidates (province_id);
-----------------------------------------------------------------------------------
CREATE TABLE candidates_link (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidat_id  BIGINT       NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,  
    type         VARCHAR(30)  NOT NULL,   
    link         VARCHAR(500) NOT NULL
);
CREATE INDEX idx_candidates_link_candidat_id ON candidates_link (candidat_id);
-----------------------------------------------------------------------------------
CREATE TABLE companies (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_type  VARCHAR(10)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    logo_url      VARCHAR(500),
    banner_url    VARCHAR(500),
    email         VARCHAR(255),
    phone         VARCHAR(20),
    description   TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'pending',
    CONSTRAINT ck_companies_type CHECK (company_type IN ('big', 'small')),
    CONSTRAINT ck_companies_status CHECK (status IN ('pending', 'approved'))
);

CREATE INDEX idx_companies_name ON companies (name);
CREATE INDEX idx_companies_status ON companies (status);
-----------------------------------------------------------------------------------
CREATE TABLE company_address (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    province_id     BIGINT NOT NULL REFERENCES provinces(id),
    address_detail  VARCHAR(255) NOT NULL
);
CREATE INDEX idx_company_address_company_id  ON company_address (company_id);
CREATE INDEX idx_company_address_province_id ON company_address (province_id);
-----------------------------------------------------------------------------------
CREATE TABLE company_links (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    tpye        VARCHAR(30)  NOT NULL,         
    link        VARCHAR(500) NOT NULL
);
CREATE INDEX idx_company_links_company_id ON company_links (company_id);
-----------------------------------------------------------------------------------
CREATE TABLE company_images (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL
);
CREATE INDEX idx_company_images_company_id ON company_images (company_id);
-----------------------------------------------------------------------------------
CREATE TABLE company_license (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    file_url    VARCHAR(500) NOT NULL
);
CREATE INDEX idx_company_license_company_id ON company_license (company_id);
-----------------------------------------------------------------------------------
CREATE TABLE recruiter (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT       NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE, 
    companyid   BIGINT       NOT NULL REFERENCES companies(id),
    full_name   VARCHAR(150) NOT NULL
);
CREATE INDEX idx_recruiter_companyid ON recruiter (companyid);
-----------------------------------------------------------------------------------
CREATE TABLE conversations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id  BIGINT NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    recruiter_id  BIGINT NOT NULL REFERENCES recruiter(id)  ON DELETE CASCADE,
    CONSTRAINT uq_conversations_pair UNIQUE (candidate_id, recruiter_id)
);
CREATE INDEX idx_conversations_recruiter_id ON conversations (recruiter_id);
-----------------------------------------------------------------------------------
CREATE TABLE messages (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_type      VARCHAR(20) NOT NULL,      
    content          TEXT        NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_messages_sender_type CHECK (sender_type IN ('candidate', 'recruiter')),
    CONSTRAINT ck_messages_content CHECK (LENGTH(TRIM(content)) > 0)   
);
CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);
-----------------------------------------------------------------------------------
CREATE TABLE skill_categories (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE
);
-----------------------------------------------------------------------------------
CREATE TABLE skill (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id  BIGINT       NOT NULL REFERENCES skill_categories(id),
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    CONSTRAINT uq_skill_category_name UNIQUE (category_id, name)
);
-----------------------------------------------------------------------------------
CREATE TABLE cvs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id  BIGINT       NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL
);
CREATE INDEX idx_cvs_candidate_id ON cvs (candidate_id);
-----------------------------------------------------------------------------------
CREATE TABLE cv_pdf_files (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id           BIGINT NOT NULL
                        REFERENCES cvs(id) ON DELETE CASCADE,

    file_name       VARCHAR(255) NOT NULL,
    bucket_name     VARCHAR(255) NOT NULL,
    object_key      VARCHAR(1024) NOT NULL UNIQUE,

    file_size_bytes BIGINT,
    version_no      INTEGER NOT NULL DEFAULT 1,
    is_current      BOOLEAN NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_cv_pdf_files_size
        CHECK (file_size_bytes IS NULL OR file_size_bytes > 0),

    CONSTRAINT ck_cv_pdf_files_version
        CHECK (version_no > 0),

    CONSTRAINT uq_cv_pdf_files_version
        UNIQUE (cv_id, version_no)
);

CREATE INDEX idx_cv_pdf_files_cv_id
    ON cv_pdf_files(cv_id);

-- Mỗi CV chỉ có một file PDF hiện hành
CREATE UNIQUE INDEX uq_cv_pdf_files_current
    ON cv_pdf_files(cv_id)
    WHERE is_current = TRUE;
-----------------------------------------------------------------------------------
CREATE TABLE cv_career_objectives (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id       BIGINT NOT NULL UNIQUE REFERENCES cvs(id) ON DELETE CASCADE,
    content     TEXT NOT NULL
);
-----------------------------------------------------------------------------------
CREATE TABLE cv_edudation (                
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cvid          BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    school_name   VARCHAR(255) NOT NULL,
    start_date    DATE,
    end_date      DATE,                  
    rank          VARCHAR(50),              
    achivements   TEXT,                     
    CONSTRAINT ck_cv_edudation_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_cv_edudation_cvid ON cv_edudation (cvid);
-----------------------------------------------------------------------------------
CREATE TABLE cv_skill_groups (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id BIGINT NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
	content TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_cv_skill_groups_cv_id ON cv_skill_groups(cv_id);
-----------------------------------------------------------------------------------
CREATE TABLE cv_work_experiences (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id             BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    company_name      VARCHAR(255) NOT NULL,
    position          VARCHAR(255) NOT NULL,
    employment_type   VARCHAR(50),
    start_date        DATE,
    end_date          DATE,
    is_current        BOOLEAN      NOT NULL DEFAULT FALSE,
    description       TEXT,
    CONSTRAINT ck_cv_work_experiences_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_cv_work_experiences_current
        CHECK (is_current = FALSE OR end_date IS NULL)
);
CREATE INDEX idx_cv_work_experiences_cv_id ON cv_work_experiences (cv_id);
-----------------------------------------------------------------------------------
CREATE TABLE cv_certificate (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id            BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    certificate_date DATE         NOT NULL,
    certificate_name VARCHAR(255) NOT NULL,
    description      TEXT
);
CREATE INDEX idx_cv_certificate_cv_id ON cv_certificate (cv_id);
-----------------------------------------------------------------------------------
CREATE TABLE cv_awards (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id         BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    award_date    DATE         NOT NULL,
    award_name    VARCHAR(255) NOT NULL,
    description   TEXT
);
CREATE INDEX idx_cv_awards_cv_id ON cv_awards (cv_id);
-----------------------------------------------------------------------------------
CREATE TABLE cv_hobbies (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id        BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    hobby_name   VARCHAR(255) NOT NULL,

    CONSTRAINT uq_cv_hobbies
        UNIQUE (cv_id, hobby_name)
);

CREATE INDEX idx_cv_hobbies_cv_id
    ON cv_hobbies (cv_id);

CREATE TABLE cv_projects (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id           BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    project_name    VARCHAR(255) NOT NULL,
    role            VARCHAR(255),
    start_date      DATE,
    end_date        DATE,
    is_current      BOOLEAN      NOT NULL DEFAULT FALSE,
    description     TEXT,
    technologies    TEXT,
    project_url     VARCHAR(500),
    repository_url  VARCHAR(500),

    CONSTRAINT ck_cv_projects_dates
        CHECK (
            start_date IS NULL
            OR end_date IS NULL
            OR end_date >= start_date
        ),

    CONSTRAINT ck_cv_projects_current
        CHECK (
            is_current = FALSE
            OR end_date IS NULL
        )
);

CREATE INDEX idx_cv_projects_cv_id
    ON cv_projects (cv_id);

CREATE TABLE cv_mores (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id    BIGINT       NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    title    VARCHAR(255) NOT NULL,
    content  TEXT
);
CREATE INDEX idx_cv_mores_cv_id ON cv_mores (cv_id);

CREATE TABLE cv_section_orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    cv_id BIGINT NOT NULL
        REFERENCES cvs(id) ON DELETE CASCADE,

    section_type VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT ck_cv_section_orders_type
        CHECK (
            section_type IN (
                'education',
                'skills',
                'work_experiences',
                'certificates',
                'awards',
                'hobbies',
                'projects',
                'mores'
            )
        ),

    CONSTRAINT uq_cv_section_orders
        UNIQUE (cv_id, section_type)
);

CREATE INDEX idx_cv_section_orders_cv_order
    ON cv_section_orders (cv_id, display_order);

CREATE TABLE cv_analysis (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cv_id          BIGINT NOT NULL REFERENCES cvs(id) ON DELETE CASCADE,
    parsed_data    JSONB,                        -- dữ liệu CV sau khi phân tích
    quality_score  NUMERIC(5,2),
    suggestions    TEXT,
    -- [GIẢ ĐỊNH] điểm chất lượng thang 0 - 100
    CONSTRAINT ck_cv_analysis_score CHECK (quality_score IS NULL OR quality_score BETWEEN 0 AND 100)
);
CREATE INDEX idx_cv_analysis_cv_id ON cv_analysis (cv_id);

-- =====================================================================
-- 8. TIN TUYỂN DỤNG
-- =====================================================================

CREATE TABLE jobs_categories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    description  TEXT
);

CREATE TABLE job_specializations (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_job_category  BIGINT NOT NULL
                         REFERENCES jobs_categories(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,

    CONSTRAINT uq_job_specializations
        UNIQUE (id_job_category, name)
);

CREATE INDEX idx_job_specializations_category
    ON job_specializations (id_job_category);

CREATE TABLE job_post_specializations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    job_post_id BIGINT NOT NULL
        REFERENCES job_posts(id) ON DELETE CASCADE,

    specialization_id BIGINT NOT NULL
        REFERENCES job_specializations(id),

    CONSTRAINT uq_job_post_specializations
        UNIQUE (job_post_id, specialization_id)
);

CREATE INDEX idx_job_post_specializations_specialization_id
    ON job_post_specializations (specialization_id);

CREATE TABLE job_posts (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES companies(id),
    category_id             BIGINT       NOT NULL REFERENCES jobs_categories(id),
    postedby                BIGINT       NOT NULL REFERENCES recruiter(id),   -- recruiter đăng tin
    status                  VARCHAR(20)  NOT NULL DEFAULT 'draft',
    title                   VARCHAR(255) NOT NULL,
    educational_requirement VARCHAR(255),
    application_deadline    DATE,
    vacancies               INT          NOT NULL DEFAULT 1,
    workingmode             VARCHAR(20)  NOT NULL,
    salary_min              NUMERIC(15,2),
    salary_max              NUMERIC(15,2),
    posted_at               TIMESTAMP,
    -- [GIẢ ĐỊNH] trạng thái tin: nháp / đang mở / đã đóng / hết hạn
    CONSTRAINT ck_job_posts_status CHECK (status IN ('draft', 'open', 'closed', 'expired')),
    -- [GIẢ ĐỊNH] hình thức làm việc: tại văn phòng / từ xa / kết hợp
    CONSTRAINT ck_job_posts_workingmode CHECK (workingmode IN ('onsite', 'remote', 'hybrid')),
    -- Số lượng tuyển phải > 0
    CONSTRAINT ck_job_posts_vacancies CHECK (vacancies > 0),
    -- Lương không âm và min <= max
    CONSTRAINT ck_job_posts_salary CHECK (
        (salary_min IS NULL OR salary_min >= 0)
        AND (salary_max IS NULL OR salary_max >= 0)
        AND (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
    )
);
CREATE INDEX idx_job_posts_company_id   ON job_posts (company_id);
CREATE INDEX idx_job_posts_category_id  ON job_posts (category_id);
CREATE INDEX idx_job_posts_postedby     ON job_posts (postedby);
-- Màn hình tìm kiếm: lọc tin đang mở, mới nhất trước
CREATE INDEX idx_job_posts_status_posted ON job_posts (status, posted_at DESC);
CREATE INDEX idx_job_posts_deadline     ON job_posts (application_deadline);

CREATE TABLE job_address (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_post_id      BIGINT NOT NULL
                         REFERENCES job_posts(id) ON DELETE CASCADE,
    province_id      BIGINT NOT NULL
                         REFERENCES provinces(id),
    address_detail   VARCHAR(255),

    CONSTRAINT uq_job_address
        UNIQUE (job_post_id, province_id, address_detail)
);

CREATE INDEX idx_job_address_province_id
    ON job_address (province_id);

CREATE TABLE job_details (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jobid    BIGINT       NOT NULL REFERENCES job_posts(id) ON DELETE CASCADE,
    title    VARCHAR(255) NOT NULL,              -- vd: Mô tả công việc, Quyền lợi
    content  TEXT
);
CREATE INDEX idx_job_details_jobid ON job_details (jobid);

-- =====================================================================
-- 9. ỨNG TUYỂN & LƯU TIN
-- =====================================================================

CREATE TABLE applications (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jobpost_id    BIGINT      NOT NULL REFERENCES job_posts(id) ON DELETE CASCADE,
    cvid          BIGINT      NOT NULL REFERENCES cvs(id),
    cover_latter  TEXT,                          -- (giữ nguyên tên cột "cover_latter")
    status        VARCHAR(20) NOT NULL DEFAULT 'pending',
    applied_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- [GIẢ ĐỊNH] trạng thái hồ sơ
    CONSTRAINT ck_applications_status CHECK (status IN ('pending', 'viewed', 'shortlisted', 'interview', 'rejected', 'accepted')),
    -- Mỗi CV chỉ ứng tuyển 1 lần vào 1 tin
    CONSTRAINT uq_applications_job_cv UNIQUE (jobpost_id, cvid)
);
CREATE INDEX idx_applications_cvid ON applications (cvid);
CREATE INDEX idx_applications_job_status ON applications (jobpost_id, status);

CREATE TABLE saved_jobs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jobid           BIGINT NOT NULL REFERENCES job_posts(id)  ON DELETE CASCADE,
    candidated_id   BIGINT NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,   -- (giữ nguyên tên "candidated_id")
    -- 1 ứng viên chỉ lưu 1 tin 1 lần
    CONSTRAINT uq_saved_jobs UNIQUE (jobid, candidated_id)
);
CREATE INDEX idx_saved_jobs_candidated_id ON saved_jobs (candidated_id);

-- =====================================================================
-- 10. GÓI ĐẨY TIN & THANH TOÁN
-- =====================================================================
CREATE TABLE feature_packages (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL UNIQUE,
    description    TEXT,
    price          BIGINT       NOT NULL,
    duration_days  INT          NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_feature_packages_price
        CHECK (price > 0),

    CONSTRAINT ck_feature_packages_duration
        CHECK (duration_days > 0)
);

CREATE INDEX idx_feature_packages_active
    ON feature_packages (is_active);

-- =========================================================
-- 2. ĐƠN HÀNG MUA GÓI
-- =========================================================

CREATE TABLE featured_orders (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    recruiter_id      BIGINT        NOT NULL
        REFERENCES recruiter(id) ON DELETE RESTRICT,

    plan_id           BIGINT        NOT NULL
        REFERENCES feature_packages(id) ON DELETE RESTRICT,

    -- Giá được chốt tại thời điểm tạo đơn, đơn vị VND
    amount            BIGINT        NOT NULL,

    currency          VARCHAR(3)    NOT NULL DEFAULT 'VND',

    status            VARCHAR(20)   NOT NULL DEFAULT 'pending',

    -- Khóa chống tạo trùng đơn khi client gửi lại request
    idempotency_key   VARCHAR(100)  NOT NULL,

    -- SHA-256 của dữ liệu yêu cầu đã chuẩn hóa
    -- Dùng để phát hiện cùng key nhưng khác nội dung
    request_hash      CHAR(64)      NOT NULL,

    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at           TIMESTAMP,
    cancelled_at      TIMESTAMP,
    refunded_at       TIMESTAMP,

    CONSTRAINT ck_featured_orders_amount
        CHECK (amount > 0),

    CONSTRAINT ck_featured_orders_currency
        CHECK (currency = 'VND'),

    CONSTRAINT ck_featured_orders_status
        CHECK (
            status IN (
                'pending',
                'paid',
                'failed',
                'cancelled',
                'refunded'
            )
        ),

    CONSTRAINT ck_featured_orders_paid
        CHECK (status <> 'paid' OR paid_at IS NOT NULL),

    CONSTRAINT ck_featured_orders_cancelled
        CHECK (status <> 'cancelled' OR cancelled_at IS NOT NULL),

    CONSTRAINT ck_featured_orders_refunded
        CHECK (status <> 'refunded' OR refunded_at IS NOT NULL),

    -- Một recruiter không thể tạo hai đơn từ cùng một request key
    CONSTRAINT uq_featured_orders_idempotency
        UNIQUE (recruiter_id, idempotency_key)
);

CREATE INDEX idx_featured_orders_recruiter_created
    ON featured_orders (recruiter_id, created_at DESC);

CREATE INDEX idx_featured_orders_status_created
    ON featured_orders (status, created_at);

-- =========================================================
-- 3. CÁC LẦN THỬ THANH TOÁN
-- =========================================================

CREATE TABLE payment_transactions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    orderid             BIGINT        NOT NULL
        REFERENCES featured_orders(id) ON DELETE RESTRICT,

    provider            VARCHAR(20)   NOT NULL,

    -- ID giao dịch thực tế do cổng thanh toán cấp
    provider_txn_id     VARCHAR(100),

    -- Mã đơn/lần thanh toán gửi sang cổng:
    -- MoMo: orderId; VNPay: vnp_TxnRef
    provider_order_ref  VARCHAR(100)  NOT NULL,

    -- Request ID gửi cổng nếu được hỗ trợ, ví dụ MoMo requestId
    provider_request_id VARCHAR(100),

    -- Khóa idempotency nội bộ cho việc tạo giao dịch
    idempotency_key     VARCHAR(100)  NOT NULL,

    -- Dấu vân tay của yêu cầu thanh toán đã chuẩn hóa
    request_hash        CHAR(64)      NOT NULL,

    amount              BIGINT        NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'VND',

    status              VARCHAR(20)   NOT NULL DEFAULT 'pending',

    failure_code        VARCHAR(100),
    failure_message     VARCHAR(500),

    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    refunded_at         TIMESTAMP,

    CONSTRAINT ck_payment_provider
        CHECK (provider IN ('momo', 'vnpay')),

    CONSTRAINT ck_payment_status
        CHECK (
            status IN (
                'pending',
                'success',
                'failed',
                'cancelled',
                'refunded'
            )
        ),

    CONSTRAINT ck_payment_amount
        CHECK (amount > 0),

    CONSTRAINT ck_payment_currency
        CHECK (currency = 'VND'),

    CONSTRAINT ck_payment_success_time
        CHECK (status <> 'success' OR completed_at IS NOT NULL),

    CONSTRAINT ck_payment_refunded_time
        CHECK (status <> 'refunded' OR refunded_at IS NOT NULL),

    CONSTRAINT uq_payment_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT uq_payment_provider_order_ref
        UNIQUE (provider, provider_order_ref)
);

-- Một mã giao dịch thực của cùng một cổng chỉ được ghi nhận một lần.
-- PostgreSQL cho phép nhiều giá trị NULL trong unique index thông thường,
-- nhưng dùng partial index để thể hiện rõ ý định.

CREATE UNIQUE INDEX uq_payment_provider_txn
    ON payment_transactions (provider, provider_txn_id)
    WHERE provider_txn_id IS NOT NULL;

-- Một requestId của cùng một cổng chỉ được dùng một lần.
CREATE UNIQUE INDEX uq_payment_provider_request
    ON payment_transactions (provider, provider_request_id)
    WHERE provider_request_id IS NOT NULL;

-- Không tạo nhiều giao dịch pending cho cùng một đơn.
-- Muốn thử lại, backend cần xử lý hoặc đóng lần thử cũ trước.
CREATE UNIQUE INDEX uq_payment_one_pending_per_order
    ON payment_transactions (orderid)
    WHERE status = 'pending';

CREATE INDEX idx_payment_order_created
    ON payment_transactions (orderid, created_at DESC);

CREATE INDEX idx_payment_status_created
    ON payment_transactions (status, created_at);

ALTER TABLE payment_transactions
ALTER COLUMN request_hash TYPE VARCHAR(64)
USING TRIM(request_hash)::VARCHAR(64);


CREATE TABLE feedbacks (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT NOT NULL
                    REFERENCES account(id) ON DELETE CASCADE,
    subject     VARCHAR(255) NOT NULL,
    rating      INTEGER NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_feedbacks_rating
        CHECK (rating BETWEEN 1 AND 5),

    CONSTRAINT ck_feedbacks_subject
        CHECK (LENGTH(TRIM(subject)) > 0),

    CONSTRAINT ck_feedbacks_content
        CHECK (LENGTH(TRIM(content)) > 0)
);

CREATE INDEX idx_feedbacks_account_id
    ON feedbacks(account_id);

CREATE INDEX idx_feedbacks_created_at
    ON feedbacks(created_at DESC);

-- 2. Lưu lịch sử kiểm duyệt AI
CREATE TABLE job_moderations (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    result JSONB NOT NULL,
    moderation_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT fk_job_moderations_job
        FOREIGN KEY (job_id)
        REFERENCES job_posts(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_job_moderations_reviewer
        FOREIGN KEY (reviewed_by)
        REFERENCES account(id),
    CONSTRAINT ck_job_moderations_status
        CHECK (
            moderation_status IN (
                'passed',
                'pending_review',
                'approved',
                'rejected',
                'error'
            )
        )
);

CREATE INDEX idx_job_moderations_job_id
ON job_moderations(job_id);
