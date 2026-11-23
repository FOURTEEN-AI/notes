package de.fourteen.notes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptanceCriteriaTest {

    @Test
    void old_titles_are_the_replaced_and_the_removed_scenarios() {
        String gherkin =
                """
                # language: de
                Funktionalität: Notizen

                  Szenario: Neu

                  @ersetzt("Eine Notiz speichern")
                  Szenario: Eine Notiz mit Titel speichern
                    Wenn ich speichere

                  @manuell("Nur das Frontend") @ersetzt("Alt ändern")
                  Szenario: Geändert

                  @entfällt
                  Szenario: Eine Notiz löschen
                """;

        assertThat(AcceptanceCriteria.oldTitles(gherkin))
                .containsExactly("Eine Notiz speichern", "Alt ändern", "Eine Notiz löschen");
    }

    private static final Comment PROPOSAL =
            new Comment("## Refine\n\n```gherkin\nSzenario: Neu\n```");

    @Test
    void the_newest_proposal_is_pending_until_it_is_released_or_answered() {
        assertThat(AcceptanceCriteria.pendingProposal(List.of(PROPOSAL))).contains("Szenario: Neu");
        assertThat(
                        AcceptanceCriteria.pendingProposal(
                                List.of(PROPOSAL, new Comment("## Acceptance criteria\n\n..."))))
                .isEmpty();
        assertThat(
                        AcceptanceCriteria.pendingProposal(
                                List.of(PROPOSAL, new Comment("## Idea chat\n\nLieber als CSV."))))
                .isEmpty();
        assertThat(
                        AcceptanceCriteria.pendingProposal(
                                List.of(PROPOSAL, new Comment("Preview: https://..."))))
                .contains("Szenario: Neu");
    }

    @Test
    void a_gherkin_block_without_closing_fence_runs_to_the_end() {
        assertThat(
                        AcceptanceCriteria.gherkinBlock(
                                "## Refine\n\n```gherkin\nSzenario: Neu\n  Wenn x\n\n"))
                .contains("Szenario: Neu\n  Wenn x");
    }
}
