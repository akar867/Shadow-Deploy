package com.shadowdeploy.api.model;

public record RiskItem(
        String label,
        String severity,
        String impact,
        String owner
) {
}
