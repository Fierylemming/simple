package com.simplecolonies.colony;

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

    /** A harbor export: how many units a trader takes for one emerald, and what to keep. */
    public record Export(String itemId, int unitsPerEmerald, int reserve) {}

    /** A market order: units delivered for a fixed emerald price. */
    public record Order(String itemId, int units, int emeralds) {}

    public static final List<Export> EXPORTS = List.of(
            new Export("minecraft:cod", 8, 12),
            new Export("minecraft:salmon", 8, 12),
            new Export("minecraft:cooked_cod", 6, 8),
            new Export("minecraft:cooked_salmon", 6, 8),
            new Export("minecraft:white_wool", 8, 12),
            new Export("minecraft:hay_block", 4, 6),
            new Export("minecraft:charcoal", 12, 16),
            new Export("minecraft:oak_log", 12, 24),
            new Export("minecraft:oak_planks", 16, 32));

    public static final List<Order> ORDERS = List.of(
            new Order("minecraft:cobblestone", 16, 1),
            new Order("minecraft:coal", 8, 1),
            new Order("minecraft:oak_log", 16, 2),
            new Order("minecraft:oak_planks", 16, 2),
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
        int bestSurplus = 0;
        for (Export export : EXPORTS) {
            int surplus = stock.getOrDefault(export.itemId(), 0) - export.reserve();
            if (surplus < export.unitsPerEmerald()) continue;
            // Never sell the colony's own dinner, even above the plain reserve.
            if (FoodRules.isFood(export.itemId()) && foodStock - export.unitsPerEmerald() < foodNeed) continue;
            if (surplus > bestSurplus) { bestSurplus = surplus; best = export; }
        }
        return best == null ? Optional.empty()
                : Optional.of(new Exchange(best.itemId(), best.unitsPerEmerald(), EMERALD, 1));
    }

    /** Find an affordable order for one of the materials the colony still needs. */
    public static Optional<Exchange> marketVisit(Map<String, Integer> stock, Collection<String> neededItems) {
        int emeralds = stock.getOrDefault(EMERALD, 0);
        for (Order order : ORDERS) {
            if (!neededItems.contains(order.itemId()) || emeralds < order.emeralds()) continue;
            return Optional.of(new Exchange(EMERALD, order.emeralds(), order.itemId(), order.units()));
        }
        return Optional.empty();
    }

    /** Human-readable summary for /colony status. */
    public static String describe(Exchange exchange) {
        return shortName(exchange.giveItem()) + " " + exchange.giveCount() + " -> "
                + shortName(exchange.takeItem()) + " " + exchange.takeCount();
    }

    public static String shortName(String itemId) {
        int colon = itemId.indexOf(':');
        return colon < 0 ? itemId : itemId.substring(colon + 1);
    }
}
