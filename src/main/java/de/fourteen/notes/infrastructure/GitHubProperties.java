package de.fourteen.notes.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access to the repository on GitHub. The token (env GITHUB_TOKEN) is a fine-grained personal
 * access token for this repository only. Without it, the ideas and the preview bar are off.
 */
@ConfigurationProperties("github")
record GitHubProperties(String apiUrl, String repository, String token) {

    boolean configured() {
        return token != null && !token.isBlank();
    }
}
