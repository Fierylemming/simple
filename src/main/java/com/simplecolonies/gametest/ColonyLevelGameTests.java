package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.JobType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ColonyLevelGameTests {
    @GameTest(template = "isolated")
    public static void colonyLevelRequiresCompletedBuildingsAndPopulationAndPersists(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        colony.registerPlayerBuilding(new Building(BuildingType.FARM, center.east(15), CultureType.ROMAN, true));
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, center.west(15), CultureType.ROMAN));
        for (int i = 0; i < 8; i++) colony.addCitizen(new CitizenData(UUID.randomUUID(), "Citizen", JobType.UNEMPLOYED));
        if (colony.tryLevelUp() || colony.getLevel() != 1) helper.fail("Unfinished blueprint counted toward level");
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, center.north(15), CultureType.ROMAN, true));
        if (!colony.tryLevelUp() || colony.getLevel() != 2 || colony.getRadius() != 64)
            helper.fail("Eligible colony did not advance: " + colony.getLevelUpBlocker());
        Colony restored = Colony.load(colony.save());
        if (restored.getLevel() != 2 || restored.getRadius() != 64 || restored.tryLevelUp())
            helper.fail("Level was not preserved, or advanced without enough population");
        helper.succeed();
    }
}
