package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Harbor exports, market orders and physical daily meals. */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class TradeAndMealGameTests {
    private static final int COLONY_ID = -1200000600;

    private static Colony install(GameTestHelper helper, BlockPos center, BuildingType completed) {
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var colonies = (Map<Integer, Colony>) field.get(ColonyManager.getInstance());
            var colony = new Colony(COLONY_ID, CultureType.ROMAN, center);
            colonies.put(COLONY_ID, colony);
            if (completed != null) {
                var building = new Building(completed, center, CultureType.ROMAN, true);
                building.setState(BuildingState.COMPLETE);
                colony.registerPlayerBuilding(building);
            }
            return colony;
        } catch (ReflectiveOperationException e) {
            helper.fail("Cannot install test colony: " + e);
            return null;
        }
    }

    private static void uninstall() {
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var colonies = (Map<Integer, Colony>) field.get(ColonyManager.getInstance());
            colonies.remove(COLONY_ID);
        } catch (ReflectiveOperationException ignored) { }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void harborSellsSurplusForEmeralds(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = install(helper, center, BuildingType.HARBOR);
        if (colony == null) return;
        try {
            helper.setBlock(new BlockPos(9, 1, 8), Blocks.CHEST);
            var chest = helper.absolutePos(new BlockPos(9, 1, 8));
            var storage = (Container) helper.getBlockEntity(new BlockPos(9, 1, 8));
            if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), chest))
                helper.fail("Could not share the harbor chest");
            storage.setItem(0, new ItemStack(Items.COD, 40));
            storage.setChanged();
            colony.tick(helper.getLevel());
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:emerald") < 1)
                helper.fail("A harbor with surplus cod paid no emeralds: " + colony.getTradeReport());
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 32)
                helper.fail("Harbor exported the wrong amount: " + colony.getWarehouseSnapshot(helper.getLevel()));
            helper.succeed();
        } finally { uninstall(); }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void marketOrdersMissingMaterial(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = install(helper, center, BuildingType.MARKET);
        if (colony == null) return;
        try {
            // A plan that still needs materials gives the market something to order.
            colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, center.offset(4, 0, 0), CultureType.ROMAN));
            helper.setBlock(new BlockPos(9, 1, 8), Blocks.CHEST);
            var chest = helper.absolutePos(new BlockPos(9, 1, 8));
            var storage = (Container) helper.getBlockEntity(new BlockPos(9, 1, 8));
            if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), chest))
                helper.fail("Could not share the market chest");
            storage.setItem(0, new ItemStack(Items.EMERALD, 8));
            storage.setChanged();
            colony.tick(helper.getLevel());
            int emeralds = colony.getWarehouseCount(helper.getLevel(), "minecraft:emerald");
            if (emeralds >= 8) helper.fail("Market spent nothing: " + colony.getTradeReport());
            if (!colony.getTradeReport().startsWith("Market:"))
                helper.fail("No market order was recorded: " + colony.getTradeReport());
            helper.succeed();
        } finally { uninstall(); }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void citizenWalksToStorageAndEatsARealMeal(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = install(helper, center, null);
        if (colony == null) return;
        helper.getLevel().setDayTime(1000);
        helper.setBlock(new BlockPos(12, 1, 8), Blocks.CHEST);
        var chest = helper.absolutePos(new BlockPos(12, 1, 8));
        var storage = (Container) helper.getBlockEntity(new BlockPos(12, 1, 8));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), chest))
            helper.fail("Could not share the pantry");
        storage.setItem(0, new ItemStack(Items.BREAD, 3));
        storage.setChanged();
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 12));
        worker.setColony(colony);
        colony.tick(helper.getLevel());   // one simulated day: one meal is scheduled
        helper.runAfterDelay(300, () -> {
            try {
                if (colony.getCitizens().stream().allMatch(c -> c.pendingMeals > 0))
                    helper.fail("The scheduled meal was never eaten: " + colony.getTradeReport());
                if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") != 2)
                    helper.fail("A physical meal did not come from the pantry: "
                            + colony.getWarehouseSnapshot(helper.getLevel()));
                helper.succeed();
            } finally { uninstall(); }
        });
    }
}
