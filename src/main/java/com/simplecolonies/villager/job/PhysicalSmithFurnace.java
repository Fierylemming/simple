package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Physical, persisted furnace batches; do not touch busy or changed machines. */
final class PhysicalSmithFurnace {
    enum Result { MOVING, WORKED, WAITING, UNAVAILABLE, RETURN_INPUTS }
    private static final String RAW = "minecraft:raw_iron", COAL = "minecraft:coal";
    private final ColonyVillager worker;
    private final Colony colony;
    private final PhysicalBuilderMaterials materials;
    private final PhysicalJobCargo cargo;
    PhysicalSmithFurnace(ColonyVillager worker, Colony colony, PhysicalBuilderMaterials materials, PhysicalJobCargo cargo) {
        this.worker = worker; this.colony = colony; this.materials = materials; this.cargo = cargo;
    }
    boolean hasInputs() { return materials.countPrepared(RAW) > 0 || materials.countPrepared(COAL) > 0; }
    Result work(ServerLevel level, BlockPos station) {
        boolean designated = colony.getBuildings().stream().anyMatch(b -> b.getType() == BuildingType.BLACKSMITH
                && b.isOperational() && b.getPos().distSqr(station) <= 144);
        if (!designated) return hasInputs() ? Result.RETURN_INPUTS : Result.UNAVAILABLE;
        BlockPos owned = null, available = null;
        for (var direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = station.relative(direction);
            if (!level.isLoaded(pos) || !colony.isInside(pos)) continue;
            if (!(level.getBlockState(pos).is(Blocks.FURNACE) || level.getBlockState(pos).is(Blocks.BLAST_FURNACE))) continue;
            if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            var data = furnace.getPersistentData();
            if (data.contains(SmithFurnace.TAG)) {
                if (data.getCompound(SmithFurnace.TAG).getInt("colony") == colony.getId()) owned = pos;
            } else if (furnace.isEmpty() && !level.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT) && available == null) available = pos;
        }
        if (owned != null) {
            if (!near(level, owned)) return Result.MOVING;
            var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(owned);
            if (furnace == null) return Result.UNAVAILABLE;
            var data = furnace.getPersistentData();
            var batch = data.getCompound(SmithFurnace.TAG);
            var input = furnace.getItem(0); var fuel = furnace.getItem(1); var output = furnace.getItem(2);
            int pending = batch.getInt("remaining");
            boolean valid = pending > 0 && pending <= 3
                    && (input.isEmpty() || (input.is(Items.RAW_IRON) && ItemStack.isSameItemSameComponents(input, Items.RAW_IRON.getDefaultInstance())))
                    && (fuel.isEmpty() || (fuel.is(Items.COAL) && fuel.getCount() == 1 && ItemStack.isSameItemSameComponents(fuel, Items.COAL.getDefaultInstance())))
                    && (output.isEmpty() || (output.is(Items.IRON_INGOT) && ItemStack.isSameItemSameComponents(output, Items.IRON_INGOT.getDefaultInstance())))
                    && input.getCount() + output.getCount() == pending;
            if (!valid) { data.remove(SmithFurnace.TAG); furnace.setChanged(); return hasInputs() ? Result.RETURN_INPUTS : Result.UNAVAILABLE; }
            if (output.isEmpty()) return Result.WAITING;
            var taken = furnace.removeItem(2, output.getCount());
            int count = taken.getCount(); cargo.collectStack(taken);
            if (!taken.isEmpty()) worker.spawnAtLocation(taken);
            pending -= count;
            if (pending == 0) data.remove(SmithFurnace.TAG);
            else { batch.putInt("remaining", pending); data.put(SmithFurnace.TAG, batch); }
            furnace.setChanged(); return Result.WORKED;
        }
        if (available == null || materials.countAvailable(level, RAW) < 3 || materials.countAvailable(level, COAL) < 1
                || (!hasInputs() && materials.countAvailable(level, "minecraft:iron_ingot") >= 12))
            return hasInputs() ? Result.RETURN_INPUTS : Result.UNAVAILABLE;
        var raw = materials.prepareCount(level, RAW, 3);
        if (raw != PhysicalBuilderMaterials.Result.READY) return supplyResult(raw);
        var fuel = materials.prepareCount(level, COAL, 1);
        if (fuel != PhysicalBuilderMaterials.Result.READY) return supplyResult(fuel);
        if (!near(level, available)) return Result.MOVING;
        // Revalidate immediately before inserting: the player may have used it.
        var be = level.getBlockEntity(available);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace) || !furnace.isEmpty()
                || furnace.getPersistentData().contains(SmithFurnace.TAG)
                || !(level.getBlockState(available).is(Blocks.FURNACE) || level.getBlockState(available).is(Blocks.BLAST_FURNACE))
                || level.getBlockState(available).getValue(AbstractFurnaceBlock.LIT)) return Result.RETURN_INPUTS;
        for (int i = 0; i < 3; i++) if (!materials.takePrepared(RAW)) throw new IllegalStateException("Smelting inputs changed unexpectedly");
        if (!materials.takePrepared(COAL)) throw new IllegalStateException("Smelting fuel changed unexpectedly");
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 3)); furnace.setItem(1, new ItemStack(Items.COAL, 1));
        var batch = new net.minecraft.nbt.CompoundTag(); batch.putInt("colony", colony.getId()); batch.putInt("remaining", 3);
        furnace.getPersistentData().put(SmithFurnace.TAG, batch); furnace.setChanged();
        return Result.WORKED;
    }
    private static Result supplyResult(PhysicalBuilderMaterials.Result result) {
        return switch (result) {
            case MOVING -> Result.MOVING;
            case WORKING -> Result.WORKED;
            case BLOCKED -> Result.WAITING;
            default -> Result.RETURN_INPUTS;
        };
    }
    private boolean near(ServerLevel level, BlockPos pos) {
        if (worker.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) <= 9) return true;
        var path = worker.getNavigation().createPath(pos, 1);
        if (path != null && path.canReach()) worker.getNavigation().moveTo(path, .7);
        return false;
    }
}
