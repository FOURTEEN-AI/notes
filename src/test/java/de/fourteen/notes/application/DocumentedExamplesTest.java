package de.fourteen.notes.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.fourteen.notes.domain.AcceptanceCriteria;
import de.fourteen.notes.domain.DemoStep;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The machine-readable examples in the docs (a fenced ```demo or ```gherkin block at the start of a
 * line) are read by the parsers of the app, so a doc that no longer parses or no longer says what
 * the text around it says fails here. Reads the Markdown files from the repository root.
 */
class DocumentedExamplesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Pattern FENCE =
            Pattern.compile("^```(demo|gherkin)[ \\t]*$", Pattern.MULTILINE);
    private static final Pattern DEMO_JSON =
            Pattern.compile("^```demo[ \\t]*\\R(.*?)^```", Pattern.DOTALL | Pattern.MULTILINE);

    /** text runs from the fence to the end of the file, so a parser finds this block first. */
    private record Example(Path file, String kind, String text) {}

    @Test
    void claude_md_documents_a_demo_tour() {
        assertThat(examples("demo"))
                .anyMatch(example -> example.file().equals(Path.of("CLAUDE.md")));
    }

    @Test
    void a_demo_example_parses_to_the_steps_it_is_written_as() {
        for (Example example : examples("demo")) {
            Matcher block = DEMO_JSON.matcher(example.text());
            assertThat(block.find()).as("%s: closed ```demo block", example.file()).isTrue();
            JsonNode written = JSON.readTree(block.group(1));
            assertThat(written.isArray()).as("%s: a JSON list", example.file()).isTrue();

            List<DemoStep> expected = new ArrayList<>();
            for (JsonNode step : written) {
                expected.add(
                        new DemoStep(
                                step.path("do").asString(""),
                                step.path("expect").asString(""),
                                step.has("selector") ? step.get("selector").asString() : null));
            }

            assertThat(expected).as("%s: at least one step", example.file()).isNotEmpty();
            assertThat(expected)
                    .as("%s: every step says what to do and what to expect", example.file())
                    .allSatisfy(
                            step -> {
                                assertThat(step.action()).isNotBlank();
                                assertThat(step.expect()).isNotBlank();
                            });
            assertThat(PreviewServicePropertiesTest.demoOf(example.text()))
                    .as("%s: the app reads the steps as written", example.file())
                    .containsExactlyElementsOf(expected);
        }
    }

    /** CLAUDE.md says a selector is taken from the code: its tags and classes exist there. */
    @Test
    void the_selectors_of_a_demo_example_exist_in_the_frontend() throws IOException {
        String markup = frontend(".tsx");
        String styles = frontend(".css");
        for (Example example : examples("demo")) {
            for (DemoStep step : PreviewServicePropertiesTest.demoOf(example.text())) {
                if (step.selector() == null) {
                    continue;
                }
                Matcher tag =
                        Pattern.compile("(?<![.#\\w-])[a-z][a-z0-9]*").matcher(step.selector());
                while (tag.find()) {
                    assertThat(markup)
                            .as("%s: <%s> of '%s'", example.file(), tag.group(), step.selector())
                            .contains("<" + tag.group());
                }
                Matcher css = Pattern.compile("\\.([\\w-]+)").matcher(step.selector());
                while (css.find()) {
                    assertThat(styles)
                            .as("%s: .%s of '%s'", example.file(), css.group(1), step.selector())
                            .contains("." + css.group(1));
                }
            }
        }
    }

    @Test
    void a_gherkin_example_has_a_block_the_app_finds() {
        for (Example example : examples("gherkin")) {
            assertThat(AcceptanceCriteria.gherkinBlock(example.text()))
                    .as("%s: non-blank ```gherkin block", example.file())
                    .isPresent();
        }
    }

    private static List<Example> examples(String kind) {
        List<Example> examples = new ArrayList<>();
        try (Stream<Path> files =
                Stream.concat(
                        Stream.of(Path.of("CLAUDE.md"), Path.of("README.md")),
                        markdown(Path.of("docs")))) {
            for (Path file : files.toList()) {
                String text = Files.readString(ROOT.resolve(file));
                Matcher fence = FENCE.matcher(text);
                while (fence.find()) {
                    if (fence.group(1).equals(kind)) {
                        examples.add(new Example(file, kind, text.substring(fence.start())));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return examples;
    }

    private static Stream<Path> markdown(Path dir) throws IOException {
        try (Stream<Path> all = Files.walk(ROOT.resolve(dir))) {
            return all
                    .filter(path -> path.toString().endsWith(".md"))
                    .map(ROOT::relativize)
                    .toList()
                    .stream();
        }
    }

    private static String frontend(String extension) throws IOException {
        try (Stream<Path> all = Files.walk(ROOT.resolve("frontend/src"))) {
            StringBuilder text = new StringBuilder();
            for (Path path : all.filter(p -> p.toString().endsWith(extension)).toList()) {
                text.append(Files.readString(path)).append('\n');
            }
            return text.toString();
        }
    }
}
