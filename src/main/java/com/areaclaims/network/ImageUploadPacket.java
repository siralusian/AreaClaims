package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.image.ImageMetadata;
import com.areaclaims.image.ServerImageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Client -> Server: über mehrere Pakete verteilter Upload eines Claim-Bilds (ROADMAP.md Phase
 * 7-Nachtrag 4, Punkt 6). Modelliert auf CopycatSigns {@code ImageUploadPayload} (siehe
 * {@link ServerImageStore}-Klassenkommentar für die "eigenständige Parallel-Implementierung"-
 * Begründung) - ein einzelner Upload wird clientseitig in mehrere dieser Pakete zerlegt (siehe
 * {@link ChunkSender}), hier über {@link SegmentManager} wieder zusammengesetzt, dann erst nach dem
 * letzten Segment dekodiert/validiert/gespeichert.
 *
 * <p><b>Punkt 6 (feste Basis-Höhe):</b> JEDES hochgeladene Bild wird HIER, beim Upload, auf eine
 * feste Höhe von {@link #BASELINE_HEIGHT}px herunter-/hochskaliert (Breite proportional) - "for
 * consistent baseline display across all viewers" (Nutzer-Vorgabe). Die gespeicherten
 * {@link ImageMetadata#width}/{@link ImageMetadata#height} sind bereits die SKALIERTEN Maße.
 */
public record ImageUploadPacket(byte[] data, int segment, int totalSegments) implements CustomPacketPayload {

    /** Punkt 6 (Nachtrag 4, Nutzer-Vorgabe: "Auto-resize every uploaded image to a fixed 256px HEIGHT"). */
    /** Nachtrag 5, Punkt 9: nach echtem Live-Test von 256 auf 128px reduziert - 256 war deutlich zu groß. */
    public static final int BASELINE_HEIGHT = 128;
    /** Verteidigung in der Tiefe gegen absurd große Uploads, BEVOR irgendetwas dekodiert wird. */
    private static final int MAX_UPLOAD_BYTES = 12 * 1024 * 1024;

    public static final CustomPacketPayload.Type<ImageUploadPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_upload"));

    public static final StreamCodec<ByteBuf, ImageUploadPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE_ARRAY, ImageUploadPacket::data,
        ByteBufCodecs.VAR_INT, ImageUploadPacket::segment,
        ByteBufCodecs.VAR_INT, ImageUploadPacket::totalSegments,
        ImageUploadPacket::new);

    private static final SegmentManager SEGMENTS = new SegmentManager();

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImageUploadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String key = player.getStringUUID();
            SEGMENTS.handleSegmentedPayload(key, packet.data(), packet.segment(), packet.totalSegments())
                .ifPresent(bytes -> finish(player, bytes));
        });
    }

    private static void finish(ServerPlayer player, byte[] bytes) {
        if (bytes.length > MAX_UPLOAD_BYTES) {
            AreaClaims.LOGGER.warn("rejected AreaClaims image upload from {}: {} bytes exceeds the limit", player.getGameProfile().getName(), bytes.length);
            ImageUploadResultPacket.sendFailure(player, "areaclaims.image.upload.too_large");
            return;
        }

        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            original = null;
        }
        if (original == null) {
            AreaClaims.LOGGER.warn("rejected AreaClaims image upload from {}: not a readable image", player.getGameProfile().getName());
            ImageUploadResultPacket.sendFailure(player, "areaclaims.image.upload.invalid");
            return;
        }

        BufferedImage resized = resizeToBaselineHeight(original);
        byte[] resizedBytes;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", out);
            resizedBytes = out.toByteArray();
        } catch (IOException e) {
            AreaClaims.LOGGER.error("could not re-encode resized AreaClaims image upload", e);
            ImageUploadResultPacket.sendFailure(player, "areaclaims.image.upload.invalid");
            return;
        }

        String hash = sha256Hex(resizedBytes);
        if (ServerImageStore.get(hash).isEmpty()) {
            ServerImageStore.fileCache().set(hash, resizedBytes);
            ServerImageStore.put(new ImageMetadata(hash, resized.getWidth(), resized.getHeight(), player.getUUID(), player.getGameProfile().getName()));
        }
        ImageUploadResultPacket.sendSuccess(player, hash, resized.getWidth(), resized.getHeight());
    }

    /** Höhe fest auf {@link #BASELINE_HEIGHT}, Breite proportional (Seitenverhältnis erhalten) - siehe Klassenkommentar "Punkt 6". */
    private static BufferedImage resizeToBaselineHeight(BufferedImage source) {
        int targetHeight = BASELINE_HEIGHT;
        int targetWidth = Math.max(1, Math.round(source.getWidth() * (targetHeight / (float) source.getHeight())));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
