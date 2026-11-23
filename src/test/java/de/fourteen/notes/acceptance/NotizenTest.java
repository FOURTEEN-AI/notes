package de.fourteen.notes.acceptance;

import com.tngtech.jgiven.annotation.Description;
import com.tngtech.jgiven.junit5.ScenarioTest;
import org.junit.jupiter.api.Test;

/** Acceptance tests of the notes. The description is the scenario title (the report shows it, not the method name). */
class NotizenTest extends ScenarioTest<NotizenStage, NotizenStage, NotizenStage> {

    @Test
    @Description("Eine Notiz speichern")
    void eine_Notiz_speichern() {
        when().ich_speichere_die_Notiz_$("Milch kaufen");
        then()
                .die_Liste_zeigt_die_Notizen_$_in_dieser_Reihenfolge("Milch kaufen")
                .and()
                .die_Notiz_hat_einen_Zeitstempel();
    }

    @Test
    @Description("Die neueste Notiz steht oben")
    void die_neueste_Notiz_steht_oben() {
        given().eine_Notiz_$_existiert("erste");
        when().ich_speichere_die_Notiz_$("zweite");
        then().die_Liste_zeigt_die_Notizen_$_in_dieser_Reihenfolge("zweite", "erste");
    }

    @Test
    @Description("Eine leere Notiz wird abgelehnt")
    void eine_leere_Notiz_wird_abgelehnt() {
        when().ich_speichere_eine_leere_Notiz();
        then().die_Eingabe_wird_abgelehnt().and().die_Liste_ist_leer();
    }

    @Test
    @Description("Eine Notiz löschen")
    void eine_Notiz_loeschen() {
        given().eine_Notiz_$_existiert("Milch kaufen");
        when().ich_loesche_die_Notiz_$("Milch kaufen");
        then().die_Liste_ist_leer();
    }

    @Test
    @Description("Eine unbekannte Notiz löschen")
    void eine_unbekannte_Notiz_loeschen() {
        when().ich_loesche_eine_unbekannte_Notiz();
        then().die_Notiz_wird_nicht_gefunden();
    }
}
