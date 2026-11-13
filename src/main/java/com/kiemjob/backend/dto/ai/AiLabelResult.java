package com.kiemjob.backend.dto.ai;

public record AiLabelResult(
        int prediction,
        double probability
) {
}