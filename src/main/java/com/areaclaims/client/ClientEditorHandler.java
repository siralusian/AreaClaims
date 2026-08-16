package com.areaclaims.client;

import com.areaclaims.client.gui.AreaClaimsEditorScreen;
import net.minecraft.client.Minecraft;

/**
 * Öffnet den echten Claim-Editor (Phase 5). Der Server schickt VOR diesem OpenEditorPacket immer
 * schon einen frischen {@code ClaimSyncPacket} (siehe AreaClaimsCommands#openEditor), der
 * clientseitige Cache ist also bereits gefüllt, wenn der Screen aufgeht.
 */
public class ClientEditorHandler {

    public static void openEditor() {
        Minecraft.getInstance().setScreen(new AreaClaimsEditorScreen());
    }
}
