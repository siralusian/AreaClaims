package com.areaclaims;

import com.areaclaims.claim.ClaimManager;
import com.areaclaims.commands.AreaClaimsCommands;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.economy.PriceConfigManager;
import com.areaclaims.event.ClaimEntryListener;
import com.areaclaims.event.ClaimProtectionListener;
import com.areaclaims.event.ToolInteractionListener;
import com.areaclaims.integration.CobblemonAvailability;
import com.areaclaims.integration.ModAvailability;
import com.areaclaims.network.BeginSelectionPacket;
import com.areaclaims.network.BuyoutRulePacket;
import com.areaclaims.network.ClaimSyncPacket;
import com.areaclaims.network.ConfirmPendingActionPacket;
import com.areaclaims.network.DeleteClaimPacket;
import com.areaclaims.network.DiscardPendingActionPacket;
import com.areaclaims.network.EndSelectionPacket;
import com.areaclaims.network.EnterSelectionModePacket;
import com.areaclaims.network.OpenEditorPacket;
import com.areaclaims.network.RenameClaimPacket;
import com.areaclaims.network.RequestServerConfigPacket;
import com.areaclaims.network.ResumePendingActionPacket;
import com.areaclaims.network.ServerConfigSyncPacket;
import com.areaclaims.network.SetBoundaryColorOverridePacket;
import com.areaclaims.network.SetEntryMessagePacket;
import com.areaclaims.network.SetPriceRowPacket;
import com.areaclaims.network.SetRolePacket;
import com.areaclaims.network.SetRulePacket;
import com.areaclaims.network.SetServerConfigPacket;
import com.areaclaims.network.SetShowcaseModePacket;
import com.areaclaims.network.ShowClaimPacket;
import com.areaclaims.network.ShowPriceConfirmPacket;
import com.areaclaims.network.ShowcaseGeometrySyncPacket;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(AreaClaims.MOD_ID)
public class AreaClaims {

