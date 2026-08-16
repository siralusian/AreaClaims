package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> Client: die vollständige Bild-Galerie als JSON-Liste von {@code ImageMetadata} - siehe {@link ImageGalleryRequestPacket}. */
public record ImageGallerySyncPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImageGallerySyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_gallery_sync"));

    public static final StreamCodec<ByteBuf, ImageGallerySyncPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(ImageGallerySyncPacket::new, ImageGallerySyncPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar.
    public static void handle(ImageGallerySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.data.ClientImageGalleryCache.update(packet.json()));
    }
}
