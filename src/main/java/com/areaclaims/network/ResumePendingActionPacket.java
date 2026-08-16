package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.StakingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: "Abbrechen"-Button im Preisbestätigung-Popup (ROADMAP.md Phase 8). Siehe {@link StakingService#resumePending}. */
public record ResumePendingActionPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResumePendingActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "resume_pending_action"));

    public static final StreamCodec<ByteBuf, ResumePendingActionPacket> CODEC =
        StreamCodec.unit(new ResumePendingActionPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResumePendingActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caller) {
                StakingService.resumePending(caller);
            }
        });
    }
}
