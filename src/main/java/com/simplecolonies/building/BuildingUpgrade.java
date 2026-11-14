package com.simplecolonies.building;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles building level-ups: each building has 1 villager working initially,
 * upgrades add +1 job block / worker slot and rebuild bigger with better materials.
 *
 * You described: "each building has 1 villager working and so the buildings can be upgraded by the builder
 * to then put more job blocks inside and after each upgrade the buildings are bigger and contain more job blocks"
 *
 * Size & capacity:
 *  Lv1: 1 worker, small 5×5, +4 beds if House
 *  Lv2: 2 workers, 7×7, +8 beds
 *  Lv3: 3 workers, 9×9, +12
 *  Lv4: 4 workers, 11×11
 *  Lv5: 5 workers, 13×13 (max)
 */
public class BuildingUpgrade {

    public static int maxLevel(BuildingType type) { return 5; }

    public static int workerSlotsForLevel(int level) {
        return Math.min(level, 5); // 1 per level
    }

    public static int sizeForLevel(int level) {
        return 5 + (level - 1) * 2; // 5,7,9,11,13
    }

    public static Map<String, Integer> costForUpgrade(Building building) {
        int nextLevel = building.getLevel() + 1;
        if (nextLevel > 5) return Map.of();
        return costForLevel(building.getType(), building.getCulture(), nextLevel);
    }

    public static Map<String, Integer> costForUpgradePreview(Building building, int targetLevel) {
        return costForLevel(building.getType(), building.getCulture(), targetLevel);
    }

