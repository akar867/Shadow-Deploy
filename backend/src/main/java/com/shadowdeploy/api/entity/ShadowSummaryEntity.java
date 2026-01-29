package com.shadowdeploy.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shadow_summaries")
public class ShadowSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deploymentId;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    private double riskScore;

    @Lob
    @Column(nullable = false)
    private String metricsJson;

    @Lob
    @Column(nullable = false)
    private String findingsJson;

    @Lob
    @Column(nullable = false)
    private String riskItemsJson;

    @Lob
    @Column(nullable = false)
    private String aiInsightsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traffic_dump_id")
    private TrafficDump trafficDump;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public String getRiskItemsJson() {
        return riskItemsJson;
    }

    public void setRiskItemsJson(String riskItemsJson) {
        this.riskItemsJson = riskItemsJson;
    }

    public String getAiInsightsJson() {
        return aiInsightsJson;
    }

    public void setAiInsightsJson(String aiInsightsJson) {
        this.aiInsightsJson = aiInsightsJson;
    }

    public TrafficDump getTrafficDump() {
        return trafficDump;
    }

    public void setTrafficDump(TrafficDump trafficDump) {
        this.trafficDump = trafficDump;
    }
}
