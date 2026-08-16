package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.StakingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: "Beenden"-Button im Preisbestätigung-Popup (ROADMAP.md Phase 8). Siehe {@link StakingService#discardPending}. */
public record DiscardPendingActionPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiscardPendingActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "discard_pending_action"));

    public static final StreamCodec<ByteBuf, DiscardPendingActionPacket> CODEC =
        StreamCodec.unit(new DiscardPendingActionPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DiscardPendingActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caller) {
                StakingService.discardPending(caller);
            }
        });
    }
}
