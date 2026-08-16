package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: "schick mir den aktuellen {@link ServerConfigSnapshot}" - ausgelöst vom
 * "Server-Konfiguration"-Button im Admin-Modus des GUI-Editors (ROADMAP.md Phase 6-Nachtrag
 * "Admin-GUI"). OP4-Prüfung läuft hier NOCHMAL server-seitig (der Button ist clientseitig nur
 * sichtbar, wenn der Admin-Modus aktiv ist - kein Ersatz für die eigentliche Berechtigungsprüfung).
 */
public record RequestServerConfigPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestServerConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "request_server_config"));

    public static final StreamCodec<ByteBuf, RequestServerConfigPacket> CODEC =
        StreamCodec.unit(new RequestServerConfigPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestServerConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer caller && caller.hasPermissions(4)) {
                ServerConfigSnapshotBuilder.sendTo(caller);
            }
        });
    }
}
