package com.teammemory.mcp.security;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        http
                // No cookies, no session — every request must carry an explicit
                // Authorization header a cross-site page can't attach automatically,
                // so CSRF's threat model (forged requests riding an existing session)
                // doesn't apply here.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                // Without this, Spring Security's AnonymousAuthenticationFilter (on by
                // default) means a missing/invalid key is treated as an authorization
                // failure (403) rather than an authentication failure (401) — 401 is
                // the correct code for "you didn't prove who you are" on a bearer-token
                // API, and is the contract the rest of this service assumes.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Replaces the autoconfigured transport provider bean (which is
     * {@code @ConditionalOnMissingBean}, so defining this one here wins) to add
     * a contextExtractor. Everything else mirrors
     * {@code McpServerStreamableHttpWebMvcAutoConfiguration} exactly — only the
     * {@code contextExtractor(...)} call is new. This extractor runs
     * synchronously on the original request thread (confirmed by reading
     * {@code WebMvcStreamableServerTransportProvider}'s source: it's called
     * before the reactive {@code .contextWrite(...)} hand-off, not after), so
     * reading the request attribute set by {@link ApiKeyAuthenticationFilter}
     * here is safe — unlike {@code SecurityContextHolder}, which is
     * ThreadLocal-based and is NOT guaranteed to survive into the actual
     * {@code @McpTool} method body once the MCP SDK's Reactor pipeline takes
     * over.
     */
    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties serverProperties) {

        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(serverProperties.getMcpEndpoint())
                .keepAliveInterval(serverProperties.getKeepAliveInterval())
                .disallowDelete(serverProperties.isDisallowDelete())
                .contextExtractor(serverRequest -> {
                    String actor = (String) serverRequest.servletRequest()
                            .getAttribute(McpAuthContext.ACTOR_ATTRIBUTE);
                    return actor == null
                            ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of(McpAuthContext.ACTOR_KEY, actor));
                })
                .build();
    }
}
