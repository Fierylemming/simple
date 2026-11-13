package com.simplecolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class FounderSpawnGameTests {
    @GameTest(template = "isolated")
    public static void unsafeSpawnSearchDoesNotReplaceBlocks(GameTestHelper helper) {
        BlockPos relative = new BlockPos(56, 1, 56);
        // Cover the full local search and its heightmap column with deep water.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = -3; y <= 9; y++) helper.setBlock(relative.offset(x, y, z), Blocks.WATER);
            for (int y = 10; y <= 18; y++) helper.setBlock(relative.offset(x, y, z), Blocks.AIR);
        }
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(relative);
        BlockPos result = ColonyManager.getInstance().findSafeSpawn(level, origin);
        if (result != null) helper.fail("Found a dry spawn inside the water-filled search area: " + result);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -3; y <= 9; y++)
            if (!helper.getBlockState(relative.offset(x, y, z)).is(Blocks.WATER))
                helper.fail("Spawn search changed water or terrain");
        helper.succeed();
    }
}
