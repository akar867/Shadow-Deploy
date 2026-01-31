package com.shadowdeploy.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowdeploy.api.entity.ShadowSummaryEntity;
import com.shadowdeploy.api.entity.TrafficDump;
import com.shadowdeploy.api.model.DiffFinding;
import com.shadowdeploy.api.model.DiffMetric;
import com.shadowdeploy.api.model.RiskItem;
import com.shadowdeploy.api.model.ShadowSummaryResponse;
import com.shadowdeploy.api.repository.ShadowSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ShadowSummaryService {

    private static final double BASELINE_P95_MS = 200.0;
    private static final double PAYLOAD_DRIFT_THRESHOLD = 0.08;
    private static final Pattern LATENCY_PATTERN = Pattern.compile("(?:latency|latencyms|duration|p95)[=:\\s]+(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private record DiffStats(
            int totalRequests,
            int structuredRequests,
            int statusMismatchCount,
            int payloadDiffCount,
            int errorCount,
            int driftCount,
            List<Integer> shadowLatencies,
            List<Integer> prodLatencies
    ) {
    }

    private record DiffScore(int differences, int total) {
        private double ratio() {
            if (total == 0) {
                return 0.0;
            }
            return (double) differences / total;
        }
    }

    private final ShadowSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;
    private final AiInsightService aiInsightService;

    public ShadowSummaryService(ShadowSummaryRepository summaryRepository,
                                ObjectMapper objectMapper,
                                AiInsightService aiInsightService) {
        this.summaryRepository = summaryRepository;
        this.objectMapper = objectMapper;
        this.aiInsightService = aiInsightService;
    }

    public ShadowSummaryResponse getLatestSummary() {
        Optional<ShadowSummaryEntity> latest = summaryRepository.findTopByOrderByGeneratedAtDesc();
        if (latest.isPresent()) {
            return toResponse(latest.get());
        }
        ShadowSummaryResponse seed = buildSeedSummary();
        saveSummary(seed, null);
        return seed;
    }

    public ShadowSummaryEntity saveSummary(ShadowSummaryResponse response, TrafficDump dump) {
        ShadowSummaryEntity entity = toEntity(response, dump);
        return summaryRepository.save(entity);
    }

    public ShadowSummaryResponse analyzeDump(TrafficDump dump) {
        String content = dump.getContent() == null ? "" : dump.getContent();
        DiffStats stats = analyzeContent(content);
        int totalRequests = Math.max(1, stats.totalRequests());
        int errorCount = stats.errorCount();
        int driftCount = stats.driftCount();
        int statusMismatchCount = stats.statusMismatchCount();
        int payloadDiffCount = stats.payloadDiffCount();

        double mismatchRate = stats.structuredRequests() > 0
                ? roundOneDecimal(statusMismatchCount * 100.0 / totalRequests)
                : roundOneDecimal(errorCount * 100.0 / totalRequests);
        double driftRate = stats.structuredRequests() > 0
                ? roundOneDecimal(payloadDiffCount * 100.0 / totalRequests)
                : roundOneDecimal(driftCount * 100.0 / totalRequests);
        double p95Latency = computeP95(stats.shadowLatencies(), totalRequests, BASELINE_P95_MS + (totalRequests % 120));
        double p95ProdLatency = computeP95(stats.prodLatencies(), totalRequests, BASELINE_P95_MS);
        double latencyDelta = roundOneDecimal(Math.max(0, p95Latency - p95ProdLatency));
        double riskScore = roundTwoDecimal(computeRiskScore(mismatchRate, driftRate, latencyDelta));

        List<DiffMetric> metrics = List.of(
                new DiffMetric("HTTP mismatch rate", "Responses with non-matching status codes", mismatchRate, "%"),
                new DiffMetric("Payload drift", "Responses with body differences above threshold", driftRate, "%"),
                new DiffMetric("P95 latency delta", "Shadow p95 minus production p95", latencyDelta, "ms"),
                new DiffMetric("Exception increase", "New exceptions introduced by shadow build", errorCount, "count")
        );

        List<DiffFinding> findings = buildFindings(mismatchRate, driftRate, latencyDelta, dump.getServiceName());
        List<RiskItem> riskItems = buildRiskItems(findings, dump.getServiceName());
        List<String> fallbackInsights = buildInsights(
                totalRequests,
                stats.structuredRequests(),
                statusMismatchCount,
                payloadDiffCount,
                errorCount,
                mismatchRate,
                driftRate,
                latencyDelta,
                dump.getServiceName()
        );

        String status = statusForRisk(riskScore);
        AiInsightContext context = new AiInsightContext(
                dump.getDeploymentId(),
                dump.getServiceName(),
                status,
                riskScore,
                totalRequests,
                stats.structuredRequests(),
                statusMismatchCount,
                payloadDiffCount,
                errorCount,
                driftCount,
                p95Latency,
                mismatchRate,
                driftRate,
                latencyDelta,
                metrics,
                findings
        );
        List<String> aiInsights = aiInsightService.generateInsights(context, fallbackInsights);

        return new ShadowSummaryResponse(
                dump.getDeploymentId(),
                dump.getServiceName(),
                status,
                Instant.now().toString(),
                riskScore,
                metrics,
                findings,
                riskItems,
                aiInsights
        );
    }

    public void ensureSeedSummary() {
        if (summaryRepository.count() == 0) {
            saveSummary(buildSeedSummary(), null);
        }
    }

    private ShadowSummaryResponse buildSeedSummary() {
        List<DiffMetric> metrics = List.of(
                new DiffMetric("HTTP mismatch rate", "Responses with non-matching status codes", 2.3, "%"),
                new DiffMetric("Payload drift", "Responses with body differences above threshold", 6.1, "%"),
                new DiffMetric("P95 latency delta", "Shadow p95 minus production p95", 120, "ms"),
                new DiffMetric("Exception increase", "New exceptions introduced by shadow build", 14, "count")
        );

        List<DiffFinding> findings = List.of(
                new DiffFinding(
                        "finding-001",
                        "DiscountService null handling",
                        "high",
                        2.3,
                        "Checkout requests with couponType=FLASH return 500 due to null handling in DiscountService.",
                        "Guard against null couponType and add default discount fallback."
                ),
                new DiffFinding(
                        "finding-002",
                        "Order summary serialization drift",
                        "medium",
                        1.1,
                        "Shadow responses include a new taxBreakdown field that is missing in production.",
                        "Backfill taxBreakdown in production or add a compatibility serializer."
                ),
                new DiffFinding(
                        "finding-003",
                        "Inventory read amplification",
                        "low",
                        4.8,
                        "Shadow build issues 2x inventory reads for bulk add-to-cart flows.",
                        "Cache inventory per cart session to reduce duplicate reads."
                )
        );

        List<RiskItem> riskItems = List.of(
                new RiskItem("Checkout service", "high", "Potential revenue loss from 500 errors", "payments-team"),
                new RiskItem("Order summary", "medium", "Mismatch breaks downstream tax service", "billing-team"),
                new RiskItem("Inventory", "low", "Increased database load in peak traffic", "supply-team")
        );

        List<String> aiInsights = List.of(
                "2.3% of checkout requests fail due to null handling in DiscountService when couponType=FLASH.",
                "Latency spikes correlate with bulk add-to-cart flows from mobile clients.",
                "Recommend shipping fix for DiscountService before deploying rc-2026-01-28."
        );

        return new ShadowSummaryResponse(
                "deploy-2026-01-28-rc1",
                "checkout-service",
                "needs-attention",
                Instant.now().toString(),
                0.74,
                metrics,
                findings,
                riskItems,
                aiInsights
        );
    }

    private ShadowSummaryResponse toResponse(ShadowSummaryEntity entity) {
        try {
            return new ShadowSummaryResponse(
                    entity.getDeploymentId(),
                    entity.getServiceName(),
                    entity.getStatus(),
                    entity.getGeneratedAt().toString(),
                    entity.getRiskScore(),
                    objectMapper.readValue(entity.getMetricsJson(), new TypeReference<List<DiffMetric>>() {}),
                    objectMapper.readValue(entity.getFindingsJson(), new TypeReference<List<DiffFinding>>() {}),
                    objectMapper.readValue(entity.getRiskItemsJson(), new TypeReference<List<RiskItem>>() {}),
                    objectMapper.readValue(entity.getAiInsightsJson(), new TypeReference<List<String>>() {})
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize shadow summary data", exception);
        }
    }

    private ShadowSummaryEntity toEntity(ShadowSummaryResponse response, TrafficDump dump) {
        try {
            ShadowSummaryEntity entity = new ShadowSummaryEntity();
            entity.setDeploymentId(response.deploymentId());
            entity.setServiceName(response.serviceName());
            entity.setStatus(response.status());
            entity.setGeneratedAt(Instant.parse(response.generatedAt()));
            entity.setRiskScore(response.riskScore());
            entity.setMetricsJson(objectMapper.writeValueAsString(response.metrics()));
            entity.setFindingsJson(objectMapper.writeValueAsString(response.findings()));
            entity.setRiskItemsJson(objectMapper.writeValueAsString(response.riskItems()));
            entity.setAiInsightsJson(objectMapper.writeValueAsString(response.aiInsights()));
            entity.setTrafficDump(dump);
            return entity;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize shadow summary data", exception);
        }
    }

    private DiffStats analyzeContent(String content) {
        String[] lines = content.split("\\R");
        int rawRequests = 0;
        int structuredRequests = 0;
        int statusMismatchCount = 0;
        int payloadDiffCount = 0;
        int structuredErrorCount = 0;
        int heuristicErrorCount = 0;
        int heuristicDriftCount = 0;
        List<Integer> shadowLatencies = new ArrayList<>();
        List<Integer> prodLatencies = new ArrayList<>();
        List<Integer> heuristicLatencies = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            rawRequests++;
            boolean structuredHandled = false;
            if (trimmed.startsWith("{")) {
                try {
                    JsonNode node = objectMapper.readTree(trimmed);
                    structuredRequests++;
                    structuredHandled = true;
                    StructuredResult result = analyzeStructuredNode(node);
                    if (result.statusMismatch()) {
                        statusMismatchCount++;
                    }
                    if (result.payloadDiff()) {
                        payloadDiffCount++;
                    }
                    if (result.error()) {
                        structuredErrorCount++;
                    }
                    if (result.shadowLatency() != null) {
                        shadowLatencies.add(result.shadowLatency());
                    }
                    if (result.prodLatency() != null) {
                        prodLatencies.add(result.prodLatency());
                    }
                } catch (Exception ignored) {
                    structuredHandled = false;
                }
            }

            if (!structuredHandled) {
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (lower.contains("error") || lower.contains("exception") || lower.contains("status=5")
                        || lower.contains(" 500")) {
                    heuristicErrorCount++;
                }
                if (lower.contains("diff") || lower.contains("drift") || lower.contains("mismatch")) {
                    heuristicDriftCount++;
                }
                Matcher matcher = LATENCY_PATTERN.matcher(lower);
                while (matcher.find()) {
                    try {
                        heuristicLatencies.add(Integer.parseInt(matcher.group(1)));
                    } catch (NumberFormatException ignored) {
                        break;
                    }
                }
            }
        }

        int totalRequests = structuredRequests > 0
                ? structuredRequests
                : Math.max(1, rawRequests);
        boolean structured = structuredRequests > 0;
        int errorCount = structured ? structuredErrorCount : heuristicErrorCount;
        int driftCount = structured ? payloadDiffCount : heuristicDriftCount;
        List<Integer> shadowLatencyResult = structured ? shadowLatencies : heuristicLatencies;
        List<Integer> prodLatencyResult = structured ? prodLatencies : new ArrayList<>();

        return new DiffStats(
                totalRequests,
                structuredRequests,
                statusMismatchCount,
                payloadDiffCount,
                errorCount,
                driftCount,
                shadowLatencyResult,
                prodLatencyResult
        );
    }

    private StructuredResult analyzeStructuredNode(JsonNode node) {
        JsonNode prodNode = node.path("prod");
        JsonNode shadowNode = node.path("shadow");
        boolean hasProdShadow = prodNode.isObject() || shadowNode.isObject();

        Integer statusProd = findIntField(node, "statusProd", "prodStatus");
        if (statusProd == null && prodNode.isObject()) {
            statusProd = findIntField(prodNode, "status", "statusCode", "status_code", "code");
        }

        Integer statusShadow = findIntField(node, "statusShadow", "shadowStatus");
        if (statusShadow == null && shadowNode.isObject()) {
            statusShadow = findIntField(shadowNode, "status", "statusCode", "status_code", "code");
        }
        if (statusShadow == null && !hasProdShadow) {
            statusShadow = findIntField(node, "status", "statusCode", "status_code", "code");
        }

        Integer latencyProd = findIntField(node, "latencyProdMs", "prodLatencyMs", "prodDurationMs");
        if (latencyProd == null && prodNode.isObject()) {
            latencyProd = findIntField(prodNode, "latencyMs", "durationMs", "latency");
        }

        Integer latencyShadow = findIntField(node, "latencyShadowMs", "shadowLatencyMs", "shadowDurationMs");
        if (latencyShadow == null && shadowNode.isObject()) {
            latencyShadow = findIntField(shadowNode, "latencyMs", "durationMs", "latency");
        }
        if (latencyShadow == null && !hasProdShadow) {
            latencyShadow = findIntField(node, "latencyMs", "durationMs", "latency");
        }

        JsonNode responseProd = findNode(node, "responseProd", "prodResponse", "payloadProd", "prodPayload", "bodyProd", "prodBody");
        if (responseProd == null && prodNode.isObject()) {
            responseProd = findNode(prodNode, "response", "payload", "body");
        }

        JsonNode responseShadow = findNode(node, "responseShadow", "shadowResponse", "payloadShadow", "shadowPayload", "bodyShadow", "shadowBody");
        if (responseShadow == null && shadowNode.isObject()) {
            responseShadow = findNode(shadowNode, "response", "payload", "body");
        }

        boolean statusMismatch = statusProd != null && statusShadow != null && !statusProd.equals(statusShadow);
        boolean payloadDiff = false;
        if (responseProd != null && responseShadow != null) {
            DiffScore diff = compareNodes(normalizePayload(responseProd), normalizePayload(responseShadow));
            payloadDiff = diff.ratio() >= PAYLOAD_DRIFT_THRESHOLD;
        }

        boolean error = isServerError(statusShadow)
                || hasNonEmpty(node, "errorShadow", "shadowError", "exceptionShadow", "shadowException");
        if (!error && shadowNode.isObject()) {
            error = hasNonEmpty(shadowNode, "error", "exception", "message");
        }
        if (!error && !hasProdShadow) {
            error = hasNonEmpty(node, "error", "exception");
        }

        return new StructuredResult(statusMismatch, payloadDiff, error, latencyProd, latencyShadow);
    }

    private JsonNode findNode(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private Integer findIntField(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            Integer parsed = parseIntNode(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Integer parseIntNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            Matcher matcher = NUMBER_PATTERN.matcher(node.asText());
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private JsonNode normalizePayload(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                try {
                    return objectMapper.readTree(text);
                } catch (Exception ignored) {
                    return node;
                }
            }
        }
        return node;
    }

    private DiffScore compareNodes(JsonNode left, JsonNode right) {
        if (left == null || left.isMissingNode()) {
            if (right == null || right.isMissingNode()) {
                return new DiffScore(0, 0);
            }
            return new DiffScore(1, 1);
        }
        if (right == null || right.isMissingNode()) {
            return new DiffScore(1, 1);
        }
        if (left.isValueNode() && right.isValueNode()) {
            return new DiffScore(left.equals(right) ? 0 : 1, 1);
        }
        if (left.isObject() && right.isObject()) {
            Set<String> fieldNames = new HashSet<>();
            left.fieldNames().forEachRemaining(fieldNames::add);
            right.fieldNames().forEachRemaining(fieldNames::add);
            int differences = 0;
            int total = 0;
            for (String field : fieldNames) {
                DiffScore score = compareNodes(left.get(field), right.get(field));
                differences += score.differences();
                total += score.total();
            }
            return new DiffScore(differences, total);
        }
        if (left.isArray() && right.isArray()) {
            int max = Math.max(left.size(), right.size());
            int differences = 0;
            int total = 0;
            for (int i = 0; i < max; i++) {
                JsonNode leftNode = i < left.size() ? left.get(i) : null;
                JsonNode rightNode = i < right.size() ? right.get(i) : null;
                DiffScore score = compareNodes(leftNode, rightNode);
                differences += score.differences();
                total += score.total();
            }
            return new DiffScore(differences, total);
        }
        return new DiffScore(1, 1);
    }

    private boolean hasNonEmpty(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isTextual() && !value.asText().isBlank()) {
                    return true;
                }
                if (value.isNumber() || value.isBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isServerError(Integer status) {
        return status != null && status >= 500;
    }

    private record StructuredResult(
            boolean statusMismatch,
            boolean payloadDiff,
            boolean error,
            Integer prodLatency,
            Integer shadowLatency
    ) {
    }

    private List<DiffFinding> buildFindings(double mismatchRate, double driftRate, double latencyDelta, String serviceName) {
        List<DiffFinding> findings = new ArrayList<>();
        if (mismatchRate > 0.3) {
            findings.add(new DiffFinding(
                    "finding-" + UUID.randomUUID().toString().substring(0, 6),
                    "Shadow status mismatch spikes",
                    severityForRate(mismatchRate, 3.0, 1.2),
                    mismatchRate,
                    "Shadow responses diverge from production status codes on key endpoints.",
                    "Inspect error handling for " + serviceName + " endpoints that return 5xx."
            ));
        }
        if (driftRate > 0.4) {
            findings.add(new DiffFinding(
                    "finding-" + UUID.randomUUID().toString().substring(0, 6),
                    "Payload drift detected",
                    severityForRate(driftRate, 4.0, 1.5),
                    driftRate,
                    "Payload structure changes were detected between production and shadow responses.",
                    "Validate serializer changes and ensure backward compatibility."
            ));
        }
        if (latencyDelta > 40) {
            findings.add(new DiffFinding(
                    "finding-" + UUID.randomUUID().toString().substring(0, 6),
                    "Latency regression",
                    severityForRate(latencyDelta, 180, 90),
                    roundOneDecimal(Math.min(100.0, latencyDelta / 4.0)),
                    "Shadow p95 latency exceeds production baseline by " + latencyDelta + "ms.",
                    "Check query plans and cache hit ratios under mirrored load."
            ));
        }
        if (findings.isEmpty()) {
            findings.add(new DiffFinding(
                    "finding-" + UUID.randomUUID().toString().substring(0, 6),
                    "No critical regressions detected",
                    "low",
                    0.0,
                    "Shadow traffic stayed within expected behavioral thresholds.",
                    "Proceed with canary or schedule a low-risk deploy window."
            ));
        }
        return findings;
    }

    private List<RiskItem> buildRiskItems(List<DiffFinding> findings, String serviceName) {
        List<RiskItem> items = new ArrayList<>();
        int index = 1;
        for (DiffFinding finding : findings) {
            items.add(new RiskItem(
                    serviceName + " risk " + index,
                    finding.severity(),
                    finding.title(),
                    "team-" + serviceName
            ));
            index++;
        }
        return items;
    }

    private List<String> buildInsights(int totalRequests,
                                       int structuredRequests,
                                       int statusMismatchCount,
                                       int payloadDiffCount,
                                       int errorCount,
                                       double mismatchRate,
                                       double driftRate,
                                       double latencyDelta,
                                       String serviceName) {
        List<String> insights = new ArrayList<>();
        if (structuredRequests > 0) {
            insights.add("Structured diffing analyzed " + structuredRequests + " requests for " + serviceName + ".");
        } else {
            insights.add("Heuristic scan processed " + totalRequests + " log lines for " + serviceName + ".");
        }
        if (statusMismatchCount > 0) {
            insights.add(statusMismatchCount + " requests returned different status codes in shadow (" + mismatchRate + "%).");
        } else if (mismatchRate > 0) {
            insights.add(mismatchRate + "% of requests diverged on status codes during shadow execution.");
        }
        if (payloadDiffCount > 0) {
            insights.add(payloadDiffCount + " responses showed payload drift (" + driftRate + "%).");
        } else if (driftRate > 0.5) {
            insights.add(driftRate + "% payload drift suggests response schema changes to review.");
        }
        if (errorCount > 0) {
            insights.add(errorCount + " shadow-side errors detected across the traffic sample.");
        }
        if (latencyDelta > 20) {
            insights.add("P95 latency delta reached " + latencyDelta + "ms above baseline.");
        }
        while (insights.size() < 3) {
            insights.add("No significant regressions detected from the uploaded traffic sample.");
        }
        return insights;
    }

    private String statusForRisk(double riskScore) {
        if (riskScore >= 0.7) {
            return "needs-attention";
        }
        if (riskScore >= 0.4) {
            return "review";
        }
        return "healthy";
    }

    private String severityForRate(double value, double highThreshold, double mediumThreshold) {
        if (value >= highThreshold) {
            return "high";
        }
        if (value >= mediumThreshold) {
            return "medium";
        }
        return "low";
    }

    private double computeRiskScore(double mismatchRate, double driftRate, double latencyDelta) {
        double mismatchScore = mismatchRate / 5.0;
        double driftScore = driftRate / 10.0;
        double latencyScore = latencyDelta / 300.0;
        double raw = (mismatchScore + driftScore + latencyScore) / 3.0;
        return Math.min(1.0, Math.max(0.0, raw));
    }

    private double computeP95(List<Integer> latencies, int totalLines, double fallback) {
        if (latencies.isEmpty()) {
            return fallback;
        }
        latencies.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(latencies.size() * 0.95) - 1;
        return latencies.get(Math.max(0, Math.min(index, latencies.size() - 1)));
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwoDecimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
