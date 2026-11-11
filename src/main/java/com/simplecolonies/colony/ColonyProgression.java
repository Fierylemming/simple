package com.simplecolonies.colony;

import com.simplecolonies.building.BuildingType;

import java.util.*;

/**
 * Defines natural expansion order. Each colony level unlocks new buildings.
 * This is intentionally simpler than MineColonies' research tree.
 */
public class ColonyProgression {

    private static final Map<Integer, List<BuildingType>> UNLOCKS = Map.of(
            1, List.of(BuildingType.HOUSE, BuildingType.FARM, BuildingType.LUMBERJACK_HUT),
            2, List.of(BuildingType.MINE, BuildingType.WAREHOUSE, BuildingType.BAKERY),
            3, List.of(BuildingType.BLACKSMITH, BuildingType.BARRACKS, BuildingType.TOWN_HALL),
            4, List.of(BuildingType.MARKET, BuildingType.LIBRARY, BuildingType.TEMPLE),
            5, List.of(BuildingType.CASTLE, BuildingType.WALLS, BuildingType.HARBOR)
    );

    public static BuildingType getNextBuilding(Colony colony) {
        Set<BuildingType> existing = new HashSet<>();
        for (var b : colony.getBuildings()) existing.add(b.getType());

        // Try current level unlocks first
        for (int lvl = 1; lvl <= colony.getLevel(); lvl++) {
            for (BuildingType t : UNLOCKS.getOrDefault(lvl, List.of())) {
                if (!existing.contains(t)) return t;
                // Also allow duplicate houses/farms
                if ((t == BuildingType.HOUSE || t == BuildingType.FARM) && count(colony, t) < colony.getLevel() * 2) {
                    return t;
                }
            }
        }
        return null;
    }

    private static long count(Colony c, BuildingType t) {
        return c.getBuildings().stream().filter(b -> b.getType() == t).count();
    }
}
