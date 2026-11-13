package com.kiemjob.backend.dto.job;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobAddressRequest {

    private Long id;

    @Positive(message = "ID tin tuyển dụng phải lớn hơn 0")
    private Long jobPostId;

    @NotNull(message = "ID tỉnh/thành phố không được để trống")
    @Positive(message = "ID tỉnh/thành phố phải lớn hơn 0")
    private Long provinceId;

    @Size(max = 255, message = "Địa chỉ chi tiết không được vượt quá 255 ký tự")
    private String addressDetail;
}