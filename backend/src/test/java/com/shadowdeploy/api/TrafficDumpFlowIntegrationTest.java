package com.shadowdeploy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:shadowtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "shadowdeploy.llm.enabled=false"
})
class TrafficDumpFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadTrafficDumpUpdatesSummaryAndListing() throws Exception {
        String token = loginToken();

        String payload = String.join("\n",
                "status=200 latency=120",
                "status=500 error DiscountService",
                "payload drift detected",
                "latency=360"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "traffic.log",
                "text/plain",
                payload.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/traffic-dumps")
                        .file(file)
                        .param("serviceName", "checkout-service")
                        .param("deploymentId", "deploy-test-1")
                        .header("X-Shadow-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trafficDump.deploymentId").value("deploy-test-1"))
                .andExpect(jsonPath("$.summary.deploymentId").value("deploy-test-1"))
                .andExpect(jsonPath("$.summary.aiInsights").isArray())
                .andExpect(jsonPath("$.summary.aiInsights", Matchers.hasSize(Matchers.greaterThan(0))));

        mockMvc.perform(get("/api/summary").header("X-Shadow-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentId").value("deploy-test-1"));

        mockMvc.perform(get("/api/traffic-dumps").header("X-Shadow-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deploymentId").value("deploy-test-1"));
    }

    private String loginToken() throws Exception {
        String payload = objectMapper.writeValueAsString(
                Map.of("username", "admin", "password", "shadowdeploy")
        );
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.path("token").asText();
    }
}
