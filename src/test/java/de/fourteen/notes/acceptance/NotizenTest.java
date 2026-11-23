package de.fourteen.notes.acceptance;

import com.tngtech.jgiven.annotation.Description;
import com.tngtech.jgiven.annotation.Pending;
import com.tngtech.jgiven.junit5.ScenarioTest;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests of the notes. The description is the scenario title (the report shows it, not
 * the method name).
 */
class NotizenTest extends ScenarioTest<NotizenStage, NotizenStage, NotizenStage> {

    @Test
    @Description("Eine Notiz speichern")
    void eine_Notiz_speichern() {
        when().ich_speichere_die_Notiz_$("Milch kaufen");
        then().die_Liste_zeigt_die_Notizen_$_in_dieser_Reihenfolge("Milch kaufen")
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

    @Test
    @Pending
    @Manuell("Nur das Frontend zeigt das Eingabefeld")
    @Description("Das Eingabefeld ist nach dem Speichern leer")
    void das_Eingabefeld_ist_nach_dem_Speichern_leer() {
        when().ich_speichere_die_Notiz_$("Milch kaufen");
        then().das_Eingabefeld_ist_leer();
    }

    @Test
    @Description("Eine Notiz mit genau 280 Zeichen wird gespeichert")
    void eine_Notiz_mit_genau_280_Zeichen_wird_gespeichert() {
        when().ich_eine_Notiz_mit_$_Zeichen_speichere(280);
        then().die_Liste_zeigt_die_Notiz_mit_$_Zeichen(280);
    }

    @Test
    @Description("Eine zu lange Notiz wird abgelehnt")
    void eine_zu_lange_Notiz_wird_abgelehnt() {
        when().ich_eine_Notiz_mit_$_Zeichen_speichere(281);
        then().die_Eingabe_wird_als_zu_lang_abgelehnt().and().die_Liste_ist_leer();
    }

    @Test
    @Description("Eine Notiz nur aus Leerzeichen zählt als leer")
    void eine_Notiz_nur_aus_Leerzeichen_zaehlt_als_leer() {
        when().ich_eine_Notiz_aus_$_Leerzeichen_speichere(300);
        then().die_Eingabe_wird_abgelehnt().and().die_Liste_ist_leer();
    }

    @Test
    @Pending
    @Manuell("Nur das Frontend zeigt den Zeichenzähler")
    @Description("Der Zeichenzähler zeigt die Länge der Eingabe")
    void der_Zeichenzaehler_zeigt_die_Laenge_der_Eingabe() {
        when().ich_$_ins_Eingabefeld_schreibe("Milch kaufen");
        then().der_Zaehler_zeigt_$("12/280");
    }

    @Test
    @Pending
    @Manuell("Nur das Frontend zeigt die Fehlermeldung und behält den Text")
    @Description("Die Fehlermeldung bei zu langer Notiz ist verständlich")
    void die_Fehlermeldung_bei_zu_langer_Notiz_ist_verstaendlich() {
        when().ich_eine_Notiz_mit_$_Zeichen_speichern_will(281);
        then().ich_sehe_die_Meldung_$("Die Notiz ist zu lang (höchstens 280 Zeichen).")
                .and()
                .der_Text_bleibt_im_Eingabefeld();
    }
}
