package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forager — implicit Tier 0 job every villager can do without research.
 * Farmers & Lumberjacks (and even unemployed) will forage surface cobble/coal/flint
 * from nearby stone outcrops, so the village can get its first 32 cobble for Stone Tools
 * without a Miner. Think of it as villagers picking up surface rocks.
 *
 * Once Miner is unlocked, foraging becomes inefficient vs quarrying and auto-stops.
 * This fixes the chicken-egg you spotted: Farmer+Lumberjack → need cobble → need Miner → need cobble.
 */
public class ForagerJob {

    /** Called as side-work inside Farmer/Lumberjack tick when Miner not yet unlocked */
    public static boolean tryForageSurfaceCobble(Villager villager, Colony colony, ServerLevel level) {
        // Only forage if Miner not yet unlocked (i.e. still Tier0/1)
        if (colony.getResearch().isProfessionUnlocked(JobType.MINER)) return false;
        if (level.random.nextFloat() > 0.25f) return false; // 25% chance per check

        BlockPos center = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -2, -8), center.offset(8, 2, 8))) {
            BlockState bs = level.getBlockState(pos);
            if (bs.is(Blocks.STONE) || bs.is(Blocks.ANDESITE) || bs.is(Blocks.GRANITE) || bs.is(Blocks.DIORITE)) {
                // Wooden tools can mine stone/cobble slowly
                level.destroyBlock(pos, false);
                // 80% cobble, 10% coal, 10% flint (from gravel nearby)
                float r = level.random.nextFloat();
                if (r < 0.08f) colony.depositToWarehouse(level, "minecraft:coal", 1);
                else if (r < 0.12f) colony.depositToWarehouse(level, "minecraft:flint", 1);
                else colony.depositToWarehouse(level, "minecraft:cobblestone", 1);
                // Small XP/rep
                return true;
            }
            if (bs.is(Blocks.COAL_ORE) || bs.is(Blocks.COPPER_ORE)) {
                level.destroyBlock(pos, false);
                if (bs.is(Blocks.COAL_ORE)) colony.depositToWarehouse(level, "minecraft:coal", 1);
                else colony.depositToWarehouse(level, "minecraft:raw_copper", 1);
                return true;
            }
        }
        return false;
    }
}
