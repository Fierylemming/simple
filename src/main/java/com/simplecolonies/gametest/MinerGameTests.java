package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.MinerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class MinerGameTests {
    @GameTest(template = "quarry", timeoutTicks = 1800)
    public static void minerWalksAndExcavatesWithRealTool(GameTestHelper helper) {
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) helper.setBlock(x, 11, z, Blocks.GRASS_BLOCK);
        for (int x = 37; x <= 51; x++) for (int z = 30; z <= 34; z++) for (int y = 0; y <= 10; y++)
            helper.setBlock(x, y, z, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(15, 12, 32));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.depositToWarehouse("minecraft:stone_pickaxe", 1);
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(16, 12, 32));
        MinerJob job = new MinerJob(villager, colony);
        BlockPos stone = new BlockPos(41, 10, 32);
        boolean[] sawCut = {false};
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            boolean wasStone = helper.getBlockState(stone).is(Blocks.STONE);
            job.tick(helper.getLevel());
            if (wasStone && !helper.getBlockState(stone).is(Blocks.STONE)) {
                BlockPos cut = helper.absolutePos(stone);
                if (villager.distanceToSqr(cut.getX() + 0.5, cut.getY() + 0.5, cut.getZ() + 0.5) > 16)
                    helper.fail("Miner cut stone from too far away");
                sawCut[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!sawCut[0] || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") < 1
                    || !villager.getMainHandItem().is(Items.STONE_PICKAXE))
                helper.fail("Miner " + job.getState() + " at " + villager.blockPosition().toShortString()
                        + " " + job.diagnostic() + " tool=" + villager.getMainHandItem()
                        + " stone=" + helper.getBlockState(stone) + " cobble=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone"));
            if (!villager.getMinerMemory().contains("entrance")) helper.fail("Quarry progress was not saved");
        });
    }
}
