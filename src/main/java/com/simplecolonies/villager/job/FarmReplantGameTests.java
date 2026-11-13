package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class FarmReplantGameTests {
    @GameTest(template = "empty")
    public static void replantConsumesOnlyCarriedSeedAndProtectsObstacles(GameTestHelper helper) {
        BlockPos plot = new BlockPos(8, 1, 8), pos = helper.absolutePos(plot);
        helper.setBlock(plot.below(), Blocks.FARMLAND);
        var colony = new Colony(0, CultureType.ROMAN, pos);
        var farmer = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(6, 1, 8));
        var job = new FarmerJob(farmer, colony);
        colony.depositToWarehouse("minecraft:wheat_seeds", 1);
        if (job.plantCarriedSeed(helper.getLevel(), pos) || colony.getWarehouseCount("minecraft:wheat_seeds") != 1)
            helper.fail("Planted using virtual or remotely stored seed");
        farmer.setPlantingSeeds(new ItemStack(Items.WHEAT_SEEDS, 1));
        helper.setBlock(plot, Blocks.OBSIDIAN);
        if (job.plantCarriedSeed(helper.getLevel(), pos) || farmer.getPlantingSeeds().getCount() != 1)
            helper.fail("Replanting overwrote an obstacle or spent a seed");
        helper.setBlock(plot, Blocks.AIR);
        if (!job.plantCarriedSeed(helper.getLevel(), pos)
                || !helper.getBlockState(plot).is(Blocks.WHEAT)
                || helper.getBlockState(plot).getValue(CropBlock.AGE) != 0
                || !farmer.getPlantingSeeds().isEmpty())
            helper.fail("Did not replant with the carried seed");
        helper.succeed();
    }
}
