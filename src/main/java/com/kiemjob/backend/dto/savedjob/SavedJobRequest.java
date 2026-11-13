
package com.kiemjob.backend.dto.savedjob;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavedJobRequest {

    @NotNull(message = "Job ID không được để trống")
    @Positive(message = "Job ID phải lớn hơn 0")
    private Long jobId;
}
