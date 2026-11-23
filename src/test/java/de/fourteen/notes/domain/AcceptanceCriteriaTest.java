package de.fourteen.notes.domain;

import static org.assertj.core.api.Assertions.assertThat;

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

                  @ui @ersetzt("Alt ändern")
                  Szenario: Geändert

                  @entfällt
                  Szenario: Eine Notiz löschen
                """;

        assertThat(AcceptanceCriteria.oldTitles(gherkin))
                .containsExactly("Eine Notiz speichern", "Alt ändern", "Eine Notiz löschen");
    }
}
