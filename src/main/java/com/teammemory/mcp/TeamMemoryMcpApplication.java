package com.teammemory.mcp;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded because nothing in this
// app uses AuthenticationManager/UserDetailsService — ApiKeyAuthenticationFilter
// sets SecurityContextHolder directly. Without this exclusion, Spring Boot
// generates and logs a throwaway in-memory user/password on every startup
// that nothing ever reaches — inert, but noisy and misleading in logs.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TeamMemoryMcpApplication {

	public static void main(String[] args) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(TeamMemoryMcpApplication.class);
		// --issue-api-key is a one-shot CLI action, not a server run — skip
		// starting the embedded web server entirely rather than starting it
		// and immediately tearing it down.
		if (isIssueApiKeyInvocation(args)) {
			builder.web(WebApplicationType.NONE);
		}
		builder.run(args);
	}

	private static boolean isIssueApiKeyInvocation(String[] args) {
		for (String arg : args) {
			if (arg.startsWith("--issue-api-key")) {
				return true;
			}
		}
		return false;
	}

}
