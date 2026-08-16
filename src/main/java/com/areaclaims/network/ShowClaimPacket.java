package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.data.ClaimShowcaseManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: GUI-Editor-Äquivalent von {@code /areaclaims show <Claim>} - SCHALTET die
 * Partikel-Grenzen-Vorschau für einen BEREITS COMMITTETEN Claim um (kein Timer mehr, siehe
 * {@link ClaimShowcaseManager}, gerendert von {@link com.areaclaims.event.ToolInteractionListener}).
 * Rein visuell, keine Datenänderung - deshalb kein Sync-Snapshot danach nötig.
 */
public record ShowClaimPacket(String claimId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowClaimPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "show_claim"));

    public static final StreamCodec<ByteBuf, ShowClaimPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(ShowClaimPacket::new, ShowClaimPacket::claimId);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowClaimPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;

            Claim claim;
            try {
                claim = ClaimManager.getById(UUID.fromString(packet.claimId()));
            } catch (IllegalArgumentException e) {
                return;
            }
            if (claim == null) {
                caller.displayClientMessage(Component.translatable("areaclaims.command.claim.not_found", packet.claimId()), true);
                return;
            }
            if (!ClaimEditService.canEdit(caller, claim)) {
                caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                return;
            }
            boolean nowOn = ClaimShowcaseManager.toggle(caller.getUUID(), claim.id());
            caller.displayClientMessage(Component.translatable(
                nowOn ? "areaclaims.command.show.started" : "areaclaims.command.show.stopped", claim.name()), true);
            // Geometrie neu schicken (nur relevant/nicht-leer im TINT-Modus, siehe
            // ShowcaseGeometrySnapshotBuilder) - die aktive Claim-Menge hat sich gerade geändert.
            ShowcaseGeometrySnapshotBuilder.sendTo(caller);
        });
    }
}
