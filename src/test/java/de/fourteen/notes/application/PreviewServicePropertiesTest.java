package de.fourteen.notes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fourteen.notes.domain.CheckState;
import de.fourteen.notes.domain.Checks;
import de.fourteen.notes.domain.DemoStep;
import de.fourteen.notes.domain.GitHub;
import de.fourteen.notes.domain.PullRequest;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import tools.jackson.databind.json.JsonMapper;

class PreviewServicePropertiesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Provide
    Arbitrary<DemoStep> step() {
        Arbitrary<String> text = Arbitraries.strings().ofMaxLength(30);
        return Combinators.combine(text.filter(s -> !s.isBlank()), text)
                .as((action, expect) -> new DemoStep(action, expect, null));
    }

    @Property
    void the_steps_of_a_demo_block_come_back_in_order(
            @ForAll List<@From("step") DemoStep> steps,
            @ForAll String before,
            @ForAll String after) {
        String body = before.replace("```", "") + "\n```demo\n" + json(steps) + "\n```\n" + after;

        assertThat(demoOf(body)).containsExactlyElementsOf(steps);
    }

    @Property
    void a_body_without_a_valid_block_gives_an_empty_tour(@ForAll String body) {
        assertThat(demoOf(body.replace("```demo", ""))).isEmpty();
        assertThat(demoOf("```demo\n" + body.replace("```", "") + "\n```")).isNotNull();
    }

    private static String json(List<DemoStep> steps) {
        return JSON.writeValueAsString(
                steps.stream()
                        .map(
                                s ->
                                        java.util.Map.of(
                                                "do", s.action(),
                                                "expect", s.expect()))
                        .toList());
    }

    static List<DemoStep> demoOf(String body) {
        GitHub github = mock(GitHub.class);
        when(github.pullRequest(1))
                .thenReturn(new PullRequest(1, "t", body, "u", "feature", "sha", "open"));
        when(github.checks("sha"))
                .thenReturn(
                        new Checks(
                                CheckState.PENDING,
                                CheckState.PENDING,
                                CheckState.PENDING,
                                CheckState.PENDING,
                                CheckState.PENDING));
        return new PreviewService(github, JSON, 1, "sha").get().demo();
    }
}
