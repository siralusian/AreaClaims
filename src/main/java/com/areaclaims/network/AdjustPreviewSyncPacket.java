package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.client.data.ClientAdjustPreviewCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: aktuelle Kandidaten-Geometrie der "Anpassen"-Vorschau (siehe
 * {@link AdjustPreviewSnapshot}-Klassenkommentar) - verschickt bei jedem Block-Umschalten sowie
 * beim Starten/Beenden von "Anpassen" (leere Nutzlast räumt den Client-Cache). Eigener, dedizierter
 * Kanal statt Wiederverwendung von {@link ShowcaseGeometrySyncPacket} - konzeptionell etwas anderes
 * (temporäre, EIN-Spieler-Sitzung statt der dauerhaften "Grenzen anzeigen"-Liste).
 */
public record AdjustPreviewSyncPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdjustPreviewSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "adjust_preview_sync"));

    /** Siehe {@link ShowcaseGeometrySyncPacket}-Klassenkommentar - gleiche großzügige Obergrenze statt Vanillas knappem Standard. */
    private static final int MAX_JSON_LENGTH = 500_000;

    public static final StreamCodec<ByteBuf, AdjustPreviewSyncPacket> CODEC =
        ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH).map(AdjustPreviewSyncPacket::new, AdjustPreviewSyncPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdjustPreviewSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAdjustPreviewCache.update(packet.json()));
    }
}
