package com.areaclaims.image;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Punkt 6 (Nachtrag 4, großes exploratives Bild-Feature "just to see if it looks good", Nutzer-
 * Vorgabe): pro-Welt-Metadaten für jedes von Spielern hochgeladene Claim-Bild - modelliert auf
 * CopycatSigns bereits bewährter, funktionierender Upload-Pipeline
 * ({@code CopycatSign/src/main/java/com/copycatsign/image/ServerImageStore.java} u. a., vom Nutzer
 * explizit als Vorlage benannt), aber als FRISCHE, EIGENSTÄNDIGE Parallel-Implementierung - AreaClaims
 * bleibt komplett unabhängig von CopycatSign, keine Gradle-Abhängigkeit, kein gemeinsamer Code, nur
 * dasselbe bewährte ARCHITEKTUR-MUSTER (Metadaten getrennt von den eigentlichen Bytes, Inhalts-Hash
 * als Schlüssel, verteiltes Upload/Download-Paket-Protokoll) neu abgetippt.
 *
 * <p>Bilder sind über den SHA-256-Hash ihrer (bereits serverseitig auf eine feste Basis-Höhe
 * skalierten, siehe {@code ImageUploadPacket#finish}) PNG-Bytes indiziert, NICHT über eine
 * Zufalls-ID - zwei Spieler, die dasselbe Bild hochladen (oder derselbe Spieler zweimal dieselbe
 * Datei), fallen automatisch auf EINEN gespeicherten Eintrag zusammen (natürliche Deduplizierung).
 *
 * <p><b>Wichtiger Unterschied zu CopycatSign (Nutzer-Vorgabe, von Anfang an eingebaut statt
 * nachgerüstet):</b> CopycatSigns {@code ServerImageStore} kennt nur {@code get(hash)}/{@code put(...)},
 * KEINE Auflistung aller gespeicherten Bilder - der Nutzer möchte hier ausdrücklich eine
 * DURCHSTÖBERBARE GALERIE bereits hochgeladener Bilder (damit ein Claim-Besitzer ein bestehendes
 * Bild wiederverwenden kann, statt es jedes Mal neu hochzuladen) - das gibt es in CopycatSign
 * ebenfalls noch nicht (bestätigt außerhalb des dortigen Umfangs), es gab also keine Vorlage zum
 * Abschreiben. Diese Klasse bekommt deshalb {@link #getAll()} von Anfang an.
 *
 * <p>Persistenz bewusst als Gson-JSON-Datei im Weltordner (statt CopycatSigns
 * {@code SavedData}/NBT-Ansatz) - konsistent mit dem einzigen in DIESEM Mod etablierten
 * Persistenz-Stil (siehe {@code ClaimManager}/{@code PriceConfigManager}), kein neues Muster
 * eingeführt nur um CopycatSigns internen Persistenz-Mechanismus 1:1 zu spiegeln.
 */
public final class ServerImageStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path dataFile;
    private static final Map<String, ImageMetadata> images = new HashMap<>();
    private static ImageFileCache fileCache;

    private ServerImageStore() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("areaclaims_images.json");
        fileCache = new ImageFileCache(server.getWorldPath(LevelResource.ROOT).resolve("areaclaims_images"));
        load();
    }

    public static ImageFileCache fileCache() {
        return fileCache;
    }

    public static Optional<ImageMetadata> get(String hash) {
        return Optional.ofNullable(images.get(hash));
    }

    /** Punkt 6 (Nachtrag 4): durchstöberbare Galerie - siehe Klassenkommentar "Wichtiger Unterschied zu CopycatSign". Kopie der Werte, damit Aufrufer nicht versehentlich die interne Map mutieren. */
    public static Collection<ImageMetadata> getAll() {
        return new java.util.ArrayList<>(images.values());
    }

    public static void put(ImageMetadata metadata) {
        images.put(metadata.hash, metadata);
        save();
    }

    /**
     * Punkt 2 (Nachtrag 6): tatsächliches Löschen - ersetzt/ergänzt die in Nachtrag 5 (Punkt 8)
     * bewusst auf reine Sichtbarkeit beschränkte Admin-Übersicht. Entfernt Metadaten UND die
     * eigentliche Datei. Löscht bewusst UNABHÄNGIG davon, ob das Bild gerade irgendwo zugewiesen
     * ist - die Admin-Entscheidung wird nicht bevormundet (der Screen zeigt Nutzung bereits deutlich
     * an); betroffene Claims fallen beim nächsten Anzeige-Update einfach auf ihren Textnamen zurück
     * (siehe {@code EntryDisplayOverlayRenderer#renderNameOrImageSlot} - ein fehlendes Bild löst dort
     * bereits einen stillen Text-Fallback aus, kein Fehler/Absturz).
     */
    public static void delete(String hash) {
        images.remove(hash);
        save();
        if (fileCache != null) fileCache.delete(hash);
    }

    private static class Data {
        Map<String, ImageMetadata> images = new HashMap<>();
    }

    private static void load() {
        images.clear();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null && data.images != null) images.putAll(data.images);
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        Data data = new Data();
        data.images.putAll(images);
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
