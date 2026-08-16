package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: der aktuelle {@link ServerConfigSnapshot} (Freischaltungsschwellen + Preise)
 * für den Admin-Server-Konfigurations-Screen (ROADMAP.md Phase 6-Nachtrag "Admin-GUI"). Öffnet
 * beim Empfang IMMER frisch den Screen neu (sowohl beim ersten Öffnen als auch nach jeder
 * erfolgreichen Änderung über {@link SetServerConfigPacket} - bewusst einfach gehalten, siehe
 * {@code com.areaclaims.client.data.ClientServerConfigCache}-Klassenkommentar).
 */
public record ServerConfigSyncPacket(String configJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerConfigSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "server_config_sync"));

    public static final StreamCodec<ByteBuf, ServerConfigSyncPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(ServerConfigSyncPacket::new, ServerConfigSyncPacket::configJson);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar - der Verweis auf
    // client-only Code hier wird auf dem Dedicated Server nie ausgeführt (S2C-Paket).
    public static void handle(ServerConfigSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.data.ClientServerConfigCache.updateAndOpen(packet.configJson()));
    }
}
