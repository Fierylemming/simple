package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** A small, walkable descending quarry, dug one reachable natural block at a time. */
public class MinerJob extends AbstractJob {
    private static final int[][] SITES = {{24,0},{17,17},{0,24},{-17,17},{-24,0},{-17,-17},{0,-24},{17,-17}};
    private static final int MAX_STEPS = 10;
    private BlockPos entrance;
    private Direction direction;
    private int step;
    private int siteIndex;
    private boolean restored;

    public MinerJob(Villager villager, Colony colony) { super(villager, colony, JobType.MINER); }
    public String diagnostic() { return "entrance=" + entrance + ", direction=" + direction + ", step=" + step + ", nextSite=" + siteIndex; }

    @Override protected void doWork(ServerLevel level) {
        if (!restored) { readMemory(); restored = true; }
        int tier = equipPickaxe(level);
        if (tier == 0) { state = JobState.NEEDS_TOOL; ticksUntilNextWork = 200; return; }
        if (entrance == null && !selectSite(level)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        BlockPos stand = entrance.relative(direction, step).below(step);
        if (!level.isLoaded(stand) || !colony.isInside(stand)) { state = JobState.STUCK; ticksUntilNextWork = 80; return; }
        if (villager.distanceToSqr(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5) > 0.81) {
            var path = villager.getNavigation().createPath(stand, 0);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        if (step >= MAX_STEPS) { resetSite(); state = JobState.IDLE; ticksUntilNextWork = 60; return; }
        BlockPos next = entrance.relative(direction, step + 1).below(step + 1);
        // Clear headroom first, then the step. This leaves two blocks of room throughout.
        for (int dy = 2; dy >= 0; dy--) {
            BlockPos target = next.above(dy);
            if (!level.isLoaded(target) || !colony.isInside(target)) { resetSite(); state = JobState.STUCK; return; }
            BlockState block = level.getBlockState(target);
            if (block.isAir()) continue;
            if (!safeToMine(level, target, tier)) { resetSite(); state = JobState.STUCK; ticksUntilNextWork = 100; return; }
            if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 16) {
                state = JobState.STUCK; ticksUntilNextWork = 40; return;
            }
            ItemStack tool = villager.getMainHandItem();
            var drops = Block.getDrops(block, level, target, null, villager, tool);
            if (!level.destroyBlock(target, false)) { state = JobState.STUCK; ticksUntilNextWork = 40; return; }
            for (ItemStack drop : drops) if (!drop.isEmpty()) depositToColony(level, BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(), drop.getCount());
            villager.swing(InteractionHand.MAIN_HAND);
            tool.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
            state = JobState.WORKING; ticksUntilNextWork = 25; return;
        }
        var path = villager.getNavigation().createPath(next, 0);
        if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
        // Advance only when the worker arrives at the next step.
        if (villager.distanceToSqr(next.getX() + 0.5, next.getY(), next.getZ() + 0.5) <= 0.81) {
            step++; writeMemory(); state = JobState.WORKING;
        } else {
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK;
        }
        ticksUntilNextWork = 10;
    }

    private int equipPickaxe(ServerLevel level) {
        ItemStack held = villager.getMainHandItem();
        int tier = tier(held);
        if (tier != 0 || !held.isEmpty()) return tier;
        for (var item : new net.minecraft.world.item.Item[]{Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE}) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (colony.consumeStock(level, id, 1)) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
                return tier(villager.getMainHandItem());
            }
        }
        return 0;
    }
    private static int tier(ItemStack tool) {
        if (tool.is(Items.NETHERITE_PICKAXE) || tool.is(Items.DIAMOND_PICKAXE) || tool.is(Items.IRON_PICKAXE)) return 2;
        return tool.is(Items.STONE_PICKAXE) ? 1 : 0;
    }

    private boolean selectSite(ServerLevel level) {
        for (int tries = 0; tries < SITES.length; tries++) {
            int index = siteIndex++ % SITES.length;
            int[] d = SITES[index];
            int x = colony.getCenter().getX() + d[0], z = colony.getCenter().getZ() + d[1];
            BlockPos column = new BlockPos(x, colony.getCenter().getY(), z);
            if (!level.isLoaded(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!colony.isInside(p) || y - MAX_STEPS - 3 <= level.getMinBuildHeight() || !safeArea(level, p.below())) continue;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
            entrance = p;
            direction = Math.abs(d[0]) >= Math.abs(d[1]) ? (d[0] > 0 ? Direction.EAST : Direction.WEST)
                    : (d[1] > 0 ? Direction.SOUTH : Direction.NORTH);
            step = 0; writeMemory(); return true;
        }
        return false;
    }
    private boolean safeToMine(ServerLevel level, BlockPos pos, int tier) {
        BlockState block = level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
        if (!(block.is(BlockTags.DIRT) || block.is(BlockTags.BASE_STONE_OVERWORLD) || MinerRules.allowedForTool(id, tier))) return false;
        if (MinerRules.dropFor(id) != null && !MinerRules.allowedForTool(id, tier)) return false;
        return safeArea(level, pos);
    }
    private boolean safeArea(ServerLevel level, BlockPos pos) {
        if (!MinerRules.farFromBuilding(pos.getX(), pos.getZ(), colony.getCenter().getX(), colony.getCenter().getZ(), 12)) return false;
        for (var building : colony.getBuildings()) {
            if (!MinerRules.farFromBuilding(pos.getX(), pos.getZ(), building.getPos().getX(), building.getPos().getZ(), Math.max(12, building.getSize() / 2 + 8))) return false;
        }
        for (var structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            if (level.structureManager().getStructureAt(pos, structure).isValid()) return false;
        }
        for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!level.isLoaded(neighbor) || level.getBlockEntity(neighbor) != null) return false;
            BlockState block = level.getBlockState(neighbor);
            if (!block.getFluidState().isEmpty()) return false;
            String id = BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
            if (!(block.isAir() || block.is(BlockTags.DIRT) || block.is(BlockTags.BASE_STONE_OVERWORLD)
                    || MinerRules.dropFor(id) != null)) return false;
        }
        return true;
    }
    private void resetSite() { entrance = null; direction = null; step = 0; writeMemory(); }
    private void readMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = cv.getMinerMemory();
        siteIndex = Math.floorMod(tag.getInt("siteIndex"), SITES.length);
        if (!tag.contains("entrance")) return;
        entrance = BlockPos.of(tag.getLong("entrance"));
        direction = Direction.from2DDataValue(tag.getInt("direction"));
        step = Math.clamp(tag.getInt("step"), 0, MAX_STEPS);
    }
    private void writeMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("siteIndex", Math.floorMod(siteIndex, SITES.length));
        if (entrance != null && direction != null) {
            tag.putLong("entrance", entrance.asLong()); tag.putInt("direction", direction.get2DDataValue()); tag.putInt("step", step);
        }
        cv.setMinerMemory(tag);
    }
}
