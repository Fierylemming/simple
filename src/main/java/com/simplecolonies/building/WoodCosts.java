package com.simplecolonies.building;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wooden costs name a <em>family</em> instead of one species, so a colony builds
 * and researches with the trees it actually has. A Roman colony in a birch
 * forest must not be stuck forever because its blueprint asks for oak.
 *
 * <p>A family token is written as {@code #planks}, {@code #log}, {@code #fence},
 * {@code #boat}, {@code #slab}, {@code #stairs}, {@code #door} or
 * {@code #sapling}. Anything else is an exact item id and behaves as before.</p>
 *
 * <p>Resolution is deterministic: the largest available stock wins, ties break
 * alphabetically, and the amount is spread over the species that are present
 * before a final fallback fills the remainder. Two colonies with the same stock
 * therefore always order exactly the same items.</p>
 */
public final class WoodCosts {
    public static final String PLANKS = "#planks";
    public static final String LOG = "#log";
    public static final String FENCE = "#fence";
    public static final String BOAT = "#boat";
    public static final String SLAB = "#slab";
    public static final String STAIRS = "#stairs";
    public static final String DOOR = "#door";
    public static final String SAPLING = "#sapling";

    private static final Map<String, String> SUFFIX = Map.of(
            PLANKS, "planks", LOG, "log", FENCE, "fence", BOAT, "boat",
            SLAB, "slab", STAIRS, "stairs", DOOR, "door", SAPLING, "sapling");

    public static boolean isFamily(String costId) {
        return costId != null && SUFFIX.containsKey(costId);
    }

    public static String familySuffix(String family) { return SUFFIX.get(family); }

    /** Every registered vanilla species for this family, in timber order. */
    public static List<String> speciesItems(String family) {
        String suffix = SUFFIX.get(family);
        if (suffix == null) return List.of();
        var out = new ArrayList<String>();
        for (var wood : HouseTimber.values()) {
            String id = itemId(wood, suffix);
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getOptional(net.minecraft.resources.ResourceLocation.parse(id)).isPresent()) out.add(id);
        }
        return List.copyOf(out);
    }

    public static String itemId(HouseTimber wood, String suffix) {
        return "minecraft:" + wood.id + "_" + suffix;
    }

    /** Logs and planks of one species, the two families a builder actually spends. */
    public static int woodCount(Map<String, Integer> stock, HouseTimber wood) {
        if (stock == null || wood == null) return 0;
        return Math.max(0, stock.getOrDefault(itemId(wood, "log"), 0))
                + Math.max(0, stock.getOrDefault(itemId(wood, "planks"), 0));
    }

    /** Stock counted for a cost entry: a family sums every species. */
    public static int count(Map<String, Integer> stock, String costId) {
        if (stock == null || costId == null) return 0;
        if (!isFamily(costId)) return Math.max(0, stock.getOrDefault(costId, 0));
        int total = 0;
        for (String id : speciesItems(costId)) total += Math.max(0, stock.getOrDefault(id, 0));
        return total;
    }

    /** True when this concrete stock item can pay that cost entry. */
    public static boolean matches(String costId, String stockId) {
        if (costId == null || stockId == null) return false;
        return isFamily(costId) ? speciesItems(costId).contains(stockId) : costId.equals(stockId);
    }

    /**
     * Replace every family entry with the concrete items this stock can supply.
     * Exact entries are copied unchanged, so old saves keep working.
     */
    public static Map<String, Integer> resolve(Map<String, Integer> available, Map<String, Integer> cost) {
        var result = new LinkedHashMap<String, Integer>();
        var ordered = new java.util.TreeMap<>(cost == null ? Map.<String, Integer>of() : cost);
        for (var entry : ordered.entrySet()) {
            String id = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) continue;
            if (!isFamily(id)) { result.merge(id, amount, Integer::sum); continue; }
            var candidates = new ArrayList<>(speciesItems(id));
            if (candidates.isEmpty()) continue;
            candidates.sort(Comparator.comparingInt((String item) -> stock(available, item)).reversed()
                    .thenComparing(Comparator.naturalOrder()));
            int remaining = amount;
            for (String item : candidates) {
                if (remaining == 0) break;
                int take = Math.min(remaining, stock(available, item));
                if (take <= 0) continue;
                result.merge(item, take, Integer::sum);
                remaining -= take;
            }
            // Insufficient stock is a separate, explicit failure; keep the order
            // itself concrete so a later refill cannot silently change species.
            if (remaining > 0) result.merge(candidates.getFirst(), remaining, Integer::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    /** The concrete item for a family in one species, e.g. {@code #planks} + birch. */
    public static String defaultItem(String costId, HouseTimber home) {
        if (!isFamily(costId)) return costId;
        String suffix = SUFFIX.get(costId);
        if (suffix == null) return costId;
        return itemId(home == null ? HouseTimber.OAK : home, suffix);
    }

    /** Human-readable cost entry for menus and messages. */
    public static String describe(String costId) {
        if (!isFamily(costId)) return costId == null ? "" : costId.replace("minecraft:", "");
        return "any " + SUFFIX.get(costId);
    }

    /**
     * Timber order for new plans: what the colony holds most of first, so a birch
     * forest builds birch houses; the home timber and enum order break ties.
     */
    public static List<HouseTimber> preferredTimbers(Map<String, Integer> stock, HouseTimber home) {
        var timbers = new ArrayList<>(List.of(HouseTimber.values()));
        timbers.sort(Comparator.comparingInt((HouseTimber wood) -> woodCount(stock, wood)).reversed()
                .thenComparingInt(wood -> wood == home ? 0 : 1)
                .thenComparingInt(HouseTimber::ordinal));
        return List.copyOf(timbers);
    }

    /** The species most present in stock, or {@code fallback} when nothing is stored. */
    public static HouseTimber dominantTimber(Map<String, Integer> stock, HouseTimber fallback) {
        HouseTimber best = fallback;
        int bestCount = 0;
        for (var wood : HouseTimber.values()) {
            int count = woodCount(stock, wood);
            if (count > bestCount) { best = wood; bestCount = count; }
        }
        return best;
    }

    private static int stock(Map<String, Integer> available, String item) {
        return available == null ? 0 : Math.max(0, available.getOrDefault(item, 0));
    }

    private WoodCosts() {}
}
