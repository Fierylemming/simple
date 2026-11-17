package com.simplecolonies.colony;

import com.simplecolonies.building.HouseTimber;
import com.simplecolonies.building.WoodCosts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic trade routes run by completed civic buildings.
 *
 * <p>A completed <b>harbor</b> sends a boat/trader visit that buys surplus
 * goods for emeralds. A completed <b>market</b> places an order for a material
 * the colony still needs, paid with emeralds. Both keep a reserve, so a route
 * can never strip a settlement of something it needs itself.</p>
 *
 * <p>Emeralds only enter a colony through these visits (and through player
 * trading), and they are spent on real delivered goods, so the economy stays
 * closed and inspectable.</p>
 */
public final class TradeRoutes {
    public static final String EMERALD = "minecraft:emerald";

    /** One completed exchange: give {@code giveCount} of {@code giveItem}, get {@code takeCount} of {@code takeItem}. */
    public record Exchange(String giveItem, int giveCount, String takeItem, int takeCount) {}

    /**
     * A harbor export: how many units a trader takes for one emerald, and what to keep.
     * The item may be a wood family such as {@link WoodCosts#LOG}, in which case the
     * colony sells whichever species it actually has a surplus of.
     */
    public record Export(String itemId, int unitsPerEmerald, int reserve) {}

    /** A market order: units delivered for a fixed emerald price; may be a wood family. */
    public record Order(String itemId, int units, int emeralds) {}

    public static final List<Export> EXPORTS = List.of(
            new Export("minecraft:cod", 8, 12),
            new Export("minecraft:salmon", 8, 12),
            new Export("minecraft:cooked_cod", 6, 8),
            new Export("minecraft:cooked_salmon", 6, 8),
            new Export("minecraft:white_wool", 8, 12),
            new Export("minecraft:hay_block", 4, 6),
            new Export("minecraft:charcoal", 12, 16),
            // Any species: a birch or spruce forest must be able to sell its own wood.
            new Export(WoodCosts.LOG, 12, 24),
            new Export(WoodCosts.PLANKS, 16, 32));

    public static final List<Order> ORDERS = List.of(
            new Order("minecraft:cobblestone", 16, 1),
            new Order("minecraft:coal", 8, 1),
            new Order(WoodCosts.LOG, 16, 2),
            new Order(WoodCosts.PLANKS, 16, 2),
            new Order("minecraft:glass", 8, 2),
            new Order("minecraft:iron_ingot", 4, 3),
            new Order("minecraft:bookshelf", 4, 3),
            new Order("minecraft:quartz_block", 8, 4));

    private TradeRoutes() {}

    /**
     * Pick the single best export for today's visit.
     *
     * @param stock    combined warehouse contents
     * @param foodStock food units the colony still holds
     * @param foodNeed  food units the colony must keep for its citizens
     */
    public static Optional<Exchange> harborVisit(Map<String, Integer> stock, int foodStock, int foodNeed) {
        Export best = null;
        String bestItem = null;
        int bestSurplus = 0;
        for (Export export : EXPORTS) {
            int available = WoodCosts.count(stock, export.itemId());
            int surplus = available - export.reserve();
            if (surplus < export.unitsPerEmerald()) continue;
            // Never sell the colony's own dinner, even above the plain reserve.
            if (FoodRules.isFood(export.itemId()) && foodStock - export.unitsPerEmerald() < foodNeed) continue;
            String concrete = concretise(stock, export.itemId(), export.reserve() + export.unitsPerEmerald());
            if (concrete == null) continue;
            if (surplus > bestSurplus) { bestSurplus = surplus; best = export; bestItem = concrete; }
        }
        return best == null ? Optional.empty()
                : Optional.of(new Exchange(bestItem, best.unitsPerEmerald(), EMERALD, 1));
    }

    /**
     * The exact item to hand over: an exact cost is itself, a family becomes the
     * species with the most spare stock so the visit never mixes woods.
     */
    private static String concretise(Map<String, Integer> stock, String costId, int keepUnits) {
        if (!WoodCosts.isFamily(costId)) return costId;
        String chosen = null;
        int best = -1;
        for (String item : WoodCosts.speciesItems(costId)) {
            int spare = stock.getOrDefault(item, 0) - keepUnits;
            if (spare >= 0 && spare > best) { best = spare; chosen = item; }
        }
        return chosen;
    }

    /** Find an affordable order for one of the materials the colony still needs. */
    public static Optional<Exchange> marketVisit(Map<String, Integer> stock, Collection<String> neededItems) {
        int emeralds = stock.getOrDefault(EMERALD, 0);
        var needed = new ArrayList<>(neededItems);
        needed.sort(String::compareTo);
        for (Order order : ORDERS) {
            if (emeralds < order.emeralds()) continue;
            String concrete = order.itemId();
            if (WoodCosts.isFamily(order.itemId())) {
                // Buy the exact species a plan is missing, never "any wood".
                concrete = needed.stream().filter(item -> WoodCosts.matches(order.itemId(), item)).findFirst().orElse(null);
            } else if (!needed.contains(order.itemId())) continue;
            if (concrete == null) continue;
            return Optional.of(new Exchange(EMERALD, order.emeralds(), concrete, order.units()));
        }
        return Optional.empty();
    }

    /** Human-readable summary for /colony status. */
    public static String describe(Exchange exchange) {
        return shortName(exchange.giveItem()) + " " + exchange.giveCount() + " -> "
                + shortName(exchange.takeItem()) + " " + exchange.takeCount();
    }

    public static String shortName(String itemId) {
        if (itemId == null) return "nothing";
        if (WoodCosts.isFamily(itemId)) return WoodCosts.describe(itemId);
        int colon = itemId.indexOf(':');
        return colon < 0 ? itemId : itemId.substring(colon + 1);
    }

    /** Preferred local species for a family, used by callers without stock. */
    public static String defaultItem(String costId, HouseTimber home) {
        if (!WoodCosts.isFamily(costId)) return costId;
        return WoodCosts.itemId(home == null ? HouseTimber.OAK : home, WoodCosts.familySuffix(costId));
    }
}
