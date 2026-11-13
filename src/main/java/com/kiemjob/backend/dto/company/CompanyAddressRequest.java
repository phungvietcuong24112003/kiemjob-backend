
package com.kiemjob.backend.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CompanyAddressRequest(
    @NotNull(message = "ID tỉnh/thành phố không được để trống")
    @Positive(message = "ID tỉnh/thành phố phải lớn hơn 0")
    Long provinceId,

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    String addressDetail
) {}
