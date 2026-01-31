package com.shadowdeploy.api.controller;

import com.shadowdeploy.api.dto.ShadowReplayRequest;
import com.shadowdeploy.api.dto.ShadowReplayResponse;
import com.shadowdeploy.api.service.ShadowReplayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shadow")
public class ShadowReplayController {

    private final ShadowReplayService shadowReplayService;

    public ShadowReplayController(ShadowReplayService shadowReplayService) {
        this.shadowReplayService = shadowReplayService;
    }

    @PostMapping("/replay")
    public ShadowReplayResponse replay(@RequestBody ShadowReplayRequest request) {
        return shadowReplayService.replay(request);
    }
}
