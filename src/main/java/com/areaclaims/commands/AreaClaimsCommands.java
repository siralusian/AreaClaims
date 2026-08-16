package com.areaclaims.commands;

import com.areaclaims.claim.Claim;
import com.areaclaims.claim.ClaimEditService;
import com.areaclaims.claim.ClaimManager;
import com.areaclaims.claim.ClaimRole;
import com.areaclaims.claim.EntryMessageService;
import com.areaclaims.claim.RuleType;
import com.areaclaims.data.AdminViewManager;
import com.areaclaims.data.ClaimShowcaseManager;
import com.areaclaims.data.FeatureConfigManager;
import com.areaclaims.economy.AdminConfigService;
import com.areaclaims.economy.PriceCharger;
import com.areaclaims.economy.PriceConfig;
import com.areaclaims.economy.PriceConfigManager;
import com.areaclaims.integration.ModAvailability;
import com.areaclaims.network.ClaimSnapshotBuilder;
import com.areaclaims.network.OpenEditorPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Verbleibende Chat-Befehle nach ROADMAP.md Phase 7 ("Klick-zum-Abstecken"): {@code claim}/
 * {@code expand}/{@code subclaim}/{@code tool} gibt es NICHT mehr - Claims anlegen/erweitern
 * läuft jetzt AUSSCHLIESSLICH über die GUI-Buttons + Klick-in-der-Welt-Interaktion (siehe
 * {@link com.areaclaims.claim.StakingService}). {@code cancel} bleibt als Sicherheitsventil.
 * role/rule sind auf den Claim-Besitzer bzw. Vanilla-OP-Stufe 4 beschränkt (siehe ClaimEditService -
 * die frühere eigenständige Admin-Liste wurde auf Nutzer-Vorgabe entfernt, siehe ROADMAP.md Phase 0).
 * {@code config} (Preise/Freischaltungsschwellen setzen) ist OP-Stufe-4-Admin-Werkzeug, per Befehl
 * UND per GUI (siehe {@code AreaClaimsServerConfigScreen}, ROADMAP.md Phase 6-Nachtrag "Admin-GUI").
 */
