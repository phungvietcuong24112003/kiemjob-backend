package com.kiemjob.backend.dto.job;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class JobPostRequest {

    @Positive(message = "ID công ty phải lớn hơn 0")
    private Long companyId;

    @NotNull(message = "ID danh mục không được để trống")
    @Positive(message = "ID danh mục phải lớn hơn 0")
    private Long categoryId;

    /*
     * Backend xác định recruiter từ token đăng nhập của người dùng.
     * Trường postedById có thể truyền từ client hoặc tự động lấy từ phiên đăng nhập.
     */
    @Positive(message = "ID nhà tuyển dụng phải lớn hơn 0")
    private Long postedById;

    @NotBlank(message = "Tiêu đề tin tuyển dụng không được để trống")
    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    private String title;

    @Size(max = 255, message = "Yêu cầu học vấn tối đa 255 ký tự")
    private String educationalRequirement;

    private LocalDate applicationDeadline;

    @NotNull(message = "Số lượng tuyển không được để trống")
    @Positive(message = "Số lượng tuyển phải lớn hơn 0")
    private Integer vacancies = 1;

    @NotBlank(message = "Hình thức làm việc không được để trống")
    @Pattern(
        regexp = "onsite|remote|hybrid",
        message = "Hình thức làm việc chỉ nhận onsite, remote hoặc hybrid"
    )
    private String workingMode;

    @DecimalMin(value = "0.00", message = "Lương tối thiểu không được âm")
    @Digits(integer = 13, fraction = 2,
        message = "Lương tối thiểu chỉ có tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal salaryMin;

    @DecimalMin(value = "0.00", message = "Lương tối đa không được âm")
    @Digits(integer = 13, fraction = 2,
        message = "Lương tối đa chỉ có tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal salaryMax;

    @Pattern(
        regexp = "draft|open|closed|expired",
        message = "Trạng thái chỉ nhận draft, open, closed hoặc expired"
    )
    private String status = "draft";

    private java.util.List<JobAddressRequest> addresses;
}