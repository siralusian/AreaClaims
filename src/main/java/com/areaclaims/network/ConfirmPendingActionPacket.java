package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.StakingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: "Bestätigen"-Button im Preisbestätigung-Popup (ROADMAP.md Phase 8). Siehe {@link StakingService#confirmPending}. */
public record ConfirmPendingActionPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConfirmPendingActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "confirm_pending_action"));

    public static final StreamCodec<ByteBuf, ConfirmPendingActionPacket> CODEC =
        StreamCodec.unit(new ConfirmPendingActionPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfirmPendingActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caller) {
                StakingService.confirmPending(caller);
            }
        });
    }
}
