package com.simplecolonies.colony;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.progression.ResearchManager;
import com.simplecolonies.villager.job.JobType;
import com.simplecolonies.warehouse.WarehouseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core colony data. One colony = one village with:
 * - culture (Egyptian/Roman/etc)
 * - center (town hall pos) + border radius
 * - citizens (villager UUIDs + job assignments)
 * - inventory (shared warehouse stockpile + distributed chests)
 * - buildings (placed + blueprints queued + player-designated via Building Marker)
 * - reputation per player, leader, diplomacy
 * - tick state (food, expansion, supply chain)
 */
public class Colony {
    private final int id;
    private final CultureType culture;
    private BlockPos center;
    private int level; // 1-5: hamlet -> village -> town -> city -> metropolis
    private int radius;
    private final Map<UUID, CitizenData> citizens = new ConcurrentHashMap<>();
    private final List<Building> buildings = new ArrayList<>();
    // Legacy abstract stockpile — still used as fallback when no chests scanned (e.g. freshly generated village)
    private final Map<String, Integer> warehouse = new HashMap<>();
    // New: distributed chest network
    private final WarehouseManager warehouseManager;
    private final ResearchManager researchManager;
    private final Map<UUID, Integer> reputation = new HashMap<>(); // player -> 0..100
    private UUID leaderId; // player who is leader
    private final Diplomacy diplomacy = new Diplomacy();
    private int daysWithoutFood = 0;
    private int daysSinceExpansion = 0;
    private ColonyState state = ColonyState.ACTIVE;
    private long lastTickGameTime = 0;
    private long lastSimulatedDay = -1;

    // For persistence
    private final UUID colonyUuid = UUID.randomUUID();

    public Colony(int id, CultureType culture, BlockPos center) {
        this.id = id;
        this.culture = culture;
        this.center = center;
        this.level = 1;
        this.radius = 32 + level * 16; // grows with level
        this.warehouseManager = new WarehouseManager(this);
        this.researchManager = new ResearchManager(this);
    }

    public int getId() { return id; }
    public CultureType getCulture() { return culture; }
    public BlockPos getCenter() { return center; }
    public int getLevel() { return level; }
    public int getRadius() { return radius; }
    public ColonyState getState() { return state; }
    public UUID getLeaderId() { return leaderId; }
    public Diplomacy getDiplomacy() { return diplomacy; }
    public Collection<CitizenData> getCitizens() { return citizens.values(); }
    public List<Building> getBuildings() { return Collections.unmodifiableList(buildings); }
    public Map<UUID, Integer> getReputationMap() { return reputation; }
    public WarehouseManager getWarehouseManager() { return warehouseManager; }
    public ResearchManager getResearch() { return researchManager; }

    public int citizenCount() { return citizens.size(); }
    public boolean isLeader(Player player) { return leaderId != null && leaderId.equals(player.getUUID()); }

    public int getReputation(Player player) {
        return reputation.getOrDefault(player.getUUID(), 0);
    }

    public void addReputation(Player player, int delta, String reason) {
        int cur = getReputation(player);
        int next = Math.clamp(cur + delta, 0, 100);
        reputation.put(player.getUUID(), next);
        if (ColonyManager.DEBUG) {
            System.out.println("[SimpleColonies] Rep " + player.getName().getString() + " " + cur + "->" + next + " (" + reason + ")");
        }
        if (leaderId == null && next >= com.simplecolonies.config.ColonyConfig.REPUTATION_NEEDED_TO_LEAD.get()) {
            setLeader(player);
        }
    }

    public void setLeader(Player player) {
        this.leaderId = player.getUUID();
        state = ColonyState.ACTIVE;
        ColonyManager.getInstance().markDirty(this);
        player.displayClientMessage(Component.literal("§6You are now Leader of the " + culture.displayName + " colony #" + id + "! Use the Town Hall to command expansion and armies."), false);
    }

