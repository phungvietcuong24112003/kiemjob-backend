package com.kiemjob.backend.dto.province;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProvinceRequest {

    @NotBlank(message = "Tên tỉnh/thành không được để trống")
    @Size(max = 100, message = "Tên tỉnh/thành tối đa 100 ký tự")
    private String name;
}