package com.teammemory.mcp.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyIssuer {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    public ApiKeyIssuer(ApiKeyRepository apiKeyRepository, ApiKeyHasher apiKeyHasher) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    /** Returns the raw token — shown to the caller exactly once, never stored or logged. */
    @Transactional
    public String issue(String teammateName) {
        String token = apiKeyHasher.generateToken();
        ApiKey apiKey = new ApiKey(teammateName, apiKeyHasher.prefixOf(token), apiKeyHasher.hash(token));
        apiKeyRepository.save(apiKey);
        return token;
    }
}
