package com.teammemory.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    ApiKeyRepository apiKeyRepository;
    @Mock
    HttpServletRequest request;
    @Mock
    HttpServletResponse response;
    @Mock
    FilterChain filterChain;

    ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyRepository, new ApiKeyHasher());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validKeyAuthenticatesAndSetsTheActorAttribute() throws Exception {
        ApiKeyHasher hasher = new ApiKeyHasher();
        String token = hasher.generateToken();
        ApiKey apiKey = new ApiKey("ali", hasher.prefixOf(token), hasher.hash(token));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(hasher.hash(token))).thenReturn(Optional.of(apiKey));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ali");
        verify(request).setAttribute(McpAuthContext.ACTOR_ATTRIBUTE, "ali");
        verify(apiKeyRepository).save(apiKey);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationHeaderLeavesRequestUnauthenticatedButStillCallsChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(request, never()).setAttribute(eq(McpAuthContext.ACTOR_ATTRIBUTE), any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void unknownKeyLeavesRequestUnauthenticatedButStillCallsChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer tmk_doesnotexist");
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(any())).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void revokedKeyIsTreatedAsUnknownBecauseTheRepositoryQueryExcludesIt() throws Exception {
        // findByKeyHashAndRevokedAtIsNull already excludes revoked keys at the
        // query level, so from the filter's perspective a revoked key behaves
        // exactly like an unknown one — this test documents that contract.
        when(request.getHeader("Authorization")).thenReturn("Bearer tmk_revoked");
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(any())).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
