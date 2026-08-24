package com.areaclaims.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Gson-Transport-DTO (gleicher Stil wie {@link ShowcaseGeometrySnapshot}) für die Block-
 * Einfärbung-Vorschau des "Anpassen"-Modus (Nutzer-Vorgabe 2026-08-18: die Partikel-Linie war
 * "teilweise sehr verwirrend", stattdessen Block-Einfärbung NUR für "Anpassen" - NICHT die
 * allgemeine Grenzanzeige, die bleibt unverändert Partikel/Tint je Spieler-Einstellung).
 *
 * <p>Anders als {@link ShowcaseGeometrySnapshot} (beliebig viele gleichzeitig gezeigte Claims) gibt
 * es hier höchstens EINEN aktiven "Haupt"-Eintrag - den gerade angepassten Claim. {@code parts} leer =
 * "Anpassen" gerade nicht aktiv (räumt den clientseitigen Cache automatisch).
 *
 * <p>{@link #nearbyClaims} (Nutzer-Vorgabe 2026-08-19, "Claims anderer Spieler am Boden mit
 * einfärben") - Claims anderer Spieler in der Nähe des angepassten Claims, jeweils in ihrer eigenen
 * Farbe, damit man beim Erweitern/Verkleinern sieht, wo fremde Claims liegen. Wiederverwendet
 * {@link ShowcaseGeometrySnapshot.ClaimGeometry} (gleiches id/color/parts-Format).
 */
public class AdjustPreviewSnapshot {

    /** {@code Claim#effectiveBoundaryColor()} - 0xRRGGBB. */
    public int color;
    /** Die KANDIDATEN-Geometrie (Original-Teile + alle bisher umgeschalteten Spalten) - siehe {@code PolygonUtil#applyToggleSetToParts}. Leer = inaktiv. */
    public List<List<int[]>> parts = new ArrayList<>();
    /** Claims anderer Spieler in der Nähe (siehe Klassenkommentar) - leer, solange "Anpassen" inaktiv ist. */
    public List<ShowcaseGeometrySnapshot.ClaimGeometry> nearbyClaims = new ArrayList<>();
}
