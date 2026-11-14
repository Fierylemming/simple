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
    public static void diplomacyUsesSelectedSnapshotTargetAndChecksLeader(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var self = new Colony(40001, CultureType.ROMAN, center);
        var first = new Colony(40002, CultureType.GREEK, center.east(2));
        var second = new Colony(40003, CultureType.PERSIAN, center.east(4));
        var available = new java.util.HashMap<Integer, Colony>();
        available.put(first.getId(), first); available.put(second.getId(), second);
        helper.setBlock(new BlockPos(8, 1, 8), com.simplecolonies.block.ModBlocks.TOWN_HALL.get());
        ((com.simplecolonies.block.entity.TownHallBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8))).setColonyId(self.getId());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(center.getX()+.5, center.getY(), center.getZ()+.5);
        var targets = java.util.List.of(new TownHallMenu.DiplomacyTarget(first.getId(), "first"), new TownHallMenu.DiplomacyTarget(second.getId(), "second"));
        var snapshot = TownHallMenu.snapshotWithTargets(self, targets);
        var saved = snapshot.getList("menuDiplomacyTargets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (saved.size() != 2 || saved.getCompound(1).getInt("id") != second.getId()) helper.fail("Target snapshot lost ordering");
        var menu = new TownHallMenu(1, player.getInventory(), self, center, targets, available::get);
        menu.clickMenuButton(player, TownHallMenu.BTN_WAR_TARGET_BASE+1);
        if (self.getDiplomacy().getRelation(second.getId()) != com.simplecolonies.colony.Diplomacy.Relation.NEUTRAL) helper.fail("Non-leader changed relations");
        self.setLeader(player);
        menu.clickMenuButton(player, TownHallMenu.BTN_WAR_TARGET_BASE+1);
        if (self.getDiplomacy().getRelation(second.getId()) != com.simplecolonies.colony.Diplomacy.Relation.WAR
                || second.getDiplomacy().getRelation(self.getId()) != com.simplecolonies.colony.Diplomacy.Relation.WAR
                || self.getDiplomacy().getRelation(first.getId()) != com.simplecolonies.colony.Diplomacy.Relation.NEUTRAL)
            helper.fail("War affected wrong colony");
        available.remove(first.getId());
        menu.clickMenuButton(player, TownHallMenu.BTN_ALLY_TARGET_BASE);
        if (self.getDiplomacy().getRelation(second.getId()) != com.simplecolonies.colony.Diplomacy.Relation.WAR)
            helper.fail("Deleted target redirected action");
        second.setLeader(player);
        menu.clickMenuButton(player, TownHallMenu.BTN_PEACE_TARGET_BASE+1);
        long day = Math.floorDiv(helper.getLevel().getDayTime(), 24000L);
        if (self.getDiplomacy().getRelation(second.getId()) != com.simplecolonies.colony.Diplomacy.Relation.WAR
                || second.getDiplomacy().incomingOffer(self.getId(), day) != com.simplecolonies.colony.Diplomacy.Offer.PEACE)
            helper.fail("Peace was imposed without an offer");
        var restored = new com.simplecolonies.colony.Diplomacy();
        restored.load(second.getDiplomacy().save());
        if (restored.incomingOffer(self.getId(), day + 6) != com.simplecolonies.colony.Diplomacy.Offer.PEACE
                || restored.incomingOffer(self.getId(), day + 7) != null)
            helper.fail("Offer did not persist or expire");
        var reverseTargets = java.util.List.of(new TownHallMenu.DiplomacyTarget(self.getId(), "self"));
        var otherHall = second.getCenter();
        helper.setBlock(new BlockPos(12, 1, 8), com.simplecolonies.block.ModBlocks.TOWN_HALL.get());
        ((com.simplecolonies.block.entity.TownHallBlockEntity) helper.getBlockEntity(new BlockPos(12, 1, 8))).setColonyId(second.getId());
        player.moveTo(otherHall.getX()+.5, otherHall.getY(), otherHall.getZ()+.5);
        var reverse = new TownHallMenu(2, player.getInventory(), second, otherHall, reverseTargets, id -> id == self.getId() ? self : null);
        reverse.clickMenuButton(player, TownHallMenu.BTN_PEACE_TARGET_BASE);
        player.moveTo(center.getX()+.5, center.getY(), center.getZ()+.5);
        menu.clickMenuButton(player, TownHallMenu.BTN_WAR_TARGET_BASE+1);
        if (self.getDiplomacy().getRelation(second.getId()) != com.simplecolonies.colony.Diplomacy.Relation.PEACE_TREATY)
            helper.fail("Peace treaty failed to prevent war");
        helper.succeed();
    }

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
        var chestPos = new BlockPos(10, 1, 8);
        helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        var chest = (net.minecraft.world.Container) helper.getBlockEntity(chestPos);
        var absoluteChest = helper.absolutePos(chestPos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(center.getX() + .5, center.getY(), center.getZ() + .5);
        ItemStack special = new ItemStack(Items.BREAD, 3);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Keepsake"));
        player.getInventory().setItem(0, special);
        player.getInventory().setItem(1, new ItemStack(Items.BREAD, 2));
        var menu = new TownHallMenu(1, player.getInventory(), colony, center);
        menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD);
        if (player.getInventory().getItem(1).getCount() != 2 || chest.countItem(Items.BREAD) != 0)
            helper.fail("Unshared storage accepted a donation");
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absoluteChest)) helper.fail("Could not share hall chest");
        menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD);
        if (player.getInventory().getItem(0).getCount() != 3 || !player.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)
                || !player.getInventory().getItem(1).isEmpty() || chest.countItem(Items.BREAD) != 2
                || colony.getWarehouseCount("minecraft:bread") != 0)
            helper.fail("Donation consumed named bread or failed to deposit real bread");
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        chest.setItem(0, new ItemStack(Items.BREAD, 63));
        player.getInventory().setItem(1, new ItemStack(Items.BREAD, 5));
        menu.clickMenuButton(player, TownHallMenu.BTN_DONATE_FOOD);
        if (player.getInventory().getItem(1).getCount() != 4 || chest.getItem(0).getCount() != 64)
            helper.fail("Full storage did not preserve unaccepted bread");
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
