package com.simplecolonies.colony;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Natural-looking terrain only. Player-placed stone cannot be distinguished from natural stone. */
final class NaturalGround {
    static boolean supportsConstruction(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }
    private NaturalGround() {}
}
