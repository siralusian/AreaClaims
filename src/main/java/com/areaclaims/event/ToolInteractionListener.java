package com.areaclaims.event;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.StakingService;
import com.areaclaims.data.ActiveSelectionManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.areaclaims.data.SelectionManager;
import com.areaclaims.geometry.PolygonUtil;
import com.areaclaims.geometry.Vertex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Verwaltet den "Klick-zum-Abstecken"-Modus (ROADMAP.md Phase 7 - ersetzt den früheren goldene-
 * Hacke-Werkzeug-Modus vollständig, siehe {@link ActiveSelectionManager}). Ausgelöst NUR über die
 * GUI-Buttons "Erweitern"/"Neuer SubClaim"/"Neuer Claim" ({@code BeginSelectionPacket} ->
 * {@code StakingService#begin}) - solange ein Spieler danach aktiv ist, fängt diese Klasse JEDEN
 * Links-/Rechtsklick ab, UNABHÄNGIG vom gehaltenen Item (vorher war das an die goldene Hacke
 * gebunden). Linksklick auf einen Block fügt einen Punkt hinzu; JEDER Rechtsklick (Block, Entity,
 * Item-Nutzung) beendet die Auswahl ({@code StakingService#end}).
 *
 * <p><b>Wichtige Nebenwirkungs-Unterdrückung (Nutzer-Vorgabe, nicht übersehen):</b> da der Spieler
 * jetzt ein BELIEBIGES Item halten kann (nicht mehr nur eine Hacke ohne besondere Aktion), MUSS
 * jede normale Konsequenz von Links-/Rechtsklick unterdrückt werden, solange die Auswahl aktiv
 * ist - sonst würde der Spieler versehentlich Blöcke abbauen, Mobs angreifen, Items benutzen
 * (Eimer, Nahrung, Tränke, ...). Verifiziert (dekompilierte {@code ServerPlayerGameMode.java}):
 * das Canceln von {@link PlayerInteractEvent.LeftClickBlock} verhindert BEIDES - normales
 * Abbauen UND den Sofort-Abbau im Kreativmodus (die Events-Prüfung
 * {@code if (event.isCanceled()) return;} steht in {@code handleBlockBreakAction} VOR der
 * Kreativmodus-Sonderbehandlung, kein Kreativmodus-Schlupfloch). Rechtsklick wird über
 * {@link PlayerInteractEvent.RightClickBlock}/{@link PlayerInteractEvent.RightClickItem}/
 * {@link PlayerInteractEvent.EntityInteract}/{@link PlayerInteractEvent.EntityInteractSpecific}
 * abgedeckt - EIN Sonderfall bleibt: laut NeoForge-Doc-Kommentar wird
 * {@link PlayerInteractEvent.RightClickEmpty} (Rechtsklick mit LEERER Hand in reine Luft, kein
 * Block/Entity in Reichweite) NUR clientseitig gefeuert und NIE an den Server gemeldet, ist außerdem
 * nicht abbrechbar - dafür gibt es {@code com.areaclaims.client.ClientSelectionEndListener} +
 * {@code EndSelectionPacket} als client-initiierten Ersatzweg (siehe deren Klassenkommentare).
 *
 * <p>Zusätzlich rendert derselbe Tick-Loop wie vorher die aktuelle Punkt-Auswahl UND alle aktiven
 * "Grenzen anzeigen"-Vorschauen (siehe {@link ClaimShowcaseManager}) - unverändert gegenüber den
 * vorherigen Runden, nur die Gating-Bedingung für die Live-Auswahl wechselte von
 * {@code ToolStateManager} auf {@link ActiveSelectionManager}.
 */
public class ToolInteractionListener {

    /** Alle 15 Ticks (im vorgegebenen 10-20-Tick-Fenster) neu zeichnen, nicht jeden Tick. */
    private static final int PARTICLE_INTERVAL_TICKS = 15;
    private static final double PARTICLE_STEP_BLOCKS = 1.0;
    /** Siehe ROADMAP.md Phase 6-Nachtrag 2, Punkt 1 - dichtere/größere Partikel nur für Claim-Umrisse, nicht die Live-Auswahl. */
    private static final double SHOWCASE_PARTICLE_STEP_BLOCKS = 0.35;
    private static final float SHOWCASE_PARTICLE_SCALE = 1.5F;
    /** Wie weit der Abtast-Punkt für die Kanten-Abdeckungsprüfung von der Kante absteht. */
    private static final double EDGE_COVERAGE_PROBE_DISTANCE = 0.5;

    private int tickCounter = 0;

    // ---------------------------------------------------------------- Linksklick: Punkt hinzufügen

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!ActiveSelectionManager.isActive(serverPlayer.getUUID())) return;

        // IMMER canceln, solange aktiv (verhindert Blockabbau, siehe Klassenkommentar) - Punkt
        // aber nur beim START eines Linksklicks hinzufügen (CLIENT_HOLD/STOP/ABORT ignorieren,
        // sonst würde gehaltenes Linksklicken viele Punkte auf einmal hinzufügen).
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;

        // Nachtrag 7, Punkt 3 ("zu viele Punkte -> stiller Abbruch beim Rechtsklick"): sofortiges,
        // klares Feedback BEIM Klicken statt eines stillen Fehlschlags erst beim späteren
        // Rechtsklick-Abschluss - siehe PolygonUtil#MAX_POINTS_PER_PART-Klassenkommentar für die
        // vollständige Root-Cause-Recherche. Der Klick wird schlicht NICHT gezählt (Auswahl bleibt
        // unverändert), der Spieler kann trotzdem normal abschließen (Rechtsklick) oder den letzten
        // Punkt entfernen und anders weitermachen.
        if (SelectionManager.get(serverPlayer.getUUID()).size() >= PolygonUtil.MAX_POINTS_PER_PART) {
            serverPlayer.displayClientMessage(
                Component.translatable("areaclaims.tool.too_many_points", PolygonUtil.MAX_POINTS_PER_PART), true);
            return;
        }

        BlockPos pos = event.getPos();
        SelectionManager.addPoint(serverPlayer.getUUID(), pos.getX(), pos.getZ());
        sendFeedback(serverPlayer);
    }

    // ---------------------------------------------------------------- Linksklick auf Entity: nur unterdrücken, kein Punkt (kein Block-Ziel)

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (ActiveSelectionManager.isActive(serverPlayer.getUUID())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- Rechtsklick (alle Varianten): Auswahl beenden

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) return;
        if (cancelAndEndIfActive(event.getEntity())) event.setCanceled(true);
    }

    /**
     * Gemeinsamer Rechtsklick-Handler für alle abbrechbaren Interact-Varianten: liest den
     * "war aktiv"-Zustand VOR dem Beenden aus (damit der Aufrufer weiß, ob ER selbst canceln
     * muss - {@code event.setCanceled} kann nicht hier drin passieren, jeder Event-Typ hat eine
     * eigene Klasse) und beendet die Auswahl GENAU EINMAL - {@link ActiveSelectionManager#isActive}
     * als Guard verhindert ein doppeltes Auslösen, falls für denselben physischen Klick mehrere
     * dieser Events nacheinander feuern (z. B. RightClickBlock gefolgt von RightClickItem): der
     * erste Handler, der tatsächlich {@code StakingService#end} aufruft, entfernt den Spieler
     * bereits aus {@link ActiveSelectionManager} - ein nachfolgender Handler im selben Tick sieht
     * dann bereits "nicht mehr aktiv" und tut (und cancelt) nichts mehr.
     *
     * @return true, wenn die Auswahl gerade aktiv WAR (der Aufrufer sein Event also canceln muss).
     */
    private boolean cancelAndEndIfActive(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        boolean wasActive = ActiveSelectionManager.isActive(serverPlayer.getUUID());
        if (wasActive) StakingService.end(serverPlayer);
        return wasActive;
    }

    private void sendFeedback(ServerPlayer player) {
        List<Vertex> selection = SelectionManager.get(player.getUUID());
        int count = selection.size();
        if (count >= 3) {
            double area = PolygonUtil.area(selection);
            player.displayClientMessage(
                Component.translatable("areaclaims.tool.selection_status_area", count, String.format("%.1f", area)), true);
        } else {
            player.displayClientMessage(Component.translatable("areaclaims.tool.selection_status", count), true);
        }
    }

    // ---------------------------------------------------------------- Rendern (unverändert gegenüber vorherigen Runden, nur Gating-Quelle geändert)

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % PARTICLE_INTERVAL_TICKS != 0) return;

        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;

            if (ActiveSelectionManager.isActive(player.getUUID())) {
                List<Vertex> selection = SelectionManager.get(player.getUUID());
                if (selection.size() >= 2) {
                    renderPolygonOutline(level, player, selection, ParticleTypes.END_ROD, PARTICLE_STEP_BLOCKS);
                }
            }

            // Nur im Partikel-Modus rendern (siehe ClaimShowcaseManager.DisplayMode) - im TINT-
            // Modus übernimmt com.areaclaims.client.BoundaryTintRenderer rein clientseitig, ohne
            // Server-Partikel.
            if (ClaimShowcaseManager.mode(player.getUUID()) == ClaimShowcaseManager.DisplayMode.PARTICLES) {
                for (UUID claimId : ClaimShowcaseManager.activeClaimIds(player.getUUID())) {
                    Claim claim = ClaimManager.getById(claimId);
                    if (claim == null) continue;
                    renderClaimOutline(level, player, claim);
                }
            }
        }
    }

    /** Rendert alle Teile eines Claims in dessen eigener Farbe, Kanten, die von einem ANDEREN Teil desselben Claims abgedeckt werden, werden übersprungen (siehe Klassenkommentar). */
    private void renderClaimOutline(ServerLevel level, ServerPlayer player, Claim claim) {
        // Persönliche Farb-Überschreibung des Betrachters (ROADMAP.md Phase 7-Nachtrag, Punkt 5
        // "Einstellungen"-Popup) hat Vorrang vor der vom Claim-Besitzer konfigurierten Farbe.
        ParticleOptions particle = coloredParticle(ClaimShowcaseManager.effectiveColorFor(player.getUUID(), claim));
        List<List<Vertex>> parts = claim.parts();
        for (List<Vertex> part : parts) {
            int n = part.size();
            if (n < 2) continue;
            int edgeCount = n >= 3 ? n : n - 1;
            for (int i = 0; i < edgeCount; i++) {
                Vertex a = part.get(i);
                Vertex b = part.get((i + 1) % n);
                if (parts.size() > 1 && n >= 3 && isEdgeCoveredByOtherPart(a, b, parts, part)) continue;
                renderEdge(level, player, a, b, particle, SHOWCASE_PARTICLE_STEP_BLOCKS);
            }
        }
    }

    /**
     * Kanten-Abdeckungstest (siehe Klassenkommentar): tastet einen Punkt knapp außerhalb des
     * Kanten-Mittelpunkts ab (die Richtung, die NICHT ins eigene Teil zeigt) und prüft, ob dieser
     * Punkt in einem ANDEREN Teil desselben Claims liegt - wenn ja, ist diese Kante "innen
     * liegend" (von einem angrenzenden/überlappenden Teil bedeckt) und soll nicht gezeichnet werden.
     */
    private boolean isEdgeCoveredByOtherPart(Vertex a, Vertex b, List<List<Vertex>> allParts, List<Vertex> currentPart) {
        double midX = (a.x() + b.x()) / 2.0 + 0.5;
        double midZ = (a.z() + b.z()) / 2.0 + 0.5;
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return false;
        double nx = -dz / len;
        double nz = dx / len;
        double candidateX = midX + nx * EDGE_COVERAGE_PROBE_DISTANCE;
        double candidateZ = midZ + nz * EDGE_COVERAGE_PROBE_DISTANCE;
        double outwardX, outwardZ;
        if (PolygonUtil.pointInPolygon(currentPart, candidateX, candidateZ)) {
            outwardX = midX - nx * EDGE_COVERAGE_PROBE_DISTANCE;
            outwardZ = midZ - nz * EDGE_COVERAGE_PROBE_DISTANCE;
        } else {
            outwardX = candidateX;
            outwardZ = candidateZ;
        }
        for (List<Vertex> other : allParts) {
            if (other == currentPart) continue;
            if (PolygonUtil.pointInPolygon(other, outwardX, outwardZ)) return true;
        }
        return false;
    }

    private static ParticleOptions coloredParticle(int rgb) {
        return new net.minecraft.core.particles.DustParticleOptions(Vec3.fromRGB24(rgb).toVector3f(), SHOWCASE_PARTICLE_SCALE);
    }

    private void renderPolygonOutline(ServerLevel level, ServerPlayer player, List<Vertex> points, ParticleOptions particle, double stepBlocks) {
        int edgeCount = points.size() >= 3 ? points.size() : points.size() - 1;
        for (int i = 0; i < edgeCount; i++) {
            Vertex a = points.get(i);
            Vertex b = points.get((i + 1) % points.size());
            renderEdge(level, player, a, b, particle, stepBlocks);
        }
    }

    private void renderEdge(ServerLevel level, ServerPlayer player, Vertex a, Vertex b, ParticleOptions particle, double stepBlocks) {
        double y = player.getY() + 0.1;
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.round(length / stepBlocks));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double px = a.x() + 0.5 + dx * t;
            double pz = a.z() + 0.5 + dz * t;
            level.sendParticles(player, particle, false, px, y, pz, 1, 0, 0, 0, 0);
        }
    }
}
