package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.data.ClaimShowcaseManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: schaltet den Anzeigemodus (Partikel-Umriss vs. Block-Einfärbung, ROADMAP.md
 * Phase 6-Nachtrag 2, Punkt 7) für den sendenden Spieler um ({@link ClaimShowcaseManager#cycleMode}) -
 * unabhängig von {@code /areaclaims show}: betrifft ALLE aktuell aktiven Showcases dieses Spielers
 * gleichzeitig (ein Anzeigestil pro Spieler, kein Mix aus beidem). Schickt danach immer einen
 * frischen {@link ShowcaseGeometrySnapshot} (leer, wenn jetzt PARTICLES aktiv ist - räumt den
 * clientseitigen Tint-Cache; gefüllt, wenn jetzt TINT aktiv ist).
 */
public record SetShowcaseModePacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetShowcaseModePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_showcase_mode"));

    public static final StreamCodec<ByteBuf, SetShowcaseModePacket> CODEC =
        StreamCodec.unit(new SetShowcaseModePacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetShowcaseModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;
            ClaimShowcaseManager.cycleMode(caller.getUUID());
            ShowcaseGeometrySnapshotBuilder.sendTo(caller);
            // Auch den Claim-Snapshot neu schicken, damit ClaimEditorSnapshot#showcaseMode (für
            // die Button-Beschriftung im Editor, siehe AreaClaimsEditorScreen) aktuell bleibt.
            ClaimSnapshotBuilder.sendTo(caller);
        });
    }
}