    public static final String MOD_ID = "areaclaims";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AreaClaims(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.register(new ToolInteractionListener());
        NeoForge.EVENT_BUS.register(new ClaimEntryListener());
        NeoForge.EVENT_BUS.register(new ClaimProtectionListener());
        LOGGER.info("AreaClaims loading...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("AreaClaims common setup complete.");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.optional()
            .playToClient(
                OpenEditorPacket.TYPE,
                OpenEditorPacket.CODEC,
                OpenEditorPacket::handle)
            .playToClient(
                ClaimSyncPacket.TYPE,
                ClaimSyncPacket.CODEC,
                ClaimSyncPacket::handle)
            .playToServer(
                SetRolePacket.TYPE,
                SetRolePacket.CODEC,
                SetRolePacket::handle)
            .playToServer(
                SetRulePacket.TYPE,
                SetRulePacket.CODEC,
                SetRulePacket::handle)
            .playToServer(
                DeleteClaimPacket.TYPE,
                DeleteClaimPacket.CODEC,
                DeleteClaimPacket::handle)
            .playToServer(
                ShowClaimPacket.TYPE,
                ShowClaimPacket.CODEC,
                ShowClaimPacket::handle)
            .playToClient(
                ServerConfigSyncPacket.TYPE,
                ServerConfigSyncPacket.CODEC,
                ServerConfigSyncPacket::handle)
            .playToServer(
                RequestServerConfigPacket.TYPE,
                RequestServerConfigPacket.CODEC,
                RequestServerConfigPacket::handle)
            .playToServer(
                SetServerConfigPacket.TYPE,
                SetServerConfigPacket.CODEC,
                SetServerConfigPacket::handle)
            .playToClient(
                ShowcaseGeometrySyncPacket.TYPE,
                ShowcaseGeometrySyncPacket.CODEC,
                ShowcaseGeometrySyncPacket::handle)
            .playToClient(
                com.areaclaims.network.ClaimMapSyncPacket.TYPE,
                com.areaclaims.network.ClaimMapSyncPacket.CODEC,
                com.areaclaims.network.ClaimMapSyncPacket::handle)
            .playToServer(
                SetShowcaseModePacket.TYPE,
                SetShowcaseModePacket.CODEC,
                SetShowcaseModePacket::handle)
            .playToServer(
                RenameClaimPacket.TYPE,
                RenameClaimPacket.CODEC,
                RenameClaimPacket::handle)
            .playToServer(
                SetEntryMessagePacket.TYPE,
                SetEntryMessagePacket.CODEC,
                SetEntryMessagePacket::handle)
            .playToServer(
                SetPriceRowPacket.TYPE,
                SetPriceRowPacket.CODEC,
                SetPriceRowPacket::handle)
            // ROADMAP.md Phase 7-8 "Klick-zum-Abstecken + Preisbestätigung" - ersetzt die
            // vorherigen AddClaimPartPacket/CreateSubClaimPacket (direkt-mutierende GUI-Pakete)
            // komplett, siehe StakingService-Klassenkommentar für den vollen Ablauf.
            .playToServer(
                BeginSelectionPacket.TYPE,
                BeginSelectionPacket.CODEC,
                BeginSelectionPacket::handle)
            .playToServer(
                EndSelectionPacket.TYPE,
                EndSelectionPacket.CODEC,
                EndSelectionPacket::handle)
            .playToClient(
                EnterSelectionModePacket.TYPE,
                EnterSelectionModePacket.CODEC,
                EnterSelectionModePacket::handle)
            .playToClient(
                ShowPriceConfirmPacket.TYPE,
                ShowPriceConfirmPacket.CODEC,
                ShowPriceConfirmPacket::handle)
            .playToServer(
                ConfirmPendingActionPacket.TYPE,
                ConfirmPendingActionPacket.CODEC,
                ConfirmPendingActionPacket::handle)
            .playToServer(
                ResumePendingActionPacket.TYPE,
                ResumePendingActionPacket.CODEC,
                ResumePendingActionPacket::handle)
            .playToServer(
                DiscardPendingActionPacket.TYPE,
                DiscardPendingActionPacket.CODEC,
                DiscardPendingActionPacket::handle)
            // ROADMAP.md Phase 7-Nachtrag "Punkt 4/5: Kaufen-Button + Einstellungen-Popup".
            .playToServer(
                BuyoutRulePacket.TYPE,
                BuyoutRulePacket.CODEC,
                BuyoutRulePacket::handle)
            .playToServer(
                SetBoundaryColorOverridePacket.TYPE,
                SetBoundaryColorOverridePacket.CODEC,
                SetBoundaryColorOverridePacket::handle)
            // ROADMAP.md Phase 7-Nachtrag 3, Punkte 13-15 "Betreten-Nachricht-Overhaul": persönliche
            // Anzeige-Einstellungen (Dauer/Position/Dauerhaft) je Spieler + das neue, personalisierte
            // Betreten-Nachricht-Overlay - ersetzt Vanillas Titel-/Untertitel-/Actionbar-Pakete für
            // diesen Zweck komplett, siehe ClaimEntryListener/EntryDisplaySlotPacket-Klassenkommentare.
            .playToServer(
                com.areaclaims.network.RequestDisplayPrefsPacket.TYPE,
                com.areaclaims.network.RequestDisplayPrefsPacket.CODEC,
                com.areaclaims.network.RequestDisplayPrefsPacket::handle)
            .playToClient(
                com.areaclaims.network.DisplayPrefsSyncPacket.TYPE,
                com.areaclaims.network.DisplayPrefsSyncPacket.CODEC,
                com.areaclaims.network.DisplayPrefsSyncPacket::handle)
            .playToServer(
                com.areaclaims.network.SetDisplayPrefsPacket.TYPE,
                com.areaclaims.network.SetDisplayPrefsPacket.CODEC,
                com.areaclaims.network.SetDisplayPrefsPacket::handle)
            .playToClient(
                com.areaclaims.network.EntryDisplaySlotPacket.TYPE,
                com.areaclaims.network.EntryDisplaySlotPacket.CODEC,
                com.areaclaims.network.EntryDisplaySlotPacket::handle)
            // ROADMAP.md Phase 7-Nachtrag 4, Punkt 6 "Bild statt Text" - Upload-/Download-/Galerie-
            // Protokoll, modelliert auf CopycatSigns bewährter Bild-Upload-Pipeline (siehe
            // ServerImageStore-Klassenkommentar für die "eigenständige Parallel-Implementierung"-
            // Begründung, AreaClaims bleibt unabhängig von CopycatSign).
            .playToServer(
                com.areaclaims.network.ImageUploadPacket.TYPE,
                com.areaclaims.network.ImageUploadPacket.CODEC,
                com.areaclaims.network.ImageUploadPacket::handle)
            .playToClient(
                com.areaclaims.network.ImageUploadResultPacket.TYPE,
                com.areaclaims.network.ImageUploadResultPacket.CODEC,
                com.areaclaims.network.ImageUploadResultPacket::handle)
            .playToServer(
                com.areaclaims.network.ImageRequestPacket.TYPE,
                com.areaclaims.network.ImageRequestPacket.CODEC,
                com.areaclaims.network.ImageRequestPacket::handle)
            .playToClient(
                com.areaclaims.network.ImageDataResponsePacket.TYPE,
                com.areaclaims.network.ImageDataResponsePacket.CODEC,
                com.areaclaims.network.ImageDataResponsePacket::handle)
            .playToServer(
                com.areaclaims.network.ImageGalleryRequestPacket.TYPE,
                com.areaclaims.network.ImageGalleryRequestPacket.CODEC,
                com.areaclaims.network.ImageGalleryRequestPacket::handle)
            .playToClient(
                com.areaclaims.network.ImageGallerySyncPacket.TYPE,
                com.areaclaims.network.ImageGallerySyncPacket.CODEC,
                com.areaclaims.network.ImageGallerySyncPacket::handle)
            .playToServer(
                com.areaclaims.network.SetClaimImagePacket.TYPE,
                com.areaclaims.network.SetClaimImagePacket.CODEC,
                com.areaclaims.network.SetClaimImagePacket::handle)
            // Punkt 8 (Nachtrag 5): Admin-Übersicht "welche Claims nutzen welches Bild".
            .playToServer(
                com.areaclaims.network.ImageUsageRequestPacket.TYPE,
                com.areaclaims.network.ImageUsageRequestPacket.CODEC,
                com.areaclaims.network.ImageUsageRequestPacket::handle)
            .playToClient(
                com.areaclaims.network.ImageUsageSyncPacket.TYPE,
                com.areaclaims.network.ImageUsageSyncPacket.CODEC,
                com.areaclaims.network.ImageUsageSyncPacket::handle)
            // Punkt 2 (Nachtrag 6): tatsächliches Löschen aus der Bild-Nutzungsübersicht.
            .playToServer(
                com.areaclaims.network.DeleteImagePacket.TYPE,
                com.areaclaims.network.DeleteImagePacket.CODEC,
                com.areaclaims.network.DeleteImagePacket::handle)
            // Nutzer-Vorgabe (2026-08-18, "Anpassen"-Block-Einfärbung statt Partikel-Linie).
            .playToClient(
                com.areaclaims.network.AdjustPreviewSyncPacket.TYPE,
                com.areaclaims.network.AdjustPreviewSyncPacket.CODEC,
                com.areaclaims.network.AdjustPreviewSyncPacket::handle);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AreaClaimsCommands.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        ModAvailability.refresh();
        CobblemonAvailability.refresh();
        if (CobblemonAvailability.isAvailable()) {
            // Nur bei tatsächlich installiertem Cobblemon abonnieren - com.areaclaims.integration.CobblemonBridge
            // ist die einzige Klasse, die com.cobblemon.mod.common.api.* referenziert, siehe deren
            // Klassenkommentar (ROADMAP.md Phase 6-Nachtrag 2 "Cobblemon-Fang-Verifizierung").
            com.areaclaims.integration.CobblemonBridge.subscribeIfNeeded();
        }
        ClaimManager.init(event.getServer());
        FeatureConfigManager.init(event.getServer());
        PriceConfigManager.init(event.getServer());
        com.areaclaims.display.PlayerDisplayPreferencesManager.init(event.getServer());
        com.areaclaims.image.ServerImageStore.init(event.getServer());
    }
}
