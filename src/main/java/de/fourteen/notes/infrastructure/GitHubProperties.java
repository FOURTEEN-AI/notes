package de.fourteen.notes.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access to the repository on GitHub. The token (env GITHUB_TOKEN) is a fine-grained personal
 * access token for this repository only. Without it, the ideas and the preview bar are off. A
 * malformed api-url or repository stops the start.
 */
@ConfigurationProperties("github")
@Validated
record GitHubProperties(
        @NotBlank @Pattern(regexp = "https?://\\S+") String apiUrl,
        @NotBlank @Pattern(regexp = "[\\w.-]+/[\\w.-]+") String repository,
        String token) {

    boolean configured() {
        return token != null && !token.isBlank();
    }
}
