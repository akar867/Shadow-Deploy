package com.shadowdeploy.api.controller;

import com.shadowdeploy.api.dto.TrafficDumpResponse;
import com.shadowdeploy.api.dto.TrafficDumpUploadResponse;
import com.shadowdeploy.api.service.TrafficDumpService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/traffic-dumps")
public class TrafficDumpController {

    private final TrafficDumpService trafficDumpService;

    public TrafficDumpController(TrafficDumpService trafficDumpService) {
        this.trafficDumpService = trafficDumpService;
    }

    @GetMapping
    public List<TrafficDumpResponse> list() {
        return trafficDumpService.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrafficDumpUploadResponse upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "serviceName", required = false) String serviceName,
                                            @RequestParam(value = "deploymentId", required = false) String deploymentId) {
        return trafficDumpService.upload(file, serviceName, deploymentId);
    }
}
