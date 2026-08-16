package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
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
 * Client -> Server: GUI-Editor-Äquivalent von {@code /areaclaims delete <Claim>} - siehe
 * {@link com.areaclaims.commands.AreaClaimsCommands}. Ruft dieselbe
 * {@link ClaimEditService#deleteClaim} auf wie der Chat-Befehl. Der GUI-seitige "Löschen"-Button
 * hat sein eigenes Klick-zum-Bestätigen (siehe AreaClaimsEditorScreen) - dieses Paket wird erst
 * beim BESTÄTIGTEN zweiten Klick verschickt, hier gibt es keine weitere Bestätigungslogik mehr.
 */
public record DeleteClaimPacket(String claimId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteClaimPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "delete_claim"));

    public static final StreamCodec<ByteBuf, DeleteClaimPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(DeleteClaimPacket::new, DeleteClaimPacket::claimId);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeleteClaimPacket packet, IPayloadContext context) {
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

            String claimName = claim.name();
            ClaimEditService.Result result = ClaimEditService.deleteClaim(caller, claim);
            switch (result) {
                case OK -> {
                    caller.displayClientMessage(Component.translatable("areaclaims.command.claim.deleted", claimName), true);
                    ClaimSnapshotBuilder.sendTo(caller);
                }
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                default -> {}
            }
        });
    }
}
