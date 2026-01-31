package com.shadowdeploy.api.service;

import com.shadowdeploy.api.dto.TrafficDumpResponse;
import com.shadowdeploy.api.dto.TrafficDumpUploadResponse;
import com.shadowdeploy.api.entity.ShadowSummaryEntity;
import com.shadowdeploy.api.entity.TrafficDump;
import com.shadowdeploy.api.model.ShadowSummaryResponse;
import com.shadowdeploy.api.repository.TrafficDumpRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class TrafficDumpService {

    private static final String DEMO_RESOURCE = "samples/shadowdeploy-demo.jsonl";

    private final TrafficDumpRepository trafficDumpRepository;
    private final ShadowSummaryService summaryService;

    public TrafficDumpService(TrafficDumpRepository trafficDumpRepository, ShadowSummaryService summaryService) {
        this.trafficDumpRepository = trafficDumpRepository;
        this.summaryService = summaryService;
    }

    public TrafficDumpUploadResponse upload(MultipartFile file, String serviceName, String deploymentId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Traffic dump file is required");
        }
        TrafficDump dump = new TrafficDump();
        dump.setFileName(file.getOriginalFilename() == null ? "traffic-dump" : file.getOriginalFilename());
        dump.setContentType(file.getContentType() == null ? "text/plain" : file.getContentType());
        dump.setSizeBytes(file.getSize());
        dump.setServiceName(resolveServiceName(serviceName));
        dump.setDeploymentId(resolveDeploymentId(deploymentId));
        dump.setUploadedAt(Instant.now());
        dump.setContent(readContent(file));
        TrafficDump savedDump = trafficDumpRepository.save(dump);

        ShadowSummaryResponse summary = summaryService.analyzeDump(savedDump);
        ShadowSummaryEntity summaryEntity = summaryService.saveSummary(summary, savedDump);
        savedDump.setSummaryId(summaryEntity.getId());
        trafficDumpRepository.save(savedDump);

        return new TrafficDumpUploadResponse(toResponse(savedDump), summary);
    }

    public TrafficDumpUploadResponse runDemo(String deploymentId) {
        String content = loadDemoContent();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TrafficDump dump = new TrafficDump();
        dump.setFileName("shadowdeploy-demo.jsonl");
        dump.setContentType("application/json");
        dump.setSizeBytes(bytes.length);
        dump.setServiceName("checkout-service");
        dump.setDeploymentId(resolveDeploymentId(deploymentId != null ? deploymentId : "demo-" + Instant.now()));
        dump.setUploadedAt(Instant.now());
        dump.setContent(content);
        TrafficDump savedDump = trafficDumpRepository.save(dump);

        ShadowSummaryResponse summary = summaryService.analyzeDump(savedDump);
        ShadowSummaryEntity summaryEntity = summaryService.saveSummary(summary, savedDump);
        savedDump.setSummaryId(summaryEntity.getId());
        trafficDumpRepository.save(savedDump);

        return new TrafficDumpUploadResponse(toResponse(savedDump), summary);
    }

    public List<TrafficDumpResponse> list() {
        return trafficDumpRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadedAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TrafficDumpResponse toResponse(TrafficDump dump) {
        return new TrafficDumpResponse(
                dump.getId(),
                dump.getFileName(),
                dump.getServiceName(),
                dump.getDeploymentId(),
                dump.getSizeBytes(),
                dump.getUploadedAt().toString(),
                dump.getSummaryId()
        );
    }

    private String resolveServiceName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return "checkout-service";
        }
        return serviceName.trim();
    }

    private String resolveDeploymentId(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return "upload-" + Instant.now().toString();
        }
        return deploymentId.trim();
    }

    private String readContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded traffic dump", ex);
        }
    }

    private String loadDemoContent() {
        ClassPathResource resource = new ClassPathResource(DEMO_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Demo traffic dump resource missing: " + DEMO_RESOURCE);
        }
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read demo traffic dump", ex);
        }
    }
}
