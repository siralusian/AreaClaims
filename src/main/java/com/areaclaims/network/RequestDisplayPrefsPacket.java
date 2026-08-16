package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: "schick mir meine persönlichen {@link com.areaclaims.display.PlayerDisplayPreferences}"
 * - ausgelöst vom neuen "Anzeige-Einstellungen"-Button im GUI-Editor (ROADMAP.md Phase 7-Nachtrag
 * 3, Punkte 13-15). JEDER Spieler darf das (keine Berechtigungsprüfung nötig - es sind seine
 * eigenen, rein persönlichen Einstellungen) - gleiches Grundmuster wie
 * {@link RequestServerConfigPacket}, nur ohne die dortige OP4-Prüfung.
 */
public record RequestDisplayPrefsPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDisplayPrefsPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "request_display_prefs"));

    public static final StreamCodec<ByteBuf, RequestDisplayPrefsPacket> CODEC =
        StreamCodec.unit(new RequestDisplayPrefsPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestDisplayPrefsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caller) {
                DisplayPrefsSnapshotBuilder.sendTo(caller);
            }
        });
    }
}
