package com.areaclaims.image;

import java.util.UUID;

/**
 * Alles über ein hochgeladenes Claim-Bild AUSSER den eigentlichen Pixeldaten (siehe
 * {@link ImageFileCache}) - Breite/Höhe sind die Maße NACH der serverseitigen Größenanpassung auf
 * eine feste Basis-Höhe (siehe {@link ServerImageStore}-Klassenkommentar "Punkt 6"), nicht die
 * Original-Upload-Maße. Reines POJO statt eines Records (Gson-Persistenz, gleicher Stil wie überall
 * sonst in diesem Mod - siehe {@code PriceConfigManager} für dasselbe Muster).
 */
public class ImageMetadata {
    public String hash;
    public int width;
    public int height;
    public String uploader;
    /** Für die Galerie-Anzeige vorgehalten, damit der Client nicht extra einen Namen auflösen muss (siehe {@code ImageGallerySyncPacket}). */
    public String uploaderName;

    public ImageMetadata() {}

    public ImageMetadata(String hash, int width, int height, UUID uploader, String uploaderName) {
        this.hash = hash;
        this.width = width;
        this.height = height;
        this.uploader = uploader.toString();
        this.uploaderName = uploaderName;
    }
}
