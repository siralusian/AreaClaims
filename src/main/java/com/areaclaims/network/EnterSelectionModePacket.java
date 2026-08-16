package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.client.data.ClientSelectionState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: "du bist jetzt im Klick-zum-Abstecken-Modus" (ROADMAP.md Phase 7 "Klick-zum-
 * Abstecken" - ersetzt den goldene-Hacke-Werkzeug-Modus). Schließt den Editor-Screen (falls offen)
 * UND setzt {@link ClientSelectionState#active}, damit der clientseitige
 * {@code ClientSelectionEndListener} weiß, dass er auf {@code RightClickEmpty} reagieren muss (der
 * einzige Rechtsklick-Fall, den NeoForge NICHT an den Server meldet, siehe dessen
 * Klassenkommentar für die Herleitung). {@link OpenEditorPacket#handle} setzt das Flag beim
 * nächsten Öffnen des Editors wieder zurück - die Auswahl endet IMMER damit, dass der Editor
 * erneut geöffnet wird, egal über welchen Pfad (siehe {@code StakingService}).
 */
public record EnterSelectionModePacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EnterSelectionModePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "enter_selection_mode"));

    public static final StreamCodec<ByteBuf, EnterSelectionModePacket> CODEC =
        StreamCodec.unit(new EnterSelectionModePacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnterSelectionModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientSelectionState.setActive(true);
            if (Minecraft.getInstance().screen instanceof com.areaclaims.client.gui.AreaClaimsEditorScreen) {
                Minecraft.getInstance().setScreen(null);
            }
        });
    }
}
