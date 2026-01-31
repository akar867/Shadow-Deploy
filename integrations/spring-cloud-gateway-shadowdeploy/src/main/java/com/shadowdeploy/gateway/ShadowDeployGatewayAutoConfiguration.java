package com.shadowdeploy.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ShadowDeployGatewayProperties.class)
public class ShadowDeployGatewayAutoConfiguration {

    @Bean
    public ShadowDeployGatewayReplayClient shadowDeployGatewayReplayClient(ShadowDeployGatewayProperties properties,
                                                                           WebClient.Builder webClientBuilder) {
        return new ShadowDeployGatewayReplayClient(properties, webClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "shadowdeploy.gateway", name = "enabled", havingValue = "true")
    public ShadowDeployGatewayFilter shadowDeployGatewayFilter(ShadowDeployGatewayReplayClient replayClient,
                                                               ShadowDeployGatewayProperties properties,
                                                               ObjectMapper objectMapper) {
        return new ShadowDeployGatewayFilter(replayClient, properties, objectMapper);
    }
}
