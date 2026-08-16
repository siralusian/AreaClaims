package com.areaclaims.client;

import com.areaclaims.AreaClaims;
import com.areaclaims.image.ImageFileCache;
import com.areaclaims.network.ImageDataResponsePacket;
import com.areaclaims.network.ImageRequestPacket;
import com.areaclaims.network.SegmentManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-seitiger Zwischenspeicher für Claim-Bilder, indiziert über den Inhalts-Hash (siehe
 * {@code ServerImageStore}). Ein von {@link com.areaclaims.client.EntryDisplayOverlayRenderer}
 * angefordertes Bild ist beim allerersten Aufruf sehr wahrscheinlich noch nicht geladen -
 * {@link #resolveTexture} liefert dann {@code null} und stößt (höchstens einmal pro Hash) erst
 * einen Festplatten-Cache-Check, dann bei Bedarf eine Serveranfrage an.
 *
 * <p>Modelliert auf CopycatSigns {@code ClientImageManager} (siehe {@code ServerImageStore}-
 * Klassenkommentar für die "eigenständige Parallel-Implementierung"-Begründung), aber bewusst
 * VEREINFACHT - AreaClaims braucht keine Silhouetten-/Rückseiten-/Material-Textur-Logik (das ist
 * CopycatSign-spezifisch für ausgeschnittene Schild-Bilder), nur eine einfache texturierte Fläche
 * für das Betreten-Nachricht-Overlay.
 *
 * <p><b>Nachtrag 6 (Bild-Qualität-Fund, "Pixel Suppe" trotz scharfer Quelldatei):</b>
 * {@link DynamicTexture} ruft NIRGENDS {@code setFilter(...)} auf (verifiziert in den dekompilierten
 * Sourcen) - sie erbt daher {@link net.minecraft.client.renderer.texture.AbstractTexture}s
 * Standardwerte ({@code blur=false, mipmap=false} = {@code GL_NEAREST}, hartes Pixel-Sampling ohne
 * jede Glättung) OHNE dass irgendjemand das je umgestellt hätte - hochgeladene Bilder wurden also
 * IMMER mit dem hartestmöglichen, unskalierten Nächster-Nachbar-Sampling gezeichnet, komplett
 * unabhängig von der tatsächlichen Quellbild-Schärfe. Genau das erzeugt die "blockige"-Optik,
 * besonders deutlich bei größerem Zoom (bis 500%, siehe {@code imageZoomPercent}). Fix: registrierte
 * Texturen bekommen jetzt explizit {@code setFilter(...)} - {@code blur=true} (glattes
 * {@code GL_LINEAR}) als NEUER STANDARD, siehe {@link #resolveTexture(String, boolean)}s
 * {@code pixelArt}-Parameter für den optionalen Gegenteil-Umschalter ("Pixelkunst", bewusst pro
 * Betrachter/Namens-Typ einstellbar wie Zoom/Bild-Anzeigen). Rein zur technischen Einordnung
 * (KEIN Code übernommen, nur die vom Nutzer genannte Referenz-Mod als Bestätigung gelesen, dass
 * "glatt per Standard + Pixelkunst als Opt-in" ein sinnvolles, bereits anderswo bewährtes Muster
 * ist - Immersive Paintings steht unter GPL-3.0, AreaClaims bleibt MIT-lizenziert und unabhängig).
 */
public final class ClientImageManager {

    /** Schlüssel = {@code hash + '|' + (pixelArt ? "pixel" : "smooth")} - siehe Klassenkommentar "Nachtrag 6": dieselbe Bilddatei kann gleichzeitig als GLATT-gefilterte UND als Pixelkunst-Textur im Umlauf sein, je nachdem, welche Betrachter sie mit welcher Einstellung anfordern. */
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, int[]> DIMENSIONS = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final SegmentManager SEGMENTS = new SegmentManager();
    // Wie CopycatSigns Vorlage: NativeImage.read darf NICHT auf dem Render-Thread laufen (siehe
    // NativeImageDecoder-Klassenkommentar) - Dekodierung läuft auf diesem eigenen Hintergrund-Thread,
    // nur die GL-anfassende DynamicTexture-Registrierung hüpft danach zurück zum Render-Thread.
    private static final ExecutorService DECODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "areaclaims-image-decode");
        thread.setDaemon(true);
        return thread;
    });
    private static ImageFileCache diskCache;

    private ClientImageManager() {}

    private static ImageFileCache diskCache() {
        if (diskCache == null) {
            diskCache = new ImageFileCache(Minecraft.getInstance().gameDirectory.toPath().resolve("areaclaims/imagecache"));
        }
        return diskCache;
    }

    /** Bequemlichkeits-Überladung für Aufrufer, denen der Filter-Modus egal ist (Galerie-/Übersicht-Vorschaubilder) - immer glatt gefiltert. */
    public static ResourceLocation resolveTexture(String hash) {
        return resolveTexture(hash, false);
    }

    /**
     * {@code null} = "noch nicht bereit" - eine Anfrage kann bereits laufen, im nächsten Frame
     * erneut prüfen. {@code pixelArt}: siehe Klassenkommentar "Nachtrag 6" - {@code false} (glatt,
     * NEUER Standard) für die meisten Aufrufer, {@code true} nur wenn der Betrachter für DIESEN
     * Namens-Typ ausdrücklich "Pixelkunst" aktiviert hat.
     */
    public static ResourceLocation resolveTexture(String hash, boolean pixelArt) {
        if (hash == null || hash.isBlank()) return null;
        String cacheKey = cacheKey(hash, pixelArt);
        ResourceLocation cached = TEXTURES.get(cacheKey);
        if (cached != null) return cached;
        if (!REQUESTED.add(cacheKey)) return null;

        Optional<byte[]> onDisk = diskCache().get(hash);
        if (onDisk.isPresent()) {
            registerTexture(hash, onDisk.get(), pixelArt);
            return null;
        }

        // Nur EINMAL pro Hash tatsächlich anfragen, unabhängig davon, wie viele Filter-Modi
        // gleichzeitig angefordert werden - REQUESTED ist zwar pro cacheKey (Hash+Modus) geführt,
        // ein bereits für den ANDEREN Modus laufender Server-Download liefert dieselben Bytes für
        // beide Modi (siehe handleResponse, das für den Hash EINMAL herunterlädt, aber hier ggf.
        // erneut anfragt, falls der zweite Modus zuerst dran ist - harmlose doppelte Anfrage im
        // Randfall, kein Korrektheitsproblem).
        PacketDistributor.sendToServer(new ImageRequestPacket(hash));
        return null;
    }

    private static String cacheKey(String hash, boolean pixelArt) {
        return hash + '|' + (pixelArt ? "pixel" : "smooth");
    }

    /** Ursprüngliche Pixel-Breite/Höhe des Bildes (für Seitenverhältnis-korrektes Zeichnen) - {@code null}, solange nicht geladen. Unabhängig vom Filter-Modus (beide zeigen dieselben Maße). */
    public static int[] dimensions(String hash) {
        return DIMENSIONS.get(hash);
    }

    public static void handleResponse(ImageDataResponsePacket packet) {
        if (packet.totalSegments() == 0) {
            AreaClaims.LOGGER.warn("server has no AreaClaims image stored for hash {}", packet.hash());
            return;
        }
        SEGMENTS.handleSegmentedPayload(packet.hash(), packet.data(), packet.segment(), packet.totalSegments())
            .ifPresent(bytes -> {
                diskCache().set(packet.hash(), bytes);
                // Beide Modi registrieren, die tatsächlich angefordert wurden (siehe REQUESTED) -
                // einfacher/robuster als nachzuverfolgen, WELCHER Modus konkret diese Antwort ausgelöst
                // hat; unnötige Registrierungen kosten hier nur eine zusätzliche (billige) Textur-
                // Erzeugung für einen Modus, den am Ende doch niemand anzeigt.
                if (REQUESTED.contains(cacheKey(packet.hash(), false))) registerTexture(packet.hash(), bytes, false);
                if (REQUESTED.contains(cacheKey(packet.hash(), true))) registerTexture(packet.hash(), bytes, true);
            });
    }

    private static void registerTexture(String hash, byte[] pngBytes, boolean pixelArt) {
        DECODE_EXECUTOR.execute(() -> {
            NativeImage image;
            try {
                image = NativeImageDecoder.read(pngBytes);
            } catch (java.io.IOException e) {
                AreaClaims.LOGGER.error("could not decode AreaClaims image {}", hash, e);
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            Minecraft.getInstance().execute(() -> {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID,
                    "claim_image/" + hash + (pixelArt ? "_pixel" : "_smooth"));
                DynamicTexture texture = new DynamicTexture(image);
                // Nachtrag 6: der eigentliche Fix - siehe Klassenkommentar. blur=true (GL_LINEAR) ist
                // der neue Standard (pixelArt=false), blur=false (GL_NEAREST) nur im expliziten
                // Pixelkunst-Opt-in. mipmap bewusst immer false - diese Texturen werden nie mit einer
                // vollen Mip-Kette hochgeladen (nur Stufe 0, siehe NativeImage#upload-Aufruf in
                // DynamicTexture#upload), mipmap=true ohne echte Mip-Stufen würde fehlerhaftes
                // Sampling riskieren.
                texture.setFilter(!pixelArt, false);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                TEXTURES.put(cacheKey(hash, pixelArt), id);
                DIMENSIONS.put(hash, new int[] {width, height});
            });
        });
    }
}
