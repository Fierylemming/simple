package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class FarmReplantGameTests {
    @GameTest(template = "empty")
    public static void seedFallbackConsumesRealStockAndProtectsObstacles(GameTestHelper helper) {
        BlockPos plot = new BlockPos(8, 1, 8), pos = helper.absolutePos(plot);
        helper.setBlock(plot.below(), Blocks.FARMLAND);
        var colony = new Colony(0, CultureType.ROMAN, pos);
        var farmer = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(6, 1, 8));
        var job = new FarmerJob(farmer, colony);
        if (job.replantFromStock(helper.getLevel(), pos, (CropBlock) Blocks.WHEAT, Items.WHEAT_SEEDS))
            helper.fail("Planted without a seed");
        colony.depositToWarehouse("minecraft:wheat_seeds", 1);
        helper.setBlock(plot, Blocks.OBSIDIAN);
        if (job.replantFromStock(helper.getLevel(), pos, (CropBlock) Blocks.WHEAT, Items.WHEAT_SEEDS)
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat_seeds") != 1)
            helper.fail("Replanting overwrote an obstacle or spent a seed");
        helper.setBlock(plot, Blocks.AIR);
        if (!job.replantFromStock(helper.getLevel(), pos, (CropBlock) Blocks.WHEAT, Items.WHEAT_SEEDS)
                || !helper.getBlockState(plot).is(Blocks.WHEAT)
                || helper.getBlockState(plot).getValue(CropBlock.AGE) != 0
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat_seeds") != 0)
            helper.fail("Did not replant with the real stored seed");
        helper.succeed();
    }
}
