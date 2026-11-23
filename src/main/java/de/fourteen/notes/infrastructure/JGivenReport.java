package de.fourteen.notes.infrastructure;

import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.Scenario;
import de.fourteen.notes.domain.ScenarioReport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The port {@link ScenarioReport} on the JGiven HTML report in the jar. Its data files ({@code
 * jgivenReport.addZippedScenarios('<base64 of gzipped JSON>')}) hold the scenarios.
 */
@Component
class JGivenReport implements ScenarioReport {

    private static final Pattern ZIPPED = Pattern.compile("addZippedScenarios\\('([^']*)'\\)");
    private static final Map<String, String> KEYWORDS =
            Map.of(
                    "given", "Gegeben sei",
                    "when", "Wenn",
                    "then", "Dann",
                    "and", "Und",
                    "but", "Aber");

    private final JsonMapper json;
    private final String location;

    JGivenReport(
            JsonMapper json,
            @Value("${notes.report-data:classpath*:static/jgiven/data/*.js}") String location) {
        this.json = json;
        this.location = location;
    }

    @Override
    public Optional<Scenario> find(String title) {
        return scenarios().stream().filter(scenario -> scenario.title().equals(title)).findFirst();
    }

    // Without a report (tests, bootTestRun) there is nothing to show.
    private List<Scenario> scenarios() {
        List<Scenario> scenarios = new ArrayList<>();
        try {
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(location)) {
                String data =
                        new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Matcher zipped = ZIPPED.matcher(data);
                while (zipped.find()) {
                    try (InputStream in =
                            new GZIPInputStream(
                                    new ByteArrayInputStream(
                                            Base64.getDecoder().decode(zipped.group(1))))) {
                        for (JsonNode test : json.readTree(in).path("scenarios")) {
                            for (JsonNode scenario : test.path("scenarios")) {
                                scenarios.add(toScenario(scenario));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new DomainException(
                    DomainException.Kind.UPSTREAM_FAILED, "the acceptance report is unreadable");
        }
        return scenarios;
    }

    private static Scenario toScenario(JsonNode scenario) {
        List<String> steps = new ArrayList<>();
        for (JsonNode step : scenario.path("scenarioCases").path(0).path("steps")) {
            if (step.path("depth").asInt() != 0) {
                continue;
            }
            JsonNode words = step.path("words");
            StringBuilder line =
                    new StringBuilder(
                            KEYWORDS.getOrDefault(
                                    words.path(0).path("value").asString("").toLowerCase(),
                                    words.path(0).path("value").asString("")));
            for (int i = 1; i < words.size(); i++) {
                String value = words.get(i).path("value").asString("");
                line.append(' ')
                        .append(words.get(i).has("argumentInfo") ? "\"" + value + "\"" : value);
            }
            steps.add(line.toString());
        }
        return new Scenario(scenario.path("description").asString(""), steps);
    }
}
