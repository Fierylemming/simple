package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.ButcherJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ButcherGameTests {
    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void gathersOnlyOwnActualDropsAndLeavesBreedingPair(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        var worker = helper.spawn(EntityType.VILLAGER, new BlockPos(54, 1, 56));
        for (int x = 57; x <= 59; x++) {
            var cow = helper.spawn(EntityType.COW, new BlockPos(x, 1, 56));
            cow.setNoAi(true);
        }
        var playerDrop = new ItemEntity(helper.getLevel(), center.getX() + 1.5, center.getY(), center.getZ() + 0.5, new ItemStack(Items.BEEF, 7));
        helper.getLevel().addFreshEntity(playerDrop);
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(1000, () -> {
            int cows = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(center).inflate(8, 4, 8), Cow::isAlive).size();
            int beef = colony.getWarehouseCount(helper.getLevel(), "minecraft:beef");
            if (cows != 2 || beef < 1 || beef > 3) helper.fail("Livestock or real loot incorrect: cows=" + cows + " beef=" + beef + " state=" + job.getState());
            if (!playerDrop.isAlive() || !playerDrop.getItem().is(Items.BEEF) || playerDrop.getItem().getCount() != 7)
                helper.fail("Butcher collected an unrelated existing item");
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 650)
    public static void protectsBreedingPairNamedAndYoungAnimals(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        var worker = helper.spawn(EntityType.VILLAGER, new BlockPos(54, 1, 56));
        var first = helper.spawn(EntityType.COW, new BlockPos(57, 1, 56));
        var second = helper.spawn(EntityType.COW, new BlockPos(58, 1, 56));
        var named = helper.spawn(EntityType.COW, new BlockPos(59, 1, 56));
        named.setCustomName(net.minecraft.network.chat.Component.literal("Pet"));
        var calf = helper.spawn(EntityType.COW, new BlockPos(60, 1, 56));
        calf.setBaby(true);
        first.setNoAi(true); second.setNoAi(true); named.setNoAi(true); calf.setNoAi(true);
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 650; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(600, () -> {
            if (!first.isAlive() || !second.isAlive() || !named.isAlive() || !calf.isAlive()
                    || first.getHealth() != first.getMaxHealth() || second.getHealth() != second.getMaxHealth()
                    || named.getHealth() != named.getMaxHealth() || calf.getHealth() != calf.getMaxHealth()
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:beef") != 0)
                helper.fail("Butcher attacked protected livestock or created meat");
            helper.succeed();
        });
    }

}
