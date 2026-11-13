package com.kiemjob.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecruiterProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @NotBlank(message = "Tên công ty không được để trống")
        @Size(max = 255, message = "Tên công ty tối đa 255 ký tự")
        String companyName,

        @Pattern(regexp = "big|small", message = "Loại công ty phải là 'big' hoặc 'small'")
        String companyType,

        @Size(max = 500, message = "Logo URL tối đa 500 ký tự")
        String logoUrl,

        @Size(max = 500, message = "Banner URL tối đa 500 ký tự")
        String bannerUrl,

        @Size(max = 255, message = "Email công ty tối đa 255 ký tự")
        String companyEmail,

        @Size(max = 20, message = "Số điện thoại công ty tối đa 20 ký tự")
        String companyPhone,

        String companyDescription
) {
}
