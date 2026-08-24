package com.areaclaims.client;

import com.areaclaims.client.data.ClientAdjustPreviewCache;
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
 * Nutzer-Vorgabe (2026-08-18): die Partikel-Linie der "Anpassen"-Vorschau war "teilweise sehr
 * verwirrend" - ersetzt sie durch eine Block-Einfärbung, NUR für "Anpassen" (die allgemeine
 * Grenzanzeige bleibt unverändert bei {@code BoundaryTintRenderer}/Partikeln je Spieler-
 * Einstellung, siehe dortigen Klassenkommentar - dieser Renderer ist eine EIGENE, kleinere
 * Variante speziell für die Anpassen-Sitzung).
 *
 * <p><b>Volle Fläche, nicht nur der Rand</b> (Nutzer-Fund nach Praxis-Test 2026-08-19: "nur die
 * äußersten Blöcke... war doch keine gute Idee" - ursprünglich als Vereinfachung gedacht, im
 * Live-Test aber schwerer lesbar als eine durchgehend eingefärbte Fläche) - JEDE zur Kandidaten-
 * Fläche gehörende Block-Spalte innerhalb der Reichweite wird eingefärbt, exakt wie
 * {@link BoundaryTintRenderer}s Innenfläche.
 *
 * <p><b>Reichweite 15 Blöcke NUR in X/Z, NICHT in Y begrenzt</b> (Nutzer-Vorgabe wörtlich) - anders
 * als {@link BoundaryTintRenderer}s Zylinder-Reichweite (horizontal UND vertikal begrenzt) wird
 * hier für jede Spalte die KOMPLETTE Bauhöhe durchsucht. Neuberechnung wie beim Vorbild nur bei
 * Bewegung in eine neue Blockposition bzw. einem neuen Server-Snapshot, nicht jeden Frame.
 *
 * <p><b>Fremde Claims (Nutzer-Vorgabe 2026-08-19, "Claims anderer Spieler am Boden mit
 * einfärben"):</b> zusätzlich zur eigenen Kandidaten-Fläche (immer in {@link #color}) werden
 * {@link ClientAdjustPreviewCache#nearbyClaims()} - Claims anderer Spieler in der Nähe - in ihrer
 * JEWEILIGEN Farbe eingefärbt, damit man beim Erweitern/Verkleinern sieht, wo man nicht
 * hineinragen darf. Gleiche Flächen-Kulierung/Format wie {@link BoundaryTintRenderer}s
 * {@code CachedGroup}.
 */
public class AdjustPreviewTintRenderer {

    private static final int RADIUS = 15;
    /** ~36% Deckkraft - gleicher Wert wie {@link BoundaryTintRenderer} für einen konsistenten Look. */
    private static final int ALPHA = 92;
    private static final float FACE_EPSILON = 0.002F;

    private record Face(int x, int y, int z, Direction direction) {}
    private record CachedGroup(int color, List<Face> faces) {}

    private int lastGeneration = -1;
    private int lastPlayerX = Integer.MIN_VALUE;
    private int lastPlayerZ = Integer.MIN_VALUE;
    private List<CachedGroup> cachedGroups = List.of();

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!ClientAdjustPreviewCache.isActive()) {
            cachedGroups = List.of();
            lastGeneration = ClientAdjustPreviewCache.generation();
            return;
        }

        int generation = ClientAdjustPreviewCache.generation();
        int playerX = Mth.floor(mc.player.getX());
        int playerZ = Mth.floor(mc.player.getZ());

        if (generation != lastGeneration || playerX != lastPlayerX || playerZ != lastPlayerZ) {
            lastGeneration = generation;
            lastPlayerX = playerX;
            lastPlayerZ = playerZ;
            recompute(mc.level, playerX, playerZ);
        }

        if (cachedGroups.isEmpty()) return;
        renderCachedQuads(event, mc);
    }

    private void recompute(ClientLevel level, int playerX, int playerZ) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        List<CachedGroup> groups = new ArrayList<>();
        List<Face> ownFaces = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = playerX + dx;
                int z = playerZ + dz;
                if (!ClientAdjustPreviewCache.containsPoint(x + 0.5, z + 0.5)) continue;
                collectColumnFaces(level, x, minY, maxY, z, ownFaces);
            }
        }
        if (!ownFaces.isEmpty()) groups.add(new CachedGroup(ClientAdjustPreviewCache.color(), ownFaces));

        for (com.areaclaims.client.data.ClientBoundaryGeometryCache.Entry other : ClientAdjustPreviewCache.nearbyClaims()) {
            List<Face> faces = new ArrayList<>();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                    int x = playerX + dx;
                    int z = playerZ + dz;
                    if (!other.containsPoint(x + 0.5, z + 0.5)) continue;
                    collectColumnFaces(level, x, minY, maxY, z, faces);
                }
            }
            if (!faces.isEmpty()) groups.add(new CachedGroup(other.color, faces));
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
                if (isFullCube(level, neighborPos, neighborState)) continue;
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
