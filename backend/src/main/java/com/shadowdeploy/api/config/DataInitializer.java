package com.shadowdeploy.api.config;

import com.shadowdeploy.api.service.ShadowSummaryService;
import com.shadowdeploy.api.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final ShadowSummaryService summaryService;

    public DataInitializer(UserService userService, ShadowSummaryService summaryService) {
        this.userService = userService;
        this.summaryService = summaryService;
    }

    @Override
    public void run(String... args) {
        userService.ensureDefaultUser();
        summaryService.ensureSeedSummary();
    }
}
