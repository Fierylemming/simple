package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

/**
 * Culture-specific material tiers — Greek houses start wood at lvl1, stone bases at lvl2, terracotta at lvl3 etc.
 * This makes villages that spawn naturally evolve their look as they level up, using resources around them.
 *
 * The builder picks palette based on Culture + Building Level + local resource availability (prefers nearby mats).
 */
public class MaterialProgression {

    public record TierMaterials(List<Block> primary, List<Block> secondary, List<Block> roof, String lore) {}

    public static TierMaterials get(CultureType culture, int level) {
        // level 1-5
        level = Math.max(1, Math.min(5, level));
        return PROGRESSION.get(culture).get(level - 1);
    }

    private static final Map<CultureType, List<TierMaterials>> PROGRESSION = Map.of(
            CultureType.GREEK, List.of(
                    new TierMaterials(List.of(Blocks.OAK_PLANKS, Blocks.OAK_LOG), List.of(Blocks.COBBLESTONE), List.of(Blocks.OAK_SLAB), "Lvl1 — Wooden huts (frontier)"),
                    new TierMaterials(List.of(Blocks.STONE_BRICKS, Blocks.STONE), List.of(Blocks.OAK_PLANKS), List.of(Blocks.OAK_STAIRS), "Lvl2 — Stone bases"),
                    new TierMaterials(List.of(Blocks.WHITE_TERRACOTTA, Blocks.WHITE_CONCRETE), List.of(Blocks.STONE_BRICKS), List.of(Blocks.BRICKS), "Lvl3 — Whitewashed + terracotta"),
                    new TierMaterials(List.of(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR), List.of(Blocks.WHITE_TERRACOTTA), List.of(Blocks.CUT_COPPER), "Lvl4 — Marble columns"),
                    new TierMaterials(List.of(Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CALCITE), List.of(Blocks.QUARTZ_PILLAR), List.of(Blocks.BLUE_GLAZED_TERRACOTTA), "Lvl5 — Grand temples, blue domes")
            ),
            CultureType.ROMAN, List.of(
                    new TierMaterials(List.of(Blocks.OAK_PLANKS, Blocks.COBBLESTONE), List.of(Blocks.OAK_LOG), List.of(Blocks.OAK_SLAB), "Lvl1 — Wooden frontier posts"),
                    new TierMaterials(List.of(Blocks.STONE_BRICKS, Blocks.COBBLESTONE), List.of(Blocks.OAK_PLANKS), List.of(Blocks.BRICKS), "Lvl2 — Stone forts"),
                    new TierMaterials(List.of(Blocks.BRICKS, Blocks.TERRACOTTA), List.of(Blocks.STONE_BRICKS), List.of(Blocks.RED_TERRACOTTA), "Lvl3 — Red tile roofs"),
                    new TierMaterials(List.of(Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ), List.of(Blocks.BRICKS), List.of(Blocks.QUARTZ_STAIRS), "Lvl4 — Marble forum"),
                    new TierMaterials(List.of(Blocks.QUARTZ_PILLAR, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CUT_COPPER), List.of(Blocks.SMOOTH_QUARTZ), List.of(Blocks.CHISELED_STONE_BRICKS), "Lvl5 — Imperial city")
            ),
            CultureType.EGYPTIAN, List.of(
                    new TierMaterials(List.of(Blocks.SANDSTONE, Blocks.SAND), List.of(Blocks.ACACIA_PLANKS), List.of(Blocks.SANDSTONE_SLAB), "Lvl1 — Sandy shelters"),
                    new TierMaterials(List.of(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE), List.of(Blocks.SANDSTONE), List.of(Blocks.ACACIA_SLAB), "Lvl2 — Cut sandstone"),
                    new TierMaterials(List.of(Blocks.TERRACOTTA, Blocks.YELLOW_TERRACOTTA), List.of(Blocks.CUT_SANDSTONE), List.of(Blocks.SANDSTONE_STAIRS), "Lvl3 — Terracotta walls"),
                    new TierMaterials(List.of(Blocks.GOLD_BLOCK, Blocks.CHISELED_SANDSTONE), List.of(Blocks.TERRACOTTA), List.of(Blocks.SMOOTH_SANDSTONE), "Lvl4 — Gilded pyramid bases"),
                    new TierMaterials(List.of(Blocks.QUARTZ_BLOCK, Blocks.GOLD_BLOCK, Blocks.CHISELED_SANDSTONE), List.of(Blocks.CUT_SANDSTONE), List.of(Blocks.GOLD_BLOCK), "Lvl5 — Pharaoh's city")
            ),
            CultureType.PERSIAN, List.of(
                    new TierMaterials(List.of(Blocks.TERRACOTTA, Blocks.MUD_BRICKS), List.of(Blocks.ACACIA_PLANKS), List.of(Blocks.ACACIA_SLAB), "Lvl1 — Mud huts"),
                    new TierMaterials(List.of(Blocks.RED_TERRACOTTA, Blocks.ORANGE_TERRACOTTA), List.of(Blocks.TERRACOTTA), List.of(Blocks.BRICKS), "Lvl2 — Glazed brick beginnings"),
                    new TierMaterials(List.of(Blocks.BRICKS, Blocks.CHISELED_STONE_BRICKS), List.of(Blocks.RED_TERRACOTTA), List.of(Blocks.DARK_OAK_STAIRS), "Lvl3 — Grand archways"),
                    new TierMaterials(List.of(Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS), List.of(Blocks.BRICKS), List.of(Blocks.CUT_COPPER), "Lvl4 — Palace walls"),
                    new TierMaterials(List.of(Blocks.QUARTZ_BLOCK, Blocks.GOLD_BLOCK), List.of(Blocks.CHISELED_STONE_BRICKS), List.of(Blocks.GOLD_BLOCK), "Lvl5 — Hanging gardens")
            ),
            CultureType.MESOPOTAMIAN, List.of(
                    new TierMaterials(List.of(Blocks.MUD_BRICKS, Blocks.PACKED_MUD), List.of(Blocks.OAK_PLANKS), List.of(Blocks.MANGROVE_SLAB), "Lvl1 — Reed huts on mud"),
                    new TierMaterials(List.of(Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICKS), List.of(Blocks.PACKED_MUD), List.of(Blocks.BAMBOO_MOSAIC), "Lvl2 — Ziggurat bases"),
                    new TierMaterials(List.of(Blocks.TERRACOTTA, Blocks.BRICKS), List.of(Blocks.MUD_BRICKS), List.of(Blocks.BAMBOO_MOSAIC_SLAB), "Lvl3 — Baked brick city"),
                    new TierMaterials(List.of(Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_SANDSTONE), List.of(Blocks.TERRACOTTA), List.of(Blocks.BRICKS), "Lvl4 — Canal city"),
                    new TierMaterials(List.of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.GOLD_BLOCK), List.of(Blocks.CHISELED_STONE_BRICKS), List.of(Blocks.CUT_COPPER), "Lvl5 — Grand ziggurat")
            )
    );

    /** Helper for builder: pick block from tier based on what colony has in chests locally. */
    public static Block pickAvailable(CultureType culture, int level, List<Block> options, com.simplecolonies.colony.Colony colony) {
        // Prefer what we have most of in warehouse
        Block best = options.get(0);
        int bestCount = -1;
        for (Block b : options) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString().replace("minecraft:", "minecraft:");
            // blocks map to item ids for counting
            String itemId = b.asItem().toString(); // crude
            // Try warehouse count
            int count = 0;
            try { count = colony.getWarehouseManager().count("minecraft:" + b.getName().getString().toLowerCase()); } catch (Exception ignored) {}
            if (count > bestCount) { best = b; bestCount = count; }
        }
        return best;
    }
}
