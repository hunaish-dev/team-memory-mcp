package com.teammemory.mcp.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Usage: {@code ./mvnw spring-boot:run -Dspring-boot.run.arguments="--issue-api-key=<teammate-name>"}
 * — see TeamMemoryMcpApplication.main() for why this skips the web server
 * entirely rather than starting and stopping it.
 */
@Component
public class IssueApiKeyRunner implements ApplicationRunner {

    private static final String FLAG = "issue-api-key";

    private final ApiKeyIssuer apiKeyIssuer;
    private final ConfigurableApplicationContext context;

    public IssueApiKeyRunner(ApiKeyIssuer apiKeyIssuer, ConfigurableApplicationContext context) {
        this.apiKeyIssuer = apiKeyIssuer;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(FLAG)) {
            return;
        }

        List<String> values = args.getOptionValues(FLAG);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            System.err.println("Usage: --issue-api-key=<teammate-name>");
            System.exit(SpringApplication.exit(context, () -> 1));
            return;
        }

        String teammateName = values.get(0);
        String token = apiKeyIssuer.issue(teammateName);

        System.out.println();
        System.out.println("API key issued for '" + teammateName + "':");
        System.out.println();
        System.out.println("    " + token);
        System.out.println();
        System.out.println("Copy this now — it is not stored anywhere and cannot be shown again.");
        System.out.println();

        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
