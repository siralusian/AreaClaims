package com.areaclaims.client.data;

import com.areaclaims.image.ImageMetadata;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/** Hält die zuletzt vom Server empfangene, durchstöberbare Bild-Galerie (siehe {@code ImageGallerySyncPacket}) für den {@code AreaClaimsImagePickerScreen}. */
public final class ClientImageGalleryCache {

    private static final Gson GSON = new Gson();
    private static List<ImageMetadata> images = new ArrayList<>();

    private ClientImageGalleryCache() {}

    public static void update(String json) {
        try {
            List<ImageMetadata> parsed = GSON.fromJson(json, new TypeToken<List<ImageMetadata>>() {}.getType());
            images = parsed != null ? parsed : new ArrayList<>();
        } catch (RuntimeException e) {
            images = new ArrayList<>();
        }
    }

    public static List<ImageMetadata> get() {
        return images;
    }
}
