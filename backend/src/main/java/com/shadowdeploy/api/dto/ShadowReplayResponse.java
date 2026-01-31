package com.shadowdeploy.api.dto;

import com.shadowdeploy.api.model.ShadowSummaryResponse;

public record ShadowReplayResponse(
        TrafficDumpResponse trafficDump,
        ShadowSummaryResponse summary,
        int totalRequests,
        int replayedRequests,
        int failedRequests
) {
}
