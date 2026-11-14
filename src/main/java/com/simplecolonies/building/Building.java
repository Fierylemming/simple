package com.simplecolonies.building;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Building instance — now supports leveling: each building has 1 villager initially, upgrades add job blocks and size,
 * and material palette evolves with culture tier (Greek wood→stone→terracotta, etc).
 */
public class Building {
    private final BuildingType type;
    private final BlockPos pos;
    private final CultureType culture;
    private BuildingState state = BuildingState.BLUEPRINT;
    private int level = 1;
    // Version 1 is the old all-log plan, retained for buildings saved before 0.1.28.
    private int planVersion = 13;
    private HouseTimber houseTimber = HouseTimber.OAK;
    private int houseVariant;
    // The current/most recently completed wing, plus prior completed wings.
    // Version 1 is the original smithy plan; version 2 supports other worksites.
    private BlockPos upgradeSite;
    private int upgradePlanVersion;
    private final List<BlockPos> previousWings = new ArrayList<>();
    private final Map<String, Integer> requiredResources = new HashMap<>();
    private final Queue<BlockPlacement> placements = new ArrayDeque<>();
    private final List<BlockPlacement> completed = new ArrayList<>();
    public final boolean isPlayerPlaced;

    public Building(BuildingType type, BlockPos pos, CultureType culture) {
        this(type, pos, culture, false);
    }

    public Building(BuildingType type, BlockPos pos, CultureType culture, HouseTimber timber) {
        this(type, pos, culture, false, timber);
    }

    public Building(BuildingType type, BlockPos pos, CultureType culture, boolean isPlayerPlaced) {
        this(type, pos, culture, isPlayerPlaced, HouseTimber.OAK);
    }

    private Building(BuildingType type, BlockPos pos, CultureType culture, boolean isPlayerPlaced, HouseTimber timber) {
        this.houseTimber = timber == null ? HouseTimber.OAK : timber;
        this.type = type;
        this.pos = pos;
        this.culture = culture;
        this.houseVariant = LevelOneHouses.variant(pos);
        this.isPlayerPlaced = isPlayerPlaced;
        if (!isPlayerPlaced) {
            loadSchematicPlacements();
            this.state = BuildingState.BLUEPRINT;
        } else {
            this.requiredResources.clear();
            this.state = BuildingState.COMPLETE;
        }
    }

