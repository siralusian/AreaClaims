package com.areaclaims.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Gson-Transport-DTO (gleicher Stil wie {@link ShowcaseGeometrySnapshot}) für ALLE aktuell
 * existierenden Claims (Haupt- UND Unterbereiche), öffentlich für JEDEN Online-Spieler - im
 * Gegensatz zu {@link ShowcaseGeometrySnapshot} (nur die vom jeweiligen Betrachter aktiv gezeigten
 * Claims im TINT-Modus) ist das hier eine komplette, immer aktuelle Übersichtskarte.
 *
 * <p><b>Hintergrund (2026-08-17):</b> ersetzt den früheren Versuch, Claim-Grenzen über JourneyMaps
 * eigene {@code IServerOverlayAPI} direkt an den Client zu pushen - dieser Weg erwies sich nach
 * ausführlicher Untersuchung (Server ruft {@code overlayApi.show()} nachweislich fehlerfrei auf,
 * der Client-seitige {@code ClientPacketHandler.onOverlayShow()} wird aber NIE aufgerufen, per
 * eigenem Diagnose-Mixin bestätigt, reproduziert über JourneyMap 6.0.0-beta.81 UND 6.0.5) als
 * offenbar echter Bug in JourneyMaps eigener Server-Overlay-Funktion. Nutzer-Vorgabe: nach Create's
 * Vorbild (eigenes {@code IClientPlugin} + eigene Datenübertragung statt JourneyMaps
 * Server-API) umbauen - dieses Paket überträgt die rohen Claim-Daten über UNSER eigenes,
 * nachweislich funktionierendes Netzwerksystem; das eigentliche JourneyMap-Overlay baut der Client
 * anschließend selbst per {@code IClientAPI#show}, siehe {@code AreaClaimsJourneyMapClientPlugin}.
 */
public class ClaimMapSnapshot {

    public static class ClaimEntry {
        public String id;
        public String dimension;
        public String name;
        /** {@code Claim#effectiveBoundaryColor()} - 0xRRGGBB. */
        public int color;
        public boolean isMain;
        /** Liste unabhängiger Teile, jeder Teil eine Liste von [x,z]-Punkten. */
        public List<List<int[]>> parts;
    }

    public List<ClaimEntry> claims = new ArrayList<>();
}
