package com.simplecolonies.colony;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.culture.CultureType;
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
    private final Map<UUID, Integer> reputation = new HashMap<>(); // player -> 0..100
    private UUID leaderId; // player who is leader
    private final Diplomacy diplomacy = new Diplomacy();
    private int daysWithoutFood = 0;
    private int daysSinceExpansion = 0;
    private ColonyState state = ColonyState.ACTIVE;
    private long lastTickGameTime = 0;

    // For persistence
    private final UUID colonyUuid = UUID.randomUUID();

    public Colony(int id, CultureType culture, BlockPos center) {
        this.id = id;
        this.culture = culture;
        this.center = center;
        this.level = 1;
        this.radius = 32 + level * 16; // grows with level
        this.warehouseManager = new WarehouseManager(this);
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

        boolean isNewDay = gameTime % 24000 == 0;
        if (isNewDay) {
            int neededFood = (int) Math.ceil(citizens.size() * com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get());
            int availableFood = countFoodInWarehouse(level);
            if (availableFood >= neededFood) {
                consumeFood(level, neededFood);
                daysWithoutFood = 0;
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
                int housingSlots = buildings.stream().filter(b -> b.getType() == BuildingType.HOUSE).mapToInt(Building::getHousingCapacity).sum();
                if (housingSlots < citizens.size()) {
                    broadcast(Component.literal("§eColony #" + id + " overcrowded: " + citizens.size() + "/" + housingSlots + " beds. Build more houses! Use Building Marker to designate your own houses."));
                }
            }

            daysSinceExpansion++;
            if (com.simplecolonies.config.ColonyConfig.ENABLE_NATURAL_EXPANSION.get() &&
                    daysSinceExpansion >= com.simplecolonies.config.ColonyConfig.EXPANSION_CHECK_DAYS.get() &&
                    citizens.size() < com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get()) {
                tryExpand(level);
                daysSinceExpansion = 0;
            }
        }

        for (Building b : buildings) {
            b.tick(level, this);
        }

        diplomacy.tick(this, level);
    }

    private int countFoodInWarehouse(ServerLevel level) {
        // Prefer chest counts if any chests found, else fallback to abstract map
        if (level != null && warehouseManager.getChestCount() > 0) {
            int sum = 0;
            for (var e : warehouseManager.snapshot().entrySet()) {
                if (isFood(e.getKey())) sum += e.getValue();
            }
            if (sum > 0) return sum;
        }
        return warehouse.entrySet().stream()
                .filter(e -> isFood(e.getKey()))
                .mapToInt(Map.Entry::getValue).sum();
    }
    private int countFoodInWarehouse() { return countFoodInWarehouse(null); }

    private boolean isFood(String itemId) {
        return itemId.contains("bread") || itemId.contains("wheat") || itemId.contains("carrot") || itemId.contains("potato")
                || itemId.contains("cooked_") || itemId.contains("apple") || itemId.contains("stew") || itemId.contains("bread");
    }

    private void consumeFood(ServerLevel level, int amount) {
        if (amount <= 0) return;
        // Try chests first
        if (level != null && warehouseManager.getChestCount() > 0) {
            int remaining = amount;
            String[] foodIds = {"minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:carrot", "minecraft:potato", "minecraft:wheat", "minecraft:apple"};
            for (String fid : foodIds) {
                if (remaining <= 0) break;
                int have = warehouseManager.count(fid);
                int take = Math.min(have, remaining);
                if (take > 0) {
                    warehouseManager.extract(level, center, fid, take, false);
                    remaining -= take;
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

    private void tryExpand(ServerLevel level) {
        if (countFoodInWarehouse(level) < citizens.size() * 2) return;
        boolean hasBuilder = citizens.values().stream().anyMatch(c -> c.job == JobType.BUILDER);
        if (!hasBuilder) return;

        BuildingType next = ColonyProgression.getNextBuilding(this);
        if (next == null) {
            if (canLevelUp()) {
                levelUp(level);
            }
            return;
        }

        BlockPos buildPos = findBuildSite(level, next);
        if (buildPos == null) return;

        Building blueprint = new Building(next, buildPos, culture);
        blueprint.setState(com.simplecolonies.building.BuildingState.BLUEPRINT);
        buildings.add(blueprint);
        broadcast(Component.literal("§aColony #" + id + " (" + culture.displayName + ") is expanding: new " + next.displayName + " at " + buildPos.toShortString()));
        ColonyManager.getInstance().markDirty(this);
    }

    private boolean canLevelUp() {
        int requiredBuildings = level * 3;
        return buildings.size() >= requiredBuildings && citizenCount() >= level * 8;
    }

    private void levelUp(ServerLevel level) {
        level++;
        radius = 32 + level * 16;
        broadcast(Component.literal("§6§lColony #" + id + " leveled up to " + ColonyLevel.fromLevel(level).displayName + "! Radius now " + radius + " blocks."));
    }

    private BlockPos findBuildSite(ServerLevel level, BuildingType type) {
        BlockPos center = this.center;
        for (int r = 16; r < radius; r += 8) {
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                BlockPos pos = center.offset((int)(Math.cos(rad)*r), 0, (int)(Math.sin(rad)*r));
                if (level.isLoaded(pos) && level.getBlockState(pos.below()).isSolid()) {
                    return pos;
                }
            }
        }
        return null;
    }

    public void addCitizen(CitizenData citizen) {
        citizens.put(citizen.uuid, citizen);
        ColonyManager.getInstance().markDirty(this);
    }

    public void removeCitizen(UUID villagerUuid) {
        citizens.remove(villagerUuid);
    }

    /** Deposit: try chests first, fallback to abstract map. Returns true if any deposited. */
    public void depositToWarehouse(ServerLevel level, String itemId, int count) {
        if (level != null && warehouseManager.getChestCount() > 0) {
            int deposited = warehouseManager.deposit(level, center, itemId, count);
            if (deposited >= count) return;
            count -= deposited;
        }
        if (count > 0) warehouse.merge(itemId, count, Integer::sum);
    }
    public void depositToWarehouse(String itemId, int count) {
        // No level context: use abstract map (e.g. during NBT load or job tick without level)
        // Try to resolve level lazily via ColonyManager server - omitted for simplicity, use abstract
        warehouse.merge(itemId, count, Integer::sum);
    }

    public int getWarehouseCount(ServerLevel level, String itemId) {
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
        tag.putString("state", state.name());
        if (leaderId != null) tag.putUUID("leader", leaderId);
        if (warehouseManager.getWarehouseCenter() != null) tag.putLong("warehouseCenter", warehouseManager.getWarehouseCenter().asLong());
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
        return c;
    }
}
