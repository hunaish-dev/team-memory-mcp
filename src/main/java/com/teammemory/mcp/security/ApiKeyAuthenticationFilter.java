package com.teammemory.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Resolves an {@code Authorization: Bearer <token>} header to a teammate
 * identity. Deliberately does NOT reject the request on a missing/invalid
 * key — it just leaves the request unauthenticated and calls the chain;
 * {@link SecurityConfig}'s {@code authorizeHttpRequests} is what turns that
 * into a 401. This is the idiomatic Spring Security split: the filter's job
 * is authentication, not authorization.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository, ApiKeyHasher apiKeyHasher) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        extractToken(request)
                .map(apiKeyHasher::hash)
                .flatMap(apiKeyRepository::findByKeyHashAndRevokedAtIsNull)
                .ifPresent(apiKey -> authenticate(request, apiKey));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(HttpServletRequest request, ApiKey apiKey) {
        String teammateName = apiKey.getTeammateName();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(teammateName, null, List.of()));
        // The actual actor handoff to MCP tool methods — see SecurityConfig's
        // contextExtractor, which reads this attribute, not SecurityContextHolder.
        request.setAttribute(McpAuthContext.ACTOR_ATTRIBUTE, teammateName);

        apiKey.recordUse();
        apiKeyRepository.save(apiKey);
    }
}
