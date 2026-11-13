
package com.kiemjob.backend.dto.ai;

import java.util.Map;

public record AiModerationResponse(
        String title,
        Map<String, AiLabelResult> results
) {
}
