package com.areaclaims.network;

import com.areaclaims.AreaClaims;
import com.areaclaims.display.PlayerDisplayPreferences;
import com.areaclaims.display.PlayerDisplayPreferencesManager;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: speichert die persönlichen {@link PlayerDisplayPreferences} des SENDENDEN
 * Spielers komplett auf einmal (ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15) - gleiches
 * "komplette Zeile/kompletter Datensatz als JSON"-Muster wie {@link SetPriceRowPacket}. Läuft immer
 * gegen {@code context.player()} selbst, NIE gegen eine vom Client mitgeschickte Ziel-UUID (jeder
 * Spieler darf ausschließlich seine EIGENEN Einstellungen ändern - kein Berechtigungsproblem, aber
 * ein Manipulationsschutz).
 */
public record SetDisplayPrefsPacket(String prefsJson) implements CustomPacketPayload {

    private static final Gson GSON = new Gson();

    public static final CustomPacketPayload.Type<SetDisplayPrefsPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AreaClaims.MOD_ID, "set_display_prefs"));

    public static final StreamCodec<ByteBuf, SetDisplayPrefsPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(SetDisplayPrefsPacket::new, SetDisplayPrefsPacket::prefsJson);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetDisplayPrefsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer caller)) return;
            PlayerDisplayPreferences prefs;
            try {
                prefs = GSON.fromJson(packet.prefsJson(), PlayerDisplayPreferences.class);
            } catch (RuntimeException e) {
                return;
            }
            if (prefs == null) return;
            clamp(prefs);
            PlayerDisplayPreferencesManager.set(caller.getUUID(), prefs);
            DisplayPrefsSnapshotBuilder.sendTo(caller);
        });
    }

    /** Verteidigung in der Tiefe gegen manipulierte/kaputte Client-Werte - negative/Null-Dauer wäre eine sofort wieder unsichtbare Anzeige. */
    private static void clamp(PlayerDisplayPreferences prefs) {
        if (prefs.mainClaim == null) prefs.mainClaim = PlayerDisplayPreferences.defaultMain();
        if (prefs.subClaim == null) prefs.subClaim = PlayerDisplayPreferences.defaultSub();
        if (prefs.welcome == null) prefs.welcome = PlayerDisplayPreferences.defaultWelcome();
        prefs.mainClaim.durationTicks = Math.max(1, prefs.mainClaim.durationTicks);
        prefs.subClaim.durationTicks = Math.max(1, prefs.subClaim.durationTicks);
        prefs.welcome.durationTicks = Math.max(1, prefs.welcome.durationTicks);
    }
}
