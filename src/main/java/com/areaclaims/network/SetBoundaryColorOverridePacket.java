package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.data.ClaimShowcaseManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: setzt (oder löscht) die persönliche Grenzfarbe-Überschreibung des sendenden
 * Spielers (ROADMAP.md Phase 7-Nachtrag, Punkt 5 "Einstellungen"-Popup - die "Verwende Claim-
 * Farbe"-Checkbox UND der Hex-/Regenbogen-Farbwähler zusammen). {@code hex} leer = Überschreibung
 * löschen (Checkbox angehakt, "benutze die Farbe des Claims"); sonst ein 6-stelliger Hex-Wert.
 * Ruft NUR {@link ClaimShowcaseManager#setColorOverride} auf und schickt danach einen frischen
 * Snapshot (für die Popup-Anzeige) UND eine frische Showcase-Geometrie (damit ein aktiver TINT-
 * Modus die neue Farbe sofort übernimmt).
 */
public record SetBoundaryColorOverridePacket(String hex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetBoundaryColorOverridePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_boundary_color_override"));

    public static final StreamCodec<ByteBuf, SetBoundaryColorOverridePacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(SetBoundaryColorOverridePacket::new, SetBoundaryColorOverridePacket::hex);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetBoundaryColorOverridePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;

            Integer color = null;
            String hex = packet.hex();
            if (hex != null && !hex.isBlank()) {
                String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
                try {
                    long value = Long.parseLong(cleaned, 16);
                    if (value >= 0 && value <= 0xFFFFFF) color = (int) value;
                } catch (NumberFormatException ignored) {}
            }
            ClaimShowcaseManager.setColorOverride(caller.getUUID(), color);
            ClaimSnapshotBuilder.sendTo(caller);
            ShowcaseGeometrySnapshotBuilder.sendTo(caller);
        });
    }
}
