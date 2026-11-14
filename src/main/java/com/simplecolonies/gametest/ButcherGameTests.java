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
    private static void designatePasture(GameTestHelper helper, Colony colony, BlockPos center) {
        BlockPos pos = center.north(2);
        helper.getLevel().setBlock(pos, com.simplecolonies.block.ModBlocks.BUILDING_MARKER.get().defaultBlockState(), 3);
        var marker = (com.simplecolonies.block.entity.BuildingMarkerBlockEntity) helper.getLevel().getBlockEntity(pos);
        marker.setBuildingType(BuildingType.PASTURE); marker.setLinkedBuildingPos(pos);
        colony.registerPlayerBuilding(new Building(BuildingType.PASTURE, pos, CultureType.ROMAN, true));
    }

    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void gathersOnlyOwnActualDropsAndLeavesBreedingPair(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        designatePasture(helper, colony, center);
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
        designatePasture(helper, colony, center);
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
        designatePasture(helper, colony, center);
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
        designatePasture(helper, colony, center);
        BlockPos chest = new BlockPos(54, 1, 56);
        helper.setBlock(chest, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.WHEAT, 4));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(56, 1, 56));
        worker.setNoAi(true);
        var first = helper.spawn(EntityType.COW, new BlockPos(58, 1, 55));
        var second = helper.spawn(EntityType.COW, new BlockPos(58, 1, 56));
        first.setNoAi(true); second.setNoAi(true);
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(250, () -> {
            if (first.isInLove() || second.isInLove()
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") + worker.getWorkSupplies().getCount() != 4)
                helper.fail("Butcher fed livestock through a wall or lost its carried wheat");
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

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void feedsPairToProduceRealCalf(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int i = 50; i <= 62; i++) {
            helper.setBlock(i, 1, 50, Blocks.OAK_FENCE); helper.setBlock(i, 1, 62, Blocks.OAK_FENCE);
            helper.setBlock(50, 1, i, Blocks.OAK_FENCE); helper.setBlock(62, 1, i, Blocks.OAK_FENCE);
        }
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        designatePasture(helper, colony, center);
        BlockPos chest = new BlockPos(52, 1, 54);
        helper.setBlock(chest, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.WHEAT, 4));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        Cow first = helper.spawn(EntityType.COW, new BlockPos(56, 1, 56));
        Cow second = helper.spawn(EntityType.COW, new BlockPos(58, 1, 56));
        var job = new ButcherJob(worker, colony);
        boolean[] fed = {false, false};
        boolean[] collected = {false};
        BlockPos absChest = helper.absolutePos(chest);
        long[] fedAt = {-1, -1};
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> {
            boolean firstBefore = first.isInLove(), secondBefore = second.isInLove();
            job.tick(helper.getLevel());
            if (!collected[0] && !worker.getWorkSupplies().isEmpty()) {
                if (worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9)
                    helper.fail("Butcher collected food remotely");
                var tag = new net.minecraft.nbt.CompoundTag(); worker.addAdditionalSaveData(tag);
                var saved = ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("workSupplies"));
                if (!saved.is(Items.WHEAT) || saved.getCount() != worker.getWorkSupplies().getCount()) helper.fail("Breeding food did not persist");
                collected[0] = true;
            }
            if (!firstBefore && first.isInLove()) {
                if (worker.distanceToSqr(first) > 6.25) helper.fail("Fed cow from too far away");
                fed[0] = true; fedAt[0] = helper.getLevel().getGameTime();
            }
            if (!secondBefore && second.isInLove()) {
                if (worker.distanceToSqr(second) > 6.25) helper.fail("Fed cow from too far away");
                fed[1] = true; fedAt[1] = helper.getLevel().getGameTime();
            }
        });
        helper.runAtTickTime(2100, () -> {
            var herd = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(center).inflate(8, 4, 8), Cow::isAlive);
            if (!collected[0] || !fed[0] || !fed[1] || herd.stream().noneMatch(Cow::isBaby)
                    || !first.isAlive() || !second.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") != 2)
                helper.fail("Breeding incomplete: fed=" + fed[0] + "," + fed[1] + " herd=" + herd.size()
                        + " wheat=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") + " state=" + job.getState()
                        + " fedAt=" + fedAt[0] + "," + fedAt[1] + " love=" + first.isInLove() + "," + second.isInLove()
                        + " positions=" + first.blockPosition().toShortString() + "," + second.blockPosition().toShortString());
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void feedsPigsWithRealCarrots(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int i = 50; i <= 62; i++) {
            helper.setBlock(i, 1, 50, Blocks.OAK_FENCE); helper.setBlock(i, 1, 62, Blocks.OAK_FENCE);
            helper.setBlock(50, 1, i, Blocks.OAK_FENCE); helper.setBlock(62, 1, i, Blocks.OAK_FENCE);
        }
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        designatePasture(helper, colony, center);
        BlockPos chest = new BlockPos(52, 1, 54);
        helper.setBlock(chest, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.CARROT, 4));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        var first = helper.spawn(EntityType.PIG, new BlockPos(56, 1, 56));
        var second = helper.spawn(EntityType.PIG, new BlockPos(58, 1, 56));
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(2100, () -> {
            var pigs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                    new AABB(center).inflate(8, 4, 8), net.minecraft.world.entity.animal.Pig::isAlive);
            if (!first.isAlive() || !second.isAlive() || pigs.stream().noneMatch(net.minecraft.world.entity.animal.Pig::isBaby)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:carrot") != 2)
                helper.fail("Pig breeding incomplete: pigs=" + pigs.size() + " carrots="
                        + colony.getWarehouseCount(helper.getLevel(), "minecraft:carrot") + " state=" + job.getState());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void breedingUsesOnlyPlainSharedFoodAndPreservesReserve(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8)), chest = new BlockPos(6, 1, 8);
        helper.setBlock(chest, Blocks.CHEST);
        var container = (net.minecraft.world.Container) helper.getBlockEntity(chest);
        ItemStack special = new ItemStack(Items.WHEAT, 4);
        special.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keepsake"));
        container.setItem(0, special);
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        designatePasture(helper, colony, center);
        colony.depositToWarehouse("minecraft:wheat", 20); // Cannot be physically collected.
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 9)); worker.setNoAi(true);
        var first = helper.spawn(EntityType.COW, new BlockPos(8, 1, 9)); first.setNoAi(true);
        var second = helper.spawn(EntityType.COW, new BlockPos(9, 1, 9)); second.setNoAi(true);
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 700; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(140, () -> {
            if (container.getItem(0).getCount() != 4 || first.isInLove() || second.isInLove() || !worker.getWorkSupplies().isEmpty())
                helper.fail("Used virtual or customized breeding food");
            container.setItem(0, new ItemStack(Items.WHEAT, 3));
        });
        helper.runAtTickTime(280, () -> {
            if (container.countItem(Items.WHEAT) != 3 || first.isInLove() || second.isInLove() || !worker.getWorkSupplies().isEmpty())
                helper.fail("Did not preserve two-item reserve for a pair");
            container.setItem(0, new ItemStack(Items.WHEAT, 4));
        });
        helper.succeedWhen(() -> {
            if (!first.isInLove() || !second.isInLove() || container.countItem(Items.WHEAT) != 2)
                helper.fail("Did not physically feed pair with reserve intact: " + job.getState());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void waitsUntilBothParentsAreReachableBeforeFeeding(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8)), chest = new BlockPos(6, 1, 7);
        helper.setBlock(chest, Blocks.CHEST);
        var inventory = (net.minecraft.world.Container) helper.getBlockEntity(chest);
        inventory.setItem(0, new ItemStack(Items.WHEAT, 4));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        designatePasture(helper, colony, center);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(6, 1, 8)); worker.setNoAi(true);
        var first = helper.spawn(EntityType.COW, new BlockPos(7, 1, 8)); first.setNoAi(true);
        var second = helper.spawn(EntityType.COW, new BlockPos(11, 1, 8)); second.setNoAi(true);
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 700; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(220, () -> {
            if (first.isInLove() || second.isInLove() || inventory.countItem(Items.WHEAT) != 2
                    || worker.getWorkSupplies().getCount() != 2)
                helper.fail("Fed a parent before both were reachable or lost food");
            var pos = helper.absolutePos(new BlockPos(8, 1, 8));
            second.teleportTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5);
        });
        helper.succeedWhen(() -> {
            if (!first.isInLove() || !second.isInLove() || inventory.countItem(Items.WHEAT) != 2)
                helper.fail("Nearby pair not fed: " + job.getState());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void butcherRequiresLivePasturePermission(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.BUTCHER_HUT, center, CultureType.ROMAN, true));
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8));
        worker.setNoAi(true);
        var cows = new java.util.ArrayList<Cow>();
        for (int z = 7; z <= 9; z++) { var cow = helper.spawn(EntityType.COW, new BlockPos(9, 1, z)); cow.setNoAi(true); cows.add(cow); }
        var job = new ButcherJob(worker, colony);
        for (int tick = 1; tick < 500; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(160, () -> {
            for (var cow : cows) if (cow.getHealth() != cow.getMaxHealth()) helper.fail("Attacked livestock without a pasture");
            designatePasture(helper, colony, center);
            // A stale record without its marker is not permission either.
            helper.getLevel().setBlock(center.north(2), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAtTickTime(450, () -> {
            for (var cow : cows) if (cow.getHealth() != cow.getMaxHealth()) helper.fail("Attacked livestock after marker removal");
            helper.succeed();
        });
    }

}
