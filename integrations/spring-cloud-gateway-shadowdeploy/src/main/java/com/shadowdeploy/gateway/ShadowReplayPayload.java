package com.shadowdeploy.gateway;

import java.util.List;

public record ShadowReplayPayload(
        String serviceName,
        String deploymentId,
        String shadowBaseUrl,
        List<CapturedRequestPayload> requests
) {
}
