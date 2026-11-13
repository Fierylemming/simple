package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.integration.CreateSchematicIntegration;
import com.simplecolonies.menu.TownHallMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class TownHallSafetyGameTests {
    @GameTest(template = "empty")
    public static void schematicPlaceholderNeverConsumesItemsOrQueuesFakeHouse(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.depositToWarehouse("create:schematic", 1);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        colony.setLeader(player);
        var menu = new TownHallMenu(1, player.getInventory(), colony, center);
        menu.clickMenuButton(player, TownHallMenu.BTN_CREATE_SCHEMATIC);
        if (CreateSchematicIntegration.tryCreateSchematicTask(colony, helper.getLevel(), center) != null
                || colony.getWarehouseCount("create:schematic") != 1 || !colony.getBuildings().isEmpty())
            helper.fail("Unimplemented schematic consumed stock or queued a fake building");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void donationPreservesNamedBread(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack special = new ItemStack(Items.BREAD, 3);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Keepsake"));
        player.getInventory().setItem(0, special);
        player.getInventory().setItem(1, new ItemStack(Items.BREAD, 2));
        var menu = new TownHallMenu(1, player.getInventory(), colony, center);
        menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD);
        if (player.getInventory().getItem(0).getCount() != 3 || !player.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)
                || !player.getInventory().getItem(1).isEmpty() || colony.getWarehouseCount("minecraft:bread") != 2)
            helper.fail("Donation consumed or converted named bread");
        helper.succeed();
    }
}
