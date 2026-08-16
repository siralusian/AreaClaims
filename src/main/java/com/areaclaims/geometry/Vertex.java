package com.areaclaims.geometry;

/**
 * Ein Eckpunkt eines XZ-Grundriss-Polygons (Blockkoordinaten, keine Y-Komponente - siehe
 * ROADMAP.md Phase 1: Claims sind reine 2D-Grundrisse und erstrecken sich immer über die
 * gesamte Weltbauhöhe).
 */
public record Vertex(int x, int z) {
}