    public static Map<String, Integer> costForLevel(BuildingType type, CultureType culture, int level) {
        if (level < 2 || level > 5) return Map.of();
        Map<String, Integer> base = type.baseCost;
        Map<String, Integer> cost = new HashMap<>();
        double mult = 1.0 + (level - 1) * 0.6;
        for (var e : base.entrySet()) {
            cost.put(e.getKey(), (int)Math.ceil(e.getValue() * mult));
        }
        var tier = com.simplecolonies.building.MaterialProgression.get(culture, Math.min(level, 5));
        Block primary = tier.primary().get(0);
        // Use item id for counting
        String primaryItemId = "minecraft:" + primary.asItem().toString().replace("minecraft:", "");
        try {
            primaryItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.asItem()).toString();
        } catch (Exception ignored) {}
        int blocksNeeded = sizeForLevel(level) * 4 * 3;
        cost.merge(primaryItemId, blocksNeeded / 4, Integer::sum);
        if (cost.isEmpty()) cost.put("minecraft:cobblestone", 16 * level);
        return cost;
    }

    public static boolean canUpgrade(Colony colony, Building building, net.minecraft.server.level.ServerLevel level) {
        if (building.canBuildWorksiteWing()) return findWorksiteWing(colony, building, level) != null;
        if (!building.isPlayerPlaced || building.getType() != BuildingType.HOUSE) return false;
        // Other marker upgrades currently change no functioning capacity. Never
        // charge for an upgrade that neither builds nor does useful work.
        if (building.getLevel() >= maxLevel(building.getType())) return false;
        if (building.getState() != BuildingState.COMPLETE) return false; // only upgrade finished buildings
        Map<String, Integer> cost = costForUpgrade(building);
        for (var e : cost.entrySet()) {
            if (colony.getWarehouseCount(level, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public static boolean tryUpgrade(Colony colony, Building building, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player) {
        if (!canUpgrade(colony, building, level)) {
            if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cUpgrade unavailable or insufficient materials for " + building.getType().displayName + " to Lv" + (building.getLevel()+1)), true);
            return false;
        }
        if (building.canBuildWorksiteWing()) {
            var site = findWorksiteWing(colony, building, level);
            if (site == null || !building.beginWorksiteWing(site)) return false;
            com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
            if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§aQueued a " + building.getType().displayName + " wing at " + site.toShortString() + "; builder consumes materials as placed."), false);
            return true;
        }
        // The marker upgrade changes capacity only: it does not build a larger
        // shell or create beds/workstations. Consume live stock transactionally.
        Map<String, Integer> paid = new HashMap<>();
        for (var e : costForUpgrade(building).entrySet()) {
            if (!colony.consumeStock(level, e.getKey(), e.getValue())) {
                paid.forEach(colony::depositToWarehouse);
                if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cUpgrade materials changed; no upgrade was applied."), true);
                return false;
            }
            paid.put(e.getKey(), e.getValue());
        }
        building.upgradeToNextLevel();
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aUpgraded marker capacity for " + building.getType().displayName + " → Lv" + building.getLevel()), false);
        colony.broadcast(net.minecraft.network.chat.Component.literal("§6Updated marker capacity for " + building.getType().displayName + " at " + building.getPos().toShortString() + " to level " + building.getLevel()));
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        return true;
    }

    /** Each tier adds an independent workshop with a real additional POI.
     * Keep wings close enough to associate their workstations with the parent. */
    private static net.minecraft.core.BlockPos findWorksiteWing(Colony colony, Building building,
            net.minecraft.server.level.ServerLevel level) {
        if (level == null || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || !colony.getBuildings().contains(building) || !building.canBuildWorksiteWing()
                || !level.isLoaded(building.getPos())
                || (building.getType() != BuildingType.HOUSE && !level.getBlockState(building.getPos()).is(Building.wingStation(building.getType()).getBlock()))
                || colony.getCitizens().stream().noneMatch(c -> !c.isChild && c.job == com.simplecolonies.villager.job.JobType.BUILDER)
                || colony.getBuildings().stream().anyMatch(b -> b != building && b.hasSupportedPlan() && b.getState() != BuildingState.COMPLETE)) return null;
        var stock = colony.getWarehouseSnapshot(level);
        boolean house = building.getType() == BuildingType.HOUSE;
        int gap = house ? 7 : 5;
        int[][] offsets = {{gap,0},{-gap,0},{0,-gap},{0,gap},{gap*2,0},{-gap*2,0},{0,-gap*2},{0,gap*2}, {gap,-gap},{-gap,-gap},{gap,gap},{-gap,gap}};
        for (var offset : offsets) {
            var site = building.getPos().offset(offset[0], 0, offset[1]);
            if (colony.getBuildings().stream().anyMatch(b -> b != building
                    && b.footprintSites().stream().anyMatch(p -> p.distSqr(site) < 144))) continue;
            // Do not wall off an existing north-facing entrance, or put the new
            // entrance inside an existing footprint. Touching edges are fine.
            int radius = house ? 3 : 2;
            var entrance = site.north(radius + 1);
            if (building.footprintSites().stream().anyMatch(p ->
                    overlaps(site, radius, p, radius)
                    || inside(p.north(radius + 1), site, radius)
                    || inside(entrance, p, radius))) continue;
            if (!clearWing(level, colony, site, house)) continue;
            if (ConstructionCrafting.canBuild(stock, building.previewWorksiteWing(site))) return site;
        }
        return null;
    }

    private static boolean inside(net.minecraft.core.BlockPos point, net.minecraft.core.BlockPos center, int radius) {
        return Math.abs(point.getX() - center.getX()) <= radius && Math.abs(point.getZ() - center.getZ()) <= radius;
    }
    private static boolean overlaps(net.minecraft.core.BlockPos a, int ar, net.minecraft.core.BlockPos b, int br) {
        return Math.abs(a.getX() - b.getX()) <= ar + br && Math.abs(a.getZ() - b.getZ()) <= ar + br;
    }

    private static boolean clearWing(net.minecraft.server.level.ServerLevel level, Colony colony, net.minecraft.core.BlockPos site, boolean house) {
        int radius = house ? 3 : 2;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            var ground = site.offset(x, -1, z);
            if (!colony.isInside(ground) || !level.isLoaded(ground)) return false;
            var soil = level.getBlockState(ground);
            if (!soil.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)
                    || !(soil.is(net.minecraft.tags.BlockTags.DIRT) || soil.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                        || soil.is(net.minecraft.world.level.block.Blocks.SAND) || soil.is(net.minecraft.world.level.block.Blocks.RED_SAND)
                        || soil.is(net.minecraft.world.level.block.Blocks.GRAVEL))) return false;
            for (int y = 0; y <= (house ? 5 : 3); y++) {
                var p = site.offset(x, y, z);
                if (!colony.isInside(p) || !level.isLoaded(p) || !level.getBlockState(p).isAir()
                        || level.getBlockEntity(p) != null || !level.getFluidState(p).isEmpty()) return false;
            }
        }
        var entrance = site.north(house ? 4 : 3);
        return colony.isInside(entrance) && level.isLoaded(entrance) && level.isLoaded(entrance.above())
                && level.isLoaded(entrance.below()) && level.getBlockState(entrance).isAir()
                && level.getBlockState(entrance.above()).isAir()
                && level.getBlockState(entrance.below()).isFaceSturdy(level, entrance.below(), net.minecraft.core.Direction.UP);
    }
}
