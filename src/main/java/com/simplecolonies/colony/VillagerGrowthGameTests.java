package com.simplecolonies.colony;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.JobType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class VillagerGrowthGameTests {
    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void birthsRequireLoadedNearbyAdultsAndRealFood(GameTestHelper helper) {
        for (int x = 44; x <= 68; x++) for (int z = 44; z <= 68; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var center = helper.absolutePos(new BlockPos(56, 1, 56));
        var colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Missing one", JobType.UNEMPLOYED));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Missing two", JobType.UNEMPLOYED));
        colony.depositToWarehouse("minecraft:bread", 20);
        if (colony.tryBreedWithNearbyAdults(helper.getLevel()) || colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") != 20)
            helper.fail("Unloaded citizen records created a child or spent food");
        for (var c : java.util.List.copyOf(colony.getCitizens())) colony.removeCitizen(c.uuid);
        var first = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(52, 1, 56));
        var second = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(65, 1, 56));
        first.setNoAi(true); second.setNoAi(true);
        first.setColony(colony); second.setColony(colony);
        if (colony.tryBreedWithNearbyAdults(helper.getLevel())) helper.fail("Far-apart villagers created a child");
        var together = helper.absolutePos(new BlockPos(54, 1, 56));
        second.moveTo(together.getX() + 0.5, together.getY(), together.getZ() + 0.5);
        if (!colony.tryBreedWithNearbyAdults(helper.getLevel())) { helper.fail("Nearby fed adults did not have a child"); return; }
        if (colony.citizenCount() != 3 || colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") != 17
                || first.getAge() <= 0 || second.getAge() <= 0
                || colony.getCitizens().stream().filter(c -> c.isChild).count() != 1)
            helper.fail("Birth cost, parent cooldown, or child record incorrect");
        if (colony.tryBreedWithNearbyAdults(helper.getLevel())) helper.fail("Parents ignored breeding cooldown");
        var restored = Colony.load(colony.save());
        if (restored.getCitizens().stream().filter(c -> c.isChild).count() != 1)
            helper.fail("Child record did not survive save/load");
        helper.succeed();
    }
}
