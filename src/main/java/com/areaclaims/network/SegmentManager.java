package com.areaclaims.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Punkt 6 (Nachtrag 4, Bild-Upload): setzt einen über mehrere Pakete verteilten Upload/Download
 * (siehe {@link ChunkSender}) pro Absender-Schlüssel wieder zusammen. Eigene, unabhängige Kopie
 * nach dem Vorbild von CopycatSigns gleichnamiger Klasse - siehe {@link ChunkSender}-Klassenkommentar
 * für die Begründung, warum das eine frische Parallel-Implementierung statt einer Abhängigkeit ist.
 */
public class SegmentManager {

    private final Map<String, ByteArrayOutputStream> buffer = new HashMap<>();

    public Optional<byte[]> handleSegmentedPayload(String key, byte[] data, int segment, int totalSegments) {
        ByteArrayOutputStream stream = buffer.computeIfAbsent(key, k -> new ByteArrayOutputStream());
        try {
            stream.write(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (segment + 1 == totalSegments) {
            buffer.remove(key);
            return Optional.of(stream.toByteArray());
        }
        return Optional.empty();
    }
}
