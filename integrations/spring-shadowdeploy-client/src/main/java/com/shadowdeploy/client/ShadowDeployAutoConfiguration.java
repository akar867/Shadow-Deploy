package com.shadowdeploy.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(ShadowDeployProperties.class)
public class ShadowDeployAutoConfiguration {

    @Bean
    public ShadowDeployReplayClient shadowDeployReplayClient(ShadowDeployProperties properties,
                                                             RestTemplateBuilder restTemplateBuilder) {
        return new ShadowDeployReplayClient(properties, restTemplateBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "shadowdeploy.client", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<TrafficCaptureFilter> shadowDeployFilter(ShadowDeployReplayClient replayClient,
                                                                           ShadowDeployProperties properties,
                                                                           ObjectMapper objectMapper) {
        FilterRegistrationBean<TrafficCaptureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TrafficCaptureFilter(replayClient, properties, objectMapper));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
