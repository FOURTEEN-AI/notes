package de.fourteen.notes.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.As;
import com.tngtech.jgiven.annotation.Pending;
import com.tngtech.jgiven.annotation.ProvidedScenarioState;
import com.tngtech.jgiven.annotation.ScenarioState;
import de.fourteen.notes.application.NoteService;
import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.Note;
import java.util.List;

/**
 * Steps of the notes scenarios. The When steps call the application service; the repository is an
 * in-memory fake. Methods are named in German like the scenarios.
 */
public class NotizenStage extends Stage<NotizenStage> {

    @ProvidedScenarioState private final NoteService notizen = new NoteService(new InMemoryNoteRepository());

    @ScenarioState private Note zuletztGespeichert;
    @ScenarioState private DomainException fehler;

    public NotizenStage eine_Notiz_$_existiert(String text) {
        notizen.create(text);
        return self();
    }

    public NotizenStage ich_speichere_die_Notiz_$(String text) {
        zuletztGespeichert = notizen.create(text);
        return self();
    }

    public NotizenStage ich_speichere_eine_leere_Notiz() {
        try {
            notizen.create("  ");
        } catch (DomainException e) {
            fehler = e;
        }
        return self();
    }

    public NotizenStage ich_eine_Notiz_mit_$_Zeichen_speichere(int anzahl) {
        try {
            zuletztGespeichert = notizen.create("x".repeat(anzahl));
        } catch (DomainException e) {
            fehler = e;
        }
        return self();
    }

    public NotizenStage ich_eine_Notiz_mit_$_Leerzeichen_speichere(int anzahl) {
        try {
            zuletztGespeichert = notizen.create(" ".repeat(anzahl));
        } catch (DomainException e) {
            fehler = e;
        }
        return self();
    }

    public NotizenStage zeigt_die_Liste_die_Notiz_mit_$_Zeichen(int anzahl) {
        assertThat(notizen.list().stream().map(Note::text).toList()).contains("x".repeat(anzahl));
        return self();
    }

    public NotizenStage ich_loesche_die_Notiz_$(String text) {
        long id =
                notizen.list().stream()
                        .filter(note -> note.text().equals(text))
                        .findFirst()
                        .orElseThrow()
                        .id();
        notizen.delete(id);
        return self();
    }

    public NotizenStage ich_loesche_eine_unbekannte_Notiz() {
        try {
            notizen.delete(4711);
        } catch (DomainException e) {
            fehler = e;
        }
        return self();
    }

    public NotizenStage die_Liste_zeigt_die_Notizen_$_in_dieser_Reihenfolge(String... texte) {
        assertThat(notizen.list().stream().map(Note::text).toList()).isEqualTo(List.of(texte));
        return self();
    }

    public NotizenStage die_Liste_ist_leer() {
        assertThat(notizen.list()).isEmpty();
        return self();
    }

    @As("die Notiz hat einen Zeitstempel")
    public NotizenStage die_Notiz_hat_einen_Zeitstempel() {
        assertThat(zuletztGespeichert.createdAt()).isNotNull();
        return self();
    }

    public NotizenStage die_Eingabe_wird_abgelehnt() {
        assertThat(fehler).isNotNull();
        assertThat(fehler.kind()).isEqualTo(DomainException.Kind.INVALID);
        return self();
    }

    public NotizenStage wird_die_Eingabe_abgelehnt() {
        assertThat(fehler).isNotNull();
        assertThat(fehler.kind()).isEqualTo(DomainException.Kind.INVALID);
        return self();
    }

    public NotizenStage die_Notiz_wird_nicht_gefunden() {
        assertThat(fehler).isNotNull();
        assertThat(fehler.kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
        return self();
    }

    @Pending
    public NotizenStage das_Eingabefeld_ist_leer() {
        return self();
    }

    @Pending
    public NotizenStage ich_$_Zeichen_in_das_Eingabefeld_tippe(int anzahl) {
        return self();
    }

    @Pending
    @As("zeigt das Formular „12/280“ unter dem Textfeld")
    public NotizenStage das_Formular_zeigt_den_Zaehler() {
        return self();
    }

    @Pending
    @As("ich eine Notiz mit mehr als 280 Zeichen speichern möchte")
    public NotizenStage ich_eine_zu_lange_Notiz_speichern_moechte() {
        return self();
    }

    @Pending
    public NotizenStage zeigt_die_App_eine_verstaendliche_Fehlermeldung() {
        return self();
    }
}
