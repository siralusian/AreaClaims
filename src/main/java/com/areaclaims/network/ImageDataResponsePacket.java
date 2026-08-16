package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Antwort auf {@link ImageRequestPacket} - die angefragten PNG-Bytes, in
 * derselben Weise verteilt wie ein Upload (siehe {@link ChunkSender}). {@code totalSegments == 0}
 * (mit leeren Daten) bedeutet "kein solches Bild gespeichert" - der Client soll dann NICHT ewig
 * warten, sondern auf einen Platzhalter zurückfallen.
 */
public record ImageDataResponsePacket(String hash, byte[] data, int segment, int totalSegments) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImageDataResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_data_response"));

    public static final StreamCodec<ByteBuf, ImageDataResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ImageDataResponsePacket::hash,
        ByteBufCodecs.BYTE_ARRAY, ImageDataResponsePacket::data,
        ByteBufCodecs.VAR_INT, ImageDataResponsePacket::segment,
        ByteBufCodecs.VAR_INT, ImageDataResponsePacket::totalSegments,
        ImageDataResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, String hash, byte[] data) {
        if (data == null) {
            PacketDistributor.sendToPlayer(player, new ImageDataResponsePacket(hash, new byte[0], 0, 0));
            return;
        }
        int total = ChunkSender.totalSegments(data);
        for (int segment = 0; segment < total; segment++) {
            PacketDistributor.sendToPlayer(player, new ImageDataResponsePacket(hash, ChunkSender.segment(data, segment), segment, total));
        }
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar.
    public static void handle(ImageDataResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.ClientImageManager.handleResponse(packet));
    }
}
