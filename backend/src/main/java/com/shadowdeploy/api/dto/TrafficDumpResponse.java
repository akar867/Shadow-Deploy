package com.shadowdeploy.api.dto;

public record TrafficDumpResponse(
        Long id,
        String fileName,
        String serviceName,
        String deploymentId,
        long sizeBytes,
        String uploadedAt,
        Long summaryId
) {
}
