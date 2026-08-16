package com.areaclaims.integration;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.geometry.Vertex;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.overlay.IServerOverlayAPI;
import journeymap.api.v2.server.overlay.OverlayPoints;
import journeymap.api.v2.server.overlay.OverlayPolygon;
import journeymap.api.v2.server.overlay.OverlayShapeProps;
import journeymap.api.v2.server.overlay.ServerPolygon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Nutzer-Wunsch (2026-08-16): Community möchte Claim-Grenzen auf JourneyMap sehen - "natürlich nur
 * für Server, die JourneyMap eh drauf haben, keine Zwang dafür". JourneyMap 6.x (bestätigt per
 * javap gegen die real installierte {@code journeymap-neoforge-1.21.1-6.0.0-beta.81.jar}, siehe
 * jarJar-gebündelte {@code journeymap-api-neoforge-2.0.0-1.21.1-SNAPSHOT.jar}) bringt dafür eine
 * ECHTE SERVER-seitige API mit ({@link IServerOverlayAPI}) - der Server pusht fertige
 * Polygon-Overlays direkt an einzelne Spieler-Clients, komplett OHNE dass wir irgendeinen
 * client-seitigen JourneyMap-Code schreiben müssen. Ist auf einem Client kein JourneyMap
 * installiert, verwirft dieser das Netzwerk-Paket einfach unbemerkt (JourneyMaps eigener,
 * optionaler Netzwerkkanal) - kein Fehlerfall für uns.
 *
 * <p><b>Sichtbarkeit (Nutzer-Vorgabe):</b> ALLE Claims (Haupt- UND Unterbereiche) werden ALLEN
 * Online-Spielern gezeigt - eine öffentliche Übersichtskarte, kein privates "nur eigene Claims".
 *
 * <p><b>Isolations-Muster</b> (identisch zu {@link CobbleCompanionBridge}/{@link CobblemonBridge}):
 * diese Klasse referenziert echte JourneyMap-API-Typen in Feld-/Methodensignaturen - sie darf daher
 * NIEMALS von einer immer-geladenen Klasse aus unbedingt aufgerufen werden, sondern nur hinter einem
 * vorherigen {@link ModAvailability#isJourneyMapAvailable()}-Check (reiner Mod-ID-String-Vergleich,
 * keine JourneyMap-Klasse involviert). Erst wenn dieser Check {@code true} liefert, lädt die JVM
 * diese Klasse tatsächlich - andernfalls bleibt sie unberührt, kein {@code NoClassDefFoundError}.
 */
public final class JourneyMapBridge {

    /** JourneyMaps eigener Gruppen-/Layer-Bezeichner für all unsere Overlays (siehe {@link IServerOverlayAPI#show}). */
    private static final String OVERLAY_GROUP = "areaclaims";

    private static volatile IServerOverlayAPI overlayApi;

    private JourneyMapBridge() {}

    /** Von {@link AreaClaimsJourneyMapPlugin#initialize} aufgerufen, sobald JourneyMap selbst bereit ist. */
    public static void onInitialized(IServerAPI api) {
        overlayApi = api.getOverlayApi();
    }

    private static boolean isActive() {
        return overlayApi != null && FeatureConfigManager.journeyMapIntegrationEnabled();
    }

    /**
     * Baut ALLE aktuell existierenden Claims neu auf und schickt sie an JEDEN Online-Spieler - vorher
     * wird der bisherige Overlay-Stand dieses Spielers für unsere Gruppe komplett geleert
     * ({@link IServerOverlayAPI#clearAll}), damit gelöschte/veränderte Claims nie als Geister-Overlay
     * hängen bleiben. Bewusst "alles neu" statt Einzel-Diffing (add/remove pro geändertem Claim) -
     * Claims ändern sich selten genug (Spieler-Aktion, kein Tick-Takt), dass der zusätzliche
     * Netzwerk-Traffic keine Rolle spielt, macht diese Klasse dafür aber deutlich einfacher/robuster
     * (kein Tracking nötig, WELCHER Claim sich seit dem letzten Push geändert hat).
     */
    public static void refreshAll() {
        if (!isActive()) return;
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return; // z.B. save() theoretisch vor vollständigem Serverstart
        List<ServerPolygon> polygons = buildAllPolygons();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            pushTo(player, polygons);
        }
    }

    /** Wie {@link #refreshAll}, aber nur für EINEN Spieler - für den frisch verbundenen Spieler beim Login (siehe ClaimEntryListener). */
    public static void pushAllTo(ServerPlayer player) {
        if (!isActive()) return;
        pushTo(player, buildAllPolygons());
    }

    /**
     * Leert unsere Overlay-Gruppe bei allen Online-Spielern, OHNE danach neu zu befüllen - anders
     * als {@link #refreshAll} bewusst OHNE {@link #isActive()}-Check (nur {@code overlayApi != null}),
     * da dies genau dann aufgerufen wird, wenn die Integration GERADE per Admin-Konfiguration
     * abgeschaltet wurde (siehe {@code AdminConfigService#setJourneyMapIntegrationEnabled}) -
     * {@link #isActive()} wäre in diesem Moment bereits {@code false} und würde das Leeren selbst
     * verhindern.
     */
    public static void clearAllForEveryone() {
        if (overlayApi == null) return;
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            overlayApi.clearAll(player, OVERLAY_GROUP);
        }
    }

    private static void pushTo(ServerPlayer player, List<ServerPolygon> polygons) {
        overlayApi.clearAll(player, OVERLAY_GROUP);
        if (!polygons.isEmpty()) {
            overlayApi.show(player, OVERLAY_GROUP, polygons.toArray(new ServerPolygon[0]));
        }
    }

    private static List<ServerPolygon> buildAllPolygons() {
        List<ServerPolygon> polygons = new ArrayList<>();
        for (Claim claim : ClaimManager.findAllClaims()) {
            ServerPolygon polygon = toServerPolygon(claim);
            if (polygon != null) polygons.add(polygon);
        }
        return polygons;
    }

    /** {@code null}, wenn der Claim keine gültige Dimension oder keinen sinnvollen Teil (< 3 Punkte) hat. */
    private static ServerPolygon toServerPolygon(Claim claim) {
        ResourceLocation dimId = ResourceLocation.tryParse(claim.dimension());
        if (dimId == null) return null;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

        List<OverlayPolygon> polys = new ArrayList<>();
        for (List<Vertex> part : claim.parts()) {
            if (part.size() < 3) continue;
            List<Long> points = new ArrayList<>(part.size());
            // Y ist für ein Top-Down-Kartenoverlay irrelevant (Claims sind reine XZ-Grundrisse, siehe
            // Claim-Klassenkommentar) - 64 nur als neutraler, sicher innerhalb der Weltbauhöhe liegender Wert.
            for (Vertex v : part) points.add(BlockPos.asLong(v.x(), 64, v.z()));
            polys.add(new OverlayPolygon(new OverlayPoints(points), List.of()));
        }
        if (polys.isEmpty()) return null;

        int color = claim.effectiveBoundaryColor() & 0xFFFFFF;
        OverlayShapeProps props = new OverlayShapeProps(
            color, claim.isMain() ? 0.15f : 0.30f,
            color, claim.isMain() ? 2.0f : 1.5f, 0.8f,
            claim.isMain() ? 0 : 1,
            0, 24,
            Set.of(), Set.of(),
            claim.name(), claim.name());
        return new ServerPolygon(claim.id().toString(), dimKey, polys, props);
    }
}
