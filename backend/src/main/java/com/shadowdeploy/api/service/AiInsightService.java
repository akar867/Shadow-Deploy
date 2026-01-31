package com.shadowdeploy.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiInsightService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiInsightService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public AiInsightService(ObjectMapper objectMapper,
                            @Value("${shadowdeploy.llm.enabled:false}") boolean enabled,
                            @Value("${shadowdeploy.llm.base-url:https://api.openai.com/v1/chat/completions}")
                            String baseUrl,
                            @Value("${shadowdeploy.llm.api-key:}") String apiKey,
                            @Value("${shadowdeploy.llm.model:gpt-4o-mini}") String model,
                            @Value("${shadowdeploy.llm.timeout-seconds:20}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .build();
    }

    public List<String> generateInsights(AiInsightContext context, List<String> fallbackInsights) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return fallbackInsights;
        }
        try {
            String requestBody = buildRequest(context);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("LLM request failed with status {}", response.statusCode());
                return fallbackInsights;
            }
            String content = extractContent(response.body());
            List<String> parsed = parseInsights(content);
            return mergeInsights(parsed, fallbackInsights);
        } catch (Exception ex) {
            LOGGER.warn("LLM insight generation failed", ex);
            return fallbackInsights;
        }
    }

    private String buildRequest(AiInsightContext context) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.2);
        payload.put("max_tokens", 280);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", "You are ShadowDeploy, an expert release analyst. "
                + "Provide 3 to 5 concise, actionable insights about shadow traffic diffs. "
                + "Each insight must be one sentence on its own line. No headings.");
        messages.add(system);

        Map<String, String> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", buildUserPrompt(context));
        messages.add(user);

        payload.put("messages", messages);
        return objectMapper.writeValueAsString(payload);
    }

    private String buildUserPrompt(AiInsightContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Service: ").append(context.serviceName()).append("\n");
        builder.append("Deployment: ").append(context.deploymentId()).append("\n");
        builder.append("Status: ").append(context.status()).append("\n");
        builder.append("Risk score: ").append(context.riskScore()).append("\n");
        builder.append("Total requests: ").append(context.totalRequests()).append("\n");
        builder.append("Structured requests: ").append(context.structuredRequests()).append("\n");
        builder.append("Status mismatches: ").append(context.statusMismatchCount()).append("\n");
        builder.append("Payload diffs: ").append(context.payloadDiffCount()).append("\n");
        builder.append("Error count: ").append(context.errorCount()).append("\n");
        builder.append("Drift count: ").append(context.driftCount()).append("\n");
        builder.append("P95 latency: ").append(context.p95Latency()).append("ms").append("\n");
        builder.append("Mismatch rate: ").append(context.mismatchRate()).append("%").append("\n");
        builder.append("Drift rate: ").append(context.driftRate()).append("%").append("\n");
        builder.append("Latency delta: ").append(context.latencyDelta()).append("ms").append("\n");
        builder.append("Findings:");
        context.findings().forEach(finding -> builder
                .append("\n- ")
                .append(finding.title())
                .append(" (")
                .append(finding.severity())
                .append(", ")
                .append(finding.affectedPercent())
                .append("%)"));
        return builder.toString();
    }

    private String extractContent(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            if (!message.isMissingNode()) {
                return message.path("content").asText("");
            }
            return choices.get(0).path("text").asText("");
        }
        return root.path("output_text").asText("");
    }

    private List<String> parseInsights(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> insights = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            cleaned = cleaned.replaceAll("^[\\-*\\d\\.\\)]+\\s*", "");
            if (!cleaned.isEmpty()) {
                insights.add(normalizeSentence(cleaned));
            }
        }
        if (insights.size() < 2) {
            insights.clear();
            String[] sentences = content.split("\\.\\s+");
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (!trimmed.isEmpty()) {
                    insights.add(normalizeSentence(trimmed));
                }
            }
        }
        if (insights.size() > 5) {
            return insights.subList(0, 5);
        }
        return insights;
    }

    private List<String> mergeInsights(List<String> aiInsights, List<String> fallback) {
        if (aiInsights.isEmpty()) {
            return fallback;
        }
        List<String> merged = new ArrayList<>(aiInsights);
        for (String fallbackItem : fallback) {
            if (merged.size() >= 5) {
                break;
            }
            if (!containsSimilar(merged, fallbackItem)) {
                merged.add(fallbackItem);
            }
        }
        return merged;
    }

    private boolean containsSimilar(List<String> items, String candidate) {
        String normalized = candidate.toLowerCase(Locale.ROOT);
        return items.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.contains(normalized) || normalized.contains(item));
    }

    private String normalizeSentence(String sentence) {
        String trimmed = sentence.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (!trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?")) {
            return trimmed + ".";
        }
        return trimmed;
    }
}