    public BuildingType getType() { return type; }
    public BlockPos getPos() { return pos; }
    public BuildingState getState() { return state; }
    public void setState(BuildingState state) { this.state = state; }
    public int getLevel() { return level; }
    public boolean isUpgrading() { return upgradeSite != null && state != BuildingState.COMPLETE; }
    public BlockPos getUpgradeSite() { return upgradeSite; }
    public boolean canBuildSmithyWing() {
        return !isPlayerPlaced && type == BuildingType.BLACKSMITH && planVersion >= 7
                && level == 1 && state == BuildingState.COMPLETE && upgradeSite == null;
    }
    /** Generated worksite upgrades add a separate real workstation wing. */
    public boolean canBuildWorksiteWing() {
        return !isPlayerPlaced && state == BuildingState.COMPLETE && level < 5
                && (type == BuildingType.HOUSE && planVersion >= 5
                    || LevelOneWorksites.supports(type, planVersion) && wingStation(type) != null);
    }
    public List<BlockPos> completedWingSites() {
        var sites = new ArrayList<>(previousWings);
        if (!isUpgrading() && upgradeSite != null && !sites.contains(upgradeSite)) sites.add(upgradeSite);
        return List.copyOf(sites);
    }
    public boolean isOperational() { return state == BuildingState.COMPLETE || isUpgrading(); }
    /** All physical footprints, including an unfinished wing, for spatial safety checks. */
    public List<BlockPos> footprintSites() {
        var sites = new ArrayList<BlockPos>();
        sites.add(pos);
        sites.addAll(previousWings);
        if (upgradeSite != null && !sites.contains(upgradeSite)) sites.add(upgradeSite);
        return List.copyOf(sites);
    }
    /** Distance to the nearest completed workplace, not just the original building. */
    public double distanceToOperationalSite(BlockPos target) {
        double nearest = pos.distSqr(target);
        for (var wing : completedWingSites()) nearest = Math.min(nearest, wing.distSqr(target));
        return nearest;
    }
    public List<BlockPos> shelterSites() {
        if (type != BuildingType.HOUSE) return List.of(pos);
        var sites = new ArrayList<BlockPos>(); sites.add(pos); sites.addAll(completedWingSites());
        return List.copyOf(sites);
    }
    public static BlockState wingStation(BuildingType type) {
        return switch (type) {
            case BUILDER_HUT -> net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState();
            case LUMBERJACK_HUT -> net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false);
            case MINE -> net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState();
            case BLACKSMITH -> net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState();
            case BUTCHER_HUT, BAKERY -> net.minecraft.world.level.block.Blocks.SMOKER.defaultBlockState();
            case LEATHER_SHOP -> net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState();
            case HARBOR, WAREHOUSE -> net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState();
            case BARRACKS -> net.minecraft.world.level.block.Blocks.GRINDSTONE.defaultBlockState();
            default -> null;
        };
    }
    public int getHouseVariant() { return houseVariant; }
    public static int houseVariantAt(BlockPos pos) { return LevelOneHouses.variant(pos); }
    /** Footprint for newly queued houses; legacy saved plans remain unchanged. */
    public static boolean newHouseFootprint(BlockPos origin, int x, int z) {
        return CulturalHousePlans.footprint(houseVariantAt(origin), x, z);
    }
    public void setLevel(int lvl) { this.level = Math.clamp(lvl, 1, 5); }
    public int getWorkerSlots() { return BuildingUpgrade.workerSlotsForLevel(level); }
    public int getSize() { return BuildingUpgrade.sizeForLevel(level); }
    public int getHousingCapacity() { return type.housingCapacity * level; }
    public CultureType getCulture() { return culture; }
    public Map<String,Integer> getRequiredResources() { return Collections.unmodifiableMap(requiredResources); }

    private void loadSchematicPlacements() {
        // A deterministic plan makes partially completed construction resumable after reload.
        // Preserve the original plans of all in-progress saves. Worksite plans were introduced in v4.
        placements.clear();
        requiredResources.clear();
        if (upgradeSite != null) {
            if (upgradePlanVersion != 1 && upgradePlanVersion != 2 && upgradePlanVersion != 3) return;
            var plan = new ArrayList<BlockPlacement>();
            if (upgradePlanVersion == 3 && type == BuildingType.HOUSE) {
                CulturalHousePlans.generate(upgradeSite, culture, houseVariant, (at, block) ->
                        plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(block) : block)));
                addPlan(plan, true);
                return;
            }
            BuildingType wingType = upgradePlanVersion == 1 ? BuildingType.BLACKSMITH : type;
            BlockState station = wingStation(wingType);
            if (upgradePlanVersion == 3 || !LevelOneWorksites.supports(wingType) || station == null) return;
            LevelOneWorksites.generate(wingType, upgradeSite, culture, (at, block) ->
                    plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(block) : block)));
            plan.add(placement(upgradeSite, station));
            addPlan(plan, false);
            return;
        }
        if (type != BuildingType.HOUSE) {
            if (planVersion >= 4 && LevelOneWorksites.supports(type, planVersion)) {
                var plan = new ArrayList<BlockPlacement>();
                LevelOneWorksites.generate(type, pos, culture, (at, state) ->
                        plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(state) : state)));
                addPlan(plan, false);
            }
            return;
        }
        if (planVersion >= 5) {
            var plan = new ArrayList<BlockPlacement>();
            CulturalHousePlans.generate(pos, culture, houseVariant, (at, state) ->
                    plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(state) : state)));
            addPlan(plan, true);
            return;
        }
        if (planVersion >= 3) {
            LevelOneHouses.generate(pos, culture, houseVariant, (at, state) -> {
                String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
                addPlacement(at, state, item);
            });
            return;
        }
        BlockState timber = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        for (int y = 0; y < 3; y++) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                if (x == 0 && z == -2 && y < 2) continue; // doorway
                boolean stoneFoundation = planVersion >= 2 && y == 0;
                addPlacement(pos.offset(x, y, z), stoneFoundation ? net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState() : timber,
                        stoneFoundation ? "minecraft:cobblestone" : "minecraft:oak_log");
            }
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            addPlacement(pos.offset(x, 3, z), timber, "minecraft:oak_log");
        }
    }

    private BlockPlacement placement(BlockPos at, BlockState state) {
        String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
        return new BlockPlacement(at, state, item);
    }

    private void addPlan(List<BlockPlacement> plan, boolean house) {
        if (planVersion >= 6) {
            // Floors first, then roofs while the interior is still accessible. Walls and
            // doors go last; this avoids sealing the builder outside the center roof.
            plan.sort(Comparator.comparingInt(p -> {
                int y = p.pos.getY() - pos.getY();
                if (house && y == 0) return 0;
                if (y >= (house ? 4 : 3)) return 1;
                if (p.state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return 3;
                return 2;
            }));
        }
        for (BlockPlacement p : plan) addPlacement(p.pos, p.state, p.itemId);
    }

    /** Standing height inside a modern house; old shells and workshops use ground level. */
    public int constructionWalkY() { return pos.getY() + (type == BuildingType.HOUSE && planVersion >= 3 ? 1 : 0); }

    private void addPlacement(BlockPos at, BlockState block, String item) {
        placements.add(new BlockPlacement(at, block, item));
        requiredResources.merge(item, 1, Integer::sum);
    }

    public boolean hasSupportedPlan() {
        return !isPlayerPlaced && (upgradeSite == null || upgradePlanVersion == 1 || upgradePlanVersion == 2 || upgradePlanVersion == 3)
                && (type == BuildingType.HOUSE || (planVersion >= 4 && LevelOneWorksites.supports(type, planVersion)));
    }

    /** Preview is deterministic and does not change the building or stock. */
    public List<BlockPlacement> previewSmithyWing(BlockPos site) {
        if (!canBuildSmithyWing()) return List.of();
        var plan = new ArrayList<BlockPlacement>();
        LevelOneWorksites.generate(BuildingType.BLACKSMITH, site, culture, (at, block) ->
                plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(block) : block)));
        plan.add(placement(site, net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState()));
        // Use the same deterministic ordering as the builder and reload.
        plan.sort(Comparator.comparingInt(p -> {
            int y = p.pos.getY() - pos.getY();
            if (y >= 3) return 1;
            if (p.state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return 3;
            return 2;
        }));
        return List.copyOf(plan);
    }

    /** Preview a wing without consuming resources or changing the current plan. */
    public List<BlockPlacement> previewWorksiteWing(BlockPos site) {
        if (!canBuildWorksiteWing() || site == null) return List.of();
        var plan = new ArrayList<BlockPlacement>();
        if (type == BuildingType.HOUSE) {
            CulturalHousePlans.generate(site, culture, houseVariant, (at, block) ->
                    plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(block) : block)));
        } else {
            LevelOneWorksites.generate(type, site, culture, (at, block) ->
                    plan.add(placement(at, planVersion >= 12 ? houseTimber.replaceOak(block) : block)));
            plan.add(placement(site, wingStation(type)));
        }
        plan.sort(Comparator.comparingInt(p -> {
            int y = p.pos.getY() - pos.getY();
            if (type == BuildingType.HOUSE && y == 0) return 0;
            if (y >= (type == BuildingType.HOUSE ? 4 : 3)) return 1;
            if (p.state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return 3;
            return 2;
        }));
        return List.copyOf(plan);
    }

    public boolean beginWorksiteWing(BlockPos site) {
        if (!canBuildWorksiteWing() || site == null) return false;
        if (upgradeSite != null && !previousWings.contains(upgradeSite)) previousWings.add(upgradeSite);
        upgradeSite = site.immutable(); upgradePlanVersion = type == BuildingType.HOUSE ? 3 : 2;
        completed.clear(); loadSchematicPlacements(); state = BuildingState.UNDER_CONSTRUCTION;
        return true;
    }

    public boolean beginSmithyWing(BlockPos site) {
        if (!canBuildSmithyWing() || site == null) return false;
        upgradeSite = site.immutable(); upgradePlanVersion = 1;
        completed.clear(); loadSchematicPlacements();
        state = BuildingState.UNDER_CONSTRUCTION;
        return true;
    }

    /** Upgrade cancellation preserves the completed parent and all placed blocks. */
    public boolean cancelUpgrade() {
        if (!isUpgrading()) return false;
        upgradeSite = previousWings.isEmpty() ? null : previousWings.remove(previousWings.size() - 1);
        upgradePlanVersion = upgradeSite == null ? 0 : (type == BuildingType.HOUSE ? 3 : 2);
        placements.clear(); completed.clear(); requiredResources.clear();
        state = BuildingState.COMPLETE;
        return true;
    }

    /** Only award the tier after the builder has installed every real block. */
    public void finishConstruction() {
        if (!placements.isEmpty()) return;
        if (isUpgrading()) level = Math.min(5, level + 1);
        state = BuildingState.COMPLETE;
    }

    public void upgradeToNextLevel() {
        if (level >= 5) return;
        level++;
        // Rebuild placement queue for the new level's expanded shell
        // For player marker buildings we keep COMPLETE but still grow housing; for blueprints we set UNDER_CONSTRUCTION
        if (!isPlayerPlaced) {
            requiredResources.clear();
            requiredResources.putAll(BuildingUpgrade.costForUpgradePreview(this, level)); // preview of what was consumed; real cost already paid
            placements.clear();
            loadSchematicPlacements();
            state = BuildingState.UNDER_CONSTRUCTION;
        } else {
            // Player building instantly grows: just update level, no placements needed
            state = BuildingState.COMPLETE;
        }
    }

    public boolean hasResources(Colony colony) {
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            int have = colony.getWarehouseCount(e.getKey());
            try {
                var wm = colony.getWarehouseManager();
                if (wm.getChestCount() > 0) have = wm.count(e.getKey()) + have;
            } catch (Exception ignored) {}
            if (have < e.getValue()) return false;
        }
        return true;
    }

    public Map<String, Integer> getMissingResources(Colony colony) {
        Map<String, Integer> missing = new HashMap<>();
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            int have = colony.getWarehouseCount(e.getKey());
            try {
                var wm = colony.getWarehouseManager();
                if (wm.getChestCount() > 0) have = Math.max(have, wm.count(e.getKey()));
            } catch (Exception ignored) {}
            if (have < e.getValue()) missing.put(e.getKey(), e.getValue() - have);
        }
        return missing;
    }

    public BlockPlacement getNextPlacement() { return placements.peek(); }
    public List<BlockPlacement> getRemainingPlacements() { return List.copyOf(placements); }
    public void markPlaced(BlockPlacement p) {
        placements.poll(); completed.add(p);
        requiredResources.computeIfPresent(p.itemId, (key, value) -> value > 1 ? value - 1 : null);
    }
    public void tick(ServerLevel level, Colony colony) {}

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putLong("pos", pos.asLong());
        tag.putString("culture", culture.id);
        tag.putString("state", state.name());
        tag.putInt("level", level);
        tag.putInt("planVersion", planVersion);
        tag.putInt("houseVariant", houseVariant);
        if (planVersion >= 12) tag.putString("houseTimber", houseTimber.id);
        tag.putBoolean("isPlayerPlaced", isPlayerPlaced);
        if (!previousWings.isEmpty()) tag.putLongArray("previousWings", previousWings.stream().mapToLong(BlockPos::asLong).toArray());
        if (upgradeSite != null) {
            tag.putLong("upgradeSite", upgradeSite.asLong());
            tag.putInt("upgradePlanVersion", upgradePlanVersion);
        }
        CompoundTag req = new CompoundTag();
        requiredResources.forEach((k,v) -> req.putInt(k, v));
        tag.put("required", req);
        tag.putInt("remainingPlacements", placements.size());
        tag.putInt("completedPlacements", completed.size());
        return tag;
    }

    public static Building load(CompoundTag tag) {
        BuildingType type = BuildingType.valueOf(tag.getString("type"));
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        CultureType culture = CultureType.byId(tag.getString("culture"));
        boolean isPlayer = tag.getBoolean("isPlayerPlaced");
        Building b = new Building(type, pos, culture, isPlayer);
        b.planVersion = tag.contains("planVersion") ? tag.getInt("planVersion") : 1;
        b.houseTimber = b.planVersion >= 12 ? HouseTimber.byId(tag.getString("houseTimber")) : HouseTimber.OAK;
        b.houseVariant = tag.contains("houseVariant") ? Math.floorMod(tag.getInt("houseVariant"), LevelOneHouses.VARIANTS) : LevelOneHouses.variant(pos);
        b.state = BuildingState.valueOf(tag.getString("state"));
        b.level = tag.getInt("level");
        if (!isPlayer) for (long p : tag.getLongArray("previousWings")) b.previousWings.add(BlockPos.of(p));
        if (!isPlayer && tag.contains("upgradeSite")) {
            b.upgradeSite = BlockPos.of(tag.getLong("upgradeSite"));
            b.upgradePlanVersion = tag.getInt("upgradePlanVersion");
        }
        // Regenerate the deterministic plan and discard exactly the completed prefix.
        if (!isPlayer && b.state != BuildingState.COMPLETE) {
            b.loadSchematicPlacements();
            int done = Math.max(0, tag.getInt("completedPlacements"));
            while (done-- > 0 && b.getNextPlacement() != null) b.markPlaced(b.getNextPlacement());
        } else if (b.state == BuildingState.COMPLETE) {
            b.placements.clear(); b.completed.clear(); b.requiredResources.clear();
        }
        return b;
    }

    public static class BlockPlacement {
        public final BlockPos pos;
        public final BlockState state;
        public final String itemId;
        public BlockPlacement(BlockPos pos, BlockState state, String itemId) { this.pos = pos; this.state = state; this.itemId = itemId; }
        static BlockPlacement dummy(BlockPos pos, String itemId) {
            return new BlockPlacement(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), itemId);
        }
    }
}
