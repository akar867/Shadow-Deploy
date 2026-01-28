package com.shadowdeploy.api.model;

public record DiffMetric(
        String label,
        String description,
        double value,
        String unit
) {
}
