package com.areaclaims.network;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.google.gson.Gson;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Baut den {@link ShowcaseGeometrySnapshot} und verschickt ihn als {@link ShowcaseGeometrySyncPacket} - analog zu {@link ClaimSnapshotBuilder}. */
public final class ShowcaseGeometrySnapshotBuilder {

    private static final Gson GSON = new Gson();

    private ShowcaseGeometrySnapshotBuilder() {}

    public static ShowcaseGeometrySnapshot build(ServerPlayer player) {
        ShowcaseGeometrySnapshot snapshot = new ShowcaseGeometrySnapshot();
        UUID viewer = player.getUUID();
        // Nur im TINT-Modus tatsächlich Geometrie schicken - im Partikel-Modus bleibt die Liste
        // leer, das räumt automatisch einen eventuell noch vorhandenen clientseitigen Cache-Rest
        // (z. B. direkt nach dem Umschalten von TINT zurück auf PARTICLES).
        if (ClaimShowcaseManager.mode(viewer) != ClaimShowcaseManager.DisplayMode.TINT) {
            return snapshot;
        }
        snapshot.rangeBlocks = com.areaclaims.data.FeatureConfigManager.tintRangeChunks() * 16;
        for (UUID claimId : ClaimShowcaseManager.activeClaimIds(viewer)) {
            Claim claim = ClaimManager.getById(claimId);
            if (claim == null) continue;
            ShowcaseGeometrySnapshot.ClaimGeometry geometry = new ShowcaseGeometrySnapshot.ClaimGeometry();
            geometry.id = claim.id().toString();
            geometry.color = ClaimShowcaseManager.effectiveColorFor(viewer, claim);
            geometry.parts = claim.parts().stream()
                .map(part -> part.stream().map(v -> new int[] {v.x(), v.z()}).collect(Collectors.toList()))
                .collect(Collectors.toList());
            snapshot.claims.add(geometry);
        }
        return snapshot;
    }

    /** Kanal-geprüft (siehe Klassenkommentar in AreaClaims.java) - nur an genau diesen Spieler. */
    public static void sendTo(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, ShowcaseGeometrySyncPacket.TYPE.id())) {
            return;
        }
        String json = GSON.toJson(build(player));
        PacketDistributor.sendToPlayer(player, new ShowcaseGeometrySyncPacket(json));
    }
}
