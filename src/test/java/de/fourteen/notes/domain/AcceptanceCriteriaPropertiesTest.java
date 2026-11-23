package de.fourteen.notes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class AcceptanceCriteriaPropertiesTest {

    /** Text lines without backticks, quotes and carriage returns, never blank in total. */
    @Provide
    Arbitrary<String> gherkin() {
        return Arbitraries.strings()
                .withCharRange(' ', '~')
                .excludeChars('`', '"')
                .ofMaxLength(30)
                .list()
                .ofMinSize(1)
                .ofMaxSize(6)
                .map(lines -> String.join("\n", lines))
                .filter(text -> !text.isBlank());
    }

    @Provide
    Arbitrary<String> title() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', 'ä', 'ö', 'ü', 'ß')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(text -> !text.isBlank() && text.equals(text.strip()));
    }

    @Property
    void a_released_block_is_found_again_in_its_comment(@ForAll("gherkin") String gherkin) {
        assertThat(AcceptanceCriteria.gherkinBlock(AcceptanceCriteria.toComment(gherkin)))
                .contains(gherkin);
    }

    @Property
    void a_released_block_is_not_a_pending_proposal(@ForAll("gherkin") String gherkin) {
        Comment released = new Comment(AcceptanceCriteria.toComment(gherkin));

        assertThat(AcceptanceCriteria.pendingProposal(List.of(released))).isEmpty();
    }

    @Property
    void a_refine_proposal_is_pending_and_an_answer_ends_it(@ForAll("gherkin") String gherkin) {
        Comment proposal = new Comment("## Refine\n\n```gherkin\n" + gherkin + "\n```");

        assertThat(AcceptanceCriteria.pendingProposal(List.of(proposal))).contains(gherkin);
        assertThat(
                        AcceptanceCriteria.pendingProposal(
                                List.of(proposal, new Comment("## Idea chat\n\nanswer"))))
                .isEmpty();
    }

    @Property
    void arbitrary_text_never_throws_and_never_yields_a_blank_block(@ForAll String text) {
        assertThat(AcceptanceCriteria.gherkinBlock(text).orElse("x")).isNotBlank();
        assertThat(AcceptanceCriteria.oldTitles(text)).isNotNull();
        assertThat(AcceptanceCriteria.pendingProposal(List.of(new Comment(text)))).isNotNull();
    }

    @Property
    void text_without_a_gherkin_fence_has_no_block(@ForAll String text) {
        assertThat(AcceptanceCriteria.gherkinBlock(text.replace("```gherkin", ""))).isEmpty();
    }

    @Property
    void replaced_titles_are_returned_in_order(@ForAll List<@From("title") String> titles) {
        StringBuilder gherkin = new StringBuilder("Funktionalität: X\n");
        for (String title : titles) {
            gherkin.append("\n  @ersetzt(\"").append(title).append("\")\n");
            gherkin.append("  Szenario: neu ").append(title).append('\n');
        }

        assertThat(AcceptanceCriteria.oldTitles(gherkin.toString()))
                .containsExactlyElementsOf(titles);
    }

    @Property
    void removed_scenarios_are_returned_with_their_own_title(@ForAll("title") String title) {
        String gherkin = "@entfällt\nSzenario: " + title + "\n  Wenn x\n";

        assertThat(AcceptanceCriteria.oldTitles(gherkin)).containsExactly(title);
    }

    @Property
    void scenarios_without_tags_have_no_old_title(@ForAll List<@From("title") String> titles) {
        String gherkin =
                String.join("\n", titles.stream().map(title -> "Szenario: " + title).toList());

        assertThat(AcceptanceCriteria.oldTitles(gherkin)).isEmpty();
    }
}
