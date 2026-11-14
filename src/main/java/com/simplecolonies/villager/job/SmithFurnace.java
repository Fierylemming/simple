package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** A small, persisted batch in a real furnace next to a designated smithy. */
final class SmithFurnace {
    static final String TAG = "simplecolonies_smith_batch";
    private static final int BATCH = 3;
    enum Result { UNAVAILABLE, MOVING, WAITING, WORKED }

    static Result work(ServerLevel level, Colony colony, Villager worker, BlockPos station) {
        boolean designated = colony.getBuildings().stream().anyMatch(b -> b.getType() == BuildingType.BLACKSMITH
                && b.isOperational() && b.getPos().distSqr(station) <= 144);
        if (!designated) return Result.UNAVAILABLE;
        // Finish an existing batch before starting another, even if another furnace
        // appears earlier in the scan or a different smith takes over the station.
        boolean activeBatch = false;
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos pos = station.relative(direction);
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace
                    && furnace.getPersistentData().contains(TAG)
                    && furnace.getPersistentData().getCompound(TAG).getInt("colony") == colony.getId()) {
                activeBatch = true; break;
            }
        }
        // Only an immediately adjacent regular furnace or blast furnace is eligible.
        // Arbitrary machines in the colony and pre-existing contents are not touched.
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos pos = station.relative(direction);
            if (!level.isLoaded(pos) || !colony.isInside(pos)) continue;
            if (!(level.getBlockState(pos).is(Blocks.FURNACE) || level.getBlockState(pos).is(Blocks.BLAST_FURNACE))) continue;
            if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            var data = furnace.getPersistentData();
            var batch = data.getCompound(TAG);
            boolean owned = data.contains(TAG) && batch.getInt("colony") == colony.getId();
            // Never take over another colony's batch or a busy, unmarked player furnace.
            if (data.contains(TAG) && !owned) continue;
            if (activeBatch && !owned) continue;
            if (!owned && (!furnace.isEmpty() || level.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT))) continue;
            if (worker.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 9) {
                var path = worker.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) return Result.UNAVAILABLE;
                worker.getNavigation().moveTo(path, 0.7);
                return Result.MOVING;
            }
            if (owned) return collect(level, colony, furnace, batch);
            if (colony.getWarehouseCount(level, "minecraft:iron_ingot") >= 12
                    || colony.getWarehouseCount(level, "minecraft:raw_iron") < BATCH
                    || colony.getWarehouseCount(level, "minecraft:coal") < 1) continue;
            if (!colony.consumeStock(level, "minecraft:raw_iron", BATCH)) return Result.UNAVAILABLE;
            if (!colony.consumeStock(level, "minecraft:coal", 1)) {
                colony.depositToWarehouse("minecraft:raw_iron", BATCH);
                return Result.UNAVAILABLE;
            }
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, BATCH));
            furnace.setItem(1, new ItemStack(Items.COAL, 1));
            batch.putInt("colony", colony.getId());
            batch.putInt("remaining", BATCH);
            data.put(TAG, batch);
            furnace.setChanged();
            return Result.WORKED;
        }
        return Result.UNAVAILABLE;
    }

    private static Result collect(ServerLevel level, Colony colony, AbstractFurnaceBlockEntity furnace, net.minecraft.nbt.CompoundTag batch) {
        var input = furnace.getItem(0); var fuel = furnace.getItem(1); var output = furnace.getItem(2);
        int pending = batch.getInt("remaining");
        boolean valid = pending > 0 && pending <= BATCH
                && (input.isEmpty() || (input.is(Items.RAW_IRON) && ItemStack.isSameItemSameComponents(input, Items.RAW_IRON.getDefaultInstance())))
                && (fuel.isEmpty() || (fuel.is(Items.COAL) && fuel.getCount() == 1 && ItemStack.isSameItemSameComponents(fuel, Items.COAL.getDefaultInstance())))
                && (output.isEmpty() || (output.is(Items.IRON_INGOT) && ItemStack.isSameItemSameComponents(output, Items.IRON_INGOT.getDefaultInstance())))
                && input.getCount() + output.getCount() == pending;
        if (!valid) {
            // A player, hopper, mod or replacement changed the inventory. Relinquish
            // ownership without removing or replacing any of its contents.
            furnace.getPersistentData().remove(TAG); furnace.setChanged();
            return Result.UNAVAILABLE;
        }
        if (output.isEmpty()) return Result.WAITING;
        var extracted = furnace.removeItem(2, output.getCount());
        if (extracted.isEmpty()) return Result.WAITING;
        int count = extracted.getCount();
        colony.depositToWarehouse(level, "minecraft:iron_ingot", count);
        pending -= count;
        if (pending == 0) furnace.getPersistentData().remove(TAG);
        else { batch.putInt("remaining", pending); furnace.getPersistentData().put(TAG, batch); }
        furnace.setChanged();
        return Result.WORKED;
    }
    private SmithFurnace() {}
}
