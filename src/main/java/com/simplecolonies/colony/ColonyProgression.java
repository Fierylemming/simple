package com.simplecolonies.colony;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.villager.job.JobType;

import java.util.*;

/**
 * Defines natural expansion order — now research-aware for self-sufficiency.
 * Buildings whose job is locked behind research won't be queued until that profession is unlocked,
 * and their mats are already gatherable in previous tier (see BuildingType costs rework).
 *
 * Tier mapping:
 *  1: HOUSE, FARM, LUMBERJACK_HUT (free, wood tier)
 *  2: MINE (needs stone tools), WAREHOUSE, BAKERY, BUTCHER_HUT, WALLS, HARBOR
 *  3: BLACKSMITH (after miner), BARRACKS (after iron), LEATHER_SHOP (after butcher), etc.
 *  4-5: late game MARKET/LIBRARY/TEMPLE/CASTLE (need iron/diamond)
 *
 * This ensures a village can walk the whole chain farmer→miner→builder→iron without player.
 */
public class ColonyProgression {

    private static final Map<Integer, List<BuildingType>> UNLOCKS = Map.of(
            1, List.of(BuildingType.HOUSE, BuildingType.FARM, BuildingType.LUMBERJACK_HUT),
            2, List.of(BuildingType.MINE, BuildingType.WAREHOUSE, BuildingType.BAKERY, BuildingType.BUTCHER_HUT, BuildingType.WALLS, BuildingType.HARBOR),
            3, List.of(BuildingType.BLACKSMITH, BuildingType.LEATHER_SHOP, BuildingType.BARRACKS),
            4, List.of(BuildingType.MARKET, BuildingType.LIBRARY, BuildingType.TEMPLE),
            5, List.of(BuildingType.CASTLE, BuildingType.TOWN_HALL) // town hall upgrade placeholder
    );

    public static BuildingType getNextBuilding(Colony colony) {
        Set<BuildingType> existing = new HashSet<>();
        for (var b : colony.getBuildings()) existing.add(b.getType());

        for (int lvl = 1; lvl <= colony.getLevel(); lvl++) {
            for (BuildingType t : UNLOCKS.getOrDefault(lvl, List.of())) {
                // Skip if job locked behind research — village must research first (or self-unlock when it has mats)
                if (t.job != null && !colony.getResearch().isProfessionUnlocked(t.job)) {
                    // But if village can self-unlock that profession cheaply, it will before building
                    // So we just skip this building until research done
                    continue;
                }
                if (!existing.contains(t)) return t;
                // Allow duplicate houses/farms for growth
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
