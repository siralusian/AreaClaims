package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: die persönlichen {@link com.areaclaims.display.PlayerDisplayPreferences} des
 * EMPFANGENDEN Spielers (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15) - öffnet beim Empfang immer
 * frisch den {@code AreaClaimsDisplayPrefsScreen} neu, gleiches "einfach gehalten"-Muster wie
 * {@link ServerConfigSyncPacket} (siehe dortigen Klassenkommentar).
 */
public record DisplayPrefsSyncPacket(String prefsJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DisplayPrefsSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "display_prefs_sync"));

    public static final StreamCodec<ByteBuf, DisplayPrefsSyncPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(DisplayPrefsSyncPacket::new, DisplayPrefsSyncPacket::prefsJson);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe OpenEditorPacket-Klassenkommentar - der Verweis auf
    // client-only Code hier wird auf dem Dedicated Server nie ausgeführt (S2C-Paket).
    public static void handle(DisplayPrefsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.areaclaims.client.data.ClientDisplayPrefsCache.updateAndOpen(packet.prefsJson()));
    }
}
