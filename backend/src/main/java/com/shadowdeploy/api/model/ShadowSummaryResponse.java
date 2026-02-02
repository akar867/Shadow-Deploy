package com.shadowdeploy.api.model;

import java.util.List;

public record ShadowSummaryResponse(
        String deploymentId,
        String serviceName,
        String status,
        String generatedAt,
        double riskScore,
        List<DiffMetric> metrics,
        List<DiffFinding> findings,
        List<RiskItem> riskItems,
        List<String> aiInsights
) {}
