package com.simplecolonies.building;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit vanilla timber families for generated plans; never turn one species into another. */
public enum HouseTimber {
    OAK("oak", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_DOOR, Blocks.OAK_LEAVES),
    SPRUCE("spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_LEAVES),
    BIRCH("birch", Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_DOOR, Blocks.BIRCH_LEAVES),
    JUNGLE("jungle", Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_DOOR, Blocks.JUNGLE_LEAVES),
    ACACIA("acacia", Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_DOOR, Blocks.ACACIA_LEAVES),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_LEAVES),
    MANGROVE("mangrove", Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_DOOR, Blocks.MANGROVE_LEAVES),
    CHERRY("cherry", Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_DOOR, Blocks.CHERRY_LEAVES);

    public final String id;
    private final Block log, planks, stairs, slab, door, leaves;
    HouseTimber(String id, Block log, Block planks, Block stairs, Block slab, Block door, Block leaves) {
        this.id = id; this.log = log; this.planks = planks; this.stairs = stairs; this.slab = slab;
        this.door = door; this.leaves = leaves;
    }
    public Block logBlock() { return log; }
    public Block planksBlock() { return planks; }
    public Block doorBlock() { return door; }
    public Block leavesBlock() { return leaves; }

    /** True for this species' logs and leaves; used to read the local forest. */
    public boolean growsHere(BlockState state) {
        return state.is(log) || state.is(leaves);
    }

    /**
     * The species that actually grows around a position, so a Roman colony in a
     * birch forest builds with birch instead of importing oak. Leaves count once,
     * logs three times, because a felled log is worth more than a leaf block.
     */
    public static HouseTimber dominantAround(net.minecraft.world.level.Level level, BlockPos center, int radius) {
        if (level == null || center == null) return OAK;
        int[] score = new int[values().length];
        int step = radius > 16 ? 3 : 2;
        for (int dx = -radius; dx <= radius; dx += step) for (int dz = -radius; dz <= radius; dz += step) {
            for (int dy = -6; dy <= 24; dy += 2) {
                BlockPos pos = center.offset(dx, dy, dz);
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                for (var wood : values()) {
                    if (!wood.growsHere(state)) continue;
                    score[wood.ordinal()] += state.is(wood.log) ? 3 : 1;
                    break;
                }
            }
        }
        HouseTimber best = OAK;
        for (var wood : values()) if (score[wood.ordinal()] > score[best.ordinal()]) best = wood;
        return best;
    }
    public static HouseTimber byId(String id) {
        for (var wood : values()) if (wood.id.equals(id)) return wood;
        return OAK;
    }
    public static List<HouseTimber> all() { return List.of(values()); }
    public BlockState replaceOak(BlockState state) {
        if (state.is(Blocks.OAK_LOG)) return log.defaultBlockState();
        if (state.is(Blocks.OAK_PLANKS)) return planks.defaultBlockState();
        if (state.is(Blocks.OAK_STAIRS)) return stairs.defaultBlockState().setValue(StairBlock.FACING, state.getValue(StairBlock.FACING));
        if (state.is(Blocks.OAK_SLAB)) return slab.defaultBlockState();
        if (state.is(Blocks.OAK_DOOR)) return door.defaultBlockState().setValue(DoorBlock.FACING, state.getValue(DoorBlock.FACING));
        return state;
    }
}
