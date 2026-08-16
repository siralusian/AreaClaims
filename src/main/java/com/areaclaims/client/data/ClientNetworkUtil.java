package com.areaclaims.client.data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Zentrale Prüfung, ob ein AreaClaims-Payload gerade an den Server gesendet werden darf. 1:1
 * portiert aus {@code com.cobblecompanion.client.data.ClientNetworkUtil} (siehe
 * CobbleCompanion/docs/API_EXTENSIONS.md Abschnitt 5): {@code registrar.optional()} bei der
 * Registrierung allein reicht NICHT aus, um einen Sende-Versuch an eine Verbindung zu erlauben,
 * die diesen konkreten Kanal nicht ausgehandelt hat - jeder Kanal wird pro Verbindung UNABHÄNGIG
 * von den anderen negoziiert. Deshalb hier keine einmalige globale Prüfung, sondern eine, die
 * JEDES Payload einzeln per {@link NetworkRegistry#hasChannel} gegen die aktuelle Verbindung
 * prüft, bevor gesendet wird - verhindert eine UnsupportedOperationException/Client-Crash bei
 * einem Server ohne (passende) AreaClaims-Version.
 */
public final class ClientNetworkUtil {

    private ClientNetworkUtil() {}

    /** true, wenn eine Verbindung besteht UND der Server DIESES Payload für sie ausgehandelt hat. */
    public static boolean canSendToServer(ResourceLocation payloadId) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, payloadId);
    }

    // Verhindert Chat-Spam bei wiederholten Klicks auf blockierte Aktionen - merkt sich die
    // Verbindung, für die zuletzt gewarnt wurde, und warnt pro Verbindung nur einmal.
    private static ClientPacketListener lastWarnedConnection = null;

    /** Wie canSendToServer(), zeigt aber beim ERSTEN Fehlschlag pro Verbindung einen Chat-Hinweis. */
    public static boolean canSendToServerOrWarn(ResourceLocation payloadId) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && NetworkRegistry.hasChannel(connection, payloadId)) {
            return true;
        }
        if (connection != lastWarnedConnection) {
            lastWarnedConnection = connection;
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("areaclaims.msg.server_feature_unavailable"), false);
            }
        }
        return false;
    }
}
