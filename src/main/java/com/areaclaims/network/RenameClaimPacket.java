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
 * Client -> Server: GUI-Editor-Äquivalent von {@code /areaclaims rename} (ROADMAP.md Phase
 * 6-Nachtrag 2, Punkt 3 "Claim umbenennen") - identifiziert den Claim per UUID (aus dem
 * synchronisierten Snapshot, siehe {@link ClaimEditorSnapshot}), funktioniert also für Haupt-
 * UND Unterbereiche gleichermaßen (der Chat-Befehl kann nur Hauptbereiche direkt nach Namen
 * finden, siehe {@code AreaClaimsCommands#findEditableClaim}).
 */
public record RenameClaimPacket(String claimId, String newName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameClaimPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "rename_claim"));

    public static final StreamCodec<ByteBuf, RenameClaimPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, RenameClaimPacket::claimId,
        ByteBufCodecs.STRING_UTF8, RenameClaimPacket::newName,
        RenameClaimPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenameClaimPacket packet, IPayloadContext context) {
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

            ClaimEditService.Result result = ClaimEditService.renameClaim(caller, claim, packet.newName());
            switch (result) {
                case OK -> {
                    caller.displayClientMessage(Component.translatable("areaclaims.command.rename.success", packet.newName()), true);
                    ClaimSnapshotBuilder.sendTo(caller);
                }
                case INVALID_NAME -> caller.displayClientMessage(Component.translatable("areaclaims.command.rename.invalid_name"), true);
                case NAME_TAKEN -> caller.displayClientMessage(Component.translatable("areaclaims.command.rename.name_taken"), true);
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                default -> {}
            }
        });
    }
}
