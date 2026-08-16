package com.areaclaims.display;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistiert {@link PlayerDisplayPreferences} pro Spieler (ROADMAP.md Phase 7-Nachtrag 3, Punkte
 * 13-15). Gson-JSON im Weltordner, EINE Datei mit einer {@code UUID -> PlayerDisplayPreferences}-
 * Abbildung - bewusst dasselbe "eine Datei, interne Map"-Muster wie
 * {@link com.areaclaims.economy.PriceConfigManager} (Regel-Freikaufpreise dort ebenfalls über einen
 * String-Schlüssel in EINER Datei), statt einer Datei PRO Spieler - konsistent mit dem einzigen
 * bereits in diesem Mod etablierten Persistenz-Stil (dieser Mod hat bislang keine
 * "eine-Datei-pro-Spieler"-Vorlage, an der man sich stattdessen hätte orientieren können).
 */
public final class PlayerDisplayPreferencesManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path dataFile;
    private static final Map<UUID, PlayerDisplayPreferences> prefs = new HashMap<>();

    private PlayerDisplayPreferencesManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("areaclaims_display_prefs.json");
        load();
    }

    /** Nie {@code null} - liefert bei fehlendem Eintrag frische Defaults (siehe {@link PlayerDisplayPreferences#defaults()}), OHNE sie zu speichern (reine Lesevorgabe für Spieler, die die Einstellungen nie geöffnet haben). */
    public static PlayerDisplayPreferences get(UUID player) {
        PlayerDisplayPreferences existing = prefs.get(player);
        return existing != null ? existing : PlayerDisplayPreferences.defaults();
    }

    public static void set(UUID player, PlayerDisplayPreferences value) {
        prefs.put(player, value);
        save();
    }

    private static void load() {
        prefs.clear();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null || data.players == null) return;
            data.players.forEach((uuidStr, value) -> {
                try {
                    prefs.put(UUID.fromString(uuidStr), value);
                } catch (IllegalArgumentException ignored) {}
            });
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        Data data = new Data();
        prefs.forEach((uuid, value) -> data.players.put(uuid.toString(), value));
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }

    private static class Data {
        Map<String, PlayerDisplayPreferences> players = new HashMap<>();
    }
}
