package de.fourteen.notes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Dependencies point inwards only: web and infrastructure → application → domain. Checked on the
 * imports of the sources, so no other ring is reached by name either.
 */
class OnionArchitectureTest {

    private static final Path SOURCES = Path.of("src/main/java/de/fourteen/notes");
    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)", Pattern.MULTILINE);

    @Test
    void domain_uses_only_the_jdk_and_nullness_annotations() throws IOException {
        assertThat(imports("domain"))
                .allSatisfy(
                        i ->
                                assertThat(i)
                                        .matches(
                                                "(java\\.|de\\.fourteen\\.notes\\.domain\\.|org\\.jspecify\\.).*"));
    }

    @Test
    void application_knows_neither_web_nor_infrastructure() throws IOException {
        assertThat(imports("application"))
                .noneMatch(
                        i ->
                                i.startsWith("de.fourteen.notes.web.")
                                        || i.startsWith("de.fourteen.notes.infrastructure.")
                                        || i.startsWith("org.springframework.web.")
                                        || i.startsWith("org.springframework.http.")
                                        || i.startsWith("org.springframework.jdbc."));
    }

    @Test
    void infrastructure_implements_only_domain_ports() throws IOException {
        assertThat(imports("infrastructure"))
                .noneMatch(
                        i ->
                                i.startsWith("de.fourteen.notes.web.")
                                        || i.startsWith("de.fourteen.notes.application."));
    }

    @Test
    void web_does_not_reach_into_infrastructure() throws IOException {
        assertThat(imports("web"))
                .noneMatch(i -> i.startsWith("de.fourteen.notes.infrastructure."));
    }

    @Test
    void every_class_lives_in_a_ring() throws IOException {
        try (Stream<Path> files = Files.list(SOURCES)) {
            assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("NotesApplication.java", "package-info.java");
        }
    }

    private static List<String> imports(String ring) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES.resolve(ring))) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(sources).isNotEmpty();
            return sources.stream()
                    .flatMap(
                            p -> {
                                try {
                                    Matcher m = IMPORT.matcher(Files.readString(p));
                                    return m.results().map(r -> r.group(1)).toList().stream();
                                } catch (IOException e) {
                                    throw new java.io.UncheckedIOException(e);
                                }
                            })
                    .toList();
        }
    }
}