    public boolean isInside(BlockPos pos) {
        return center.distSqr(pos) <= (long) radius * radius;
    }

    /** Called every ColonyConfig.TICKS_BETWEEN_COLONY_TICK */
    public void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime - lastTickGameTime < 100) return; // debounce
        lastTickGameTime = gameTime;

        if (state == ColonyState.ABANDONED || state == ColonyState.DESTROYED) return;

        // Scan warehouse chests periodically
        warehouseManager.scan(level);

        long day = Math.floorDiv(level.getDayTime(), 24000L);
        if (lastSimulatedDay < 0 || day < lastSimulatedDay) lastSimulatedDay = day;
        // Process a bounded batch each tick so enormous /time jumps cannot freeze the server.
        // The persisted cursor is advanced only after each individual day has run.
        int dailyBudget = DailyCatchUp.batchSize(lastSimulatedDay, day, 64);
        while (dailyBudget-- > 0) {
            int neededFood = (int) Math.ceil(citizens.size() * com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get());
            int availableFood = countFoodInWarehouse(level);
            if (availableFood >= neededFood) {
                consumeFood(level, neededFood);
                daysWithoutFood = 0;
                if (state == ColonyState.STARVING) state = ColonyState.ACTIVE;
            } else {
                consumeFood(level, availableFood);
                daysWithoutFood++;
                broadcast(Component.literal("§cColony #" + id + " is starving! Day " + daysWithoutFood + " without enough food (" + availableFood + "/" + neededFood + ")"));
                if (daysWithoutFood >= com.simplecolonies.config.ColonyConfig.DAYS_WITHOUT_FOOD_BEFORE_STARVATION.get()) {
                    if (com.simplecolonies.config.ColonyConfig.VILLAGERS_DIE_WITHOUT_FOOD.get()) {
                        state = ColonyState.STARVING;
                    }
                }
            }

            if (com.simplecolonies.config.ColonyConfig.VILLAGERS_NEED_HOUSING.get()) {
                int housingSlots = shelterCapacity();
                if (housingSlots < citizens.size()) {
                    broadcast(Component.literal("§eColony #" + id + " overcrowded: " + citizens.size() + "/" + housingSlots + " shelter slots. Build more houses! Use Building Marker to designate your own houses."));
                }
            }

            for (CitizenData citizen : List.copyOf(citizens.values())) {
                citizen.queueDailyHealth(state == ColonyState.STARVING);
                if (level.getEntity(citizen.uuid) instanceof com.simplecolonies.entity.ColonyVillager villager) {
                    villager.applyPendingDailyHealth(citizen);
                }
            }

            // Breeding: if sheltered + fed, consume 3 bread per baby and spawn near a House
            tryBreed(level);

            // Progression: village can self-unlock research if it has surplus
            researchManager.tickAutoProgress(level);
            lastSimulatedDay++;
        }

        // Expansion is driven by actual simulation ticks and available supplies, not the calendar.
        if (com.simplecolonies.config.ColonyConfig.ENABLE_NATURAL_EXPANSION.get()) {
            tryLevelUp();
            tryExpand(level);
        }

        for (Building b : buildings) {
            b.tick(level, this);
        }

        diplomacy.tick(this, level);
        ColonyManager.getInstance().markDirty(this);
    }

    private int countFoodInWarehouse(ServerLevel level) {
        int chestFood = 0;
        if (level != null) {
            warehouseManager.scan(level);
            for (var e : warehouseManager.snapshot().entrySet()) {
                if (isFood(e.getKey())) chestFood += e.getValue();
            }
        }
        return chestFood + warehouse.entrySet().stream()
                .filter(e -> isFood(e.getKey()))
                .mapToInt(Map.Entry::getValue).sum();
    }
    private int countFoodInWarehouse() { return countFoodInWarehouse(null); }

    private boolean isFood(String itemId) { return FoodRules.isFood(itemId); }

    private void consumeFood(ServerLevel level, int amount) {
        if (amount <= 0) return;
        // Try chests first
        if (level != null && warehouseManager.getChestCount() > 0) {
            int remaining = amount;
            for (String fid : FoodRules.FOOD_IDS) {
                if (remaining <= 0) break;
                int have = warehouseManager.count(fid);
                int take = Math.min(have, remaining);
                if (take > 0) {
                    remaining -= warehouseManager.extract(level, center, fid, take, false);
                }
            }
            if (remaining == 0) return;
            amount = remaining;
        }
        int remaining = amount;
        for (Iterator<Map.Entry<String, Integer>> it = warehouse.entrySet().iterator(); it.hasNext() && remaining > 0; ) {
            Map.Entry<String, Integer> e = it.next();
            if (!isFood(e.getKey())) continue;
            int take = Math.min(e.getValue(), remaining);
            e.setValue(e.getValue() - take);
            if (e.getValue() <= 0) it.remove();
            remaining -= take;
        }
    }
    private void consumeFood(int amount) { consumeFood(null, amount); }

    // A starter town hall provides shelter for its four founders, without inventing beds.
    private int shelterCapacity() {
        return buildings.stream().filter(b -> b.getState() == com.simplecolonies.building.BuildingState.COMPLETE)
                .mapToInt(b -> b.getType() == BuildingType.TOWN_HALL ? 4
                        : b.getType() == BuildingType.HOUSE ? b.getHousingCapacity() : 0).sum();
    }

    private String expansionStatus = "Not checked yet";
    public String getExpansionStatus() { return expansionStatus; }

    private void tryExpand(ServerLevel level) {
        if (citizens.isEmpty()) { expansionStatus = "No citizens"; return; }
        if (citizens.values().stream().noneMatch(c -> c.job == JobType.BUILDER)) {
            expansionStatus = "No builder assigned"; return;
        }
        if (buildings.stream().anyMatch(b -> b.hasSupportedPlan() && b.getState() != com.simplecolonies.building.BuildingState.COMPLETE)) {
            expansionStatus = "Construction already queued"; return;
        }
        var stock = getWarehouseSnapshot(level);
        String housingReason = "Housing capacity sufficient";
        if (HousingNeeds.needsHouse(citizens.size(), shelterCapacity(),
                com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get())) {
            // Different footprints have different costs. Check all three before
            // searching, then select a site whose actual variant is affordable.
            boolean[] affordable = new boolean[3];
            for (int i = 0; i < 3; i++) {
                Building sample = new Building(BuildingType.HOUSE, center.east(i), culture);
                affordable[sample.getHouseVariant()] = com.simplecolonies.building.ConstructionCrafting.canBuild(stock, sample.getRemainingPlacements());
            }
            if (!affordable[0] && !affordable[1] && !affordable[2]) {
                housingReason = "Not enough materials for any house";
            } else {
                BlockPos site = findHouseSite(level, affordable);
                if (site == null) housingReason = "No safe loaded site for an affordable house";
                else {
                    queueExpansion(new Building(BuildingType.HOUSE, site, culture));
                    return;
                }
            }
        }
        // Worksite shells are planned around the POI actually claimed by an assigned worker.
        // Never invent or move a workstation, and never designate an unloaded/obstructed plot.
        for (var pair : java.util.List.of(
                Map.entry(JobType.BUILDER, BuildingType.BUILDER_HUT),
                Map.entry(JobType.LUMBERJACK, BuildingType.LUMBERJACK_HUT),
                Map.entry(JobType.MINER, BuildingType.MINE),
                Map.entry(JobType.BLACKSMITH, BuildingType.BLACKSMITH),
                Map.entry(JobType.BUTCHER, BuildingType.BUTCHER_HUT),
                Map.entry(JobType.BAKER, BuildingType.BAKERY),
                Map.entry(JobType.LEATHERWORKER, BuildingType.LEATHER_SHOP),
                Map.entry(JobType.FISHERMAN, BuildingType.HARBOR))) {
            for (CitizenData citizen : citizens.values()) {
                if (citizen.job != pair.getKey()) continue;
                if (!(level.getEntity(citizen.uuid) instanceof com.simplecolonies.entity.ColonyVillager worker)
                        || worker.getColonyId() != id || worker.getColonyJob() != pair.getKey()) continue;
                var memory = worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
                if (memory.isEmpty() || !memory.get().dimension().equals(level.dimension())) continue;
                BlockPos station = memory.get().pos();
                if (!level.isLoaded(station) || level.getPoiManager().getType(station)
                        .filter(worker.getVillagerData().getProfession().heldJobSite()).isEmpty()
                        || !isClearWorksite(level, station, true)
                        || (pair.getValue() == BuildingType.HARBOR && !hasFishingWater(level, station))) continue;
                Building building = new Building(pair.getValue(), station, culture);
                if (com.simplecolonies.building.ConstructionCrafting.canBuild(stock, building.getRemainingPlacements())) {
                    queueExpansion(building);
                    return;
                }
            }
        }
        expansionStatus = housingReason + "; no affordable, clear claimed worksite";
    }

    private void queueExpansion(Building blueprint) {
        buildings.add(blueprint);
        expansionStatus = "Construction already queued";
        ColonyManager.getInstance().markDirty(this);
        broadcast(Component.literal("§aColony #" + id + " is expanding: " + blueprint.getType().displayName
                + " at " + blueprint.getPos().toShortString()));
    }

    /** Validate an unchanged five-by-five plot, optionally allowing the central workstation. */
    public boolean isClearWorksite(ServerLevel level, BlockPos station, boolean allowStation) {
        if (buildings.stream().anyMatch(b -> b.getPos().distSqr(station) < 144)) return false;
        if (!clearEntrance(level, station.north(3))) return false;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos ground = station.offset(x, -1, z);
            if (!level.isLoaded(ground) || !isInside(ground)) return false;
            var soil = level.getBlockState(ground);
            if (!(soil.is(net.minecraft.tags.BlockTags.DIRT) || soil.is(net.minecraft.world.level.block.Blocks.SAND)
                    || soil.is(net.minecraft.world.level.block.Blocks.RED_SAND) || soil.is(net.minecraft.world.level.block.Blocks.GRAVEL))) return false;
            for (int y = 0; y <= 3; y++) {
                BlockPos p = station.offset(x, y, z);
                if (!level.isLoaded(p)) return false;
                if (allowStation && x == 0 && y == 0 && z == 0) continue;
                var block = level.getBlockState(p);
                if (level.getBlockEntity(p) != null || (!block.isAir() && !block.canBeReplaced())
                        || !block.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }

    /** A modest, loaded two-deep pool near the dry worksite. This does not
     * guarantee a path to the shore or that the biome will spawn fish. */
    private boolean hasFishingWater(ServerLevel level, BlockPos station) {
        for (int x = -8; x <= 7; x++) for (int z = -8; z <= 7; z++) {
            if (x * x + z * z > 49) continue;
            for (int y = -2; y <= 0; y++) {
                BlockPos corner = station.offset(x, y, z);
                boolean water = true;
                for (int dx = 0; dx <= 1 && water; dx++) for (int dz = 0; dz <= 1 && water; dz++) {
                    BlockPos surface = corner.offset(dx, 1, dz);
                    if (!level.isLoaded(surface) || !level.getBlockState(surface).isAir()) { water = false; break; }
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos p = corner.offset(dx, dy, dz);
                        if (!isInside(p) || !level.isLoaded(p) || !level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)
                                || !level.getFluidState(p).isSource()) { water = false; break; }
                    }
                }
                if (water) return true;
            }
        }
        return false;
    }

    /** Queues a house only when there is no unfinished construction and a safe site exists. */
    public Building queueHouse(ServerLevel level) {
        if (buildings.stream().anyMatch(b -> b.hasSupportedPlan() && b.getState() != com.simplecolonies.building.BuildingState.COMPLETE)) return null;
        BlockPos site = findHouseSite(level, new boolean[]{true, true, true});
        if (site == null) return null;
        Building building = new Building(BuildingType.HOUSE, site, culture);
        buildings.add(building);
        ColonyManager.getInstance().markDirty(this);
        return building;
    }

    public int getFoodCount(ServerLevel level) { return countFoodInWarehouse(level); }

    /** A tier is based on actual population and completed buildings, not elapsed days.
     * Reject boundary overlap rather than claiming another colony's territory. */
    public String getLevelUpBlocker() {
        if (this.level >= 5) return "Already at maximum level";
        long completed = buildings.stream().filter(b -> b.getState() == com.simplecolonies.building.BuildingState.COMPLETE).count();
        if (completed < this.level * 3L || citizenCount() < this.level * 8)
            return "Requires " + (this.level * 3) + " completed buildings and " + (this.level * 8) + " citizens";
        int nextRadius = 32 + (this.level + 1) * 16;
        for (Colony other : ColonyManager.getInstance().getAll()) {
            if (other == this || other.getId() == id) continue;
            long dx = (long) center.getX() - other.center.getX();
            long dz = (long) center.getZ() - other.center.getZ();
            long separation = (long) nextRadius + other.radius;
            if (dx * dx + dz * dz < separation * separation) return "Expansion would overlap another colony";
        }
        return null;
    }

    public boolean tryLevelUp() {
        if (getLevelUpBlocker() != null) return false;
        this.level++;
        radius = 32 + this.level * 16;
        ColonyManager.getInstance().markDirty(this);
        broadcast(Component.literal("§6§lColony #" + id + " grew to " + ColonyLevel.fromLevel(this.level).displayName + "! Radius now " + radius + " blocks."));
        return true;
    }

    /** Search a dense, nearest-first grid without loading chunks. */
    private BlockPos findHouseSite(ServerLevel level, boolean[] affordable) {
        for (var offset : StarterSiteSearch.offsets(radius - 4, 2)) {
            if ((long) offset.x() * offset.x() + (long) offset.z() * offset.z() < 144) continue;
            BlockPos candidate = center.offset(offset.x(), 0, offset.z());
            if (!level.isLoaded(candidate)) continue;
            BlockPos pos = new BlockPos(candidate.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ()), candidate.getZ());
            if (affordable[Building.houseVariantAt(pos)] && isClearBuildSite(level, pos)) return pos;
        }
        return null;
    }

    /** Validate an unobstructed, dry approach to a future doorway. */
    private boolean clearEntrance(ServerLevel level, BlockPos feet) {
        if (!isInside(feet) || !level.isLoaded(feet) || !level.isLoaded(feet.above()) || !level.isLoaded(feet.below())) return false;
        var below = level.getBlockState(feet.below());
        var at = level.getBlockState(feet);
        var above = level.getBlockState(feet.above());
        return below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && at.getFluidState().isEmpty() && above.getFluidState().isEmpty()
                && at.getCollisionShape(level, feet).isEmpty() && above.getCollisionShape(level, feet.above()).isEmpty();
    }

    public boolean isClearBuildSite(ServerLevel level, BlockPos pos) {
        if (buildings.stream().anyMatch(b -> b.getPos().distSqr(pos) < 144)) return false;
        int variant = Building.houseVariantAt(pos);
        if (!clearEntrance(level, pos.north(variant == 2 ? 4 : 3))) return false;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos ground = pos.offset(x, -1, z);
            if (!level.isLoaded(ground) || !isInside(ground)) return false;
            var soil = level.getBlockState(ground);
            if (!(soil.is(net.minecraft.tags.BlockTags.DIRT) || soil.is(net.minecraft.world.level.block.Blocks.SAND)
                    || soil.is(net.minecraft.world.level.block.Blocks.RED_SAND) || soil.is(net.minecraft.world.level.block.Blocks.GRAVEL))) return false;
            for (int y = 0; y <= 5; y++) {
                if (!level.isLoaded(pos.offset(x, y, z))) return false;
                var block = level.getBlockState(pos.offset(x, y, z));
                if ((!block.isAir() && !block.canBeReplaced()) || !block.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }

    public void addCitizen(CitizenData citizen) {
        citizens.put(citizen.uuid, citizen);
        ColonyManager.getInstance().markDirty(this);
    }

    public void removeCitizen(UUID villagerUuid) {
        citizens.remove(villagerUuid);
        ColonyManager.getInstance().markDirty(this);
    }

    // Vanilla-like breeding with costs → populates empty houses and drives expansion
    private void tryBreed(ServerLevel level) {
        int housingSlots = shelterCapacity();
        if (housingSlots <= citizens.size()) return; // no free shelter
        if (citizens.size() < 2) return; // need pair
        if (daysWithoutFood > 0) return; // must be well-fed
        int neededFood = (int) Math.ceil(citizens.size() * com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get());
        int availableFood = countFoodInWarehouse(level);
        // Need surplus of 2 days + 3 bread breeding cost, and at least 3% chance per day per empty bed (vanilla ~5%)
        if (availableFood < neededFood * 2 + 3) return;
        int emptyBeds = housingSlots - citizens.size();
        double chance = 0.25 + 0.08 * emptyBeds; // 25% base + more beds = faster growth, cap below
        if (level.random.nextDouble() > Math.min(chance, 0.65)) return;

        // Pay breeding cost: 3 bread (vanilla) — consume from warehouse/chests
        int breadHave = getWarehouseCount(level, "minecraft:bread");
        String costId = breadHave >= 3 ? "minecraft:bread" : null;
        if (costId == null) {
            // fallback to wheat/carrots if no bread
            int wheatHave = getWarehouseCount(level, "minecraft:wheat");
            if (wheatHave >= 12) costId = "minecraft:wheat";
            else return;
        }
        int cost = costId.equals("minecraft:bread") ? 3 : 12;
        var houses = buildings.stream().filter(b -> b.getType() == BuildingType.HOUSE
                && b.getState() == com.simplecolonies.building.BuildingState.COMPLETE).toList();
        BlockPos anchor = houses.isEmpty() ? center : houses.get(level.random.nextInt(houses.size())).getPos();
        BlockPos spawnAt = null;
        for (int dy = 0; dy <= 3 && spawnAt == null; dy++) for (int dx = -4; dx <= 4 && spawnAt == null; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos p = anchor.offset(dx, dy, dz);
            if (!level.isLoaded(p) || !isInside(p) || !level.isLoaded(p.above()) || !level.isLoaded(p.below())) continue;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    || !level.getBlockState(p).getFluidState().isEmpty()
                    || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) continue;
            spawnAt = p; break;
        }
        if (spawnAt == null || !consumeStock(level, costId, cost)) return;
        com.simplecolonies.entity.ColonyVillager baby = null;
        boolean added = false;
        try {
            baby = com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get().create(level);
            if (baby == null) return;
            baby.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            baby.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnAt), net.minecraft.world.entity.MobSpawnType.BREEDING, null);
            baby.setBaby(true);
            baby.setColony(this);
            baby.setJob(JobType.UNEMPLOYED);
            added = level.addFreshEntity(baby);
            if (added) broadcast(Component.literal("§aColony #" + id + " bred a new villager at " + spawnAt.toShortString()));
        } catch (Exception e) {
            com.simplecolonies.SimpleColonies.LOGGER.error("Failed to spawn baby for colony #{}", id, e);
        } finally {
            if (!added) {
                // finalizeSpawn may have already registered the entity, even if insertion failed.
                if (baby != null) {
                    var registered = ColonyManager.getInstance().getColony(baby.getColonyId()).orElse(null);
                    if (registered != null) registered.removeCitizen(baby.getUUID());
                }
                depositToWarehouse(costId, cost);
            }
        }
    }

    /** Take a real item when available, preserving its components; legacy virtual stock
     * stores only item IDs and therefore can only represent an undamaged item. */
    public net.minecraft.world.item.ItemStack takeStockItem(ServerLevel level, BlockPos requester,
                                                             net.minecraft.world.item.Item item) {
        if (level != null) {
            var taken = warehouseManager.extractOne(level, requester, item);
            if (!taken.isEmpty()) { ColonyManager.getInstance().markDirty(this); return taken; }
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        if (warehouse.getOrDefault(id, 0) < 1) return net.minecraft.world.item.ItemStack.EMPTY;
        depositToWarehouse(id, -1);
        return new net.minecraft.world.item.ItemStack(item);
    }

    /** Consume from chest and virtual stockpile, exactly once, or not at all. */
    public boolean consumeStock(ServerLevel level, String itemId, int amount) {
        if (amount <= 0) return true;
        // Simulate against live, loaded containers. Cached counts can be stale when players
        // move items between scans; never partially consume if the full amount is absent.
        int available = level == null ? 0 : warehouseManager.extract(level, center, itemId, amount, true);
        if ((long) available + warehouse.getOrDefault(itemId, 0) < amount) return false;
        int taken = level == null ? 0 : warehouseManager.extract(level, center, itemId, amount, false);
        int remaining = amount - taken;
        if (remaining > warehouse.getOrDefault(itemId, 0)) return false;
        if (remaining > 0) depositToWarehouse(itemId, -remaining);
        ColonyManager.getInstance().markDirty(this);
        return true;
    }

    /** Deposit: try chests first, fallback to abstract map. Returns true if any deposited. */
    public void depositToWarehouse(ServerLevel level, String itemId, int count) {
        if (level != null) warehouseManager.scan(level);
        if (level != null && warehouseManager.getChestCount() > 0) {
            int deposited = warehouseManager.deposit(level, center, itemId, count);
            if (deposited >= count) return;
            count -= deposited;
        }
        if (count > 0) warehouse.merge(itemId, count, Integer::sum);
        ColonyManager.getInstance().markDirty(this);
    }
    public void depositToWarehouse(String itemId, int count) {
        // No level context: use abstract map (e.g. during NBT load or job tick without level)
        // Try to resolve level lazily via ColonyManager server - omitted for simplicity, use abstract
        warehouse.merge(itemId, count, Integer::sum);
        if (warehouse.getOrDefault(itemId, 0) <= 0) warehouse.remove(itemId);
        ColonyManager.getInstance().markDirty(this);
    }

    public int getWarehouseCount(ServerLevel level, String itemId) {
        if (level != null) warehouseManager.scan(level);
        if (level != null && warehouseManager.getChestCount() > 0) {
            int chestCount = warehouseManager.count(itemId);
            int abstractCount = warehouse.getOrDefault(itemId, 0);
            return chestCount + abstractCount;
        }
        return warehouse.getOrDefault(itemId, 0);
    }
    public int getWarehouseCount(String itemId) { return warehouse.getOrDefault(itemId, 0); }

    /** For GUI: combined snapshot of chests + abstract */
    public Map<String, Integer> getWarehouseSnapshot(ServerLevel level) {
        Map<String, Integer> out = new HashMap<>(warehouse);
        if (level != null) {
            warehouseManager.scan(level);
            for (var e : warehouseManager.snapshot().entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return out;
    }

    public void setWarehouseCenter(BlockPos pos) {
        warehouseManager.setWarehouseCenter(pos);
    }

    public void broadcast(Component msg) {
        com.simplecolonies.SimpleColonies.LOGGER.info("[Colony #{}] {}", id, msg.getString());
    }

    // --- Building Marker integration ---
    public boolean registerPlayerBuilding(Building building) {
        if (!isInside(building.getPos())) return false;
        buildings.add(building);
        ColonyManager.getInstance().markDirty(this);
        broadcast(Component.literal("§aRegistered player building: " + building.getType().displayName + " at " + building.getPos().toShortString() + " as part of colony #" + id));
        return true;
    }

    // --- NBT ---
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("culture", culture.id);
        tag.putInt("level", level);
        tag.putInt("radius", radius);
        tag.putLong("center", center.asLong());
        tag.putInt("daysWithoutFood", daysWithoutFood);
        tag.putInt("daysSinceExpansion", daysSinceExpansion);
        tag.putLong("lastSimulatedDay", lastSimulatedDay);
        tag.putString("state", state.name());
        if (leaderId != null) tag.putUUID("leader", leaderId);
        // Do not persist the display fallback (colony center) as a designated hub.
        if (warehouseManager.getConfiguredWarehouseCenter() != null)
            tag.putLong("warehouseCenter", warehouseManager.getConfiguredWarehouseCenter().asLong());
        ListTag citizensTag = new ListTag();
        for (CitizenData c : citizens.values()) citizensTag.add(c.save());
        tag.put("citizens", citizensTag);
        ListTag buildingsTag = new ListTag();
        for (Building b : buildings) buildingsTag.add(b.save());
        tag.put("buildings", buildingsTag);
        CompoundTag wh = new CompoundTag();
        warehouse.forEach((k,v) -> wh.putInt(k, v));
        tag.put("warehouse", wh);
        CompoundTag rep = new CompoundTag();
        reputation.forEach((uuid, val) -> rep.putInt(uuid.toString(), val));
        tag.put("reputation", rep);
        tag.put("diplomacy", diplomacy.save());
        tag.put("research", researchManager.save());
        return tag;
    }

    public static Colony load(CompoundTag tag) {
        int id = tag.getInt("id");
        CultureType culture = CultureType.byId(tag.getString("culture"));
        BlockPos center = BlockPos.of(tag.getLong("center"));
        Colony c = new Colony(id, culture, center);
        c.level = tag.getInt("level");
        c.radius = tag.getInt("radius");
        c.daysWithoutFood = tag.getInt("daysWithoutFood");
        c.daysSinceExpansion = tag.getInt("daysSinceExpansion");
        c.lastSimulatedDay = tag.contains("lastSimulatedDay") ? tag.getLong("lastSimulatedDay") : -1;
        c.state = ColonyState.valueOf(tag.getString("state"));
        if (tag.hasUUID("leader")) c.leaderId = tag.getUUID("leader");
        if (tag.contains("warehouseCenter")) c.warehouseManager.setWarehouseCenter(BlockPos.of(tag.getLong("warehouseCenter")));
        ListTag citizensTag = tag.getList("citizens", 10);
        for (int i = 0; i < citizensTag.size(); i++) {
            CitizenData cd = CitizenData.load(citizensTag.getCompound(i));
            c.citizens.put(cd.uuid, cd);
        }
        ListTag buildingsTag = tag.getList("buildings", 10);
        for (int i = 0; i < buildingsTag.size(); i++) {
            c.buildings.add(Building.load(buildingsTag.getCompound(i)));
        }
        CompoundTag wh = tag.getCompound("warehouse");
        for (String k : wh.getAllKeys()) c.warehouse.put(k, wh.getInt(k));
        CompoundTag rep = tag.getCompound("reputation");
        for (String k : rep.getAllKeys()) c.reputation.put(UUID.fromString(k), rep.getInt(k));
        c.diplomacy.load(tag.getCompound("diplomacy"));
        if (tag.contains("research")) c.researchManager.load(tag.getCompound("research"));
        return c;
    }
}
