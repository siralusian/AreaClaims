package com.areaclaims;

import com.areaclaims.client.BoundaryTintRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AreaClaims.MOD_ID, dist = Dist.CLIENT)
public class AreaClaimsClient {

    public AreaClaimsClient(IEventBus modEventBus) {
        // Nur clientseitig registriert (dieser Mod-Konstruktor läuft dank dist=CLIENT nur auf dem
        // physischen Client) - siehe BoundaryTintRenderer-Klassenkommentar (ROADMAP.md Phase
        // 6-Nachtrag 2, Punkt 7 "Block-Einfärbung").
        NeoForge.EVENT_BUS.register(new BoundaryTintRenderer());
        // Schließt die RightClickEmpty-Lücke (siehe dortigen Klassenkommentar, ROADMAP.md Phase 7).
        NeoForge.EVENT_BUS.register(new com.areaclaims.client.ClientSelectionEndListener());
        // Betreten-Nachricht-Overlay (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15), verifizierter
        // Render-Hook RenderGuiEvent.Post - siehe dortigen Klassenkommentar.
        NeoForge.EVENT_BUS.register(new com.areaclaims.client.EntryDisplayOverlayRenderer());
        AreaClaims.LOGGER.info("AreaClaims client loaded.");
    }
}
