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
    public static class RejectBirth {
        private final int colonyId;
        boolean rejected;
        RejectBirth(int colonyId) { this.colonyId = colonyId; }
        @net.neoforged.bus.api.SubscribeEvent
        public void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof com.simplecolonies.entity.ColonyVillager villager
                    && villager.getColonyId() == colonyId && villager.isBaby()) {
                rejected = true; event.setCanceled(true);
            }
        }
    }

    @GameTest(template = "empty")
    public static void pendingHealthDaysApplyEachDayInOrder(GameTestHelper helper) {
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        worker.setNoAi(true);
        var citizen = new CitizenData(worker.getUUID(), "Test", JobType.UNEMPLOYED);
        for (int i = 0; i < 8; i++) citizen.queueDailyHealth(true);
        for (int i = 0; i < 3; i++) citizen.queueDailyHealth(false);
        worker.applyPendingDailyHealth(citizen);
        if (Math.abs(worker.getHealth() - 13.0f) > .01f)
            helper.fail("Starvation and healing days were not applied separately and in order: health=" + worker.getHealth());
        helper.succeed();
    }

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void cancelledBirthKeepsRealFoodInItsContainer(GameTestHelper helper) {
        for (int x = 44; x <= 68; x++) for (int z = 44; z <= 68; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var center = helper.absolutePos(new BlockPos(56, 1, 56));
        var colony = new Colony(49998, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        helper.setBlock(new BlockPos(60, 1, 60), Blocks.CHEST);
        var container = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(60, 1, 60));
        container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 20));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(new BlockPos(60, 1, 60))))
            helper.fail("Could not share birth food");
        var first = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(52, 1, 56));
        var second = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        first.setNoAi(true); second.setNoAi(true); first.setColony(colony); second.setColony(colony);
        var reject = new RejectBirth(colony.getId());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(reject);
        boolean born;
        try { born = colony.tryBreedWithNearbyAdults(helper.getLevel()); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(reject); }
        if (!reject.rejected || born || colony.citizenCount() != 2 || container.getItem(0).getCount() != 20
                || colony.getWarehouseCount("minecraft:bread") != 0 || first.getAge() != 0 || second.getAge() != 0)
            helper.fail("Cancelled birth spent or virtualized food, or changed parents");
        helper.succeed();
    }

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
