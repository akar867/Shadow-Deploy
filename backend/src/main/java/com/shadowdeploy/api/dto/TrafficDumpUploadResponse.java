package com.shadowdeploy.api.dto;

import com.shadowdeploy.api.model.ShadowSummaryResponse;

public record TrafficDumpUploadResponse(
        TrafficDumpResponse trafficDump,
        ShadowSummaryResponse summary
) {
}
