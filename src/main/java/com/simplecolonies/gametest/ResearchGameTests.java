package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ColonyVillager;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.progression.ColonyUpgrade;
import com.simplecolonies.villager.job.BuilderJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ResearchGameTests {
    private static Colony colony(GameTestHelper helper, BlockPos hall) {
        BlockPos site = helper.absolutePos(hall);
        var colony = new Colony(GameTestColonyIds.next(), CultureType.ROMAN, site);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, site, CultureType.ROMAN, true));
        return colony;
    }

    private static void stock(Container chest, int factor) {
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32 * factor));
        chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 16 * factor));
        chest.setItem(2, new ItemStack(Items.COAL, 4 * factor));
        chest.setChanged();
    }

    @GameTest(template = "isolated", timeoutTicks = 1600)
    public static void builderCarriesResearchAndOrderSurvivesReload(GameTestHelper helper) {
        for (int x = 45; x <= 68; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos chestPos = new BlockPos(50, 1, 56), hall = new BlockPos(62, 1, 56);
        helper.setBlock(chestPos, Blocks.CHEST);
        Container chest = (Container) helper.getBlockEntity(chestPos); stock(chest, 1);
        Colony[] colony = {colony(helper, hall)};
        if (!colony[0].getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chestPos))) helper.fail("Could not share research chest");
        if (!colony[0].getResearch().tryBuy(ColonyUpgrade.TOOL_STONE, helper.getLevel(), null)
                || colony[0].getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE) || chest.countItem(Items.COAL) != 4)
            helper.fail("Queueing research remotely spent items or unlocked it");
        ColonyVillager[] worker = {helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(52, 1, 56))};
        BuilderJob[] job = {new BuilderJob(worker[0], colony[0])};
        boolean[] reloaded = {false}, sawTravel = {false}, checkedCompletion = {false};
        for (int tick = 1; tick < 1600; tick++) helper.runAtTickTime(tick, () -> {
            int before = chest.countItem(Items.COBBLESTONE) + chest.countItem(Items.OAK_PLANKS) + chest.countItem(Items.COAL);
            job[0].tick(helper.getLevel());
            int after = chest.countItem(Items.COBBLESTONE) + chest.countItem(Items.OAK_PLANKS) + chest.countItem(Items.COAL);
            BlockPos source = helper.absolutePos(chestPos);
            if (after < before && worker[0].distanceToSqr(source.getX()+.5, source.getY()+.5, source.getZ()+.5) > 9)
                helper.fail("Research inputs were picked up remotely");
            if (!reloaded[0] && !worker[0].getResearchMaterials().isEmpty()) {
                CompoundTag data = new CompoundTag(); worker[0].saveWithoutId(data);
                var replacement = ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
                if (replacement == null) { helper.fail("Could not reload researcher"); return; }
                var savedColony = colony[0].save();
                worker[0].discard(); replacement.load(data); helper.getLevel().addFreshEntity(replacement);
                colony[0] = Colony.load(savedColony); worker[0] = replacement; job[0] = new BuilderJob(replacement, colony[0]);
                if (!worker[0].getUUID().equals(colony[0].getResearch().getAssignedWorker())
                        || worker[0].getResearchMaterials().isEmpty()) helper.fail("Research ownership or carried materials did not reload");
                reloaded[0] = true;
            }
            if (reloaded[0] && !worker[0].getResearchMaterials().isEmpty()
                    && worker[0].distanceToSqr(helper.absolutePos(hall).getX()+.5, helper.absolutePos(hall).getY()+.5, helper.absolutePos(hall).getZ()+.5) > 9)
                sawTravel[0] = true;
            if (!checkedCompletion[0] && colony[0].getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)) {
                BlockPos target = helper.absolutePos(hall);
                if (worker[0].distanceToSqr(target.getX()+.5, target.getY()+.5, target.getZ()+.5) > 9)
                    helper.fail("Research was completed remotely");
                checkedCompletion[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!reloaded[0] || !sawTravel[0] || !colony[0].getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)
                    || colony[0].getResearch().getPending() != null || !worker[0].getResearchMaterials().isEmpty()
                    || chest.countItem(Items.COBBLESTONE) != 0 || chest.countItem(Items.OAK_PLANKS) != 0 || chest.countItem(Items.COAL) != 0)
                helper.fail("Physical research incomplete: " + job[0].getState());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void cancelledResearchReturnsCarriedInputs(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos chestPos = new BlockPos(6, 1, 8), hall = new BlockPos(9, 1, 8);
        helper.setBlock(chestPos, Blocks.CHEST); Container chest = (Container) helper.getBlockEntity(chestPos); stock(chest, 1);
        Colony colony = colony(helper, hall);
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chestPos));
        if (!colony.getResearch().tryBuy(ColonyUpgrade.TOOL_STONE, helper.getLevel(), null)) helper.fail("Could not queue research");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8)); worker.setNoAi(true);
        var job = new BuilderJob(worker, colony); boolean[] cancelled = {false};
        for (int tick = 1; tick < 700; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!cancelled[0] && !worker.getResearchMaterials().isEmpty()) {
                if (!colony.getResearch().cancelPending()) helper.fail("Could not cancel pending research");
                cancelled[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!cancelled[0] || colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)
                    || !worker.getResearchMaterials().isEmpty() || chest.countItem(Items.COBBLESTONE) != 32
                    || chest.countItem(Items.OAK_PLANKS) != 16 || chest.countItem(Items.COAL) != 4)
                helper.fail("Cancelled inputs were not physically returned: " + job.getState());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 900)
    public static void automaticResearchRechecksReserveAfterPickup(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos chestPos = new BlockPos(6, 1, 8), hall = new BlockPos(9, 1, 8);
        helper.setBlock(chestPos, Blocks.CHEST); Container chest = (Container) helper.getBlockEntity(chestPos); stock(chest, 1);
        Colony colony = colony(helper, hall); colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chestPos));
        var tag = colony.getResearch().save(); tag.putInt("autoCooldown", 0); colony.getResearch().load(tag);
        colony.getResearch().tickAutoProgress(helper.getLevel());
        if (colony.getResearch().getPending() != null) helper.fail("Automatic research queued without its reserve");
        stock(chest, 2); tag = colony.getResearch().save(); tag.putInt("autoCooldown", 0); colony.getResearch().load(tag);
        colony.getResearch().tickAutoProgress(helper.getLevel());
        if (colony.getResearch().getPending() != ColonyUpgrade.TOOL_STONE) helper.fail("Automatic research did not queue with surplus");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8)); worker.setNoAi(true);
        var job = new BuilderJob(worker, colony); boolean[] withheld = {false}, restored = {false};
        for (int tick = 1; tick < 900; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!withheld[0] && chest.countItem(Items.COBBLESTONE) == 32 && chest.countItem(Items.OAK_PLANKS) == 16 && chest.countItem(Items.COAL) == 4) {
                chest.setItem(2, ItemStack.EMPTY); chest.setChanged(); withheld[0] = true;
            }
        });
        helper.runAtTickTime(400, () -> {
            if (!withheld[0] || colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)) helper.fail("Research spent a removed reserve");
            chest.setItem(2, new ItemStack(Items.COAL, 4)); chest.setChanged(); restored[0] = true;
        });
        helper.succeedWhen(() -> {
            if (!restored[0] || !colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)
                    || chest.countItem(Items.COBBLESTONE) != 32 || chest.countItem(Items.OAK_PLANKS) != 16 || chest.countItem(Items.COAL) != 4)
                helper.fail("Research did not preserve its restored reserve: " + job.getState());
        });
    }

    @GameTest(template = "empty")
    public static void researchRejectsVirtualStockAndRetainsLegacyUnlocks(GameTestHelper helper) {
        Colony colony = colony(helper, new BlockPos(8, 1, 8));
        colony.depositToWarehouse("minecraft:cobblestone", 32); colony.depositToWarehouse("minecraft:oak_planks", 16); colony.depositToWarehouse("minecraft:coal", 4);
        if (colony.getResearch().tryBuy(ColonyUpgrade.TOOL_STONE, helper.getLevel(), null)
                || colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE) || colony.getWarehouseCount("minecraft:cobblestone") != 32)
            helper.fail("Research consumed virtual stock");
        var tag = colony.getResearch().save();
        var unlocked = tag.getList("unlocked", net.minecraft.nbt.Tag.TAG_STRING);
        unlocked.add(net.minecraft.nbt.StringTag.valueOf("SCHEMATIC_CANNON")); tag.put("unlocked", unlocked);
        colony.getResearch().load(tag);
        if (!Colony.load(colony.save()).getResearch().isUnlocked(ColonyUpgrade.SCHEMATIC_CANNON)) helper.fail("Legacy unlock was lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void libraryTierPreservesResearchInterval(GameTestHelper helper) {
        for (int tier = 1; tier <= 5; tier++) {
            Colony colony = colony(helper, new BlockPos(8, 1, 8));
            var library = new Building(BuildingType.LIBRARY, colony.getCenter().east(5), CultureType.ROMAN, true);
            library.setLevel(tier); colony.registerPlayerBuilding(library);
            var tag = colony.getResearch().save(); tag.putInt("autoCooldown", 0); colony.getResearch().load(tag);
            colony.getResearch().tickAutoProgress(helper.getLevel());
            if (colony.getResearch().save().getInt("autoCooldown") != Math.max(0, 3-tier)) helper.fail("Wrong library cooldown");
        }
        helper.succeed();
    }
}
