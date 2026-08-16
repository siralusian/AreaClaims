package com.areaclaims.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-weite Freischaltungsschwellen (ROADMAP.md Phase 6 - Nutzer-Vorgabe: "Default AUS für
 * alle außer OP-Stufe 4, die immer vollen Zugriff hat, unabhängig von der Konfiguration"). Gleiche
 * OP-Stufen-Schwellen-Konvention wie CreativeMenus {@code minOpLevelAddRemove}/... (statt echter
 * Rollen/Gruppen oder einer eigenen Erlaubnisliste - bewusst einfach gehalten, siehe
 * Klassen-Entscheidung in ROADMAP.md). Jeder Schwellenwert wird auf [0,4] geklemmt (siehe
 * {@link #clamp}) - dadurch erfüllt {@code player.hasPermissions(4)} (= echte Vanilla-OP-Stufe 4)
 * IMMER jede mögliche Schwelle, ganz ohne separate "ist OP4"-Sonderprüfung: OP4 hat also
 * automatisch und unumgehbar immer Zugriff, exakt wie gefordert.
 */
public final class FeatureConfigManager {

    private static class Data {
        // minOpLevelUseTool wurde mit ROADMAP.md Phase 7 ("Klick-zum-Abstecken", goldene Hacke
        // komplett entfernt) ersatzlos gestrichen - alte gespeicherte JSON-Dateien mit diesem Feld
        // werden von Gson beim Laden einfach ignoriert (unbekannte Property), kein Migrationscode nötig.
        int minOpLevelCreateClaim = 4;
        int minOpLevelExpandClaim = 4;
        /** Eigene Schwelle statt den Preis (NONE = kostenlos) als "Feature aus" zu missbrauchen - sonst wäre ein Regel-Freikauf ohne gesetzten Preis unbeabsichtigt für jeden Claim-Besitzer kostenlos verfügbar. */
        int minOpLevelBuyout = 4;
        /**
         * Nutzer-Vorgabe (ROADMAP.md Phase 6-Nachtrag "Teile-Obergrenze"): EIN gemeinsamer
         * Deckel für die Anzahl Territoriums-Teile - gilt für Haupt- UND Unterbereiche GLEICH
         * (eigene Entscheidung: zwei getrennte Werte hätten die Konfiguration nur unnötig
         * verkompliziert, ohne einen klaren Anwendungsfall für unterschiedliche Deckel zu haben).
         * Default 8 - großzügig genug für normale Nutzung, aber nicht unbegrenzt (verhindert z. B.
         * Performance-Probleme durch tausende Chunk-Index-Einträge für einen einzelnen Claim).
         */
        int maxClaimParts = 8;
        /**
         * Sichtweite der Block-Einfärbung-Anzeige (TINT-Modus, siehe {@code BoundaryTintRenderer})
         * in CHUNKS um den Betrachter (ROADMAP.md Phase 6-Nachtrag 4 - ersetzt den vorher fest
         * verdrahteten 20-Block-Radius). Default 2 Chunks (= 32 Blöcke horizontal UND vertikal,
         * siehe {@code BoundaryTintRenderer}-Klassenkommentar) - Nutzer-Vorschlag war "2-3 Chunks";
         * die untere Grenze dieser Spanne wurde als Default gewählt, weil die neue Rendering-Technik
         * (volle 6-seitige Flächen-Kultierung statt nur einer Ebene) pro Block teurer ist als vorher
         * - ein zu großzügiger Default hätte das Performance-Risiko unnötig erhöht. Admins mit mehr
         * Bedarf können hochsetzen, siehe {@link #setTintRangeChunks}-Klemmung [1,8].
         */
        int tintRangeChunks = 2;
        /**
         * Punkt 1 (Nachtrag 4): die frühere, in Nachtrag 3 ersatzlos gestrichene "Wildnis"-Meldung
         * beim VERLASSEN eines Claims kehrt zurück, jetzt aber admin-konfigurierbar (an/aus + freier
         * Text) statt fest verdrahtet. Default AN mit demselben Wortlaut wie früher, damit ein
         * frischer Server sich unverändert wie vor Nachtrag 3 verhält.
         */
        boolean wildernessMessageEnabled = true;
        String wildernessMessageText = "Wildnis";
        /**
         * Nutzer-Vorgabe (2026-08-16, JourneyMap-Integration): Default AN, damit ein Server mit
         * installiertem JourneyMap die neue Funktion ohne weiteres Zutun bekommt (siehe
         * {@code com.areaclaims.integration.JourneyMapBridge}) - Admins, die das NICHT wollen,
         * können es hier gezielt abschalten. Wirkt nur, wenn JourneyMap überhaupt geladen ist
         * (siehe {@code ModAvailability#isJourneyMapAvailable}), ansonsten ohnehin folgenlos.
         */
        boolean journeyMapIntegrationEnabled = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private FeatureConfigManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("areaclaims_feature_config.json");
        load();
    }

    public static boolean canCreateClaim(ServerPlayer player) {
        return player.hasPermissions(data.minOpLevelCreateClaim);
    }

    public static boolean canExpandClaim(ServerPlayer player) {
        return player.hasPermissions(data.minOpLevelExpandClaim);
    }

    public static boolean canBuyoutRule(ServerPlayer player) {
        return player.hasPermissions(data.minOpLevelBuyout);
    }

    public static int minOpLevelCreateClaim() { return data.minOpLevelCreateClaim; }
    public static int minOpLevelExpandClaim() { return data.minOpLevelExpandClaim; }
    public static int minOpLevelBuyout() { return data.minOpLevelBuyout; }
    public static int maxClaimParts() { return data.maxClaimParts; }
    public static int tintRangeChunks() { return data.tintRangeChunks; }
    public static boolean wildernessMessageEnabled() { return data.wildernessMessageEnabled; }
    public static String wildernessMessageText() { return data.wildernessMessageText; }
    public static boolean journeyMapIntegrationEnabled() { return data.journeyMapIntegrationEnabled; }

    public static void setMinOpLevelCreateClaim(int level) { data.minOpLevelCreateClaim = clamp(level); save(); }
    public static void setMinOpLevelExpandClaim(int level) { data.minOpLevelExpandClaim = clamp(level); save(); }
    public static void setMinOpLevelBuyout(int level) { data.minOpLevelBuyout = clamp(level); save(); }
    public static void setMaxClaimParts(int max) { data.maxClaimParts = Math.max(1, max); save(); }
    public static void setTintRangeChunks(int chunks) { data.tintRangeChunks = Math.max(1, Math.min(8, chunks)); save(); }
    public static void setWildernessMessageEnabled(boolean enabled) { data.wildernessMessageEnabled = enabled; save(); }
    public static void setWildernessMessageText(String text) { data.wildernessMessageText = text == null ? "" : text; save(); }
    public static void setJourneyMapIntegrationEnabled(boolean enabled) { data.journeyMapIntegrationEnabled = enabled; save(); }

    private static int clamp(int level) {
        return Math.max(0, Math.min(4, level));
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
