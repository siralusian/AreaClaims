package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.image.ImageMetadata;
import com.areaclaims.image.ServerImageStore;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: "schick mir die durchstöberbare Galerie aller bereits hochgeladenen Bilder"
 * (Punkt 6, Nachtrag 4 - der explizit von CopycatSign fehlende Teil, siehe
 * {@code ServerImageStore#getAll}-Klassenkommentar). Ausgelöst beim Öffnen des Bild-Auswahl-Screens
 * (siehe {@code AreaClaimsImagePickerScreen}).
 */
public record ImageGalleryRequestPacket() implements CustomPacketPayload {

    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<ImageGalleryRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_gallery_request"));

    public static final StreamCodec<ByteBuf, ImageGalleryRequestPacket> CODEC =
        StreamCodec.unit(new ImageGalleryRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImageGalleryRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<ImageMetadata> all = new ArrayList<>(ServerImageStore.getAll());
            PacketDistributor.sendToPlayer(player, new ImageGallerySyncPacket(GSON.toJson(all)));
        });
    }
}
