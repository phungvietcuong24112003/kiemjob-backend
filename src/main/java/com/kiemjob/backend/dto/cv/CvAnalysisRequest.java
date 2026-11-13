package com.kiemjob.backend.dto.cv;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CvAnalysisRequest {

    private JsonNode parsedData;

    private String summary;

    private String embedding;

    @DecimalMin(value = "0.00", message = "Điểm chất lượng tối thiểu là 0")
    @DecimalMax(value = "100.00", message = "Điểm chất lượng tối đa là 100")
    private BigDecimal qualityScore;

    private String suggestions;

    @Pattern(regexp = "^(?i)(pending|processing|completed|failed)$", message = "Trạng thái phân tích không hợp lệ (chỉ chấp nhận pending, processing, completed, failed)")
    private String analysisStatus;
}
