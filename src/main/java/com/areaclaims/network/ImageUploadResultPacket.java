package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server -> Client: Antwort auf {@link ImageUploadPacket} - entweder der zugewiesene Inhalts-Hash
 * (siehe {@code ServerImageStore}) plus die tatsächlichen (bereits skalierten) Bildmaße, oder ein
 * Übersetzungsschlüssel, der erklärt, warum der Upload abgelehnt wurde.
 */
public record ImageUploadResultPacket(boolean success, String hashOrErrorKey, int width, int height) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImageUploadResultPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_upload_result"));

    public static final StreamCodec<ByteBuf, ImageUploadResultPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ImageUploadResultPacket::success,
        ByteBufCodecs.STRING_UTF8, ImageUploadResultPacket::hashOrErrorKey,
        ByteBufCodecs.VAR_INT, ImageUploadResultPacket::width,
        ByteBufCodecs.VAR_INT, ImageUploadResultPacket::height,
        ImageUploadResultPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendSuccess(ServerPlayer player, String hash, int width, int height) {
        PacketDistributor.sendToPlayer(player, new ImageUploadResultPacket(true, hash, width, height));
    }

    public static void sendFailure(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new ImageUploadResultPacket(false, translationKey, 0, 0));
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar - der Verweis auf
    // client-only Code hier wird auf dem Dedicated Server nie ausgeführt (S2C-Paket).
    public static void handle(ImageUploadResultPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.gui.AreaClaimsImagePickerScreen.onUploadResult(packet));
    }
}
