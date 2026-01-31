package com.shadowdeploy.client;

import java.util.List;

public record ShadowReplayPayload(
        String serviceName,
        String deploymentId,
        String shadowBaseUrl,
        List<CapturedRequestPayload> requests
) {
}
