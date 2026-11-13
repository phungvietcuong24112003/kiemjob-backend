package com.kiemjob.backend.dto.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkApplicationStatusResponse {
    private int updatedCount;
    private List<Long> updatedIds;
    private String status;
    private String message;
}

