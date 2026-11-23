package de.fourteen.notes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GitHubPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GitHubProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(Config.class)
                    .withPropertyValues(
                            "github.api-url=https://api.github.com",
                            "github.repository=FOURTEEN-AI/notes");

    @Test
    void starts_without_a_token() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void stops_the_start_on_a_malformed_repository() {
        runner.withPropertyValues("github.repository=notes")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void stops_the_start_on_a_malformed_api_url() {
        runner.withPropertyValues("github.api-url=api.github.com")
                .run(context -> assertThat(context).hasFailed());
    }
}
