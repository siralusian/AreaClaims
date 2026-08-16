package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.image.ImageMetadata;
import com.areaclaims.image.ServerImageStore;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client -> Server: "schick mir eine Übersicht, welche Claims welches hochgeladene Bild nutzen"
 * (Punkt 8, Nachtrag 5 - Admin-Aufräum-Hilfe). OP4-only, wie alle anderen Admin-Übersichten dieses
 * Mods. Baut für JEDES in {@link ServerImageStore#getAll()} bekannte Bild die Liste der Claims
 * (Haupt- ODER Unterbereich, server-WEIT über {@link ClaimManager#findAllClaims()} - nicht nur
 * eigene) auf, deren {@link Claim#imageHash()} exakt diesem Hash entspricht - Bilder ohne jeden
 * Treffer erscheinen mit leerer Nutzungsliste (der Screen zeigt sie dann als "ungenutzt" an).
 */
public record ImageUsageRequestPacket() implements CustomPacketPayload {

    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<ImageUsageRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "image_usage_request"));

    public static final StreamCodec<ByteBuf, ImageUsageRequestPacket> CODEC =
        StreamCodec.unit(new ImageUsageRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImageUsageRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(4)) return;
            sendTo(player);
        });
    }

    /**
     * Baut den aktuellen Nutzungsbericht und schickt ihn - herausgezogen aus {@link #handle}, damit
     * {@link DeleteImagePacket} nach einer Löschung direkt einen frischen Bericht nachschicken kann,
     * OHNE ein zweites, verschachteltes {@code context.enqueueWork(...)} auf dem bereits laufenden
     * Server-Thread aufzurufen (unnötiger Umweg, auch wenn technisch unproblematisch gewesen wäre).
     * Ruft selbst KEINE Berechtigungsprüfung mehr auf - liegt beim Aufrufer (beide Aufrufstellen
     * prüfen bereits OP4, siehe dort).
     */
    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Map<String, ImageUsageEntry> byHash = new HashMap<>();
        for (ImageMetadata meta : ServerImageStore.getAll()) {
            ImageUsageEntry entry = new ImageUsageEntry();
            entry.hash = meta.hash;
            entry.width = meta.width;
            entry.height = meta.height;
            entry.uploaderName = meta.uploaderName;
            byHash.put(meta.hash, entry);
        }
        for (Claim claim : ClaimManager.findAllClaims()) {
            String hash = claim.imageHash();
            if (hash == null || hash.isBlank()) continue;
            ImageUsageEntry entry = byHash.get(hash);
            if (entry == null) continue; // Metadaten fehlen (sollte nicht passieren) - Zuweisung wird beim Rendern ohnehin schon ignoriert, siehe ClaimEditService#setClaimImage-Klassenkommentar.
            ImageUsageEntry.ClaimRef ref = new ImageUsageEntry.ClaimRef();
            ref.claimName = claim.name();
            ref.ownerName = resolveName(server, claim.owner());
            ref.main = claim.isMain();
            entry.usedBy.add(ref);
        }

        List<ImageUsageEntry> result = new ArrayList<>(byHash.values());
        PacketDistributor.sendToPlayer(player, new ImageUsageSyncPacket(GSON.toJson(result)));
    }

    private static String resolveName(MinecraftServer server, java.util.UUID uuid) {
        if (server.getProfileCache() != null) {
            var profile = server.getProfileCache().get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }
}
