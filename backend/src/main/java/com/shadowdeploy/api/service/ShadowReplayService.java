package com.shadowdeploy.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.shadowdeploy.api.dto.CapturedRequest;
import com.shadowdeploy.api.dto.ShadowReplayRequest;
import com.shadowdeploy.api.dto.ShadowReplayResponse;
import com.shadowdeploy.api.dto.TrafficDumpUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ShadowReplayService {

    private final TrafficDumpService trafficDumpService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String defaultShadowBaseUrl;
    private final int timeoutSeconds;
    private final int maxResponseBytes;

    public ShadowReplayService(TrafficDumpService trafficDumpService,
                               ObjectMapper objectMapper,
                               @Value("${shadowdeploy.replay.shadow-base-url:}") String defaultShadowBaseUrl,
                               @Value("${shadowdeploy.replay.timeout-seconds:8}") int timeoutSeconds,
                               @Value("${shadowdeploy.replay.max-response-bytes:200000}") int maxResponseBytes) {
        this.trafficDumpService = trafficDumpService;
        this.objectMapper = objectMapper;
        this.defaultShadowBaseUrl = defaultShadowBaseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.maxResponseBytes = maxResponseBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
                .build();
    }

    public ShadowReplayResponse replay(ShadowReplayRequest request) {
        if (request == null || request.requests() == null || request.requests().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Captured requests are required");
        }
        String baseUrl = resolveBaseUrl(request.shadowBaseUrl());
        String serviceName = request.serviceName() == null || request.serviceName().isBlank()
                ? "checkout-service"
                : request.serviceName().trim();
        String deploymentId = request.deploymentId() == null || request.deploymentId().isBlank()
                ? "replay-" + Instant.now()
                : request.deploymentId().trim();

        List<String> jsonLines = new ArrayList<>();
        int failed = 0;
        int replayed = 0;

        for (CapturedRequest captured : request.requests()) {
            ReplayResult result = executeReplay(baseUrl, captured);
            if (!result.success) {
                failed++;
            }
            replayed++;
            Map<String, Object> entry = buildEntry(captured, result);
            try {
                jsonLines.add(objectMapper.writeValueAsString(entry));
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to build replay dump", ex);
            }
        }

        String content = String.join("\n", jsonLines);
        TrafficDumpUploadResponse response = trafficDumpService.ingestContent(
                "shadow-replay-" + deploymentId + ".jsonl",
                "application/json",
                content,
                serviceName,
                deploymentId
        );

        return new ShadowReplayResponse(
                response.trafficDump(),
                response.summary(),
                request.requests().size(),
                replayed,
                failed
        );
    }

    private ReplayResult executeReplay(String baseUrl, CapturedRequest captured) {
        String method = captured.method() == null ? "GET" : captured.method().trim().toUpperCase(Locale.ROOT);
        String path = captured.path() == null ? "/" : captured.path().trim();
        String query = captured.query() == null ? "" : captured.query().trim();
        String url = buildUrl(baseUrl, path, query);

        String bodyPayload = resolveBodyPayload(captured.body());
        boolean hasBody = bodyPayload != null && !bodyPayload.isEmpty();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (hasBody && allowsBody(method)) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(bodyPayload));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        Map<String, String> headers = captured.headers();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key == null || value == null) {
                    return;
                }
                String lower = key.toLowerCase(Locale.ROOT);
                if ("host".equals(lower) || "content-length".equals(lower)) {
                    return;
                }
                builder.header(key, value);
            });
        }

        if (captured.contentType() != null && !captured.contentType().isBlank()) {
            builder.header("Content-Type", captured.contentType());
        } else if (hasBody) {
            builder.header("Content-Type", "application/json");
        }
        builder.header("X-Shadow-Replay", "true");

        long start = System.currentTimeMillis();
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            long latency = System.currentTimeMillis() - start;
            int status = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("application/json");
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            JsonNode parsed = parseBody(bytes, contentType);
            return new ReplayResult(true, status, latency, parsed, null);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            return new ReplayResult(false, 599, latency, null, ex.getMessage());
        }
    }

    private Map<String, Object> buildEntry(CapturedRequest captured, ReplayResult replay) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("requestId", captured.requestId() == null ? "req-" + UUID.randomUUID() : captured.requestId());
        entry.put("path", captured.path());
        entry.put("method", captured.method());
        if (captured.prodStatus() != null) {
            entry.put("statusProd", captured.prodStatus());
        }
        entry.put("statusShadow", replay.status);
        if (captured.prodLatencyMs() != null) {
            entry.put("latencyProdMs", captured.prodLatencyMs());
        }
        entry.put("latencyShadowMs", replay.latencyMs);
        if (captured.prodBody() != null) {
            entry.put("responseProd", captured.prodBody());
        }
        if (replay.body != null) {
            entry.put("responseShadow", replay.body);
        }
        if (!replay.success && replay.errorMessage != null) {
            entry.put("errorShadow", replay.errorMessage);
        }
        return entry;
    }

    private String resolveBaseUrl(String requestBaseUrl) {
        String baseUrl = requestBaseUrl == null || requestBaseUrl.isBlank()
                ? defaultShadowBaseUrl
                : requestBaseUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shadow base URL is required");
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String buildUrl(String baseUrl, String path, String query) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (query.isEmpty()) {
            return baseUrl + normalizedPath;
        }
        String normalizedQuery = query.startsWith("?") ? query.substring(1) : query;
        return baseUrl + normalizedPath + "?" + normalizedQuery;
    }

    private boolean allowsBody(String method) {
        return !"GET".equals(method) && !"HEAD".equals(method);
    }

    private String resolveBodyPayload(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return node.toString();
        }
    }

    private JsonNode parseBody(byte[] body, String contentType) {
        if (body.length == 0) {
            return null;
        }
        int limit = Math.max(1000, maxResponseBytes);
        byte[] trimmed = body.length > limit ? trim(body, limit) : body;
        String text = new String(trimmed, StandardCharsets.UTF_8);
        if (isJsonContent(contentType) || looksLikeJson(text)) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception ignored) {
                return TextNode.valueOf(text);
            }
        }
        return TextNode.valueOf(text);
    }

    private boolean isJsonContent(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private byte[] trim(byte[] bytes, int limit) {
        byte[] slice = new byte[limit];
        System.arraycopy(bytes, 0, slice, 0, limit);
        return slice;
    }

    private static class ReplayResult {
        private final boolean success;
        private final int status;
        private final long latencyMs;
        private final JsonNode body;
        private final String errorMessage;

        private ReplayResult(boolean success, int status, long latencyMs, JsonNode body, String errorMessage) {
            this.success = success;
            this.status = status;
            this.latencyMs = latencyMs;
            this.body = body;
            this.errorMessage = errorMessage;
        }
    }
}
