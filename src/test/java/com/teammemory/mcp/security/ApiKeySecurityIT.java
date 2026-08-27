package com.teammemory.mcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth-layer behavior at the real HTTP boundary — the MCP wire protocol itself is covered by McpAuthenticationIT. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiKeySecurityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiKeyIssuer apiKeyIssuer;

    @Autowired
    ApiKeyRepository apiKeyRepository;

    private static final String INITIALIZE_BODY = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"ApiKeySecurityIT","version":"0.0.1"}}}
            """;

    @Test
    void mcpEndpointRejectsRequestsWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpointRejectsAnUnknownBearerToken() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer tmk_this_key_was_never_issued")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpointRejectsARevokedKey() throws Exception {
        String teammateName = "revoked-teammate-" + System.nanoTime();
        String token = apiKeyIssuer.issue(teammateName);
        ApiKey issuedKey = apiKeyRepository.findAll().stream()
                .filter(k -> k.getTeammateName().equals(teammateName))
                .findFirst()
                .orElseThrow();
        issuedKey.revoke();
        apiKeyRepository.save(issuedKey);

        mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealthStaysOpenWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
