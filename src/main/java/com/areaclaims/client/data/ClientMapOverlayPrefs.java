package com.areaclaims.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rein lokale, spielerseitige Ein/Aus-Einstellung für die JourneyMap-Claim-Overlays (Nutzer-Vorgabe
 * 2026-08-17: "so wie es Create auch macht" - eigener Umschalt-Knopf auf der Vollbildkarte, siehe
 * {@code MapOverlayToggleWidget}). Getrennt vom Server-Admin-Schalter
 * ({@code FeatureConfigManager#journeyMapIntegrationEnabled}) - das hier ist reine Spieler-Präferenz,
 * nichts Server-Autoritatives, deshalb bewusst lokal im Client-Konfig-Ordner statt im Welt-Speicher
 * (gleiches Muster wie {@code com.cobblecompanion.creativemenu.client.tabs.ClientTabConfigManager}).
 */
public final class ClientMapOverlayPrefs {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        boolean showOnMap = true;
    }

    private static Data data;

    private ClientMapOverlayPrefs() {}

    public static boolean showOnMap() {
        if (data == null) load();
        return data.showOnMap;
    }

    public static void toggle() {
        if (data == null) load();
        data.showOnMap = !data.showOnMap;
        save();
    }

    private static void load() {
        Path file = configFile();
        if (!Files.exists(file)) {
            data = new Data();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Data parsed = GSON.fromJson(reader, Data.class);
            data = parsed != null ? parsed : new Data();
        } catch (IOException | JsonSyntaxException e) {
            data = new Data();
        }
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(configFile())) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("areaclaims_map_overlay_client.json");
    }
}
