package com.shadowdeploy.api.service;

import com.shadowdeploy.api.model.DiffFinding;
import com.shadowdeploy.api.model.DiffMetric;
import com.shadowdeploy.api.model.RiskItem;
import com.shadowdeploy.api.model.ShadowSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ShadowSummaryService {

    public ShadowSummaryResponse buildSummary() {
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
}
