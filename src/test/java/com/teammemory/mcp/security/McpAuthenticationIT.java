package com.teammemory.mcp.security;

import com.teammemory.mcp.memory.MemoryEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The load-bearing test for this feature: drives the real streamable-HTTP
 * wire protocol end to end with a valid API key and confirms the actor
 * recorded on the resulting memory_entry row matches the key's teammate.
 * This is what actually proves the McpTransportContext plumbing works at
 * runtime — ApiKeySecurityIT only proves the auth filter runs, not that the
 * resolved identity survives into the tool method.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class McpAuthenticationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiKeyIssuer apiKeyIssuer;

    @Autowired
    MemoryEntryRepository memoryEntryRepository;

    @Test
    void memoryWriteOverTheWireRecordsTheAuthenticatedTeammateAsActor() throws Exception {
        String token = apiKeyIssuer.issue("wire-protocol-teammate");
        String path = "/test/" + UUID.randomUUID() + ".md";

        String sessionId = initializeSession(token);
        MockHttpServletResponse writeResponse = callMemoryWrite(token, sessionId, path);

        assertThat(writeResponse.getStatus()).isEqualTo(200);
        // no "actor" argument was sent at all — it no longer exists on the tool's schema
        assertThat(writeResponse.getContentAsString()).doesNotContain("\"isError\":true");

        var entry = memoryEntryRepository.findByPathAndDeletedAtIsNull(path).orElseThrow();
        assertThat(entry.getCreatedBy()).isEqualTo("wire-protocol-teammate");
    }

    private String initializeSession(String token) throws Exception {
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"McpAuthenticationIT","version":"0.0.1"}}}
                """;

        MockHttpServletResponse response = mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        String sessionId = response.getHeader("Mcp-Session-Id");
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    private MockHttpServletResponse callMemoryWrite(String token, String sessionId, String path) throws Exception {
        String body = """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"memory_write","arguments":{"path":"%s","category":"GOTCHA","content":"written over the wire"}}}
                """.formatted(path);

        return mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .header("Mcp-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andReturn()
                .getResponse();
    }
}
