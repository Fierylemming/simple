package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.building.ConstructionCrafting;
import com.simplecolonies.building.HouseTimber;
import com.simplecolonies.building.WoodCosts;
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
 *
 * <p>Deliberately no colony is founded here: a GameTest colony covers a wide
 * radius and can disturb neighbouring tests in the same batch.</p>
 */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class BiomeWoodGameTests {

    /** Flat ground with two birch stands, leaving the middle clear. */
    private static void prepareBirchForest(GameTestHelper helper) {
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 5; y <= 8; y++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int[] stand : new int[][]{{38, 44}, {68, 74}}) {
            for (int x = stand[0]; x <= stand[1]; x += 3) for (int z = stand[0]; z <= stand[1]; z += 3) {
                for (int y = 1; y <= 4; y++) helper.setBlock(x, y, z, Blocks.BIRCH_LOG);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    if (Math.abs(dx) + Math.abs(dz) <= 3) helper.setBlock(x + dx, 5, z + dz, Blocks.BIRCH_LEAVES);
            }
        }
    }

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void aRomanColonyInABirchForestUsesBirchThroughout(GameTestHelper helper) {
        prepareBirchForest(helper);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        if (HouseTimber.dominantAround(helper.getLevel(), center, 24) != HouseTimber.BIRCH) {
            helper.fail("The local forest was not read as birch");
            return;
        }

        // Founding supplies and the starter shell come from the local species.
        var supplies = ColonyManager.starterSupplies(HouseTimber.BIRCH);
        boolean oak = supplies.keySet().stream().anyMatch(id -> id.contains("oak"));
        if (oak || !supplies.containsKey("minecraft:birch_planks") || !supplies.containsKey("minecraft:birch_log")
                || !supplies.containsKey("minecraft:birch_sapling"))
            helper.fail("Founding supplies are not the local wood: " + supplies);
        if (HouseTimber.BIRCH.planksBlock() != Blocks.BIRCH_PLANKS || HouseTimber.BIRCH.doorBlock() != Blocks.BIRCH_DOOR)
            helper.fail("The birch palette does not point at birch blocks");

        // A birch house plan asks for birch planks and is affordable from birch logs.
        Building house = new Building(BuildingType.HOUSE, center.east(8), CultureType.ROMAN, HouseTimber.BIRCH);
        boolean wooden = house.getRemainingPlacements().stream().anyMatch(p -> p.itemId.endsWith("_planks"));
        boolean oakInPlan = house.getRemainingPlacements().stream().anyMatch(p -> p.itemId.contains("oak"));
        if (!wooden || oakInPlan) helper.fail("A birch house plan still asks for oak");
        if (!ConstructionCrafting.canBuild(Map.of("minecraft:birch_log", 96, "minecraft:cobblestone", 96,
                "minecraft:coal", 8), house.getRemainingPlacements()))
            helper.fail("A birch forest cannot afford its own house");

        // Research is payable in the local species, which is what lets villagers
        // improve at all.
        var cost = WoodCosts.resolve(Map.of("minecraft:birch_planks", 32, "minecraft:oak_planks", 2),
                ColonyUpgrade.TOOL_STONE.cost);
        if (!cost.containsKey("minecraft:birch_planks") || cost.keySet().stream().anyMatch(id -> id.contains("oak")))
            helper.fail("Research still demands oak: " + cost);
        if (WoodCosts.count(Map.of("minecraft:birch_planks", 32), WoodCosts.PLANKS) != 32)
            helper.fail("Plank family counting ignores birch");
        helper.succeed();
    }
}
