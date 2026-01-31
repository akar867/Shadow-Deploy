package com.shadowdeploy.api.controller;

import com.shadowdeploy.api.dto.TrafficDumpUploadResponse;
import com.shadowdeploy.api.service.TrafficDumpService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final TrafficDumpService trafficDumpService;

    public DemoController(TrafficDumpService trafficDumpService) {
        this.trafficDumpService = trafficDumpService;
    }

    @PostMapping("/run")
    public TrafficDumpUploadResponse runDemo(@RequestParam(value = "deploymentId", required = false) String deploymentId) {
        return trafficDumpService.runDemo(deploymentId);
    }
}
