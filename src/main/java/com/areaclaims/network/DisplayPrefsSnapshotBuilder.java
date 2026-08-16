package com.areaclaims.network;

import com.areaclaims.display.PlayerDisplayPreferences;
import com.areaclaims.display.PlayerDisplayPreferencesManager;
import com.google.gson.Gson;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Baut/verschickt den {@link DisplayPrefsSyncPacket} für genau EINEN Spieler - analog zu {@link ServerConfigSnapshotBuilder}. */
public final class DisplayPrefsSnapshotBuilder {

    private static final Gson GSON = new Gson();

    private DisplayPrefsSnapshotBuilder() {}

    /** Kanal-geprüft (siehe Klassenkommentar in AreaClaims.java) - nur an genau diesen Spieler, nur seine EIGENEN Einstellungen. */
    public static void sendTo(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, DisplayPrefsSyncPacket.TYPE.id())) {
            return;
        }
        PlayerDisplayPreferences prefs = PlayerDisplayPreferencesManager.get(player.getUUID());
        String json = GSON.toJson(prefs);
        PacketDistributor.sendToPlayer(player, new DisplayPrefsSyncPacket(json));
    }
}
