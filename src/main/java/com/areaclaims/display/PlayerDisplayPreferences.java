package com.areaclaims.display;

/**
 * Persönliche, EIGENE Anzeige-Einstellungen jedes Spielers für Claim-/SubClaim-Namen und die
 * Willkommensnachricht (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15 "Betreten-Nachricht-Overhaul").
 *
 * <p><b>Architektur-Umbau (Nutzer-Vorgabe):</b> Anzeigedauer war bisher pro CLAIM vom Besitzer
 * festgelegt ({@code Claim#titleDurationTicks}/{@code welcomeDurationTicks}) - das ist jetzt eine
 * rein PERSÖNLICHE, pro-BETRACHTER geltende Einstellung (jeder Spieler sieht Namen/Willkommens-
 * nachrichten mit SEINER EIGENEN bevorzugten Dauer/Position, unabhängig davon, wessen Claim er
 * betritt). Der Claim-Besitzer behält weiterhin Namenstext, Titelfarbe, Grenzfarbe sowie
 * Willkommenstext+-farbe (siehe {@code AreaClaimsEditorScreen#buildNameEditPopup}/{@code
 * buildWelcomeEditPopup} - die Dauer-Felder wurden dort entfernt).
 *
 * <p><b>Position ist bewusst IMMER als Versatz zur BILDSCHIRM-MITTE gespeichert</b> (nicht oben-
 * links, Nutzer-Vorgabe) - {@code posX}/{@code posY} werden vom Renderer zu
 * {@code screenWidth/2 + posX}/{@code screenHeight/2 + posY} addiert, siehe
 * {@code com.areaclaims.client.EntryDisplayOverlayRenderer}.
 *
 * <p><b>Wichtiger Judgment-Call (im Abschlussbericht dokumentiert):</b> Vanillas Titel/Untertitel/
 * Actionbar-Pakete unterstützen KEINE Positionierung (immer bildschirmmittig fest verdrahtet,
 * verifiziert in den dekompilierten Vanilla-Sourcen) - eine konfigurierbare Position wäre über sie
 * grundsätzlich NIEMALS umsetzbar gewesen, auch nicht im "nicht-dauerhaften" Modus. Statt zwei
 * parallele Anzeigewege zu pflegen (Vanilla-Pakete für "normal", eigenes Overlay nur für
 * "dauerhaft" - wie ursprünglich skizziert), rendert jetzt EIN EINHEITLICHES eigenes Overlay
 * (siehe {@code EntryDisplayOverlayRenderer}) BEIDE Fälle: "dauerhaft" bleibt sichtbar bis der
 * Bereich verlassen/gewechselt wird, "nicht dauerhaft" blendet nach {@code durationTicks}
 * automatisch aus. Das ist eine strikte Erweiterung (Position funktioniert jetzt überall, auch für
 * die Willkommensnachricht, die gar keinen Dauerhaft-Umschalter hat) statt einer Einschränkung.
 */
public class PlayerDisplayPreferences {

    /** Zeit-Einheit nur für die Zyklus-Taste in der GUI - intern wird IMMER in Ticks gespeichert/gerechnet. */
    public enum DurationUnit { TICKS, SECONDS, MINUTES;

        public DurationUnit next() {
            return switch (this) {
                case TICKS -> SECONDS;
                case SECONDS -> MINUTES;
                case MINUTES -> TICKS;
            };
        }
    }

    /**
     * Punkt 3 (Nachtrag 4): Text-Stil-Kontrolle je Text-Typ (Größe/Fett/Kursiv/Unterstrichen/
     * Schriftart/Schatten/Umriss) - EIGENE {@link TextStyle}-Instanz je Sektion (Haupt-/Unterbereich/
     * Willkommensnachricht), rein clientseitig ausgewertet ({@code EntryDisplayOverlayRenderer}
     * liest sie direkt aus dem lokalen {@code ClientDisplayPrefsCache} statt sie über
     * {@code EntryDisplaySlotPacket} zu übertragen - Stil ist eine reine Betrachter-Einstellung,
     * der Server muss sie nicht mitschicken, der Client kennt seine eigenen Einstellungen bereits).
     */
    public static class TextStyle {
        public float scale = 1.0f;
        public boolean bold;
        public boolean italic;
        public boolean underline;
        public boolean shadow = true;
        /** Manueller Mehrfach-Durchlauf-Umriss (kein natives Vanilla-Textrender-Feature, siehe EntryDisplayOverlayRenderer-Klassenkommentar). */
        public boolean outline;
        /** Eine der bekannten eingebauten Vanilla-Schriftarten (siehe Minecraft#DEFAULT_FONT/UNIFORM_FONT/ALT_FONT) - kein Versuch, Ressourcenpaket-Schriften zu erraten/aufzulisten. */
        public String font = "minecraft:default";
    }

