package de.fourteen.notes;

import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Local loop: `./gradlew bootTestRun` starts the app on port 8080 with a fresh PostgreSQL
 * container. Login: dev / dev.
 */
public class TestNotesApplication {

    public static void main(String[] args) {
        var withLocalUser =
                Stream.concat(
                        Stream.of(
                                "--spring.security.user.name=dev",
                                "--spring.security.user.password=dev"),
                        Stream.of(args));
        SpringApplication.from(NotesApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(withLocalUser.toArray(String[]::new));
    }
}
