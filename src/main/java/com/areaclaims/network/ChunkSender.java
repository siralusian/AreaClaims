package com.areaclaims.network;

import java.util.Arrays;

/**
 * Punkt 6 (Nachtrag 4, Bild-Upload): geteilte Chunking-Konstante/Hilfe für Pakete, die {@code byte[]}
 * über mehrere Pakete verteilen (Bilder können mehrere MB groß sein - deutlich über der üblichen
 * Netty-Paketgrößen-Obergrenze). Eigene, unabhängige Kopie nach dem Vorbild von CopycatSigns
 * gleichnamiger Klasse ({@code CopycatSign/src/main/java/com/copycatsign/network/ChunkSender.java})
 * - AreaClaims hat KEINE Abhängigkeit zu CopycatSign, dies ist eine frische Parallel-Implementierung
 * desselben bewährten Musters, wie vom Nutzer explizit gefordert.
 */
public final class ChunkSender {

    public static final int MAX_CHUNK_SIZE = 32_000;

    private ChunkSender() {}

    public static int totalSegments(byte[] data) {
        return Math.max(1, (data.length + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
    }

    public static byte[] segment(byte[] data, int segment) {
        int from = segment * MAX_CHUNK_SIZE;
        int to = Math.min(data.length, from + MAX_CHUNK_SIZE);
        return Arrays.copyOfRange(data, from, to);
    }
}
