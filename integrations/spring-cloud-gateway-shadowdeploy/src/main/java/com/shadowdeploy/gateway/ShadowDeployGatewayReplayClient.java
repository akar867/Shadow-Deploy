package com.shadowdeploy.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ShadowDeployGatewayReplayClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowDeployGatewayReplayClient.class);

    private final ShadowDeployGatewayProperties properties;
    private final WebClient webClient;
    private final LinkedBlockingQueue<CapturedRequestPayload> queue;
    private final ScheduledExecutorService scheduler;
    private final String deploymentId;

    public ShadowDeployGatewayReplayClient(ShadowDeployGatewayProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.queue = new LinkedBlockingQueue<>();
        this.deploymentId = resolveDeploymentId(properties.getDeploymentId());
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "shadowdeploy-gateway-flush");
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
            LOGGER.warn("ShadowDeploy gateway replay flush failed", ex);
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

        WebClient.RequestBodySpec request = webClient.post().uri("/api/shadow/replay");
        if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) {
            request = request.header("X-Shadow-Token", properties.getAuthToken());
        }

        request.bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(ex -> LOGGER.warn("ShadowDeploy replay failed", ex))
                .subscribe();
    }

    private String resolveDeploymentId(String deploymentId) {
        if (deploymentId != null && !deploymentId.isBlank()) {
            return deploymentId.trim();
        }
        return "shadow-gateway-" + Instant.now().toString().replace(":", "").toLowerCase(Locale.ROOT);
    }

    @PreDestroy
    public void shutdown() {
        flushSafely();
        scheduler.shutdown();
    }
}
