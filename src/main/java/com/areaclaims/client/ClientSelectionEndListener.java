package com.areaclaims.client;

import com.areaclaims.client.data.ClientNetworkUtil;
import com.areaclaims.client.data.ClientSelectionState;
import com.areaclaims.network.EndSelectionPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only Ergänzung zu {@code ToolInteractionListener} (ROADMAP.md Phase 7 "Klick-zum-
 * Abstecken") - schließt eine dokumentierte NeoForge-Lücke: {@link PlayerInteractEvent.RightClickEmpty}
 * (Rechtsklick mit LEERER Hand in offene Luft, kein Block/Entity in Reichweite) wird laut
 * eigenem Doc-Kommentar in {@code PlayerInteractEvent.java} NUR clientseitig gefeuert
 * ("This event cannot be canceled. The server is not aware of when the client right clicks empty
 * space with an empty hand, you will need to tell the server yourself.") - der Server bekommt
 * diesen einen Rechtsklick-Fall also NIE mitgeteilt, ohne dass ein Mod das explizit nachholt.
 * Da eine leere Hand ohnehin KEINE Nebenwirkung hat, die verhindert werden müsste (nichts wird
 * platziert/benutzt), reicht hier ein einfaches "sag dem Server, dass die Auswahl fertig ist" per
 * {@link EndSelectionPacket} - {@link ClientSelectionState} verhindert unnötige Paket-Spam-Sendungen,
 * wenn der Spieler gar nicht im Auswahl-Modus ist.
 *
 * <p>Registriert NUR client-seitig ({@code AreaClaimsClient}, {@code dist = Dist.CLIENT}) - referenziert
 * client-only APIs ({@code PacketDistributor.sendToServer}, {@code ClientNetworkUtil}), die auf
 * einem Dedicated Server nicht existieren dürfen.
 */
public class ClientSelectionEndListener {

    @SubscribeEvent
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!ClientSelectionState.isActive()) return;
        ClientSelectionState.setActive(false);
        EndSelectionPacket packet = new EndSelectionPacket();
        if (ClientNetworkUtil.canSendToServerOrWarn(packet.type().id())) {
            PacketDistributor.sendToServer(packet);
        }
    }
}
