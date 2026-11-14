package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.warehouse.WarehouseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Base job AI. All villagers now deposit to nearest chest in village radius via WarehouseManager,
 * and pull from chests + Warehouse hub. This answers "villagers should just interact with chests round the village".
 *
 * Vanilla AI note: vanilla Minecraft villagers have a schedule (work/bed) but do not produce items — they just
 * pathfind to job site POI and play animations. ColonyVillager completely replaces that with real logic:
 * Farmer actually plants/harvests, Lumberjack chops/plants, Miner mines, Builder places blocks, etc.
 * See ColonyVillager.tick() -> jobInstance.tick().
 */
public abstract class AbstractJob {
    protected final Villager villager;
    protected final Colony colony;
    protected final JobType type;
    protected BlockPos workplacePos;
    private BlockPos claimedSite;
    protected int ticksUntilNextWork = 0;
    protected JobState state = JobState.IDLE;

    public AbstractJob(Villager villager, Colony colony, JobType type) {
        this.villager = villager;
        this.colony = colony;
        this.type = type;
    }

    public JobType getType() { return type; }
    public JobState getState() { return state; }

    public final void tick(ServerLevel level) {
        onTick(level);
        if (ticksUntilNextWork > 0) { ticksUntilNextWork--; return; }
        if (!colony.isInside(villager.blockPosition()) && !mayWorkOutsideColony(level)) {
            state = JobState.GOING_TO_WORK;
            // Path to colony center — courier is faster
            double speed = (type == JobType.COURIER) ? 1.2 : 0.6;
            villager.getNavigation().moveTo(colony.getCenter().getX()+0.5, colony.getCenter().getY(), colony.getCenter().getZ()+0.5, speed);
            ticksUntilNextWork = 40;
            return;
        }
        // Ensure workplace is set — for player-designated buildings via Building Marker, workplacePos will be that marker pos
        var remembered = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        BlockPos currentSite = remembered.filter(p -> p.dimension().equals(level.dimension())).map(p -> p.pos()).orElse(null);
        if (!java.util.Objects.equals(currentSite, claimedSite) || workplacePos == null) {
            claimedSite = currentSite;
            workplacePos = findWorkplace(level);
        }
        ticksUntilNextWork = getWorkInterval();
        doWork(level);
    }

    protected BlockPos findWorkplace(ServerLevel level) {
        if (claimedSite == null) return colony.getCenter();
        // A farm's registered center can be several blocks from its composter.
        // Prefer the nearest matching completed building to this villager's own POI.
        return colony.getBuildings().stream()
                .filter(b -> b.getType().job == type && b.isOperational())
                .filter(b -> b.distanceToOperationalSite(claimedSite) <= 144)
                .min(java.util.Comparator.comparingDouble(b -> b.distanceToOperationalSite(claimedSite)))
                .map(com.simplecolonies.building.Building::getPos).orElse(claimedSite);
    }

