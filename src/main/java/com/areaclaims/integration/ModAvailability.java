package com.areaclaims.integration;

import net.neoforged.fml.ModList;

/**
 * Laufzeit-Erkennung von CobbleCompanion/CobbleDollars - 1:1 das Muster aus
 * {@code InvSpy/.../integration/ModAvailability.java} bzw. CobbleCompanion/docs/API_EXTENSIONS.md
 * Abschnitt 4.1. Referenziert bewusst NUR den Mod-ID-String, NIE eine Klasse von CobbleCompanion,
 * damit diese Prüfung selbst nie an einem fehlenden CobbleCompanion scheitern kann.
 * {@link #refresh()} wird defensiv in {@code AreaClaims#onServerStarting} aufgerufen.
 */
public final class ModAvailability {

    private static boolean cobbleCompanion = false;
    private static boolean cobbleDollars = false;
    private static boolean journeyMap = false;

    private ModAvailability() {}

    public static void refresh() {
        // Reiner Mod-ID-String-Check, NIE eine JourneyMap-Klasse referenziert (siehe Klassenkommentar)
        // - JourneyMapBridge selbst wird nur berührt, wenn dieser Wert bereits true ist.
        journeyMap = ModList.get().isLoaded("journeymap");
        // "cobblecompanion_everything" ist die interne Alles-in-einer-Datei-Testvariante fuer den
        // eigenen Server (siehe CobbleCompanion-Everything/gradle.properties) - enthaelt denselben
        // com.cobblecompanion.api.*-Code unter anderer Mod-ID. Ohne diesen zweiten Check wuerde die
        // Bruecke auf dem echten Server (der cobblecompanion_everything statt cobblecompanion faehrt)
        // still deaktiviert bleiben, obwohl die API tatsaechlich vorhanden ist.
        cobbleCompanion = ModList.get().isLoaded("cobblecompanion")
            || ModList.get().isLoaded("cobblecompanion_everything");
        // Kurzschluss-Auswertung wichtig: CobbleCompanionBridge (und damit
        // com.cobblecompanion.api.*) wird NUR geladen, wenn cobbleCompanion bereits true ist.
        cobbleDollars = cobbleCompanion && CobbleCompanionBridge.isCobbleDollarsAvailable();
    }

    public static boolean isCobbleCompanionAvailable() {
        return cobbleCompanion;
    }

    /** Ob eine CobbleDollars-Preis-Option überhaupt angeboten werden darf (siehe ROADMAP.md Phase 6). */
    public static boolean isCobbleDollarsAvailable() {
        return cobbleDollars;
    }

    /** Ob JourneyMap installiert ist (server- und/oder clientseitig) - siehe {@code integration.journeymap.JourneyMapBridge}. */
    public static boolean isJourneyMapAvailable() {
        return journeyMap;
    }
}
