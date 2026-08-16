package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: die vom öffnenden Spieler besessenen Claims (Mitglieder + Regeln), als
 * JSON-String (siehe {@link ClaimEditorSnapshot}-Klassenkommentar für die Begründung). Wird
 * einmal direkt vor {@code OpenEditorPacket} geschickt (siehe AreaClaimsCommands#openEditor) und
 * erneut nach jeder erfolgreichen Mutation über {@link SetRolePacket}/{@link SetRulePacket}, damit
 * der offene Editor den neuen Stand zeigt.
 *
 * <p><b>Nachtrag 7, Punkt 3:</b> wächst mit der Anzahl Claims/Mitgliedern/Regeln eines Spielers -
 * bekommt wie {@link ShowcaseGeometrySyncPacket} eine großzügigere, aber weiterhin endliche eigene
 * Obergrenze statt Vanillas knappem {@link ByteBufCodecs#STRING_UTF8}-Standard (32767 Zeichen),
 * siehe dortigen Klassenkommentar für die vollständige Begründung.
 */
public record ClaimSyncPacket(String claimsJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "claim_sync"));

    private static final int MAX_JSON_LENGTH = 500_000;

    public static final StreamCodec<ByteBuf, ClaimSyncPacket> CODEC =
        ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH).map(ClaimSyncPacket::new, ClaimSyncPacket::claimsJson);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar - der Verweis auf
    // client-only Code hier wird auf dem Dedicated Server nie ausgeführt (S2C-Paket).
    public static void handle(ClaimSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.data.ClientClaimCache.update(packet.claimsJson()));
    }
}
