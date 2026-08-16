package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> Client: Antwort auf {@link ImageUsageRequestPacket} - JSON-Liste von {@link ImageUsageEntry}. */
public record ImageUsageSyncPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImageUsageSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_usage_sync"));

    public static final StreamCodec<ByteBuf, ImageUsageSyncPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(ImageUsageSyncPacket::new, ImageUsageSyncPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar.
    public static void handle(ImageUsageSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.data.ClientImageUsageCache.update(packet.json()));
    }
}
