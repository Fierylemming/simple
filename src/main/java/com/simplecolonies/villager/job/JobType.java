package com.simplecolonies.villager.job;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * All jobs. Each job has:
 * - workplace block (where they go to "work")
 * - what they produce / consume
 * - whether they are combat capable
 * This forms the supply chain.
 */
public enum JobType {
    UNEMPLOYED("Unemployed", "No job. Assign via Town Hall.", false, List.of()),
    FARMER("Farmer", "Grows wheat/carrots/potatoes, feeds colony", false, List.of(Items.WHEAT, Items.CARROT, Items.POTATO)),
    LUMBERJACK("Woodcutter", "Plants saplings nearby, harvests logs, replants", false, List.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.ACACIA_LOG)),
    MINER("Miner", "Digs quarry/mine for stone, coal, ores for blacksmith", false, List.of(Items.COBBLESTONE, Items.COAL, Items.IRON_ORE, Items.COPPER_ORE)),
    BUILDER("Builder", "Constructs buildings from blueprints using colony stockpile", false, List.of()),
    BUTCHER("Butcher", "Raises cows/pigs, provides meat + leather to leatherworker", false, List.of(Items.BEEF, Items.PORKCHOP, Items.LEATHER)),
    LEATHERWORKER("Leatherworker", "Makes a spare leather chestplate from butcher's leather", false, List.of(Items.LEATHER_CHESTPLATE)),
    BLACKSMITH("Blacksmith", "Smelts ores, makes tools & weapons for workers/soldiers", false, List.of(Items.IRON_INGOT, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE)),
    SOLDIER("Soldier", "Defends colony and follows leader into war", true, List.of()),
    GUARD("Guard", "Patrols walls, attacks hostiles/war enemies", true, List.of()),
    BAKER("Baker", "Turns farmer wheat into bread for efficient feeding", false, List.of(Items.BREAD)),
    FISHERMAN("Fisherman", "Gathers wild fish by hand near a designated harbor", false, List.of(Items.COD, Items.SALMON)),
    COURIER("Courier", "Fast logistics: bulk-moves items between chests and warehouse hub", false, List.of());

    public final String displayName;
    public final String description;
    public final boolean isCombat;
    public final List<net.minecraft.world.item.Item> produces;

    JobType(String displayName, String description, boolean isCombat, List<net.minecraft.world.item.Item> produces) {
        this.displayName = displayName;
        this.description = description;
        this.isCombat = isCombat;
        this.produces = produces;
    }

    public boolean needsTool() {
        return this == MINER || this == LUMBERJACK || this == BUILDER || this == FARMER || this == SOLDIER;
    }
}
