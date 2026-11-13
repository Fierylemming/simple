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
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        BlockPos chest = new BlockPos(65, 1, 56);
        helper.setBlock(chest, Blocks.CHEST);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        for (int x = 57; x <= 59; x++) {
            var cow = helper.spawn(EntityType.COW, new BlockPos(x, 1, 56));
            cow.setNoAi(true);
        }
        var playerDrop = new ItemEntity(helper.getLevel(), center.getX() + 1.5, center.getY(), center.getZ() + 0.5, new ItemStack(Items.BEEF, 7));
        helper.getLevel().addFreshEntity(playerDrop);
        var job = new ButcherJob(worker, colony);
        var storage = (net.minecraft.world.Container) helper.getBlockEntity(chest);
        BlockPos absChest = helper.absolutePos(chest);
        boolean[] carried = {false}, delivered = {false};
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!carried[0] && (!worker.getCourierCargo().isEmpty() || !worker.getCourierCargoExtra().isEmpty())) {
                if (worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) <= 9
                        || colony.getWarehouseCount("minecraft:beef") != 0) helper.fail("Meat was not carried from the herd");
                var tag = new net.minecraft.nbt.CompoundTag(); worker.addAdditionalSaveData(tag);
                var saved = net.minecraft.world.item.ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("courierCargo"));
                if (!ItemStack.isSameItemSameComponents(saved, worker.getCourierCargo()) || saved.getCount() != worker.getCourierCargo().getCount())
                    helper.fail("Meat cargo did not persist");
                carried[0] = true;
            }
            if (!delivered[0] && storage.countItem(Items.BEEF) > 0) {
                if (!carried[0] || worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9)
                    helper.fail("Meat was deposited without visiting the chest");
                delivered[0] = true;
            }
        });
        helper.runAtTickTime(1000, () -> {
            int cows = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(center).inflate(8, 4, 8), Cow::isAlive).size();
            int beef = colony.getWarehouseCount(helper.getLevel(), "minecraft:beef");
            if (!carried[0] || !delivered[0] || cows != 2 || beef < 1 || beef > 3) helper.fail("Livestock or real loot incorrect: cows=" + cows + " beef=" + beef + " state=" + job.getState());
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
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
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

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void butcherCannotAttackThroughSolidWall(GameTestHelper helper) {
        for (int x = 48; x <= 64; x++) for (int z = 48; z <= 64; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int z = 50; z <= 62; z++) for (int y = 1; y <= 3; y++) helper.setBlock(57, y, z, Blocks.COBBLESTONE);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(56, 1, 56));
        worker.setNoAi(true);
        var cows = new java.util.ArrayList<Cow>();
        for (int z = 55; z <= 57; z++) {
            var cow = helper.spawn(EntityType.COW, new BlockPos(58, 1, z));
            cow.setNoAi(true); cows.add(cow);
        }
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(250, () -> {
            for (var cow : cows) if (!cow.isAlive() || cow.getHealth() != cow.getMaxHealth())
                helper.fail("Butcher attacked livestock through a wall");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void butcherCannotFeedThroughSolidWall(GameTestHelper helper) {
        for (int x = 48; x <= 64; x++) for (int z = 48; z <= 64; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int z = 50; z <= 62; z++) for (int y = 1; y <= 3; y++) helper.setBlock(57, y, z, Blocks.COBBLESTONE);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        colony.depositToWarehouse("minecraft:wheat", 4);
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(56, 1, 56));
        worker.setNoAi(true);
        var first = helper.spawn(EntityType.COW, new BlockPos(58, 1, 55));
        var second = helper.spawn(EntityType.COW, new BlockPos(58, 1, 56));
        first.setNoAi(true); second.setNoAi(true);
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(250, () -> {
            if (first.isInLove() || second.isInLove() || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") != 4)
                helper.fail("Butcher fed livestock through a wall");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void butcherLeavesLivestockOutsideColonyAlone(GameTestHelper helper) {
        for (int x = 60; x <= 74; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(18, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, helper.absolutePos(new BlockPos(66, 1, 56)), CultureType.ROMAN, true));
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(65, 1, 56));
        worker.setNoAi(true);
        var cows = new java.util.ArrayList<Cow>();
        for (int z = 55; z <= 57; z++) {
            var cow = helper.spawn(EntityType.COW, new BlockPos(67, 1, z));
            cow.setNoAi(true);
            if (colony.isInside(cow.blockPosition())) helper.fail("Fixture cow inside colony");
            cows.add(cow);
        }
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(250, () -> {
            for (var cow : cows) if (!cow.isAlive() || cow.getHealth() != cow.getMaxHealth())
                helper.fail("Butcher attacked livestock outside colony");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1300)
    public static void feedsPairToProduceRealCalf(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int i = 50; i <= 62; i++) {
            helper.setBlock(i, 1, 50, Blocks.OAK_FENCE); helper.setBlock(i, 1, 62, Blocks.OAK_FENCE);
            helper.setBlock(50, 1, i, Blocks.OAK_FENCE); helper.setBlock(62, 1, i, Blocks.OAK_FENCE);
        }
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        colony.depositToWarehouse("minecraft:wheat", 4);
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        Cow first = helper.spawn(EntityType.COW, new BlockPos(56, 1, 56));
        Cow second = helper.spawn(EntityType.COW, new BlockPos(58, 1, 56));
        var job = new ButcherJob(worker, colony);
        boolean[] fed = {false, false};
        long[] fedAt = {-1, -1};
        for (int tick = 1; tick < 1300; tick++) helper.runAtTickTime(tick, () -> {
            boolean firstBefore = first.isInLove(), secondBefore = second.isInLove();
            job.tick(helper.getLevel());
            if (!firstBefore && first.isInLove()) {
                if (worker.distanceToSqr(first) > 6.25) helper.fail("Fed cow from too far away");
                fed[0] = true; fedAt[0] = helper.getLevel().getGameTime();
            }
            if (!secondBefore && second.isInLove()) {
                if (worker.distanceToSqr(second) > 6.25) helper.fail("Fed cow from too far away");
                fed[1] = true; fedAt[1] = helper.getLevel().getGameTime();
            }
        });
        helper.runAtTickTime(1200, () -> {
            var herd = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(center).inflate(8, 4, 8), Cow::isAlive);
            if (!fed[0] || !fed[1] || herd.stream().noneMatch(Cow::isBaby)
                    || !first.isAlive() || !second.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") != 2)
                helper.fail("Breeding incomplete: fed=" + fed[0] + "," + fed[1] + " herd=" + herd.size()
                        + " wheat=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") + " state=" + job.getState()
                        + " fedAt=" + fedAt[0] + "," + fedAt[1] + " love=" + first.isInLove() + "," + second.isInLove()
                        + " positions=" + first.blockPosition().toShortString() + "," + second.blockPosition().toShortString());
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1300)
    public static void feedsPigsWithRealCarrots(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int i = 50; i <= 62; i++) {
            helper.setBlock(i, 1, 50, Blocks.OAK_FENCE); helper.setBlock(i, 1, 62, Blocks.OAK_FENCE);
            helper.setBlock(50, 1, i, Blocks.OAK_FENCE); helper.setBlock(62, 1, i, Blocks.OAK_FENCE);
        }
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        colony.depositToWarehouse("minecraft:carrot", 4);
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        var first = helper.spawn(EntityType.PIG, new BlockPos(56, 1, 56));
        var second = helper.spawn(EntityType.PIG, new BlockPos(58, 1, 56));
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 1300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(1200, () -> {
            var pigs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                    new AABB(center).inflate(8, 4, 8), net.minecraft.world.entity.animal.Pig::isAlive);
            if (!first.isAlive() || !second.isAlive() || pigs.stream().noneMatch(net.minecraft.world.entity.animal.Pig::isBaby)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:carrot") != 2)
                helper.fail("Pig breeding incomplete: pigs=" + pigs.size() + " carrots="
                        + colony.getWarehouseCount(helper.getLevel(), "minecraft:carrot") + " state=" + job.getState());
            helper.succeed();
        });
    }

}
