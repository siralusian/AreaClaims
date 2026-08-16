package com.areaclaims.client;

import com.areaclaims.network.ChunkSender;
import com.areaclaims.network.ImageUploadPacket;
import net.neoforged.neoforge.network.PacketDistributor;

/** Nur clientseitig: zerlegt die rohen Bytes einer ausgewählten Datei in {@link ImageUploadPacket}-Segmente und verschickt sie. */
public final class ImageUploader {

    private ImageUploader() {}

    public static void send(byte[] data) {
        int total = ChunkSender.totalSegments(data);
        for (int segment = 0; segment < total; segment++) {
            PacketDistributor.sendToServer(new ImageUploadPacket(ChunkSender.segment(data, segment), segment, total));
        }
    }
}
