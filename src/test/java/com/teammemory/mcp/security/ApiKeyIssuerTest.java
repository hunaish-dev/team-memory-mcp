package com.teammemory.mcp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyIssuerTest {

    @Mock
    ApiKeyRepository apiKeyRepository;

    ApiKeyIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new ApiKeyIssuer(apiKeyRepository, new ApiKeyHasher());
        when(apiKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueReturnsTheRawTokenAndPersistsOnlyItsHashAndPrefix() {
        String token = issuer.issue("ali");

        assertThat(token).startsWith("tmk_");

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();

        assertThat(saved.getTeammateName()).isEqualTo("ali");
        assertThat(saved.getKeyPrefix()).isEqualTo(token.substring(0, 12));
        // the raw token itself is never persisted, only its hash
        assertThat(saved.getKeyHash()).isNotEqualTo(token);
        assertThat(new ApiKeyHasher().hash(token)).isEqualTo(saved.getKeyHash());
    }

    @Test
    void issuingTwiceForTheSameTeammateProducesDifferentTokens() {
        String tokenA = issuer.issue("ali");
        String tokenB = issuer.issue("ali");

        assertThat(tokenA).isNotEqualTo(tokenB);
    }
}
