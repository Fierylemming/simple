package com.simplecolonies.villager.trade;

import com.simplecolonies.villager.job.JobType;

import java.util.ArrayList;
import java.util.List;

/**
 * Trades offered by colony workers.
 *
 * <p>Every trade exchanges real items: a worker buys the goods its job produces
 * for emeralds, or sells a finished product or colony-made tool for emeralds.
 * Emeralds are therefore earned by supplying a working settlement rather than
 * generated from nothing. The table is intentionally small, deterministic and
 * plain data so it can be unit tested without a running world.</p>
 *
 * <p>Offers unlock with the citizen's job level (1-5). A level-one worker always
 * offers at least two trades, so a newly founded colony is useful immediately.</p>
 */
public final class ColonyTradeTable {
    /**
     * @param costItem   item the player gives
     * @param costCount  how many of it
     * @param resultItem item the player receives
     * @param resultCount how many
     * @param maxUses    uses before the worker must restock
     * @param xp         trade experience granted to the worker
     * @param minLevel   worker job level required for this offer
     */
    public record Offer(String costItem, int costCount, String resultItem, int resultCount,
                        int maxUses, int xp, int minLevel) {}

    public static final String EMERALD = "minecraft:emerald";
    public static final int MAX_LEVEL = 5;

    private ColonyTradeTable() {}

    public static int clampLevel(int level) {
        return Math.clamp(level, 1, MAX_LEVEL);
    }

    /** All offers available to this job at the given job level. */
    public static List<Offer> offers(JobType job, int level) {
        int clamped = clampLevel(level);
        List<Offer> available = new ArrayList<>();
        for (Offer offer : allOffers(job)) {
            if (offer.minLevel() <= clamped) available.add(offer);
        }
        return List.copyOf(available);
    }

    /** Full table for a job, ignoring level gates. */
    public static List<Offer> allOffers(JobType job) {
        JobType effective = job == null ? JobType.UNEMPLOYED : job;
        return switch (effective) {
            case FARMER -> List.of(
                    buy("minecraft:wheat", 18, 1, 12, 2, 1),
                    sell("minecraft:bread", 4, 1, 10, 1, 1),
                    sell("minecraft:pumpkin", 2, 1, 8, 2, 2),
                    sell("minecraft:pumpkin_pie", 2, 1, 6, 3, 3),
                    sell("minecraft:hay_block", 1, 2, 4, 4, 4));
            case LUMBERJACK -> List.of(
                    buy("minecraft:oak_log", 12, 1, 12, 2, 1),
                    sell("minecraft:oak_planks", 16, 1, 10, 1, 1),
                    sell("minecraft:stick", 24, 1, 8, 2, 2),
                    sell("minecraft:charcoal", 8, 1, 8, 3, 3));
            case MINER -> List.of(
                    buy("minecraft:cobblestone", 24, 1, 12, 2, 1),
                    buy("minecraft:coal", 12, 1, 10, 2, 1),
                    sell("minecraft:torch", 16, 1, 8, 2, 1),
                    sell("minecraft:raw_iron", 2, 2, 6, 3, 3));
            case BUILDER -> List.of(
                    buy("minecraft:cobblestone", 20, 1, 10, 2, 1),
                    sell("minecraft:ladder", 8, 1, 8, 2, 1),
                    sell("minecraft:oak_fence", 8, 1, 8, 2, 2),
                    sell("minecraft:scaffolding", 16, 2, 6, 3, 3));
            case BUTCHER -> List.of(
                    buy("minecraft:beef", 8, 1, 8, 2, 1),
                    buy("minecraft:porkchop", 8, 1, 8, 2, 1),
                    sell("minecraft:cooked_beef", 4, 1, 8, 2, 1),
                    sell("minecraft:cooked_porkchop", 4, 1, 8, 3, 2),
                    sell("minecraft:cooked_mutton", 3, 1, 6, 3, 3));
            case LEATHERWORKER -> List.of(
                    buy("minecraft:leather", 8, 1, 10, 2, 1),
                    sell("minecraft:leather_chestplate", 1, 3, 4, 3, 1),
                    sell("minecraft:leather_boots", 1, 2, 5, 2, 2),
                    sell("minecraft:leather_helmet", 1, 2, 5, 3, 3));
            case BLACKSMITH -> List.of(
                    buy("minecraft:coal", 12, 1, 10, 2, 1),
                    sell("minecraft:iron_sword", 1, 4, 3, 4, 1),
                    sell("minecraft:iron_pickaxe", 1, 3, 3, 3, 2),
                    buy("minecraft:iron_ingot", 2, 1, 8, 3, 3),
                    sell("minecraft:iron_axe", 1, 3, 3, 4, 4));
            case BAKER -> List.of(
                    sell("minecraft:bread", 5, 1, 12, 1, 1),
                    buy("minecraft:wheat", 16, 1, 10, 2, 1),
                    sell("minecraft:cookie", 12, 1, 8, 2, 2),
                    sell("minecraft:pumpkin_pie", 3, 1, 6, 3, 3));
            case FISHERMAN -> List.of(
                    buy("minecraft:cod", 8, 1, 8, 2, 1),
                    sell("minecraft:cooked_cod", 4, 1, 8, 2, 1),
                    buy("minecraft:salmon", 8, 1, 8, 2, 2),
                    sell("minecraft:cooked_salmon", 4, 2, 6, 3, 3));
            case COURIER -> List.of(
                    sell("minecraft:barrel", 1, 1, 6, 2, 1),
                    buy("minecraft:oak_planks", 16, 1, 10, 2, 1),
                    sell("minecraft:chest", 1, 2, 5, 2, 2),
                    sell("minecraft:oak_boat", 1, 2, 4, 3, 3));
            case SHEPHERD -> List.of(
                    buy("minecraft:white_wool", 8, 1, 10, 2, 1),
                    sell("minecraft:shears", 1, 2, 4, 3, 1),
                    buy("minecraft:mutton", 8, 1, 8, 2, 2),
                    sell("minecraft:white_carpet", 8, 1, 8, 2, 3));
            case SOLDIER, GUARD -> List.of(
                    buy("minecraft:arrow", 16, 1, 10, 2, 1),
                    sell("minecraft:bow", 1, 3, 3, 3, 1),
                    sell("minecraft:shield", 1, 2, 3, 3, 2),
                    buy("minecraft:string", 12, 1, 8, 2, 3));
            case UNEMPLOYED -> List.of(
                    buy("minecraft:wheat", 20, 1, 8, 1, 1),
                    sell("minecraft:bread", 3, 1, 8, 1, 1),
                    sell("minecraft:apple", 4, 1, 6, 2, 3));
        };
    }

    private static Offer buy(String goods, int goodsCount, int emeralds, int maxUses, int xp, int minLevel) {
        return new Offer(goods, goodsCount, EMERALD, emeralds, maxUses, xp, minLevel);
    }

    private static Offer sell(String goods, int goodsCount, int emeralds, int maxUses, int xp, int minLevel) {
        return new Offer(EMERALD, emeralds, goods, goodsCount, maxUses, xp, minLevel);
    }
}
