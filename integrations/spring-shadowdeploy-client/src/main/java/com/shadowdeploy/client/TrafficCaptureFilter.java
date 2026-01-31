package com.shadowdeploy.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TrafficCaptureFilter extends OncePerRequestFilter {

    private static final List<String> SAFE_HEADER_ALLOWLIST = List.of(
            "content-type",
            "accept",
            "user-agent",
            "x-request-id",
            "x-correlation-id",
            "traceparent"
    );

    private final ShadowDeployReplayClient replayClient;
    private final ShadowDeployProperties properties;
    private final ObjectMapper objectMapper;

    public TrafficCaptureFilter(ShadowDeployReplayClient replayClient,
                                ShadowDeployProperties properties,
                                ObjectMapper objectMapper) {
        this.replayClient = replayClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return properties.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long latency = System.currentTimeMillis() - start;
            capture(requestWrapper, responseWrapper, latency);
            responseWrapper.copyBodyToResponse();
        }
    }

    private void capture(ContentCachingRequestWrapper request,
                         ContentCachingResponseWrapper response,
                         long latencyMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();

        byte[] requestBytes = truncate(request.getContentAsByteArray(), properties.getMaxBodyBytes());
        byte[] responseBytes = truncate(response.getContentAsByteArray(), properties.getMaxBodyBytes());

        String requestContentType = request.getContentType();
        String responseContentType = response.getContentType();

        JsonNode requestBody = parseBody(requestBytes, requestContentType);
        JsonNode responseBody = parseBody(responseBytes, responseContentType);

        Map<String, String> headers = captureHeaders(request);

        CapturedRequestPayload payload = new CapturedRequestPayload(
                resolveRequestId(request, headers),
                method,
                path,
                query,
                headers,
                requestBody,
                response.getStatus(),
                responseBody,
                latencyMs,
                requestContentType
        );

        replayClient.enqueue(payload);
    }

    private Map<String, String> captureHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : SAFE_HEADER_ALLOWLIST) {
            String value = request.getHeader(headerName);
            if (StringUtils.hasText(value)) {
                headers.put(headerName, value);
            }
        }
        return headers;
    }

    private String resolveRequestId(HttpServletRequest request, Map<String, String> headers) {
        String requestId = request.getHeader("X-Request-Id");
        if (!StringUtils.hasText(requestId)) {
            requestId = headers.get("x-request-id");
        }
        return StringUtils.hasText(requestId) ? requestId : "req-" + UUID.randomUUID();
    }

    private JsonNode parseBody(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (isJson(contentType) || looksLikeJson(text)) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception ignored) {
                return TextNode.valueOf(text);
            }
        }
        return TextNode.valueOf(text);
    }

    private boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private byte[] truncate(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return new byte[0];
        }
        if (bytes.length <= maxBytes) {
            return bytes;
        }
        byte[] trimmed = new byte[maxBytes];
        System.arraycopy(bytes, 0, trimmed, 0, maxBytes);
        return trimmed;
    }
}
