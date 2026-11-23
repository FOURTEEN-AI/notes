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
        then().zeigt_die_Liste_die_Notiz_mit_$_Zeichen(280);
    }

    @Test
    @Description("Eine zu lange Notiz wird abgelehnt")
    void eine_zu_lange_Notiz_wird_abgelehnt() {
        when().ich_eine_Notiz_mit_$_Zeichen_speichere(281);
        then().wird_die_Eingabe_abgelehnt().and().die_Liste_ist_leer();
    }

    @Test
    @Description("Eine nur aus Leerzeichen bestehende Notiz bleibt leer")
    void eine_nur_aus_Leerzeichen_bestehende_Notiz_bleibt_leer() {
        when().ich_eine_Notiz_mit_$_Leerzeichen_speichere(300);
        then().wird_die_Eingabe_abgelehnt().and().die_Liste_ist_leer();
    }

    @Test
    @Pending
    @Manuell("Nur das Frontend zeigt den Zeichenzähler")
    @Description("Der Zeichenzähler zeigt die Länge der Eingabe")
    void der_Zeichenzaehler_zeigt_die_Laenge_der_Eingabe() {
        when().ich_$_Zeichen_in_das_Eingabefeld_tippe(12);
        then().das_Formular_zeigt_den_Zaehler();
    }

    @Test
    @Pending
    @Manuell("Nur das Frontend zeigt die Fehlermeldung")
    @Description("Die App meldet eine zu lange Notiz verständlich")
    void die_App_meldet_eine_zu_lange_Notiz_verstaendlich() {
        when().ich_eine_zu_lange_Notiz_speichern_moechte();
        then().zeigt_die_App_eine_verstaendliche_Fehlermeldung();
    }
}
