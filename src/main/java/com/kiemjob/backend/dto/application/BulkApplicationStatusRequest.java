package com.kiemjob.backend.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkApplicationStatusRequest {

    @NotEmpty(message = "Danh sách mã đơn ứng tuyển không được rỗng")
    private List<@NotNull(message = "Mã đơn ứng tuyển không được để trống") Long> ids;

    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(
        regexp = "pending|viewed|shortlisted|interview|rejected|accepted",
        message = "Trạng thái không hợp lệ. Chỉ chấp nhận: pending, viewed, shortlisted, interview, rejected, accepted"
    )
    private String status;
}

