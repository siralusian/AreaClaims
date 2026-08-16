package com.areaclaims.data;

import com.areaclaims.claim.Claim;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verwaltet, welche BEREITS COMMITTETEN Claims sich ein Spieler gerade als Partikel-Grenzen
 * anzeigen lässt ({@code /areaclaims show <Name>} bzw. der "Grenzen anzeigen"-Button im
 * GUI-Editor, siehe ROADMAP.md Phase 6-Nachtrag "Mehrere Showcases"). Rein im Speicher (wie
 * {@link SelectionManager}) - geht bei Abmelden/Neustart verloren, für eine reine Visualisierung
 * unproblematisch.
 *
 * <p><b>Redesign (Nutzer-Vorgabe):</b> war ursprünglich "ein Claim pro Spieler, neue Auswahl
 * ersetzt die alte" - jetzt ein STANDORT-bezogenes SET pro Spieler: mehrere Claims können
 * gleichzeitig angezeigt werden (z. B. Claim A und B nebeneinander), jeder in seiner eigenen
 * Farbe (siehe {@code Claim#effectiveBoundaryColor()} - die Farbe selbst wird NICHT hier
 * gespeichert, sondern beim Rendern live vom jeweiligen Claim gelesen, damit eine nachträgliche
 * Farbänderung sofort wirkt). {@code toggle(...)} schaltet EINEN Claim im Set um, ohne die
 * anderen zu beeinflussen. Zustand ist bewusst PRO ANSCHAUENDEM SPIELER getrennt - ein OP4, der
 * fremde Claims prüft, beeinflusst nicht, was der jeweilige Besitzer selbst gerade angezeigt
 * bekommt, und umgekehrt (jeder Spieler hat sein eigenes, unabhängiges Set).
 *
 * <p><b>Anzeigemodus (ROADMAP.md Phase 6-Nachtrag 2, Punkt 7 "Block-Einfärbung"):</b> zusätzlich
 * zum Set der aktiven Claims trägt diese Klasse jetzt auch PRO SPIELER, welcher der zwei
 * Anzeigestile gerade genutzt wird - {@link DisplayMode#PARTICLES} (der ursprüngliche
 * Partikel-Umriss) oder {@link DisplayMode#TINT} (neu: halbtransparente Block-Einfärbung, siehe
 * {@code com.areaclaims.client.BoundaryTintRenderer}). Default PARTICLES (unverändertes
 * Verhalten für jeden, der den Modus nie umschaltet).
 */
public final class ClaimShowcaseManager {

    public enum DisplayMode { PARTICLES, TINT }

    private static final Map<UUID, Set<UUID>> ACTIVE = new HashMap<>();
    private static final Map<UUID, DisplayMode> MODE = new HashMap<>();
    /**
     * Persönliche Grenzfarbe-Überschreibung PRO BETRACHTER (ROADMAP.md Phase 7-Nachtrag, Punkt 5
     * "Einstellungen"-Popup) - fehlt ein Eintrag (Normalfall), sieht der Betrachter die vom
     * Claim-Besitzer konfigurierte Farbe ({@link Claim#effectiveBoundaryColor()}); ist ein Eintrag
     * vorhanden, sieht NUR DIESER Betrachter stattdessen seine eigene Wunschfarbe - wirkt sich
     * NICHT auf andere Betrachter oder den Besitzer selbst aus. Siehe {@link #effectiveColorFor}.
     */
    private static final Map<UUID, Integer> COLOR_OVERRIDE = new HashMap<>();

    private ClaimShowcaseManager() {}

    /** @return der neue Zustand für GENAU diesen Claim bei diesem Spieler (true = jetzt an). */
    public static boolean toggle(UUID player, UUID claimId) {
        Set<UUID> active = ACTIVE.computeIfAbsent(player, k -> new HashSet<>());
        if (active.remove(claimId)) {
            if (active.isEmpty()) ACTIVE.remove(player);
            return false;
        }
        active.add(claimId);
        return true;
    }

    /**
     * Fügt einen Claim NUR hinzu, falls er noch nicht angezeigt wird - im Gegensatz zu
     * {@link #toggle} niemals ausschaltend (ROADMAP.md Phase 7, Punkt 7 "Neuer SubClaim zeigt
     * Eltern-/Geschwister-Grenzen an" - dortiger Aufrufer will garantiert AN, nicht umschalten).
     */
    public static void ensureShown(UUID player, UUID claimId) {
        ACTIVE.computeIfAbsent(player, k -> new HashSet<>()).add(claimId);
    }

    public static Set<UUID> activeClaimIds(UUID player) {
        return ACTIVE.getOrDefault(player, Collections.emptySet());
    }

    public static DisplayMode mode(UUID player) {
        return MODE.getOrDefault(player, DisplayMode.PARTICLES);
    }

    /** @return der neue Modus (Umschalt-Zyklus PARTICLES -> TINT -> PARTICLES). */
    public static DisplayMode cycleMode(UUID player) {
        DisplayMode next = mode(player) == DisplayMode.PARTICLES ? DisplayMode.TINT : DisplayMode.PARTICLES;
        MODE.put(player, next);
        return next;
    }

    /** Schaltet ALLE aktiven Showcases dieses Spielers aus (genutzt von {@code /areaclaims cancel}). */
    public static void stop(UUID player) {
        ACTIVE.remove(player);
        MODE.remove(player);
        // Bewusst NICHT COLOR_OVERRIDE.remove(player) - eine persönliche Farbvorliebe ist eine
        // dauerhaftere Einstellung als "was zeige ich mir gerade an", sollte einen /areaclaims
        // cancel also überleben (anders als Anzeigemodus, der eng an das Betrachten selbst gekoppelt ist).
    }

    /** {@code null} = keine persönliche Überschreibung (Normalfall). */
    public static Integer colorOverride(UUID viewer) {
        return COLOR_OVERRIDE.get(viewer);
    }

    public static void setColorOverride(UUID viewer, Integer colorOrNull) {
        if (colorOrNull == null) {
            COLOR_OVERRIDE.remove(viewer);
        } else {
            COLOR_OVERRIDE.put(viewer, colorOrNull);
        }
    }

    /** Tatsächlich zu verwendende Grenzfarbe für DIESEN Betrachter - persönliche Überschreibung, falls vorhanden, sonst die vom Claim-Besitzer konfigurierte Farbe. */
    public static int effectiveColorFor(UUID viewer, Claim claim) {
        Integer override = COLOR_OVERRIDE.get(viewer);
        return override != null ? override : claim.effectiveBoundaryColor();
    }
}