    /** Move to the villager's claimed POI before workstation-based production. */
    protected boolean atClaimedWorkstation(ServerLevel level) {
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())) {
            state = JobState.STUCK;
            ticksUntilNextWork = 40;
            return false;
        }
        BlockPos target = site.get().pos();
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 9) return true;
        var path = villager.getNavigation().createPath(target, 1);
        if (path == null || !path.canReach()) {
            state = JobState.STUCK;
            ticksUntilNextWork = 60;
            return false;
        }
        villager.getNavigation().moveTo(path, 0.7);
        state = JobState.GOING_TO_WORK;
        ticksUntilNextWork = 20;
        return false;
    }

    /** Ordinary workers stay within the colony; specific activities may allow travel. */
    protected boolean mayWorkOutsideColony(ServerLevel level) { return false; }

    private PhysicalJobCargo recoveryCargo;
    private PhysicalBuilderMaterials recoveryMaterials;

    /** A missing POI must stop production, not strand physical items on a worker.
     * This deliberately performs only returns and never calls profession work. */
    public final void tickWithoutWorksite(ServerLevel level) {
        if (!(villager instanceof com.simplecolonies.entity.ColonyVillager worker)) return;
        if (ticksUntilNextWork > 0) { ticksUntilNextWork--; return; }
        if (recoveryCargo == null) {
            recoveryCargo = new PhysicalJobCargo(worker, colony);
            recoveryMaterials = new PhysicalBuilderMaterials(worker, colony);
        }
        var delivery = recoveryCargo.deliver(level);
        if (delivery != PhysicalJobCargo.Delivery.NONE) {
            state = delivery == PhysicalJobCargo.Delivery.MOVING ? JobState.GOING_TO_WORK
                    : delivery == PhysicalJobCargo.Delivery.DELIVERED ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = delivery == PhysicalJobCargo.Delivery.MOVING ? 10 : 40;
            return;
        }
        // Do not overwrite either cargo slot; take ownership only when both are empty.
        if (!worker.getWorkSupplies().isEmpty()) {
            worker.setCourierCargo(worker.getWorkSupplies());
            worker.setWorkSupplies(net.minecraft.world.item.ItemStack.EMPTY);
        } else if (!worker.getPlantingSeeds().isEmpty()) {
            worker.setCourierCargo(worker.getPlantingSeeds());
            worker.setPlantingSeeds(net.minecraft.world.item.ItemStack.EMPTY);
        } else {
            var result = recoveryMaterials.returnSurplus(level);
            state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                    : result == PhysicalBuilderMaterials.Result.WORKING ? JobState.WORKING
                    : result == PhysicalBuilderMaterials.Result.READY ? JobState.STUCK : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = result == PhysicalBuilderMaterials.Result.MOVING ? 10 : 40;
            return;
        }
        state = JobState.WORKING;
        ticksUntilNextWork = 0;
    }

    /** Called even between work actions, for behaviours that must not be deferred. */
    protected void onTick(ServerLevel level) {}

    /** Approach a position within reach of both parents. Alternating between
     * their positions can otherwise make the worker walk back and forth forever. */
    protected void approachPair(ServerLevel level, net.minecraft.world.entity.animal.Animal first, net.minecraft.world.entity.animal.Animal second) {
        var existing = villager.getNavigation().getPath();
        if (existing != null && !existing.isDone() && existing.canReach() && existing.getEndNode() != null) {
            BlockPos end = existing.getEndNode().asBlockPos();
            if (end.distToCenterSqr(first.position()) <= 5.0 && end.distToCenterSqr(second.position()) <= 5.0) {
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
        }
        // Ground navigation cannot calculate paths during a jump. Retrying only
        // after a long backoff can repeatedly land on the same airborne phase.
        if (!villager.onGround() && !villager.isInLiquid() && !villager.isPassenger()) {
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 0; return;
        }
        var stands = new java.util.ArrayList<BlockPos>();
        BlockPos center = first.blockPosition();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -1; y <= 1; y++) {
            BlockPos pos = center.offset(x, y, z);
            if (!level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below()) || !colony.isInside(pos)) continue;
            if (pos.distToCenterSqr(first.position()) > 5.0 || pos.distToCenterSqr(second.position()) > 5.0) continue;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
            stands.add(pos);
        }
        stands.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition())));
        for (var stand : stands) {
            if (stand.equals(villager.blockPosition())) continue; // A blocked line of sight requires another stand.
            var path = villager.getNavigation().createPath(stand, 0);
            if (path == null || !path.canReach()) continue;
            villager.getNavigation().moveTo(path, .7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        state = JobState.STUCK; ticksUntilNextWork = 40;
    }

    protected abstract void doWork(ServerLevel level);

    protected int getWorkInterval() {
        int base = 20;
        double mult = colony.getResearch().getWorkSpeedMultiplier();
        // Faster speed = lower interval
        return Math.max(5, (int)(base / mult));
    }

    /** Reuse a worker's stowed tool of this type before withdrawing another from storage. */
    protected net.minecraft.world.item.ItemStack takeTool(ServerLevel level, net.minecraft.world.item.Item item) {
        var inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) return inventory.removeItem(slot, 1);
        }
        return colony.takeStockItem(level, villager.blockPosition(), item);
    }

    /** Starter workers can make a replacement stone tool at a real table in a
     * completed hall or house, from real stock. A smith may also supply tools. */
    protected void craftStoneReplacement(ServerLevel level, String kind) {
        if (!villager.getMainHandItem().isEmpty()) { state = JobState.NEEDS_TOOL; ticksUntilNextWork = 100; return; }
        BlockPos table = null;
        double best = Double.MAX_VALUE;
        for (var building : colony.getBuildings()) {
            if (!building.isOperational()
                    || (building.getType() != com.simplecolonies.building.BuildingType.TOWN_HALL
                    && building.getType() != com.simplecolonies.building.BuildingType.HOUSE)) continue;
            for (BlockPos site : building.shelterSites())
                for (BlockPos pos : BlockPos.betweenClosed(site.offset(-3, -1, -3), site.offset(3, 3, 3))) {
                    if (!level.isLoaded(pos) || !colony.isInside(pos) || !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) continue;
                    double distance = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distance < best) { best = distance; table = pos.immutable(); }
                }
        }
        if (table == null) { state = JobState.NEEDS_TOOL; ticksUntilNextWork = 100; return; }
        if (best > 9) {
            var path = villager.getNavigation().createPath(table, 1);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        var stock = new ToolCrafting.Stock() {
            public int count(String id) { return colony.getWarehouseCount(level, id); }
            public boolean consume(String id, int amount) { return colony.consumeStock(level, id, amount); }
            public void add(String id, int amount) { depositToColony(level, id, amount); }
        };
        if (!ToolCrafting.make(stock, "minecraft:cobblestone", "minecraft:stone_" + kind)) {
            state = JobState.NEEDS_TOOL; ticksUntilNextWork = 100; return;
        }
        var item = kind.equals("pickaxe") ? net.minecraft.world.item.Items.STONE_PICKAXE : net.minecraft.world.item.Items.STONE_AXE;
        var tool = takeTool(level, item);
        if (!tool.isEmpty()) villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
        villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        state = JobState.WORKING; ticksUntilNextWork = 40;
    }

    private PhysicalBuilderMaterials physicalIngredients;
    private PhysicalJobCargo physicalOutput;

    /** A small fixed recipe made at the assigned workstation from items carried
     * from shared storage. Inputs and outputs persist on the villager. */
    protected void physicalRecipe(ServerLevel level, net.minecraft.world.item.Item input, int amount,
            net.minecraft.world.item.Item output, boolean keepSpare, net.minecraft.world.level.block.Block stationBlock) {
        if (!(villager instanceof com.simplecolonies.entity.ColonyVillager worker)) throw new IllegalStateException("Physical recipe requires colony villager");
        if (physicalIngredients == null) {
            physicalIngredients = new PhysicalBuilderMaterials(worker, colony);
            physicalOutput = new PhysicalJobCargo(worker, colony);
        }
        var delivery = physicalOutput.deliver(level);
        if (delivery != PhysicalJobCargo.Delivery.NONE) {
            state = delivery == PhysicalJobCargo.Delivery.MOVING ? JobState.GOING_TO_WORK
                    : delivery == PhysicalJobCargo.Delivery.DELIVERED ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = delivery == PhysicalJobCargo.Delivery.MOVING ? 10 : 40; return;
        }
        var warehouse = colony.getWarehouseManager();
        if (keepSpare && warehouse.courierStoragePositions(level).stream().anyMatch(p -> {
            var c = warehouse.courierContainer(level, p);
            for (int i = 0; c != null && i < c.getContainerSize(); i++) if (c.getItem(i).is(output)) return true;
            return false;
        })) {
            var returned = physicalIngredients.returnSurplus(level);
            state = returned == PhysicalBuilderMaterials.Result.READY ? JobState.IDLE
                    : returned == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                    : returned == PhysicalBuilderMaterials.Result.WORKING ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = returned == PhysicalBuilderMaterials.Result.MOVING ? 10 : 100; return;
        }
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())
                || !colony.isInside(site.get().pos()) || !level.getBlockState(site.get().pos()).is(stationBlock)) {
            state = JobState.STUCK; ticksUntilNextWork = 100; return;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input).toString();
        var result = physicalIngredients.prepareCount(level, id, amount);
        if (result != PhysicalBuilderMaterials.Result.READY) {
            state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                    : result == PhysicalBuilderMaterials.Result.WORKING ? JobState.WORKING
                    : result == PhysicalBuilderMaterials.Result.MISSING ? JobState.NEEDS_RESOURCE : JobState.STUCK;
            ticksUntilNextWork = result == PhysicalBuilderMaterials.Result.MOVING ? 10 : 40; return;
        }
        if (!atClaimedWorkstation(level)) return;
        for (int i = 0; i < amount; i++) {
            if (!physicalIngredients.takePrepared(id)) throw new IllegalStateException("Carried recipe input changed unexpectedly");
        }
        var made = new net.minecraft.world.item.ItemStack(output);
        physicalOutput.collectStack(made);
        if (!made.isEmpty()) worker.spawnAtLocation(made);
        villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        state = JobState.WORKING; ticksUntilNextWork = 40;
    }

    // --- Warehouse / chest helpers ---

    protected void depositToColony(ServerLevel level, String itemId, int count) {
        WarehouseManager wm = colony.getWarehouseManager();
        if (level != null && wm.getChestCount() > 0) {
            int deposited = wm.deposit(level, villager.blockPosition(), itemId, count);
            if (deposited < count) {
                // Remainder goes to abstract stockpile (overflow if chests full)
                colony.depositToWarehouse(level, itemId, count - deposited);
            }
        } else {
            colony.depositToWarehouse(itemId, count);
        }
    }

    // Legacy overload for jobs that haven't been updated to pass level (fallback)
    protected void depositToColony(String itemId, int count) {
        colony.depositToWarehouse(itemId, count);
    }

    protected boolean requestFromColony(ServerLevel level, String itemId, int count) {
        return colony.getWarehouseCount(level, itemId) >= count;
    }

    // Legacy overload
    protected boolean requestFromColony(String itemId, int count) {
        return requestFromColony(null, itemId, count);
    }

    protected WarehouseManager getWarehouse() { return colony.getWarehouseManager(); }

    public enum JobState { IDLE, GOING_TO_WORK, WORKING, STUCK, NEEDS_TOOL, NEEDS_RESOURCE, WAITING_FOR_CHEST }
}
