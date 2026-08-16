package com.areaclaims.client.data;

import com.areaclaims.client.gui.AreaClaimsDisplayPrefsScreen;
import com.areaclaims.display.PlayerDisplayPreferences;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Hält die zuletzt vom Server empfangenen {@link PlayerDisplayPreferences} des lokalen Spielers für
 * den {@link AreaClaimsDisplayPrefsScreen} (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15) - gleiches
 * "bei jedem Empfang frisch öffnen"-Muster wie {@link ClientServerConfigCache} (siehe dortigen
 * Klassenkommentar für die Begründung, auch für {@link #parentScreen}s "klebriges" Verhalten -
 * Punkt 3, Nachtrag 6).
 */
public final class ClientDisplayPrefsCache {

    private static final Gson GSON = new Gson();
    private static PlayerDisplayPreferences prefs = PlayerDisplayPreferences.defaults();
    private static Screen parentScreen;

    private ClientDisplayPrefsCache() {}

    public static void openFrom(Screen parent) {
        parentScreen = parent;
    }

    public static void updateAndOpen(String json) {
        try {
            PlayerDisplayPreferences parsed = GSON.fromJson(json, PlayerDisplayPreferences.class);
            prefs = parsed != null ? parsed : PlayerDisplayPreferences.defaults();
        } catch (RuntimeException e) {
            prefs = PlayerDisplayPreferences.defaults();
        }
        Minecraft.getInstance().setScreen(new AreaClaimsDisplayPrefsScreen(parentScreen));
    }

    public static PlayerDisplayPreferences get() {
        return prefs;
    }
}
