package com.areaclaims.network;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.data.FeatureConfigManager;
import com.google.gson.Gson;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.stream.Collectors;

/** Baut den {@link ClaimMapSnapshot} und verschickt ihn als {@link ClaimMapSyncPacket} - siehe dortigen Klassenkommentar. */
public final class ClaimMapSnapshotBuilder {

    private static final Gson GSON = new Gson();

    private ClaimMapSnapshotBuilder() {}

    public static ClaimMapSnapshot build() {
        ClaimMapSnapshot snapshot = new ClaimMapSnapshot();
        if (!FeatureConfigManager.journeyMapIntegrationEnabled()) return snapshot;
        for (Claim claim : ClaimManager.findAllClaims()) {
            ClaimMapSnapshot.ClaimEntry entry = new ClaimMapSnapshot.ClaimEntry();
            entry.id = claim.id().toString();
            entry.dimension = claim.dimension();
            entry.name = claim.name();
            entry.color = claim.effectiveBoundaryColor();
            entry.isMain = claim.isMain();
            entry.parts = claim.parts().stream()
                .map(part -> part.stream().map(v -> new int[] {v.x(), v.z()}).collect(Collectors.toList()))
                .collect(Collectors.toList());
            snapshot.claims.add(entry);
        }
        return snapshot;
    }

    /** Kanal-geprüft (siehe Klassenkommentar in AreaClaims.java) - nur an genau diesen Spieler. */
    public static void sendTo(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, ClaimMapSyncPacket.TYPE.id())) {
            return;
        }
        String json = GSON.toJson(build());
        PacketDistributor.sendToPlayer(player, new ClaimMapSyncPacket(json));
    }

    /** An ALLE aktuell online Spieler - für Claim-Änderungen ({@link ClaimManager#save}), siehe dortigen Klassenkommentar. */
    public static void sendToAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        String json = GSON.toJson(build());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, ClaimMapSyncPacket.TYPE.id())) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new ClaimMapSyncPacket(json));
        }
    }
}