    /** Gemeinsame Felder für Haupt-/Unterbereichsnamen: Dauer+Einheit, normale Position, Dauerhaft-Umschalter + EIGENE Dauerhaft-Position, Text-Stil, Bild-Anzeige. */
    public static class NameDisplayPrefs {
        public int durationTicks;
        public String durationUnit = DurationUnit.TICKS.name();
        public int posX;
        public int posY;
        public boolean permanent;
        public int permanentPosX;
        public int permanentPosY;
        public TextStyle style = new TextStyle();
        /**
         * Punkt 4 (Nachtrag 5, Layout-Überarbeitung - ERSETZT das vorherige Design aus Nachtrag 4):
         * "Bild statt Text zeigen" für die ZEITGESTEUERTE Phase - EIGENER Umschalter je Haupt-/
         * Unterbereich. Nur wirksam, wenn der Claim-Besitzer diesem Claim überhaupt ein Bild
         * zugewiesen hat ({@code Claim#imageHash}), sonst bleibt es beim Textnamen.
         */
        public boolean showImageTimed = true;
        /**
         * Punkt 4 (Nachtrag 5): eigenständiges GEGENSTÜCK zu {@link #showImageTimed}, aber für die
         * DAUERHAFT-Phase - Nutzer-Vorgabe hat sich gegenüber Nachtrag 4 präzisiert: statt EINES
         * globalen, für Haupt- UND Unterbereich gemeinsamen Umschalters (das vorherige
         * {@code PlayerDisplayPreferences#permanentShowImage}, in dieser Runde ersatzlos entfernt)
         * bekommt JEDER Namens-Typ jetzt SEIN EIGENES Paar aus Timed-/Permanent-Bild-Umschaltern -
         * exakt die vom Nutzer in Nachtrag 5 vorgegebene Layout-Zeilenfolge (siehe
         * AreaClaimsDisplayPrefsScreen#buildNameSection).
         */
        public boolean showImagePermanent = true;
        /** Zoom in PROZENT relativ zur festen Basis-Höhe (siehe {@code ImageUploadPacket#BASELINE_HEIGHT}) - Nachtrag 5, Punkt 10: ganzzahlig, 1-500, Default 100 (= Basisgröße), vorher ein roher Float-Multiplikator ab 1.0. */
        public int imageZoomPercent = 100;
        /**
         * Nachtrag 6 (Bild-Qualität-Fund): {@code false} (Default) = glatte {@code GL_LINEAR}-
         * Filterung (siehe {@code ClientImageManager}-Klassenkommentar - das war der eigentliche
         * "Pixel Suppe"-Fehler, NICHT die Quellbild-Qualität). {@code true} = bewusstes Opt-in auf
         * hartes Nächster-Nachbar-Sampling für Betrachter, die den blockigen Retro-Look explizit
         * wollen (Vorbild: Immersive Paintings' "Pixel Kunst"-Umschalter, NUR das KONZEPT übernommen,
         * kein Code - siehe ClientImageManager-Klassenkommentar für die Lizenz-Abgrenzung).
         */
        public boolean pixelArtFiltering = false;
    }

    /** Willkommensnachricht: Dauer+Einheit + EINE Position - bewusst KEIN Dauerhaft-Umschalter (Nutzer-Vorgabe: nicht ungefragt hinzufügen). */
    public static class WelcomeDisplayPrefs {
        public int durationTicks;
        public String durationUnit = DurationUnit.TICKS.name();
        public int posX;
        public int posY;
        public TextStyle style = new TextStyle();
    }

    public NameDisplayPrefs mainClaim = defaultMain();
    public NameDisplayPrefs subClaim = defaultSub();
    public WelcomeDisplayPrefs welcome = defaultWelcome();

    /** Default identisch zur alten claim-eigenen Vorgabe (70 Ticks, siehe altes {@code Claim#titleDurationTicks}) - unverändertes Verhalten für Spieler, die nie eigene Einstellungen vornehmen. */
    public static NameDisplayPrefs defaultMain() {
        NameDisplayPrefs p = new NameDisplayPrefs();
        p.durationTicks = 70;
        p.posX = 0;
        p.posY = -40;
        p.permanentPosX = 0;
        p.permanentPosY = -60;
        // 2x Skalierung = unverändertes Verhalten ggü. der vorherigen, fest verdrahteten
        // pose.scale(2f,2f,1f) (siehe altes EntryDisplayOverlayRenderer, vor Punkt 3 Nachtrag 4).
        p.style.scale = 2.0f;
        return p;
    }

    /** Etwas tiefer als der Hauptbereichsname voreingestellt (wie vorher Titel/Untertitel übereinander), damit beide gleichzeitig lesbar bleiben, ohne dass der Spieler das erst selbst einstellen muss. */
    public static NameDisplayPrefs defaultSub() {
        NameDisplayPrefs p = new NameDisplayPrefs();
        p.durationTicks = 70;
        p.posX = 0;
        p.posY = -20;
        p.permanentPosX = 0;
        p.permanentPosY = -40;
        p.style.scale = 2.0f;
        return p;
    }

    /** Default identisch zur alten claim-eigenen Vorgabe (60 Ticks, siehe altes {@code Claim#welcomeDurationTicks}). */
    public static WelcomeDisplayPrefs defaultWelcome() {
        WelcomeDisplayPrefs p = new WelcomeDisplayPrefs();
        p.durationTicks = 60;
        p.posX = 0;
        p.posY = 40;
        return p;
    }

    public static PlayerDisplayPreferences defaults() {
        PlayerDisplayPreferences prefs = new PlayerDisplayPreferences();
        prefs.mainClaim = defaultMain();
        prefs.subClaim = defaultSub();
        prefs.welcome = defaultWelcome();
        return prefs;
    }
}
