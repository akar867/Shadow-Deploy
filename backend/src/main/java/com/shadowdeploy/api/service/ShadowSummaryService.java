package com.shadowdeploy.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ShadowSummaryService {

    private static final double BASELINE_P95_MS = 200.0;
    private static final Pattern LATENCY_PATTERN = Pattern.compile("(?:latency|latencyms|duration|p95)[=:\\s]+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final ShadowSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    public ShadowSummaryService(ShadowSummaryRepository summaryRepository, ObjectMapper objectMapper) {
        this.summaryRepository = summaryRepository;
        this.objectMapper = objectMapper;
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
        String[] lines = content.split("\\R");
        int totalLines = Math.max(1, lines.length);
        int errorCount = 0;
        int driftCount = 0;
        List<Integer> latencies = new ArrayList<>();

        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("error") || lower.contains("exception") || lower.contains("status=5")
                    || lower.contains(" 500")) {
                errorCount++;
            }
            if (lower.contains("diff") || lower.contains("drift") || lower.contains("mismatch")) {
                driftCount++;
            }
            Matcher matcher = LATENCY_PATTERN.matcher(lower);
            while (matcher.find()) {
                try {
                    latencies.add(Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }

        double mismatchRate = roundOneDecimal(errorCount * 100.0 / totalLines);
        double driftRate = roundOneDecimal(driftCount * 100.0 / totalLines);
        double p95Latency = computeP95(latencies, totalLines);
        double latencyDelta = roundOneDecimal(Math.max(0, p95Latency - BASELINE_P95_MS));
        double riskScore = roundTwoDecimal(computeRiskScore(mismatchRate, driftRate, latencyDelta));

        List<DiffMetric> metrics = List.of(
                new DiffMetric("HTTP mismatch rate", "Responses with non-matching status codes", mismatchRate, "%"),
                new DiffMetric("Payload drift", "Responses with body differences above threshold", driftRate, "%"),
                new DiffMetric("P95 latency delta", "Shadow p95 minus production p95", latencyDelta, "ms"),
                new DiffMetric("Exception increase", "New exceptions introduced by shadow build", errorCount, "count")
        );

        List<DiffFinding> findings = buildFindings(mismatchRate, driftRate, latencyDelta, dump.getServiceName());
        List<RiskItem> riskItems = buildRiskItems(findings, dump.getServiceName());
        List<String> aiInsights = buildInsights(totalLines, mismatchRate, driftRate, latencyDelta, dump.getServiceName());

        String status = statusForRisk(riskScore);

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

    private List<String> buildInsights(int totalLines, double mismatchRate, double driftRate, double latencyDelta,
                                       String serviceName) {
        List<String> insights = new ArrayList<>();
        insights.add("Processed " + totalLines + " requests from mirrored traffic for " + serviceName + ".");
        insights.add(mismatchRate + "% of requests diverged on status codes during shadow execution.");
        if (driftRate > 0.5) {
            insights.add(driftRate + "% payload drift suggests response schema changes to review.");
        }
        if (latencyDelta > 20) {
            insights.add("P95 latency delta reached " + latencyDelta + "ms above baseline.");
        }
        if (insights.size() < 3) {
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

    private double computeP95(List<Integer> latencies, int totalLines) {
        if (latencies.isEmpty()) {
            return BASELINE_P95_MS + (totalLines % 120);
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
