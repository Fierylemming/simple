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
        helper.setBlock(new BlockPos(8, 1, 8), com.simplecolonies.block.ModBlocks.TOWN_HALL.get());
        ((com.simplecolonies.block.entity.TownHallBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8))).setColonyId(colony.getId());
        colony.depositToWarehouse("create:schematic", 1);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(center.getX() + .5, center.getY(), center.getZ() + .5);
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
        helper.setBlock(new BlockPos(8, 1, 8), com.simplecolonies.block.ModBlocks.TOWN_HALL.get());
        ((com.simplecolonies.block.entity.TownHallBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8))).setColonyId(colony.getId());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(center.getX() + .5, center.getY(), center.getZ() + .5);
        ItemStack special = new ItemStack(Items.BREAD, 3);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Keepsake"));
        player.getInventory().setItem(0, special);
        player.getInventory().setItem(1, new ItemStack(Items.BREAD, 2));
        var menu = new TownHallMenu(1, player.getInventory(), colony, center);
        menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD);
        if (player.getInventory().getItem(0).getCount() != 3 || !player.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)
                || !player.getInventory().getItem(1).isEmpty() || colony.getWarehouseCount("minecraft:bread") != 2)
            helper.fail("Donation consumed or converted named bread");
        // A stale menu cannot donate after the player moves away or the hall is removed.
        player.getInventory().setItem(1, new ItemStack(Items.BREAD, 2));
        player.moveTo(center.getX() + 40, center.getY(), center.getZ());
        if (menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD) || player.getInventory().getItem(1).getCount() != 2)
            helper.fail("Distant player used a stale town hall menu");
        player.moveTo(center.getX() + .5, center.getY(), center.getZ() + .5);
        helper.setBlock(new BlockPos(8, 1, 8), net.minecraft.world.level.block.Blocks.AIR);
        if (menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD) || player.getInventory().getItem(1).getCount() != 2)
            helper.fail("Removed hall left a usable menu");
        helper.succeed();
    }
}
