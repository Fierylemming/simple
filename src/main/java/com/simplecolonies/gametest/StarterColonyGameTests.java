package com.simplecolonies.gametest;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class StarterColonyGameTests {
    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void plannedSpawnBuildsHallFarmAndSuppliesMiner(GameTestHelper helper) {
        plannedSpawn(helper, Blocks.GRASS_BLOCK);
    }

    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void plannedSpawnWorksOnStonyGround(GameTestHelper helper) {
        plannedSpawn(helper, Blocks.STONE);
    }

    private static void plannedSpawn(GameTestHelper helper, net.minecraft.world.level.block.Block terrain) {
        // Remove the test structure's artificial roof so the natural-surface
        // heightmap reflects the ground rather than the barrier ceiling.
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, terrain);
            for (int y = 5; y <= 8; y++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockPos anchor = new BlockPos(56, 1, 56);
        helper.setBlock(anchor, Blocks.OBSIDIAN); // Search must not overwrite this.
        var manager = ColonyManager.getInstance();
        var plan = manager.findNaturalStarterPlan(helper.getLevel(), helper.absolutePos(anchor));
        if (plan == null) { helper.fail("No starter plan on safe flat land"); return; }
        // A cave beneath the validated three-block foundation must not be
        // filled by the old farm routine's unvalidated deep pillars.
        BlockPos cave = plan.farm().offset(1, -4, 1);
        for (int y = -2; y >= -5; y--)
            helper.getLevel().setBlock(plan.farm().offset(1, y, 1), Blocks.AIR.defaultBlockState(), 3);
        var colony = manager.createNaturalColony(helper.getLevel(), CultureType.ROMAN, plan);
        if (colony == null) { helper.fail("Could not create planned colony"); return; }
        if (colony.getBuildings().stream().noneMatch(b -> b.getType() == BuildingType.TOWN_HALL && b.getPos().equals(plan.house()))
                || colony.getBuildings().stream().noneMatch(b -> b.getType() == BuildingType.FARM && b.getPos().equals(plan.farm())))
            helper.fail("Starter hall and farm not both registered");
        if (!helper.getLevel().getBlockState(colony.getCenter()).is(ModBlocks.TOWN_HALL.get())
                || !helper.getLevel().getBlockState(colony.getCenter().above(3)).is(Blocks.OAK_PLANKS)
                || !helper.getLevel().getBlockState(colony.getCenter().east(3)).is(Blocks.COBBLESTONE)
                || !helper.getLevel().getBlockState(colony.getCenter().west(3)).is(Blocks.COBBLESTONE)
                || !helper.getLevel().getBlockState(colony.getCenter().north(3)).is(Blocks.OAK_DOOR))
            helper.fail("Town hall block or shelter roof missing");
        if (!helper.getBlockState(anchor).is(Blocks.OBSIDIAN)) helper.fail("Spawn overwrote an existing block");
        if (!helper.getLevel().getBlockState(cave).isAir()) helper.fail("Farm filled an unvalidated cave below its foundation");
        if (colony.citizenCount() != 4 || colony.getCitizens().stream().noneMatch(c -> c.job == com.simplecolonies.villager.job.JobType.MINER))
            helper.fail("Starter colony is missing a founder or miner");
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_pickaxe") != 1)
            helper.fail("Starter miner pickaxe missing");
        BlockPos chest = colony.getCenter().offset(2, 0, 2);
        if (!colony.getWarehouseManager().isRegisteredStorage(helper.getLevel(), chest)
                || !(helper.getLevel().getBlockEntity(chest) instanceof net.minecraft.world.Container inventory)
                || inventory.countItem(net.minecraft.world.item.Items.STONE_PICKAXE) != 1
                || inventory.countItem(net.minecraft.world.item.Items.STONE_AXE) != 1
                || inventory.countItem(net.minecraft.world.item.Items.BREAD) != 16
                || colony.getWarehouseCount("minecraft:stone_pickaxe") != 0
                || colony.getWarehouseCount("minecraft:bread") != 0)
            helper.fail("Founding supplies are not in the automatically shared hall chest, or were duplicated");
        helper.succeed();
    }
}
