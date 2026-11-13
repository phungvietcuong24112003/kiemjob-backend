
package com.kiemjob.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaymentCreateRequest(

        @NotBlank(message = "Cổng thanh toán không được để trống")
        @Pattern(
                regexp = "momo|vnpay",
                message = "Cổng thanh toán phải là momo hoặc vnpay"
        )
        String provider

) {}
