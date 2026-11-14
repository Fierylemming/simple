package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Harvests real drops and replants only when seed or produce is available. */
public class FarmerJob extends AbstractJob {
    private BlockPos target;
    private final java.util.Map<BlockPos, Long> unreachableUntil = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Long> unreachableStorageUntil = new java.util.HashMap<>();
    public FarmerJob(ColonyVillager villager, Colony colony) { super(villager, colony, JobType.FARMER); }
    private ColonyVillager worker() { return (ColonyVillager) villager; }

    @Override protected void doWork(ServerLevel level) {
        // Preserve and deliver harvest even if the farm or workstation disappears.
        if (!worker().getCourierCargo().isEmpty() || !worker().getCourierCargoExtra().isEmpty()) {
            deliverHarvest(level);
            return;
        }
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())
                || !level.getBlockState(site.get().pos()).is(Blocks.COMPOSTER)) {
            state = JobState.STUCK; ticksUntilNextWork = 100; return;
        }
        // A composter alone is not permission to harvest nearby player fields.
        // Only a completed, explicitly registered farm near this POI is worked.
        var farm = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.FARM
                        && b.getState() == com.simplecolonies.building.BuildingState.COMPLETE
                        && level.isLoaded(b.getPos()) && b.getPos().distSqr(site.get().pos()) <= 144)
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(site.get().pos()))).orElse(null);
        if (farm == null) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        // Vanilla farmers make bread. This keeps the four-founder starter settlement
        // viable without requiring a separately assigned baker.
        int targetBread = Math.max(16, colony.citizenCount() * 4);
        if (colony.getWarehouseCount(level, "minecraft:bread") < targetBread && makeBread(level)) return;
        if (target == null || !level.isLoaded(target) || !eligible(level, target, farm.getPos())) target = findWork(level, farm.getPos());
        if (target == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        // Acquire seed before approaching an empty plot, or the worker would
        // repeatedly walk back from the chest without ever collecting it.
        if (level.getBlockState(target).isAir() && worker().getPlantingSeeds().isEmpty()) {
            collectSeed(level); return;
        }
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 9.0) {
            var path = villager.getNavigation().createPath(target, 2);
            if (path == null || !path.canReach()) {
                unreachableUntil.put(target, level.getGameTime() + 400);
                state = JobState.STUCK; target = null; ticksUntilNextWork = 40; return;
            }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        BlockState cropState = level.getBlockState(target);
        if (cropState.getBlock() instanceof CropBlock crop && seedFor(cropState) != null && crop.isMaxAge(cropState)) {
            var drops = Block.getDrops(cropState, level, target, null, villager, ItemStack.EMPTY);
            if (level.destroyBlock(target, false)) {
                villager.swing(InteractionHand.MAIN_HAND);
                Item seed = seedFor(cropState);
                boolean replanted = false;
                for (ItemStack drop : drops) {
                    int count = drop.getCount();
                    if (!replanted && drop.is(seed) && count > 0 && level.getBlockState(target).isAir()
                            && level.getBlockState(target.below()).is(Blocks.FARMLAND)) {
                        if (level.setBlock(target, crop.getStateForAge(0), 3)) {
                            count--; replanted = true;
                        }
                    }
                    if (count > 0) {
                        var harvested = drop.copy();
                        harvested.setCount(count);
                        carryHarvest(harvested);
                    }
                }
                // If the crop dropped no seed, only a seed already carried by
                // this worker may be used immediately. Otherwise the plot waits
                // for a physical storage trip on a later work action.
                if (!replanted && worker().getPlantingSeeds().is(seed)) plantCarriedSeed(level, target);
                state = JobState.WORKING;
            }
        } else if (cropState.isAir() && level.getBlockState(target.below()).is(Blocks.FARMLAND)) {
            if (plantCarriedSeed(level, target)) {
                villager.swing(InteractionHand.MAIN_HAND); state = JobState.WORKING;
            }
        }
        target = null;
        ticksUntilNextWork = 40;
    }

    /** Collect plain wheat from a shared container, carry it to the claimed
     * workstation, and carry the resulting bread back. Legacy virtual wheat
     * is not an inventory and cannot be picked up by this physical recipe. */
    private boolean makeBread(ServerLevel level) {
        var supplies = worker().getWorkSupplies();
        if (!supplies.isEmpty() && !plainWheat(supplies)) { state = JobState.STUCK; return true; }
        int carried = supplies.isEmpty() ? 0 : supplies.getCount();
        if (carried >= 3) {
            if (!atClaimedWorkstation(level)) return true;
            supplies.shrink(3);
            if (supplies.isEmpty()) worker().setWorkSupplies(ItemStack.EMPTY);
            carryHarvest(new ItemStack(Items.BREAD));
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 40; return true;
        }
        var warehouse = colony.getWarehouseManager();
        var storage = warehouse.courierStoragePositions(level);
        int available = carried;
        for (var pos : storage) {
            var container = warehouse.courierContainer(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++)
                if (plainWheat(container.getItem(i))) available += container.getItem(i).getCount();
        }
        // If there is not yet enough physical wheat, continue harvesting.
        if (available < 3) return false;
        unreachableStorageUntil.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        var candidates = storage.stream().filter(p -> !unreachableStorageUntil.containsKey(p))
                .filter(p -> {
                    var container = warehouse.courierContainer(level, p);
                    if (container == null) return false;
                    for (int i = 0; i < container.getContainerSize(); i++) if (plainWheat(container.getItem(i))) return true;
                    return false;
                }).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).toList();
        for (BlockPos pos : candidates) {
            if (villager.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                var path = villager.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) { unreachableStorageUntil.put(pos, level.getGameTime()+200); continue; }
                villager.getNavigation().moveTo(path, .7);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return true;
            }
            var container = warehouse.courierContainer(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (!plainWheat(container.getItem(i))) continue;
                var taken = container.removeItem(i, 3 - carried);
                container.setChanged();
                if (supplies.isEmpty()) worker().setWorkSupplies(taken);
                else supplies.grow(taken.getCount());
                villager.swing(InteractionHand.MAIN_HAND);
                state = JobState.WORKING; ticksUntilNextWork = 10; return true;
            }
        }
        // Unreachable supplies do not prevent the farmer working its field.
        return false;
    }

    private static boolean plainWheat(ItemStack stack) {
        return stack.is(Items.WHEAT) && ItemStack.isSameItemSameComponents(stack, Items.WHEAT.getDefaultInstance());
    }

    /** The two persisted job-cargo slots retain actual stacks and components.
     * Unexpected excess remains as a real dropped item at the harvest site. */
    private void carryHarvest(ItemStack stack) {
        var remaining = stack.copy();
        if (worker().getCourierCargo().isEmpty()) worker().setCourierCargo(remaining.split(remaining.getMaxStackSize()));
        else mergeHarvest(worker().getCourierCargo(), remaining);
        if (!remaining.isEmpty()) {
            if (worker().getCourierCargoExtra().isEmpty()) worker().setCourierCargoExtra(remaining.split(remaining.getMaxStackSize()));
            else mergeHarvest(worker().getCourierCargoExtra(), remaining);
        }
        if (!remaining.isEmpty()) villager.spawnAtLocation(remaining);
    }

    private static void mergeHarvest(ItemStack target, ItemStack source) {
        if (!ItemStack.isSameItemSameComponents(target, source)) return;
        int moved = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
        if (moved > 0) { target.grow(moved); source.shrink(moved); }
    }

    private void deliverHarvest(ServerLevel level) {
        var warehouse = colony.getWarehouseManager();
        unreachableStorageUntil.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        var candidates = warehouse.courierStoragePositions(level).stream()
                .filter(p -> !unreachableStorageUntil.containsKey(p))
                .filter(p -> {
                    var container = warehouse.courierContainer(level, p);
                    return CourierJob.canInsert(container, worker().getCourierCargo())
                            || CourierJob.canInsert(container, worker().getCourierCargoExtra());
                })
                .sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).toList();
        for (BlockPos destination : candidates) {
            if (villager.distanceToSqr(destination.getX()+0.5, destination.getY()+0.5, destination.getZ()+0.5) > 9) {
                var path = villager.getNavigation().createPath(destination, 1);
                if (path == null || !path.canReach()) {
                    unreachableStorageUntil.put(destination, level.getGameTime() + 200);
                    continue;
                }
                villager.getNavigation().moveTo(path, 0.7);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
            var container = warehouse.courierContainer(level, destination);
            if (container == null) continue;
            CourierJob.insert(container, worker().getCourierCargo());
            CourierJob.insert(container, worker().getCourierCargoExtra());
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 40; return;
        }
        state = JobState.WAITING_FOR_CHEST; ticksUntilNextWork = 100;
    }

    /** Plant only a plain seed the farmer physically carries, and never replace
     * a block or charge for a failed placement. */
    boolean plantCarriedSeed(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below()) || !colony.isInside(pos)
                || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        var stack = worker().getPlantingSeeds();
        Block crop = cropForSeed(stack);
        if (crop == null || !level.setBlock(pos, crop.defaultBlockState(), 3)) return false;
        stack.shrink(1);
        if (stack.isEmpty()) worker().setPlantingSeeds(ItemStack.EMPTY);
        return true;
    }

    private static Block cropForSeed(ItemStack stack) {
        if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance())) return null;
        if (stack.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT;
        if (stack.is(Items.CARROT)) return Blocks.CARROTS;
        if (stack.is(Items.POTATO)) return Blocks.POTATOES;
        if (stack.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS;
        return null;
    }

    private void collectSeed(ServerLevel level) {
        var warehouse = colony.getWarehouseManager();
        unreachableStorageUntil.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        var candidates = warehouse.courierStoragePositions(level).stream()
                .filter(p -> !unreachableStorageUntil.containsKey(p))
                .filter(p -> {
                    var c = warehouse.courierContainer(level, p);
                    if (c == null) return false;
                    for (int i = 0; i < c.getContainerSize(); i++) if (cropForSeed(c.getItem(i)) != null) return true;
                    return false;
                }).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).toList();
        for (BlockPos pos : candidates) {
            if (villager.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                var path = villager.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) { unreachableStorageUntil.put(pos, level.getGameTime()+200); continue; }
                villager.getNavigation().moveTo(path, .7); state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
            var c = warehouse.courierContainer(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (cropForSeed(c.getItem(i)) == null) continue;
                var taken = c.removeItem(i, 8);
                if (taken.isEmpty()) continue;
                c.setChanged(); worker().setPlantingSeeds(taken);
                villager.swing(InteractionHand.MAIN_HAND); state = JobState.WORKING; ticksUntilNextWork = 10; return;
            }
        }
        state = JobState.NEEDS_RESOURCE; ticksUntilNextWork = 100;
    }

    private boolean eligible(ServerLevel level, BlockPos pos, BlockPos farm) {
        if (Math.abs(pos.getX() - farm.getX()) > 5 || Math.abs(pos.getZ() - farm.getZ()) > 5
                || Math.abs(pos.getY() - farm.getY()) > 3) return false;
        if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.getBlock() instanceof CropBlock crop && seedFor(state) != null && crop.isMaxAge(state));
    }

    private BlockPos findWork(ServerLevel level, BlockPos center) {
        unreachableUntil.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        BlockPos empty = null;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -3, -5), center.offset(5, 3, 5))) {
            if (!level.isLoaded(p) || !colony.isInside(p) || unreachableUntil.containsKey(p) || !eligible(level, p, center)) continue;
            if (!level.getBlockState(p).isAir()) return p.immutable();
            if (empty == null) empty = p.immutable();
        }
        return empty;
    }

    private Item seedFor(BlockState state) {
        if (state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (state.is(Blocks.CARROTS)) return Items.CARROT;
        if (state.is(Blocks.POTATOES)) return Items.POTATO;
        if (state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return null;
    }
}
