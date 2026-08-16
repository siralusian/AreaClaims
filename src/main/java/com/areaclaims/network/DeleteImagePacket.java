package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.event.ClaimEntryListener;
import com.areaclaims.image.ServerImageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: löscht ein hochgeladenes Bild endgültig (Punkt 2, Nachtrag 6 - ergänzt die in
 * Nachtrag 5 Punkt 8 bewusst auf reine Sichtbarkeit beschränkte {@code AreaClaimsImageUsageScreen}
 * um die tatsächliche Lösch-Funktion). OP4-only, wie jede andere Admin-Mutation dieses Mods. Räumt
 * zusätzlich alle Claims auf, die dieses Bild gerade zugewiesen hatten (statt eine tote Referenz
 * herumliegen zu lassen, auch wenn das rendering-seitig ohnehin schon sicher auf Text zurückfällt)
 * UND stößt {@link ClaimEntryListener#invalidateAll()} an, damit Betrachter, die gerade in einem
 * betroffenen Claim stehen, sofort auf den Textnamen zurückfallen statt bis zum nächsten
 * Verlassen+Wiederbetreten weiter ein totes Bild zu "zeigen" (in Wahrheit nur den letzten Frame,
 * in dem die Textur noch geladen war - siehe ClaimEntryListener-Klassenkommentar "Punkt 1").
 */
public record DeleteImagePacket(String hash) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteImagePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "delete_image"));

    public static final StreamCodec<ByteBuf, DeleteImagePacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(DeleteImagePacket::new, DeleteImagePacket::hash);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeleteImagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(4)) return;
            String hash = packet.hash();
            if (hash == null || hash.isBlank()) return;

            boolean anyCleared = false;
            for (Claim claim : ClaimManager.findAllClaims()) {
                if (hash.equals(claim.imageHash())) {
                    claim.setImageHash("");
                    anyCleared = true;
                }
            }
            if (anyCleared) ClaimManager.save();

            ServerImageStore.delete(hash);
            ClaimEntryListener.invalidateAll();

            // Bild-Nutzungsübersicht dieses Admins gleich frisch neu schicken, damit das gelöschte
            // Bild sofort aus seiner eigenen Liste verschwindet.
            ImageUsageRequestPacket.sendTo(player);
        });
    }
}
