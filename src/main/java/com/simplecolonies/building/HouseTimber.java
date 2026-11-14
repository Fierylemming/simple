package com.simplecolonies.building;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit vanilla timber families for generated plans; never turn one species into another. */
public enum HouseTimber {
    OAK("oak", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_DOOR),
    SPRUCE("spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR),
    BIRCH("birch", Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_DOOR),
    JUNGLE("jungle", Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_DOOR),
    ACACIA("acacia", Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_DOOR),
    DARK_OAK("dark_oak", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_DOOR),
    MANGROVE("mangrove", Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_DOOR),
    CHERRY("cherry", Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_DOOR);

    public final String id;
    private final Block log, planks, stairs, slab, door;
    HouseTimber(String id, Block log, Block planks, Block stairs, Block slab, Block door) {
        this.id = id; this.log = log; this.planks = planks; this.stairs = stairs; this.slab = slab; this.door = door;
    }
    static HouseTimber byId(String id) {
        for (var wood : values()) if (wood.id.equals(id)) return wood;
        return OAK;
    }
    static List<HouseTimber> all() { return List.of(values()); }
    BlockState replaceOak(BlockState state) {
        if (state.is(Blocks.OAK_LOG)) return log.defaultBlockState();
        if (state.is(Blocks.OAK_PLANKS)) return planks.defaultBlockState();
        if (state.is(Blocks.OAK_STAIRS)) return stairs.defaultBlockState().setValue(StairBlock.FACING, state.getValue(StairBlock.FACING));
        if (state.is(Blocks.OAK_SLAB)) return slab.defaultBlockState();
        if (state.is(Blocks.OAK_DOOR)) return door.defaultBlockState().setValue(DoorBlock.FACING, state.getValue(DoorBlock.FACING));
        return state;
    }
}
