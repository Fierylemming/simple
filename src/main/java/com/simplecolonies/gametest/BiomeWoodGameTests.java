package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.building.ConstructionCrafting;
import com.simplecolonies.building.HouseTimber;
import com.simplecolonies.building.WoodCosts;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * A colony must build with the trees that actually grow around it. A Roman
 * village in a birch forest used to be stuck: every blueprint, wing and research
 * order asked for oak planks the forest could not supply.
 */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class BiomeWoodGameTests {

    /** Flat, tree-free ground with a birch grove around the future colony centre. */
    private static void prepareBirchForest(GameTestHelper helper) {
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 5; y <= 8; y++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Two small birch stands, far enough out to leave the starter plots clear.
        for (int[] stand : new int[][]{{38, 44}, {68, 74}}) {
            for (int x = stand[0]; x <= stand[1]; x += 3) for (int z = stand[0]; z <= stand[1]; z += 3) {
                for (int y = 1; y <= 4; y++) helper.setBlock(x, y, z, Blocks.BIRCH_LOG);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    if (Math.abs(dx) + Math.abs(dz) <= 3) helper.setBlock(x + dx, 5, z + dz, Blocks.BIRCH_LEAVES);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void forget(Colony colony) {
        if (colony == null) return;
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            ((Map<Integer, Colony>) field.get(ColonyManager.getInstance())).remove(colony.getId());
        } catch (ReflectiveOperationException ignored) { }
    }

    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void aRomanColonyInABirchForestBuildsWithBirch(GameTestHelper helper) {
        prepareBirchForest(helper);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        if (HouseTimber.dominantAround(helper.getLevel(), center, 24) != HouseTimber.BIRCH) {
            helper.fail("The local forest was not read as birch");
            return;
        }
        Colony colony = ColonyManager.getInstance().createColony(helper.getLevel(), CultureType.ROMAN, center);
        if (colony == null) { helper.fail("Could not found a colony in the birch forest"); return; }
        try {
            if (colony.getHomeTimber() != HouseTimber.BIRCH)
                helper.fail("Roman colony adopted " + colony.getHomeTimber().id + " instead of the local birch");
            // Founding supplies come from the forest, not from an oak forest far away.
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_planks") < 32
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_log") < 16
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_log") != 0)
                helper.fail("Founding supplies are not the local wood: " + colony.getWarehouseSnapshot(helper.getLevel()));
            // The starter hall shell uses the same species.
            if (!helper.getLevel().getBlockState(colony.getCenter().above(3)).is(Blocks.BIRCH_PLANKS)
                    || !helper.getLevel().getBlockState(colony.getCenter().north(3)).is(Blocks.BIRCH_DOOR))
                helper.fail("Starter hall was not built from birch");

            // A birch house plan asks for birch planks and is affordable from birch logs.
            Building house = new Building(BuildingType.HOUSE, center.east(8), CultureType.ROMAN, HouseTimber.BIRCH);
            boolean wooden = house.getRemainingPlacements().stream().anyMatch(p -> p.itemId.endsWith("_planks"));
            boolean oak = house.getRemainingPlacements().stream().anyMatch(p -> p.itemId.contains("oak"));
            if (!wooden || oak) helper.fail("A birch house plan still asks for oak");
            if (!ConstructionCrafting.canBuild(Map.of("minecraft:birch_log", 96, "minecraft:cobblestone", 96,
                    "minecraft:coal", 8), house.getRemainingPlacements()))
                helper.fail("A birch forest cannot afford its own house");

            // Research is payable in the local species too, which is what lets the
            // villagers improve at all.
            var cost = WoodCosts.resolve(Map.of("minecraft:birch_planks", 32, "minecraft:oak_planks", 2),
                    ColonyUpgrade.TOOL_STONE.cost);
            if (!cost.containsKey("minecraft:birch_planks"))
                helper.fail("Research still demands oak: " + cost);
            if (WoodCosts.count(Map.of("minecraft:birch_planks", 32), WoodCosts.PLANKS) != 32)
                helper.fail("Plank family counting ignores birch");
            helper.succeed();
        } finally {
            forget(colony);
        }
    }
}
