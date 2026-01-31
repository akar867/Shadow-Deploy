package com.shadowdeploy.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "shadowdeploy.client")
public class ShadowDeployProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8080";
    private String authToken;
    private String shadowBaseUrl;
    private String serviceName = "checkout-service";
    private String deploymentId;
    private double sampleRate = 0.1;
    private int batchSize = 25;
    private long flushIntervalMs = 2000;
    private int maxBodyBytes = 20000;
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/actuator",
            "/health",
            "/metrics"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getShadowBaseUrl() {
        return shadowBaseUrl;
    }

    public void setShadowBaseUrl(String shadowBaseUrl) {
        this.shadowBaseUrl = shadowBaseUrl;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        if (excludedPaths == null) {
            this.excludedPaths = new ArrayList<>();
        } else {
            this.excludedPaths = excludedPaths;
        }
    }
}
