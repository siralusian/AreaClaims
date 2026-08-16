package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.StakingService;
import com.areaclaims.data.ActiveSelectionManager;
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
 * Client -> Server: Klick auf "Erweitern"/"Neuer SubClaim"/"Neuer Claim" im Editor (ROADMAP.md
 * Phase 7 "Klick-zum-Abstecken") - {@code targetClaimId} leer = {@link ActiveSelectionManager.ActionType#NEW_CLAIM}.
 * Ruft NUR {@link StakingService#begin} auf - siehe dortigen Klassenkommentar für den vollen Ablauf.
 */
public record BeginSelectionPacket(String action, String targetClaimId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BeginSelectionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "begin_selection"));

    public static final StreamCodec<ByteBuf, BeginSelectionPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, BeginSelectionPacket::action,
        ByteBufCodecs.STRING_UTF8, BeginSelectionPacket::targetClaimId,
        BeginSelectionPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BeginSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;

            ActiveSelectionManager.ActionType action;
            try {
                action = ActiveSelectionManager.ActionType.valueOf(packet.action());
            } catch (IllegalArgumentException e) {
                return;
            }
            UUID targetClaimId = null;
            if (!packet.targetClaimId().isEmpty()) {
                try {
                    targetClaimId = UUID.fromString(packet.targetClaimId());
                } catch (IllegalArgumentException e) {
                    return;
                }
            }

            StakingService.BeginResult result = StakingService.begin(caller, action, targetClaimId);
            switch (result) {
                case NOT_AUTHORIZED -> caller.displayClientMessage(Component.translatable("areaclaims.command.no_permission"), true);
                case TARGET_NOT_FOUND -> caller.displayClientMessage(Component.translatable("areaclaims.command.claim.not_found", "?"), true);
                default -> {}
            }
        });
    }
}
