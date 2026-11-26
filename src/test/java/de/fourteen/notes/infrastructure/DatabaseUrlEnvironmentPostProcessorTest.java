package de.fourteen.notes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void maps_the_url_from_fly_postgres_attach() {
        var properties =
                DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                        "postgres://notes:p%40ss@notes-db.flycast:5432/notes?sslmode=disable");

        assertThat(properties)
                .containsEntry(
                        "spring.datasource.url",
                        "jdbc:postgresql://notes-db.flycast:5432/notes?sslmode=disable")
                .containsEntry("spring.datasource.username", "notes")
                .containsEntry("spring.datasource.password", "p@ss");
    }

    @Test
    void rejects_other_schemes_without_echoing_the_password() {
        assertThatThrownBy(() ->
                        DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                                "mysql://user:secret@host/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("secret");
    }
}
