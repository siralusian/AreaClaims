package com.areaclaims.client.data;

import com.areaclaims.client.gui.AreaClaimsImageUsageScreen;
import com.areaclaims.network.ImageUsageEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält die zuletzt vom Server empfangene Bild-Nutzungsübersicht (Punkt 8, Nachtrag 5) - gleiches
 * "bei jedem Empfang frisch öffnen"-Muster wie {@link ClientServerConfigCache} (siehe dortigen
 * Klassenkommentar, auch für {@link #parentScreen}s "klebriges" Verhalten - Punkt 3, Nachtrag 6).
 * Diese Übersicht öffnet sich mehrfach neu innerhalb DERSELBEN Sitzung (nach jeder Löschung, siehe
 * {@code DeleteImagePacket}) - ohne den klebrigen Elternteil würde "Zurück" nach einer Löschung auf
 * den gerade verlassenen (veralteten) Übersicht-Screen selbst zeigen statt zum Editor.
 */
public final class ClientImageUsageCache {

    private static final Gson GSON = new Gson();
    private static List<ImageUsageEntry> entries = new ArrayList<>();
    private static Screen parentScreen;

    private ClientImageUsageCache() {}

    public static void openFrom(Screen parent) {
        parentScreen = parent;
    }

    public static void update(String json) {
        try {
            List<ImageUsageEntry> parsed = GSON.fromJson(json, new TypeToken<List<ImageUsageEntry>>() {}.getType());
            entries = parsed != null ? parsed : new ArrayList<>();
        } catch (RuntimeException e) {
            entries = new ArrayList<>();
        }
        Minecraft.getInstance().setScreen(new AreaClaimsImageUsageScreen(parentScreen));
    }

    public static List<ImageUsageEntry> get() {
        return entries;
    }
}
