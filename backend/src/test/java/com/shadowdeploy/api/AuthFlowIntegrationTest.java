package com.shadowdeploy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsSummaryWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginAndLogoutLifecycle() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/auth/me").header("X-Shadow-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        mockMvc.perform(post("/api/auth/logout").header("X-Shadow-Token", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("X-Shadow-Token", token))
                .andExpect(status().isUnauthorized());
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
