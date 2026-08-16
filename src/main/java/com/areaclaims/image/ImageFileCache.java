package com.areaclaims.image;

import com.areaclaims.AreaClaims;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Flach abgelegter Datei-Speicher für die kodierten PNG-Bytes hochgeladener Claim-Bilder, indiziert
 * über den Inhalts-Hash (siehe {@link ServerImageStore}). Bewusst reines Datei-I/O ohne eigene
 * Zwischenspeicherung im Arbeitsspeicher - Uploads sind selten (nur bei Spieleraktion), Lesevorgänge
 * passieren nur, wenn ein Client ein Bild anfragt, das er noch nicht hat (siehe
 * {@code ImageRequestPacket}) - kein heißer Pfad, der Caching bräuchte.
 *
 * <p>Eigene, unabhängige Kopie nach dem Vorbild von CopycatSigns gleichnamiger Klasse - siehe
 * {@link ServerImageStore}-Klassenkommentar für die Begründung ("frische Parallel-Implementierung,
 * keine Abhängigkeit").
 */
public final class ImageFileCache {

    private final Path directory;

    public ImageFileCache(Path directory) {
        this.directory = directory;
    }

    private Path pathFor(String hash) {
        return directory.resolve(hash + ".png");
    }

    public boolean exists(String hash) {
        return Files.isRegularFile(pathFor(hash));
    }

    public Optional<byte[]> get(String hash) {
        Path path = pathFor(hash);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            AreaClaims.LOGGER.error("could not read cached AreaClaims image {}", hash, e);
            return Optional.empty();
        }
    }

    public void set(String hash, byte[] data) {
        try {
            Files.createDirectories(directory);
            Files.write(pathFor(hash), data);
        } catch (IOException e) {
            AreaClaims.LOGGER.error("could not write cached AreaClaims image {}", hash, e);
        }
    }

    /** Punkt 2 (Nachtrag 6): tatsächliches Löschen für die Admin-Bild-Nutzungsübersicht (Nachtrag 5, Punkt 8, war dort bewusst nur Sichtbarkeit). */
    public void delete(String hash) {
        try {
            Files.deleteIfExists(pathFor(hash));
        } catch (IOException e) {
            AreaClaims.LOGGER.error("could not delete cached AreaClaims image {}", hash, e);
        }
    }
}
