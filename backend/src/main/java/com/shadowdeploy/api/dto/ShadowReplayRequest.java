package com.shadowdeploy.api.dto;

import java.util.List;

public record ShadowReplayRequest(
        String serviceName,
        String deploymentId,
        String shadowBaseUrl,
        List<CapturedRequest> requests
) {
}
