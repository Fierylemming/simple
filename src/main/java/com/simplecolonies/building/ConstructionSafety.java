package com.simplecolonies.building;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Conservative clearance for generated plans. Minecraft has no general way
 * to tell wild plants from player-placed ones: keep crops, saplings, flowers,
 * snow, modded replaceables and all block entities instead of silently deleting
 * them. The few ordinary grasses still cannot be distinguished by ownership. */
public final class ConstructionSafety {
    public static boolean clearable(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        return state.isAir() || (state.canBeReplaced() && (state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)));
    }
    private ConstructionSafety() {}
}
