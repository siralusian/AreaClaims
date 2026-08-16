package com.areaclaims.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * {@code NativeImage.read(byte[])} ist für alles außer winzigen Dateien unsicher: es kopiert das
 * GESAMTE Array auf LWJGLs MemoryStack (Standard 64 KiB pro Thread, siehe
 * {@code MemoryStack.DEFAULT_STACK_SIZE}) - jedes größere Bild überläuft diesen Stack mit
 * "OutOfMemoryError: Out of stack space" (reproduzierbar auf jedem Thread, unabhängig von der
 * JVM-Heap-Größe). Vanillas eigener {@code read(InputStream)}-Überladung umgeht das, indem sie in
 * einen normalen (Off-Heap, NICHT Stack) {@code MemoryUtil}-Puffer statt des Stacks kopiert - dasselbe
 * hier für ein bereits vorhandenes {@code byte[]} (aus Datei/Netzwerk).
 *
 * <p><b>Diese exakte Falle wurde bereits einmal in CopycatSign gefunden und behoben</b> (siehe
 * {@code CopycatSign/src/main/java/com/copycatsign/client/NativeImageDecoder.java}, vom Nutzer als
 * Vorlage benannt) - hier bewusst 1:1 übernommen, um denselben Absturz nicht in einer unabhängigen
 * Parallel-Implementierung erneut zu reproduzieren.
 */
public final class NativeImageDecoder {

    private NativeImageDecoder() {}

    public static NativeImage read(byte[] bytes) throws IOException {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes);
            buffer.rewind();
            return NativeImage.read(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