public class AreaClaimsCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> main = dispatcher.register(
            Commands.literal("areaclaims")
                .then(Commands.literal("open")
                    // Ab Phase 5 zeigt der Editor die EIGENEN Claims des öffnenden Spielers (nicht
                    // mehr nur ein Admin-Platzhalter) - deshalb für alle Spieler offen, nicht nur
                    // Admins. Admins sehen hier (noch) keine fremden Claims, siehe ROADMAP.md Phase 5.
                    .requires(source -> source.getPlayer() != null)
                    .executes(ctx -> {
                        openEditor(ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("cancel")
                    .requires(source -> source.getPlayer() != null)
                    .executes(ctx -> {
                        cancelSelection(ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("delete")
                    .requires(source -> source.getPlayer() != null)
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            deleteClaim(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
                .then(Commands.literal("show")
                    .requires(source -> source.getPlayer() != null)
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            showClaim(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
                .then(Commands.literal("showmode")
                    .requires(source -> source.getPlayer() != null)
                    .executes(ctx -> {
                        toggleShowcaseMode(ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("admin")
                    // Schaltet den Admin-Modus des GUI-Editors um (ROADMAP.md Phase 6-Nachtrag
                    // "Admin-GUI") - zeigt danach ALLE Claims des Servers statt nur eigene/
                    // Mitgliedschafts-Claims, siehe ClaimSnapshotBuilder#build.
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> {
                        toggleAdminView(ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("buyout")
                    .requires(source -> source.getPlayer() != null)
                    .then(Commands.argument("claim", StringArgumentType.word())
                        .then(Commands.argument("rule", StringArgumentType.word())
                            .executes(ctx -> {
                                buyoutRule(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "claim"),
                                    StringArgumentType.getString(ctx, "rule"));
                                return 1;
                            }))))
                .then(Commands.literal("role")
                    .requires(source -> source.getPlayer() != null)
                    .then(Commands.argument("claim", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> {
                                    setRole(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "claim"),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "role"));
                                    return 1;
                                })))))
                .then(Commands.literal("rule")
                    .requires(source -> source.getPlayer() != null)
                    .then(Commands.argument("claim", StringArgumentType.word())
                        .then(Commands.argument("rule", StringArgumentType.word())
                            .then(Commands.literal("disable")
                                .executes(ctx -> {
                                    setRule(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "claim"),
                                        StringArgumentType.getString(ctx, "rule"),
                                        false, ClaimRole.COOWNER);
                                    return 1;
                                }))
                            .then(Commands.literal("enable")
                                .then(Commands.argument("minRole", StringArgumentType.word())
                                    .executes(ctx -> {
                                        setRule(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "claim"),
                                            StringArgumentType.getString(ctx, "rule"),
                                            true, parseRole(StringArgumentType.getString(ctx, "minRole")));
                                        return 1;
                                    }))))))
                .then(Commands.literal("entrymsg")
                    .requires(source -> source.getPlayer() != null)
                    .then(entryMsgNode()))
                .then(Commands.literal("rename")
                    .requires(source -> source.getPlayer() != null)
                    .then(renameNode()))
                .then(Commands.literal("config")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.literal("feature")
                        .then(featureNode("createclaim", FeatureConfigManager::setMinOpLevelCreateClaim, FeatureConfigManager::minOpLevelCreateClaim))
                        .then(featureNode("expandclaim", FeatureConfigManager::setMinOpLevelExpandClaim, FeatureConfigManager::minOpLevelExpandClaim))
                        .then(featureNode("buyout", FeatureConfigManager::setMinOpLevelBuyout, FeatureConfigManager::minOpLevelBuyout)))
                    .then(Commands.literal("maxparts")
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int max = IntegerArgumentType.getInteger(ctx, "max");
                                AdminConfigService.setMaxClaimParts(max);
                                ctx.getSource().sendSuccess(() -> Component.translatable("areaclaims.command.config.maxparts_set", FeatureConfigManager.maxClaimParts()), true);
                                return 1;
                            })))
                    .then(Commands.literal("tintrange")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 8))
                            .executes(ctx -> {
                                int chunks = IntegerArgumentType.getInteger(ctx, "chunks");
                                AdminConfigService.setTintRangeChunks(chunks);
                                ctx.getSource().sendSuccess(() -> Component.translatable("areaclaims.command.config.tintrange_set", FeatureConfigManager.tintRangeChunks()), true);
                                return 1;
                            })))
                    .then(Commands.literal("price")
                        .then(attachPriceBranches(Commands.literal("block"),
                            (ctx, price) -> {
                                PriceConfigManager.setPerBlockPrice(price);
                                reportPriceSet(ctx.getSource(), "block", price);
                            }))
                        .then(Commands.literal("rule")
                            .then(attachPriceBranches(Commands.argument("rule", StringArgumentType.word()),
                                (ctx, price) -> {
                                    RuleType rule = ClaimEditService.parseRule(StringArgumentType.getString(ctx, "rule"));
                                    if (rule == null) {
                                        ctx.getSource().sendFailure(Component.translatable("areaclaims.command.rule.invalid", StringArgumentType.getString(ctx, "rule")));
                                        return;
                                    }
                                    PriceConfigManager.setRuleBuyoutPrice(rule, price);
                                    reportPriceSet(ctx.getSource(), rule.name(), price);
                                })))
                    )
                    .then(Commands.literal("show")
                        .executes(ctx -> {
                            showConfig(ctx.getSource());
                            return 1;
                        })))
        );

        dispatcher.register(Commands.literal("ac").redirect(main));
    }

    // ---------------------------------------------------------------- Phase 0

    private static void openEditor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        // Erst der Datenstand, DANN das "öffne den Screen"-Signal - der Screen liest beim init()
        // aus dem clientseitigen Cache, der muss also schon gefüllt sein, wenn er aufgeht.
        ClaimSnapshotBuilder.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenEditorPacket());
    }

    // ---------------------------------------------------------------- Phase 7 (Klick-zum-Abstecken - Reste)

    /**
     * Sicherheitsventil: bricht eine laufende Klick-zum-Abstecken-Auswahl (falls aktiv), eine
     * gemerkte "Auswahl merken"-Preisbestätigung (falls vorhanden) UND jede laufende "Grenzen
     * anzeigen"-Vorschau vollständig ab - EIN Befehl für alle drei "hängen gebliebenen"
     * visuellen/interaktiven Zustände (ROADMAP.md Phase 7-8 - {@code /areaclaims claim}/
     * {@code expand}/{@code subclaim}/{@code tool} gibt es nicht mehr, das läuft jetzt
     * ausschließlich über die GUI-Buttons, siehe {@link com.areaclaims.claim.StakingService}).
     */
    private static void cancelSelection(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        com.areaclaims.claim.StakingService.discardPending(player);
        ClaimShowcaseManager.stop(player.getUUID());
        source.sendSuccess(() -> Component.translatable("areaclaims.command.cancel"), false);
    }

    /** Löscht auch kaskadierend alle Unterbereiche, siehe {@link ClaimManager#deleteClaim}. Nutzt dieselbe {@link ClaimEditService}-Logik wie der GUI-Editor. */
    private static void deleteClaim(CommandSourceStack source, String name) {
        ServerPlayer caller = source.getPlayer();
        if (caller == null) return;
        Claim claim = findEditableClaim(caller, name);
        if (claim == null) {
            source.sendFailure(Component.translatable("areaclaims.command.claim.not_found", name));
            return;
        }
        ClaimEditService.Result result = ClaimEditService.deleteClaim(caller, claim);
        switch (result) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable("areaclaims.command.claim.deleted", name), true);
                ClaimSnapshotBuilder.sendTo(caller);
            }
            case NOT_AUTHORIZED -> source.sendFailure(Component.translatable("areaclaims.command.no_permission"));
            default -> {}
        }
    }

    /**
     * Schaltet die Partikel-Grenzen-Vorschau für einen BEREITS COMMITTETEN Claim um (siehe
     * {@link ClaimShowcaseManager} - kein Timer mehr, bleibt an bis explizit wieder
     * ausgeschaltet: derselbe Befehl auf denselben Claim, oder {@code /areaclaims cancel}).
     */
    private static void showClaim(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        Claim claim = findEditableClaim(player, name);
        if (claim == null) {
            source.sendFailure(Component.translatable("areaclaims.command.claim.not_found", name));
            return;
        }
        boolean nowOn = ClaimShowcaseManager.toggle(player.getUUID(), claim.id());
        source.sendSuccess(() -> Component.translatable(
            nowOn ? "areaclaims.command.show.started" : "areaclaims.command.show.stopped", claim.name()), false);
        com.areaclaims.network.ShowcaseGeometrySnapshotBuilder.sendTo(player);
    }

    /**
     * Schaltet den Anzeigestil (Partikel-Umriss vs. Block-Einfärbung, ROADMAP.md Phase 6-Nachtrag
     * 2, Punkt 7) für ALLE aktuell aktiven Showcases dieses Spielers um - GUI-Äquivalent:
     * "Anzeigestil"-Button im Editor, siehe {@link com.areaclaims.network.SetShowcaseModePacket}
     * (identische Logik, hier nur der Chat-Befehls-Zugang dazu).
     */
    private static void toggleShowcaseMode(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        var newMode = com.areaclaims.data.ClaimShowcaseManager.cycleMode(player.getUUID());
        com.areaclaims.network.ShowcaseGeometrySnapshotBuilder.sendTo(player);
        ClaimSnapshotBuilder.sendTo(player);
        source.sendSuccess(() -> Component.translatable(
            newMode == com.areaclaims.data.ClaimShowcaseManager.DisplayMode.TINT
                ? "areaclaims.command.showmode.tint" : "areaclaims.command.showmode.particles"), false);
    }

    private static void toggleAdminView(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        boolean nowActive = !AdminViewManager.isActive(player.getUUID());
        AdminViewManager.setActive(player.getUUID(), nowActive);
        ClaimSnapshotBuilder.sendTo(player);
        PacketDistributor.sendToPlayer(player, new OpenEditorPacket());
        source.sendSuccess(() -> Component.translatable(
            nowActive ? "areaclaims.command.admin.on" : "areaclaims.command.admin.off"), false);
    }

    // ---------------------------------------------------------------- Phase 6: Regel-Freikauf

    /**
     * Kauft eine Regel für den GANZEN Claim dauerhaft frei (siehe {@link Claim#boughtOutRules()}) -
     * ein SEPARATER geld-/itembasierter Mechanismus zusätzlich zum rollenbasierten Ignorieren aus
     * Phase 4. Eigener Freischaltungsschwellenwert ({@code minOpLevelBuyout}), da es sonst (Default-
     * Preis = kostenlos) ein unbeabsichtigt immer-verfügbares Feature wäre.
     */
    // Delegiert ab jetzt komplett an BuyoutService#buyout (ROADMAP.md Phase 7-Nachtrag, Punkt 4
    // "Kaufen-Button") - derselbe Umbau wie EntryMessageService/StakingService: Chat-Befehl und
    // das neue BuyoutRulePacket (GUI-"Kaufen"-Button) rufen jetzt exakt dieselbe Stelle.
    private static void buyoutRule(CommandSourceStack source, String claimName, String ruleName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        Claim claim = findEditableClaim(player, claimName);
        if (claim == null) {
            source.sendFailure(Component.translatable("areaclaims.command.claim.not_found", claimName));
            return;
        }
        RuleType rule = ClaimEditService.parseRule(ruleName);
        if (rule == null) {
            source.sendFailure(Component.translatable("areaclaims.command.rule.invalid", ruleName));
            return;
        }
        com.areaclaims.claim.BuyoutService.Outcome outcome = com.areaclaims.claim.BuyoutService.buyout(player, claim, rule);
        switch (outcome.result()) {
            case OK -> source.sendSuccess(() -> Component.translatable("areaclaims.command.buyout.success", rule.name(), claim.name()), true);
            case ALREADY_BOUGHT_OUT -> source.sendFailure(Component.translatable("areaclaims.command.buyout.already", rule.name(), claim.name()));
            case NOT_AUTHORIZED -> source.sendFailure(Component.translatable("areaclaims.command.no_permission"));
            case PRICE_FAILURE -> source.sendFailure(priceFailureMessage(outcome.priceResult()));
        }
    }

    private static Component priceFailureMessage(PriceCharger.Result result) {
        return switch (result) {
            case INSUFFICIENT_ITEM -> Component.translatable("areaclaims.command.price.insufficient_item");
            case INSUFFICIENT_COBBLE_DOLLARS -> Component.translatable("areaclaims.command.price.insufficient_dollars");
            case INSUFFICIENT_PRICE -> Component.translatable("areaclaims.command.price.insufficient_price");
            case UNAVAILABLE -> Component.translatable("areaclaims.command.price.unavailable");
            case MISCONFIGURED -> Component.translatable("areaclaims.command.price.misconfigured");
            case OK -> Component.empty();
        };
    }

    // ---------------------------------------------------------------- Phase 4

    // setRole/setRule delegieren die eigentliche Validierung/Mutation an ClaimEditService, damit
    // dieser Chat-Befehl und der GUI-Editor (SetRolePacket/SetRulePacket, siehe ROADMAP.md
    // Phase 5) sich garantiert identisch verhalten - siehe ClaimEditService-Klassenkommentar.

    private static void setRole(CommandSourceStack source, String claimName, String playerName, String roleName) {
        ServerPlayer caller = source.getPlayer();
        if (caller == null || source.getServer() == null) return;
        Claim claim = findEditableClaim(caller, claimName);
        if (claim == null) {
            source.sendFailure(Component.translatable("areaclaims.command.claim.not_found", claimName));
            return;
        }
        ClaimRole role = ClaimEditService.parseRole(roleName);
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.translatable("areaclaims.command.player_not_found", playerName));
            return;
        }
        ClaimEditService.Result result = ClaimEditService.applyRole(caller, claim, target, role);
        switch (result) {
            case OK -> source.sendSuccess(() -> Component.translatable("areaclaims.command.role.set", playerName, claim.name(), role.name()), true);
            case INVALID_ROLE -> source.sendFailure(Component.translatable("areaclaims.command.role.invalid", roleName));
            case NOT_AUTHORIZED -> source.sendFailure(Component.translatable("areaclaims.command.no_permission"));
            case CANNOT_TARGET_OWNER -> source.sendFailure(Component.translatable("areaclaims.command.role.cannot_target_owner"));
            default -> {}
        }
    }

    private static void setRule(CommandSourceStack source, String claimName, String ruleName, boolean enabled, ClaimRole minRole) {
        ServerPlayer caller = source.getPlayer();
        if (caller == null) return;
        Claim claim = findEditableClaim(caller, claimName);
        if (claim == null) {
            source.sendFailure(Component.translatable("areaclaims.command.claim.not_found", claimName));
            return;
        }
        if (enabled && minRole == null) {
            source.sendFailure(Component.translatable("areaclaims.command.role.invalid", "?"));
            return;
        }
        var rule = ClaimEditService.parseRule(ruleName);
        ClaimEditService.Result result = ClaimEditService.applyRule(caller, claim, rule, enabled, minRole);
        switch (result) {
            case OK -> source.sendSuccess(() -> Component.translatable(
                enabled ? "areaclaims.command.rule.set_enabled" : "areaclaims.command.rule.set_disabled",
                ruleName, claim.name(), minRole.name()), true);
            case INVALID_RULE -> source.sendFailure(Component.translatable("areaclaims.command.rule.invalid", ruleName));
            case NOT_AUTHORIZED -> source.sendFailure(Component.translatable("areaclaims.command.no_permission"));
            case RULE_NOT_BOUGHT_OUT -> source.sendFailure(Component.translatable("areaclaims.command.rule.not_bought_out"));
            default -> {}
        }
    }

    private static Claim findEditableClaim(ServerPlayer caller, String claimName) {
        // Frühere eigenständige Admin-Liste entfernt (Nutzer-Vorgabe) - Admin-Zugriff auf fremde
        // Claims läuft jetzt über die normale Vanilla-OP-Stufe 4, siehe ClaimEditService#canEdit.
        boolean admin = caller.hasPermissions(4);
        return ClaimManager.findMainClaimByNameForEditor(caller.getUUID(), admin, claimName);
    }

    private static ClaimRole parseRole(String roleName) {
        return ClaimEditService.parseRole(roleName);
    }

    // ---------------------------------------------------------------- Phase 6: Admin-Konfiguration

    private static LiteralArgumentBuilder<CommandSourceStack> featureNode(String name, IntConsumer setter, IntSupplier getter) {
        return Commands.literal(name)
            .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                .executes(ctx -> {
                    setter.accept(IntegerArgumentType.getInteger(ctx, "level"));
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                        "areaclaims.command.config.feature_set", name, getter.getAsInt()), true);
                    return 1;
                }));
    }

    /**
     * Hängt die drei gemeinsamen Preis-Unterbefehle (none/item/dollars) an einen beliebigen
     * Ast an - wiederverwendet für sowohl {@code price block} (direkt am Literal) als auch
     * {@code price rule <Regel>} (am Regel-Argument, das den Setter braucht, um zu wissen, WELCHE
     * Regel gerade konfiguriert wird - daher {@link CommandContext} statt nur {@link CommandSourceStack}
     * im Setter-Callback).
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T attachPriceBranches(
            T builder, BiConsumer<CommandContext<CommandSourceStack>, PriceConfig> setter) {
        builder.then(Commands.literal("none")
                .executes(ctx -> {
                    setter.accept(ctx, PriceConfig.none());
                    return 1;
                }))
            .then(Commands.literal("item")
                .then(Commands.argument("itemId", StringArgumentType.string())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            setPriceItem(ctx, setter,
                                StringArgumentType.getString(ctx, "itemId"),
                                IntegerArgumentType.getInteger(ctx, "amount"));
                            return 1;
                        }))))
            .then(Commands.literal("dollars")
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> {
                        setPriceDollars(ctx, setter, LongArgumentType.getLong(ctx, "amount"));
                        return 1;
                    })));
        return builder;
    }

    private static void setPriceItem(CommandContext<CommandSourceStack> ctx,
            BiConsumer<CommandContext<CommandSourceStack>, PriceConfig> setter, String itemId, int amount) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            ctx.getSource().sendFailure(Component.translatable("areaclaims.command.config.invalid_item", itemId));
            return;
        }
        setter.accept(ctx, PriceConfig.singleItem(itemId, amount));
    }

    private static void setPriceDollars(CommandContext<CommandSourceStack> ctx,
            BiConsumer<CommandContext<CommandSourceStack>, PriceConfig> setter, long amount) {
        // CobbleDollars ist NIEMALS Pflicht (siehe ROADMAP.md Phase 6) - ein Admin kann diese
        // Preis-Option nur wählen, wenn CobbleCompanion/CobbleDollars tatsächlich installiert ist.
        if (!ModAvailability.isCobbleDollarsAvailable()) {
            ctx.getSource().sendFailure(Component.translatable("areaclaims.command.config.cobbledollars_unavailable"));
            return;
        }
        setter.accept(ctx, PriceConfig.singleDollars(BigInteger.valueOf(amount)));
    }

    private static void reportPriceSet(CommandSourceStack source, String label, PriceConfig price) {
        source.sendSuccess(() -> Component.translatable("areaclaims.command.config.price_set", label, describePrice(price)), true);
    }

    /**
     * Beschreibt eine (seit ROADMAP.md Phase 6-Nachtrag 2 möglicherweise mehrkomponentige)
     * {@link PriceConfig} als Text - Komponenten durch ihren jeweils NACHFOLGENDEN Kombinator
     * (UND/ODER) getrennt, z. B. "5x minecraft:diamond UND 100 CobbleDollars".
     */
    private static Component describePrice(PriceConfig price) {
        if (price.isFree()) return Component.translatable("areaclaims.command.config.price_none");
        java.util.List<Component> parts = new java.util.ArrayList<>();
        if (price.hasDollars()) {
            parts.add(Component.translatable("areaclaims.command.config.price_dollars", price.dollars().toString()));
        }
        for (PriceConfig.ItemAmount item : price.items()) {
            parts.add(Component.translatable("areaclaims.command.config.price_item", item.amount(), item.itemId()));
        }
        java.util.List<PriceConfig.Combinator> combinators = price.normalized().combinators();
        net.minecraft.network.chat.MutableComponent result = parts.get(0).copy();
        for (int i = 1; i < parts.size(); i++) {
            Component op = Component.translatable(combinators.get(i - 1) == PriceConfig.Combinator.AND
                ? "areaclaims.serverconfig.combinator.and" : "areaclaims.serverconfig.combinator.or");
            result = result.append(Component.literal(" ")).append(op).append(Component.literal(" ")).append(parts.get(i));
        }
        return result;
    }

    private static void showConfig(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("areaclaims.command.config.show_features",
            FeatureConfigManager.minOpLevelCreateClaim(),
            FeatureConfigManager.minOpLevelExpandClaim(), FeatureConfigManager.minOpLevelBuyout()), false);
        source.sendSuccess(() -> Component.translatable("areaclaims.command.config.maxparts_set", FeatureConfigManager.maxClaimParts()), false);
        source.sendSuccess(() -> Component.translatable("areaclaims.command.config.tintrange_set", FeatureConfigManager.tintRangeChunks()), false);
        source.sendSuccess(() -> Component.translatable("areaclaims.command.config.price_set", "block", describePrice(PriceConfigManager.perBlockPrice())), false);
        for (RuleType rule : RuleType.values()) {
            PriceConfig price = PriceConfigManager.ruleBuyoutPrice(rule);
            if (!price.isFree()) {
                source.sendSuccess(() -> Component.translatable("areaclaims.command.config.price_set", rule.name(), describePrice(price)), false);
            }
        }
    }

    // ---------------------------------------------------------------- Phase 6-Nachtrag: Betreten-Nachricht-Konfiguration
    // Jeder Claim (Haupt- ODER Unterbereich) hat eigene Farb-/Dauer-/Willkommensnachricht-
    // Einstellungen (siehe Claim.java) - Befehle können Unterbereiche nicht direkt nach Name
    // finden (nur Hauptbereiche sind über findMainClaimByNameForEditor eindeutig ansprechbar),
    // daher "claim sub subName" als eigener Zweig statt eines optionalen mittleren Arguments.

    private static ArgumentBuilder<CommandSourceStack, ?> entryMsgNode() {
        var claimArg = Commands.argument("claim", StringArgumentType.word());
        attachEntryMsgFields(claimArg, AreaClaimsCommands::resolveMainForEntryMsg);
        claimArg.then(Commands.literal("sub")
            .then(attachEntryMsgFields(Commands.argument("subName", StringArgumentType.word()), AreaClaimsCommands::resolveSubForEntryMsg)));
        return claimArg;
    }

    // ---------------------------------------------------------------- Phase 6-Nachtrag 2: Umbenennen
    // Gleiches "claim" / "claim sub subName"-Muster wie entrymsg oben (Unterbereiche sind über
    // Befehle nicht direkt nach Namen ansprechbar, siehe findEditableClaim-Kommentar dort).

    private static ArgumentBuilder<CommandSourceStack, ?> renameNode() {
        var claimArg = Commands.argument("claim", StringArgumentType.word());
        claimArg.then(Commands.argument("newName", StringArgumentType.string())
            .executes(ctx -> executeRename(ctx, AreaClaimsCommands::resolveMainForEntryMsg, "newName")));
        claimArg.then(Commands.literal("sub")
            .then(Commands.argument("subName", StringArgumentType.word())
                .then(Commands.argument("newName", StringArgumentType.string())
                    .executes(ctx -> executeRename(ctx, AreaClaimsCommands::resolveSubForEntryMsg, "newName")))));
        return claimArg;
    }

    private static int executeRename(CommandContext<CommandSourceStack> ctx,
            Function<CommandContext<CommandSourceStack>, Claim> resolver, String newNameArg) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) return 0;
        Claim claim = resolver.apply(ctx);
        if (claim == null) return failClaimNotFound(ctx);
        String newName = StringArgumentType.getString(ctx, newNameArg);
        ClaimEditService.Result result = ClaimEditService.renameClaim(caller, claim, newName);
        switch (result) {
            case OK -> ctx.getSource().sendSuccess(() -> Component.translatable("areaclaims.command.rename.success", newName), true);
            case INVALID_NAME -> ctx.getSource().sendFailure(Component.translatable("areaclaims.command.rename.invalid_name"));
            case NAME_TAKEN -> ctx.getSource().sendFailure(Component.translatable("areaclaims.command.rename.name_taken"));
            case NOT_AUTHORIZED -> ctx.getSource().sendFailure(Component.translatable("areaclaims.command.no_permission"));
            default -> {}
        }
        return 1;
    }

    private static Claim resolveMainForEntryMsg(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return null;
        return findEditableClaim(player, StringArgumentType.getString(ctx, "claim"));
    }

    private static Claim resolveSubForEntryMsg(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return null;
        boolean admin = player.hasPermissions(4);
        return ClaimManager.findSubClaimByNameForEditor(player.getUUID(), admin,
            StringArgumentType.getString(ctx, "claim"), StringArgumentType.getString(ctx, "subName"));
    }

    // Delegiert ab jetzt komplett an EntryMessageService#apply (ROADMAP.md Phase 6-Nachtrag 2,
    // Punkt 2 "Betreten-Nachricht-GUI") - derselbe Umbau wie ClaimEditService/AdminConfigService:
    // Chat-Befehl und der neue GUI-Screen (SetEntryMessagePacket) rufen jetzt exakt dieselbe Stelle.
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T attachEntryMsgFields(
            T builder, Function<CommandContext<CommandSourceStack>, Claim> resolver) {
        builder.then(Commands.literal("color")
                .then(Commands.argument("hex", StringArgumentType.word())
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "color", StringArgumentType.getString(ctx, "hex"), 0))))
            .then(Commands.literal("duration")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "duration", "", IntegerArgumentType.getInteger(ctx, "ticks")))))
            .then(Commands.literal("welcome")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "welcome", StringArgumentType.getString(ctx, "text"), 0))))
            .then(Commands.literal("welcomecolor")
                .then(Commands.argument("hex", StringArgumentType.word())
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "welcomecolor", StringArgumentType.getString(ctx, "hex"), 0))))
            .then(Commands.literal("welcomeduration")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "welcomeduration", "", IntegerArgumentType.getInteger(ctx, "ticks")))))
            .then(Commands.literal("boundarycolor")
                .then(Commands.argument("hex", StringArgumentType.word())
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "boundarycolor", StringArgumentType.getString(ctx, "hex"), 0))))
            .then(Commands.literal("linkboundarycolor")
                .then(Commands.argument("value", BoolArgumentType.bool())
                    .executes(ctx -> executeEntryMsg(ctx, resolver, "linkboundarycolor", "", BoolArgumentType.getBool(ctx, "value") ? 1 : 0))));
        return builder;
    }

    private static int executeEntryMsg(CommandContext<CommandSourceStack> ctx,
            Function<CommandContext<CommandSourceStack>, Claim> resolver, String field, String stringArg, long longArg) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) return 0;
        Claim claim = resolver.apply(ctx);
        if (claim == null) return failClaimNotFound(ctx);
        EntryMessageService.Result result = EntryMessageService.apply(caller, claim, field, stringArg, longArg);
        switch (result) {
            case OK -> ctx.getSource().sendSuccess(() -> Component.translatable("areaclaims.command.entrymsg.set", claim.name()), true);
            case INVALID_COLOR -> ctx.getSource().sendFailure(Component.translatable("areaclaims.command.entrymsg.invalid_color"));
            case NOT_AUTHORIZED -> ctx.getSource().sendFailure(Component.translatable("areaclaims.command.no_permission"));
            case INVALID_FIELD -> {}
        }
        return 1;
    }

    private static int failClaimNotFound(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.translatable("areaclaims.command.claim.not_found", "?"));
        return 1;
    }
}
