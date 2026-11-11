package com.simplecolonies.block;

import net.minecraft.world.level.block.Block;

/**
 * Warehouse chest that links to colony stockpile. Villagers deposit/fetch here.
 * If no warehouse building exists, this is the fallback stockpile.
 */
public class WarehouseBlock extends Block {
    public WarehouseBlock(Properties props) { super(props); }
}
