package com.kiemjob.backend.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyRequest {

    @NotBlank(message = "Loại công ty không được để trống")
    @Pattern(
        regexp = "big|small",
        message = "Loại công ty phải là big hoặc small"
    )
    private String companyType;

    @NotBlank(message = "Tên công ty không được để trống")
    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String logoUrl;

    @Size(max = 500)
    private String bannerUrl;

    @Email(message = "Email không hợp lệ")
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    private String description;
}