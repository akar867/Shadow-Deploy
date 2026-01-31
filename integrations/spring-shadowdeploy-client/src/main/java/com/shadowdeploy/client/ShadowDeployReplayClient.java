package com.shadowdeploy.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ShadowDeployReplayClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowDeployReplayClient.class);

    private final ShadowDeployProperties properties;
    private final RestTemplate restTemplate;
    private final LinkedBlockingQueue<CapturedRequestPayload> queue;
    private final ScheduledExecutorService scheduler;
    private final String deploymentId;

    public ShadowDeployReplayClient(ShadowDeployProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.queue = new LinkedBlockingQueue<>();
        this.deploymentId = resolveDeploymentId(properties.getDeploymentId());
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "shadowdeploy-replay-flush");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(500, properties.getFlushIntervalMs());
        this.scheduler.scheduleAtFixedRate(this::flushSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void enqueue(CapturedRequestPayload payload) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!shouldSample()) {
            return;
        }
        queue.offer(payload);
        if (queue.size() >= properties.getBatchSize()) {
            scheduler.execute(this::flushSafely);
        }
    }

    private boolean shouldSample() {
        double rate = properties.getSampleRate();
        if (rate <= 0) {
            return false;
        }
        if (rate >= 1) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() <= rate;
    }

    private void flushSafely() {
        try {
            flush();
        } catch (Exception ex) {
            LOGGER.warn("ShadowDeploy replay flush failed", ex);
        }
    }

    private void flush() {
        List<CapturedRequestPayload> batch = new ArrayList<>();
        queue.drainTo(batch, properties.getBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        ShadowReplayPayload payload = new ShadowReplayPayload(
                properties.getServiceName(),
                deploymentId,
                properties.getShadowBaseUrl(),
                batch
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) {
            headers.set("X-Shadow-Token", properties.getAuthToken());
        } else {
            LOGGER.debug("ShadowDeploy auth token not set");
        }

        String url = buildReplayUrl(properties.getBaseUrl());
        restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
    }

    private String buildReplayUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/api/shadow/replay";
    }

    private String resolveDeploymentId(String deploymentId) {
        if (deploymentId != null && !deploymentId.isBlank()) {
            return deploymentId.trim();
        }
        return "shadow-replay-" + Instant.now().toString().replace(":", "").toLowerCase(Locale.ROOT);
    }

    @PreDestroy
    public void shutdown() {
        flushSafely();
        scheduler.shutdown();
    }
}
