package com.areaclaims.client;

import com.areaclaims.client.data.ClientBoundaryGeometryCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-seitiger Renderer für den TINT-Anzeigemodus (ROADMAP.md Phase 6-Nachtrag 2, Punkt 7
 * "Block-Einfärbung"; grundlegend überarbeitet in Phase 6-Nachtrag 4 nach Live-Test-Feedback) -
 * zeigt eine halbtransparente Farbfläche über den Blöcken innerhalb eines Claims, als Alternative
 * zum Partikel-Umriss (siehe {@code ToolInteractionListener}, {@code ClaimShowcaseManager.DisplayMode}).
 *
 * <p><b>Verifizierter Render-Hook:</b> {@link RenderLevelStageEvent}, Stage
 * {@link RenderLevelStageEvent.Stage#AFTER_PARTICLES} (laut NeoForge-Doc-Kommentar explizit für
 * Transluzenz empfohlen). <b>Render-Typ:</b> {@link RenderType#debugQuads()} -
 * {@code DefaultVertexFormat.POSITION_COLOR}, transluzent, doppelseitig (NO_CULL) - verifiziert in
 * den dekompilierten {@code RenderType.java}-Sourcen (dieselbe Definition wie Vanillas F3+G-Chunk-
 * Grenzen). <b>Referenz-Abgleich (Nutzer-Vorgabe):</b> die tatsächliche Forgematica/Litematica-Jar
 * ({@code Server/mods/forgematica-0.4.1+mc1.21.1.jar}) wurde inspiziert
 * ({@code fi/dy/masa/litematica/render/schematic/OverlayRenderType.class}) - deren eigener
 * Overlay-Render-Typ für Schematic-Übersichten nutzt EXAKT dasselbe Format/denselben Modus
 * ({@code DefaultVertexFormat.POSITION_COLOR} + {@code VertexFormat.Mode.QUADS}) wie
 * {@code RenderType.debugQuads()} - eine Bestätigung, dass der hier gewählte Ansatz kein
 * Fehlgriff war, keine Notwendigkeit, Litematicas viel größere VBO-/Chunk-Renderer-Dispatcher-
 * Infrastruktur (Worker-Threads, eigene Chunk-Mesh-Pipeline für vollständige Schematic-Blockmodelle
 * mit Texturen) zu übernehmen - das wäre für eine reine Farb-Einfärbung deutlich überdimensioniert
 * gewesen.
 *
 * <p><b>Nur volle Würfel (Nutzer-Feedback nach Live-Test):</b> {@link BlockState#isCollisionShapeFullBlock}
 * (verifiziert in den dekompilierten {@code BlockBehaviour.java}-Sourcen: intern exakt
 * {@code Block.isShapeFullBlock(state.getCollisionShape(level, pos))}, zusätzlich bereits gecacht -
 * dieselbe Methode, die Vanilla z. B. für Redstone-Leiter-Prüfungen nutzt) filtert Stufen, Treppen,
 * Zäune usw. heraus - nur echte volle Würfel werden eingefärbt.
 *
 * <p><b>Volle 6-seitige Flächen-Kulierung (Nutzer-Feedback nach Live-Test, ersetzt die vorherige
 * "nur Oberseite"-Vereinfachung):</b> für jeden qualifizierenden Würfel wird JEDE der 6 Seiten
 * einzeln geprüft - eine Seite wird NUR gezeichnet, wenn der Nachbar auf dieser Seite NICHT
 * ebenfalls ein voller Würfel ist (sonst wäre die Fläche ohnehin unsichtbar, komplett von einem
 * anderen Würfel verdeckt) - dasselbe Grundprinzip, das Vanillas eigener Chunk-Mesher für die
 * interne Flächen-Kulierung nutzt. Ergebnis: ein "Geister-Block"-artiger Umriss mit sichtbaren
 * Seitenwänden (Klippen, Höhlenwände) statt nur einer flachen Boden-Ebene.
 *
 * <p><b>Sichtweite jetzt admin-konfigurierbar in Chunks statt fest 20 Blöcke</b> (siehe
 * {@code FeatureConfigManager#tintRangeChunks}, per {@link ShowcaseGeometrySnapshot#rangeBlocks}
 * an den Client übertragen, siehe {@link ClientBoundaryGeometryCache#rangeBlocks()}) - weiterhin
 * als Zylinder um den Betrachter (horizontal Kreis, vertikal ±Reichweite).
 *
 * <p><b>Performance:</b> die teure Iteration läuft weiterhin NUR bei Bewegung in eine neue
 * Blockposition oder einem neuen Server-Snapshot (siehe {@link #recompute}), NICHT jeden Frame -
 * bewusst weiterhin naiv (kein Sichtweiten-/Frustum-Culling, keine Mehrfach-Thread-Pipeline wie
 * Litematica) gemäß expliziter Nutzer-Vorgabe "don't over-engineer". Da nun bis zu 6 statt 1 Quad
 * pro Block anfallen können, ABER vollständig umschlossene Würfel (alle 6 Nachbarn voll) jetzt
 * KEINE einzige Fläche mehr erzeugen (vorher wurden solche inneren Würfel gar nicht erst
 * betrachtet, aber auch Oberflächen-Würfel bekamen nur 1 statt bis zu 6 Flächen) - der Standard-
 * Default von 2 Chunks (statt der vom Nutzer vorgeschlagenen Spanne bis 3) wurde bewusst als
 * vorsichtigerer Startwert gewählt, siehe {@code FeatureConfigManager}-Kommentar.
 */
public class BoundaryTintRenderer {

    /** ~36% Deckkraft (Nutzer-Vorgabe: "ca. 30-40%, eigene Wahl beim genauen Alpha-Wert"). */
    private static final int ALPHA = 92;
    /** Leichte Aufweitung jeder Fläche nach außen, um Z-Fighting mit der echten Blockoberfläche zu vermeiden. */
    private static final float FACE_EPSILON = 0.002F;

    private record Face(int x, int y, int z, Direction direction) {}
    private record CachedGroup(int color, List<Face> faces) {}

    private int lastGeneration = -1;
    private int lastPlayerX = Integer.MIN_VALUE;
    private int lastPlayerY = Integer.MIN_VALUE;
    private int lastPlayerZ = Integer.MIN_VALUE;
    private List<CachedGroup> cachedGroups = List.of();

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        List<ClientBoundaryGeometryCache.Entry> claims = ClientBoundaryGeometryCache.get();
        int generation = ClientBoundaryGeometryCache.generation();
        int playerX = Mth.floor(mc.player.getX());
        int playerY = Mth.floor(mc.player.getY());
        int playerZ = Mth.floor(mc.player.getZ());

        if (claims.isEmpty()) {
            cachedGroups = List.of();
            lastGeneration = generation;
            return;
        }

        if (generation != lastGeneration || playerX != lastPlayerX || playerY != lastPlayerY || playerZ != lastPlayerZ) {
            lastGeneration = generation;
            lastPlayerX = playerX;
            lastPlayerY = playerY;
            lastPlayerZ = playerZ;
            recompute(mc.level, claims, playerX, playerY, playerZ);
        }

        if (cachedGroups.isEmpty()) return;
        renderCachedQuads(event, mc);
    }

    private void recompute(ClientLevel level, List<ClientBoundaryGeometryCache.Entry> claims, int playerX, int playerY, int playerZ) {
        int radius = Math.max(1, ClientBoundaryGeometryCache.rangeBlocks());
        int minY = Math.max(level.getMinBuildHeight(), playerY - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, playerY + radius);

        List<CachedGroup> groups = new ArrayList<>();
        for (ClientBoundaryGeometryCache.Entry claim : claims) {
            List<Face> faces = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    int x = playerX + dx;
                    int z = playerZ + dz;
                    if (!claim.containsPoint(x + 0.5, z + 0.5)) continue;
                    collectColumnFaces(level, x, minY, maxY, z, faces);
                }
            }
            if (!faces.isEmpty()) groups.add(new CachedGroup(claim.color, faces));
        }
        cachedGroups = groups;
    }

    private void collectColumnFaces(ClientLevel level, int x, int minY, int maxY, int z, List<Face> out) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!isFullCube(level, pos, state)) continue;
            for (Direction dir : Direction.values()) {
                neighborPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                BlockState neighborState = level.getBlockState(neighborPos);
                if (isFullCube(level, neighborPos, neighborState)) continue; // Fläche komplett verdeckt - nicht zeichnen
                out.add(new Face(x, y, z, dir));
            }
        }
    }

    private static boolean isFullCube(ClientLevel level, BlockPos pos, BlockState state) {
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private void renderCachedQuads(RenderLevelStageEvent event, Minecraft mc) {
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poseStack.last();

        for (CachedGroup group : cachedGroups) {
            int r = (group.color() >> 16) & 0xFF;
            int g = (group.color() >> 8) & 0xFF;
            int b = group.color() & 0xFF;
            for (Face face : group.faces()) {
                addFace(consumer, pose, face.x(), face.y(), face.z(), face.direction(), r, g, b);
            }
        }

        bufferSource.endBatch(RenderType.debugQuads());
        poseStack.popPose();
    }

    /** Zeichnet EINE Seitenfläche eines Einheitswürfels bei (x,y,z), leicht nach außen aufgeweitet (siehe {@link #FACE_EPSILON}). */
    private static void addFace(VertexConsumer consumer, PoseStack.Pose pose, int x, int y, int z, Direction dir, int r, int g, int b) {
        float x0 = x - FACE_EPSILON, x1 = x + 1 + FACE_EPSILON;
        float y0 = y - FACE_EPSILON, y1 = y + 1 + FACE_EPSILON;
        float z0 = z - FACE_EPSILON, z1 = z + 1 + FACE_EPSILON;
        switch (dir) {
            case DOWN -> quad(consumer, pose, r, g, b, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            case UP -> quad(consumer, pose, r, g, b, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            case NORTH -> quad(consumer, pose, r, g, b, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            case SOUTH -> quad(consumer, pose, r, g, b, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            case WEST -> quad(consumer, pose, r, g, b, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            case EAST -> quad(consumer, pose, r, g, b, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int r, int g, int b,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, ALPHA);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, ALPHA);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, ALPHA);
        consumer.addVertex(pose, x4, y4, z4).setColor(r, g, b, ALPHA);
    }
}
