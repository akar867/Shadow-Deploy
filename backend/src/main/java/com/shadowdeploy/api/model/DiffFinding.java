package com.shadowdeploy.api.model;

public record DiffFinding(
        String id,
        String title,
        String severity,
        double affectedPercent,
        String description,
        String recommendation
) {
}
