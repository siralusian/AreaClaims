package com.areaclaims.economy;

import com.areaclaims.claim.RuleType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistiert die vom Admin (OP-Stufe 4, siehe ROADMAP.md Phase 6) konfigurierten Preise: EIN
 * Preis pro geclaimtem Block (gilt für {@code /areaclaims claim} UND {@code /areaclaims expand} -
 * bei expand nur für die Fläche des NEUEN Teils, siehe {@code AreaClaimsCommands}), plus EIN
 * Freikauf-Preis pro {@link RuleType}. Gson-JSON im Weltordner, gleicher Stil wie
 * {@link com.areaclaims.claim.ClaimManager}. Default: alles {@link PriceConfig#none()} (kostenlos)
 * - ein frischer Server verlangt kein Geld, bis der Admin explizit Preise setzt.
 *
 * <p>Seit ROADMAP.md Phase 6-Nachtrag 2 ("Punkt 6: Preis-UI-Neugestaltung") ist ein
 * {@link PriceConfig} mehrkomponentig (CobbleDollars + bis zu 5 Items, UND/ODER-verknüpft) - die
 * DTO speichert das direkt als Liste von Item-Einträgen + Kombinator-Liste statt des früheren
 * einzelnen Typ-Felds.
 */
public final class PriceConfigManager {

    private static class ItemDto {
        String itemId;
        int amount;
    }

    private static class PriceDto {
        /** null/"0" = kein Dollars-Anteil (siehe PriceConfig#hasDollars). */
        String dollars = "0";
        List<ItemDto> items = new ArrayList<>();
        List<String> combinators = new ArrayList<>();
    }

    private static class Data {
        PriceDto perBlock = new PriceDto();
        /** Punkt 11 (Nachtrag 3): eigener, unabhängig konfigurierbarer Preis NUR für neu abgesteckte/erweiterte Unterbereiche. */
        PriceDto subClaim = new PriceDto();
        Map<String, PriceDto> ruleBuyouts = new HashMap<>();
        /** Punkt 12 (Nachtrag 3): "X pro N Blöcke"-Teiler, siehe {@link PriceCharger#chargeUnits}. 1 = wie bisher (ein Preis pro Block). */
        int perBlockDivisor = 1;
        int subClaimDivisor = 1;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path dataFile;
    private static PriceConfig perBlockPrice = PriceConfig.none();
    private static PriceConfig subClaimPrice = PriceConfig.none();
    private static int perBlockDivisor = 1;
    private static int subClaimDivisor = 1;
    private static final Map<RuleType, PriceConfig> ruleBuyoutPrices = new EnumMap<>(RuleType.class);

    private PriceConfigManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("areaclaims_prices.json");
        load();
    }

    public static PriceConfig perBlockPrice() {
        return perBlockPrice;
    }

    public static void setPerBlockPrice(PriceConfig price) {
        perBlockPrice = price.normalized();
        save();
    }

    public static PriceConfig subClaimPrice() {
        return subClaimPrice;
    }

    public static void setSubClaimPrice(PriceConfig price) {
        subClaimPrice = price.normalized();
        save();
    }

    public static int perBlockDivisor() {
        return perBlockDivisor;
    }

    public static void setPerBlockDivisor(int divisor) {
        perBlockDivisor = Math.max(1, divisor);
        save();
    }

    public static int subClaimDivisor() {
        return subClaimDivisor;
    }

    public static void setSubClaimDivisor(int divisor) {
        subClaimDivisor = Math.max(1, divisor);
        save();
    }

    public static PriceConfig ruleBuyoutPrice(RuleType rule) {
        return ruleBuyoutPrices.getOrDefault(rule, PriceConfig.none());
    }

    public static void setRuleBuyoutPrice(RuleType rule, PriceConfig price) {
        ruleBuyoutPrices.put(rule, price.normalized());
        save();
    }

    // ---------------------------------------------------------------- Persistenz

    private static PriceDto toDto(PriceConfig price) {
        PriceDto dto = new PriceDto();
        dto.dollars = price.dollars() != null ? price.dollars().toString() : "0";
        for (PriceConfig.ItemAmount item : price.items()) {
            ItemDto itemDto = new ItemDto();
            itemDto.itemId = item.itemId();
            itemDto.amount = item.amount();
            dto.items.add(itemDto);
        }
        for (PriceConfig.Combinator c : price.combinators()) dto.combinators.add(c.name());
        return dto;
    }

    private static PriceConfig fromDto(PriceDto dto) {
        if (dto == null) return PriceConfig.none();
        try {
            BigInteger dollars = dto.dollars == null ? BigInteger.ZERO : new BigInteger(dto.dollars);
            List<PriceConfig.ItemAmount> items = new ArrayList<>();
            if (dto.items != null) {
                for (ItemDto itemDto : dto.items) {
                    if (itemDto.itemId != null && !itemDto.itemId.isBlank()) {
                        items.add(new PriceConfig.ItemAmount(itemDto.itemId, itemDto.amount));
                    }
                }
            }
            List<PriceConfig.Combinator> combinators = new ArrayList<>();
            if (dto.combinators != null) {
                for (String c : dto.combinators) {
                    try {
                        combinators.add(PriceConfig.Combinator.valueOf(c));
                    } catch (IllegalArgumentException ignored) {
                        combinators.add(PriceConfig.Combinator.AND);
                    }
                }
            }
            return PriceConfig.of(dollars, items, combinators);
        } catch (RuntimeException e) {
            return PriceConfig.none();
        }
    }

    private static void load() {
        perBlockPrice = PriceConfig.none();
        subClaimPrice = PriceConfig.none();
        perBlockDivisor = 1;
        subClaimDivisor = 1;
        ruleBuyoutPrices.clear();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            perBlockPrice = fromDto(data.perBlock);
            subClaimPrice = fromDto(data.subClaim);
            perBlockDivisor = Math.max(1, data.perBlockDivisor);
            subClaimDivisor = Math.max(1, data.subClaimDivisor);
            if (data.ruleBuyouts != null) {
                data.ruleBuyouts.forEach((ruleName, dto) -> {
                    try {
                        ruleBuyoutPrices.put(RuleType.valueOf(ruleName), fromDto(dto));
                    } catch (IllegalArgumentException ignored) {}
                });
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        Data data = new Data();
        data.perBlock = toDto(perBlockPrice);
        data.subClaim = toDto(subClaimPrice);
        data.perBlockDivisor = perBlockDivisor;
        data.subClaimDivisor = subClaimDivisor;
        ruleBuyoutPrices.forEach((rule, price) -> data.ruleBuyouts.put(rule.name(), toDto(price)));
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
