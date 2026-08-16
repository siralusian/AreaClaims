package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.client.data.ClientBoundaryGeometryCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: aktuelle Polygon-Geometrie der Claims, die dieser Spieler gerade im TINT-
 * Anzeigemodus zeigt (ROADMAP.md Phase 6-Nachtrag 2, Punkt 7 "Block-Einfärbung") - JSON-String-
 * Payload (gleiches Muster wie {@link ClaimSyncPacket}/{@link ServerConfigSyncPacket}), da die
 * verschachtelte Polygon-Liste zu komplex für einen einfachen {@code StreamCodec.composite} wäre.
 *
 * <p><b>Nachtrag 7, Punkt 3 ("zu viele Punkte -> stiller Abbruch"):</b> diese Nutzlast enthält die
 * ROHE Polygon-Geometrie (siehe {@link ShowcaseGeometrySnapshot}) - genau der Payload-Typ, der bei
 * Vanillas STANDARD {@link ByteBufCodecs#STRING_UTF8} (32767 Zeichen, dekompiliert verifiziert)
 * bei genug Punkten/gleichzeitig angezeigten Claims an die Obergrenze stoßen könnte. Ein
 * überschrittenes Limit führt beim Kodieren zu einer Netty-{@code EncoderException}, die NICHT
 * als Spielernachricht ankommt - das Paket wird einfach verworfen ("stiller Abbruch"). Statt uns
 * allein auf die neue Punktzahl-Obergrenze ({@code PolygonUtil#MAX_POINTS_PER_PART}) zu verlassen,
 * bekommt dieser Kanal zusätzlich (Verteidigung in der Tiefe) eine deutlich großzügigere, aber
 * weiterhin ENDLICHE eigene Obergrenze statt des knappen Vanilla-Standardwerts.
 */
public record ShowcaseGeometrySyncPacket(String json) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowcaseGeometrySyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "showcase_geometry_sync"));

    /** Siehe Klassenkommentar - deutlich über Vanillas {@link ByteBufCodecs#STRING_UTF8}-Standard (32767). */
    private static final int MAX_JSON_LENGTH = 500_000;

    public static final StreamCodec<ByteBuf, ShowcaseGeometrySyncPacket> CODEC =
        ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH).map(ShowcaseGeometrySyncPacket::new, ShowcaseGeometrySyncPacket::json);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowcaseGeometrySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientBoundaryGeometryCache.update(packet.json()));
    }
}
