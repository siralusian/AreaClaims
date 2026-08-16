package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.image.ServerImageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Client -> Server: "schick mir die Pixeldaten dieses Bildes" (nach Hash, siehe
 * {@code ServerImageStore}). Der Client kennt anfangs nur den Hash (z. B. aus dem Claim-Snapshot,
 * siehe {@code ClaimEditorSnapshot#imageHash}), nicht die Bytes - die werden erst bei Bedarf
 * angefragt, nicht proaktiv an jeden Spieler geschickt.
 */
public record ImageRequestPacket(String hash) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImageRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_request"));

    public static final StreamCodec<ByteBuf, ImageRequestPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(ImageRequestPacket::new, ImageRequestPacket::hash);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImageRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String hash = packet.hash();
            Optional<byte[]> data = ServerImageStore.get(hash).isPresent()
                ? ServerImageStore.fileCache().get(hash)
                : Optional.empty();
            ImageDataResponsePacket.send(player, hash, data.orElse(null));
        });
    }
}
