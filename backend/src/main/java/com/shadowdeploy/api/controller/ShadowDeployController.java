package com.shadowdeploy.api.controller;

import com.shadowdeploy.api.model.ShadowSummaryResponse;
import com.shadowdeploy.api.service.ShadowSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShadowDeployController {

    private final ShadowSummaryService summaryService;

    public ShadowDeployController(ShadowSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("service", "shadowdeploy-backend");
        payload.put("time", Instant.now().toString());
        return payload;
    }

    @GetMapping("/summary")
    public ShadowSummaryResponse summary() {
        return summaryService.getLatestSummary();
    }
}
