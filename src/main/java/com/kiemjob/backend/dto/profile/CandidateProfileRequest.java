package com.kiemjob.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CandidateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullname,

        @Size(max = 500, message = "Avatar URL tối đa 500 ký tự")
        String avatarUrl,

        @Size(max = 255, message = "Headline tối đa 255 ký tự")
        String headline,

        @Pattern(regexp = "male|female|other|^$", message = "Giới tính phải là male, female hoặc other")
        String gender,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @PastOrPresent(message = "Ngày sinh không được ở tương lai")
        LocalDate dateOfBirth,

        Long provinceId,

        @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
        String addressDetail
) {
}
