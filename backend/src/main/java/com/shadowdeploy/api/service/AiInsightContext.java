package com.shadowdeploy.api.service;

import com.shadowdeploy.api.model.DiffFinding;
import com.shadowdeploy.api.model.DiffMetric;

import java.util.List;

public record AiInsightContext(
        String deploymentId,
        String serviceName,
        String status,
        double riskScore,
        int totalRequests,
        int errorCount,
        int driftCount,
        double p95Latency,
        double mismatchRate,
        double driftRate,
        double latencyDelta,
        List<DiffMetric> metrics,
        List<DiffFinding> findings
) {
}
