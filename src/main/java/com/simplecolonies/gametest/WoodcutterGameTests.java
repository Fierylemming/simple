package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.WoodcutterJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class WoodcutterGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void approachesTreeAndUsesStockSapling(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos root = new BlockPos(12, 1, 8);
        for (int y = 0; y < 4; y++) helper.setBlock(root.above(y), Blocks.OAK_LOG);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) helper.setBlock(root.offset(x, 3, z), Blocks.OAK_LEAVES);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(1, 1, 8)));
        colony.depositToWarehouse("minecraft:oak_sapling", 1);
        var job = new WoodcutterJob(villager, colony);
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            boolean[] before = new boolean[4];
            for (int y = 0; y < 4; y++) before[y] = helper.getBlockState(root.above(y)).is(Blocks.OAK_LOG);
            job.tick(helper.getLevel());
            for (int y = 0; y < 4; y++) if (before[y] && !helper.getBlockState(root.above(y)).is(Blocks.OAK_LOG)) {
                BlockPos cut = helper.absolutePos(root.above(y));
                if (villager.distanceToSqr(cut.getX() + 0.5, cut.getY() + 0.5, cut.getZ() + 0.5) > 20.25)
                    helper.fail("Woodcutter cut from too far away");
            }
        });
        helper.succeedWhen(() -> {
            if (!helper.getBlockState(root).is(Blocks.OAK_SAPLING)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_log") < 4)
                helper.fail("Tree not harvested and replanted: " + job.getState() + " at " + villager.blockPosition().toShortString());
        });
    }
}
