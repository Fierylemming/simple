package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.JobType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class WorksiteExpansionGameTests {
    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void smithyExpansionUsesAssignedStationAndPreservesLegacy(GameTestHelper helper) {
        testExpansion(helper, JobType.BLACKSMITH, BuildingType.BLACKSMITH, Blocks.SMITHING_TABLE);
    }
    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void butcherExpansionUsesAssignedStation(GameTestHelper helper) {
        testExpansion(helper, JobType.BUTCHER, BuildingType.BUTCHER_HUT, Blocks.SMOKER);
    }
    private static void testExpansion(GameTestHelper helper, JobType type, BuildingType buildingType, Block stationBlock) {
        for (int x = 20; x <= 75; x++) for (int z = 35; z <= 75; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(36, 1, 56));
        BlockPos site = new BlockPos(56, 1, 56), absSite = helper.absolutePos(site);
        helper.setBlock(site, stationBlock);
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, center.north(16), CultureType.ROMAN, true));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Builder", JobType.BUILDER));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), site.offset(0, 0, 2));
        worker.setNoAi(true);
        worker.setColony(colony);
        worker.setJob(type);
        colony.getCitizens().stream().filter(c -> c.uuid.equals(worker.getUUID())).findFirst().orElseThrow().job = type;
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), absSite));
        colony.depositToWarehouse("minecraft:oak_log", 300);
        colony.depositToWarehouse("minecraft:cobblestone", 300);
        colony.depositToWarehouse("minecraft:coal", 10);
        // Unsupported old placeholders must remain untouched and not prevent new work.
        var legacyTag = new Building(buildingType, center.south(14), CultureType.ROMAN).save();
        legacyTag.putInt("planVersion", 6);
        var legacy = Building.load(legacyTag);
        colony.registerPlayerBuilding(legacy);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            var plan = colony.getBuildings().stream().filter(b -> b.getType() == buildingType && b.hasSupportedPlan()).findFirst().orElse(null);
            if (plan == null || !plan.getPos().equals(absSite) || legacy.hasSupportedPlan() || !legacy.getRemainingPlacements().isEmpty())
                helper.fail("No supported plan at claimed station, or legacy placeholder changed: " + colony.getExpansionStatus());
            helper.succeed();
        });
    }
}
