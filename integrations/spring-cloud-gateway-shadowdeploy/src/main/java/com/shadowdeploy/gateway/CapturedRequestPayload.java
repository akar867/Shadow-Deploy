package com.shadowdeploy.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record CapturedRequestPayload(
        String requestId,
        String method,
        String path,
        String query,
        Map<String, String> headers,
        JsonNode body,
        Integer prodStatus,
        JsonNode prodBody,
        Long prodLatencyMs,
        String contentType
) {
}
