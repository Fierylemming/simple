package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class WarehouseGameTests {
    @GameTest(template = "empty")
    public static void furnaceContentsAreNotWarehouseStock(GameTestHelper helper) {
        BlockPos furnace = new BlockPos(8, 1, 8), chest = new BlockPos(10, 1, 8);
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(chest, Blocks.CHEST);
        Container machine = (Container) helper.getBlockEntity(furnace);
        machine.setItem(0, new ItemStack(Items.NETHER_STAR));
        Container storage = (Container) helper.getBlockEntity(chest);
        storage.setItem(0, new ItemStack(Items.DIAMOND));
        Colony colony = new Colony(0, CultureType.GREEK, helper.absolutePos(furnace));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest));
        colony.getWarehouseManager().scan(helper.getLevel());
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:diamond") < 1) helper.fail("Chest not scanned");
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:nether_star") != 0
                || colony.consumeStock(helper.getLevel(), "minecraft:nether_star", 1)
                || !machine.getItem(0).is(Items.NETHER_STAR)) helper.fail("Machine inventory was used as warehouse stock");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void storageRequiresExplicitOwnershipAndSurvivesReload(GameTestHelper helper) {
        BlockPos privateChest = new BlockPos(7, 1, 8), sharedChest = new BlockPos(10, 1, 8);
        helper.setBlock(privateChest, Blocks.CHEST);
        helper.setBlock(sharedChest, Blocks.CHEST);
        Container privateItems = (Container) helper.getBlockEntity(privateChest);
        Container sharedItems = (Container) helper.getBlockEntity(sharedChest);
        privateItems.setItem(0, new ItemStack(Items.DIAMOND, 5));
        sharedItems.setItem(0, new ItemStack(Items.DIAMOND, 3));
        var level = helper.getLevel();
        Colony colony = new Colony(7, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        var storage = colony.getWarehouseManager();
        if (colony.getWarehouseCount(level, "minecraft:diamond") != 0
                || colony.consumeStock(level, "minecraft:diamond", 1))
            helper.fail("Unmarked player chests were visible as stock");
        BlockPos shared = helper.absolutePos(sharedChest), other = helper.absolutePos(privateChest);
        if (!storage.registerStorage(level, shared) || colony.getWarehouseCount(level, "minecraft:diamond") != 3)
            helper.fail("Claimed storage was not visible immediately");
        Colony otherColony = new Colony(8, CultureType.ROMAN, colony.getCenter());
        if (otherColony.getWarehouseManager().registerStorage(level, shared)
                || otherColony.getWarehouseCount(level, "minecraft:diamond") != 0)
            helper.fail("Another colony could claim or use the container");
        var be = helper.getBlockEntity(sharedChest);
        var saved = be.saveWithFullMetadata(level.registryAccess());
        be.loadWithComponents(saved, level.registryAccess());
        // Copied block-entity data (e.g. from a moved shulker) must not claim a new location.
        helper.getBlockEntity(privateChest).loadWithComponents(saved, level.registryAccess());
        privateItems = (Container) helper.getBlockEntity(privateChest);
        privateItems.setItem(0, new ItemStack(Items.DIAMOND, 5));
        if (storage.isRegisteredStorage(level, other)) helper.fail("Claim followed copied data to another position");
        if (!storage.isRegisteredStorage(level, shared) || !colony.consumeStock(level, "minecraft:diamond", 2)
                || privateItems.getItem(0).getCount() != 5 || sharedItems.getItem(0).getCount() != 1)
            helper.fail("Reload or extraction violated storage ownership");
        if (!storage.unregisterStorage(level, shared) || colony.getWarehouseCount(level, "minecraft:diamond") != 0
                || storage.courierContainer(level, shared) != null || privateItems.getItem(0).getCount() != 5)
            helper.fail("Revoked storage remained accessible");
        if (!storage.registerStorage(level, shared)) helper.fail("Could not reclaim storage");
        helper.setBlock(sharedChest, Blocks.AIR);
        helper.setBlock(sharedChest, Blocks.CHEST);
        if (storage.courierContainer(level, shared) != null || colony.getWarehouseCount(level, "minecraft:diamond") != 0)
            helper.fail("A replacement chest inherited the old claim");
        if (storage.isRegisteredStorage(level, other)) helper.fail("Unmarked chest became claimed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void genericStockDoesNotConsumeOrMergeModifiedItems(GameTestHelper helper) {
        BlockPos chest = new BlockPos(8, 1, 8), pos = helper.absolutePos(chest);
        helper.setBlock(chest, Blocks.CHEST);
        Container items = (Container) helper.getBlockEntity(chest);
        ItemStack namedBread = new ItemStack(Items.BREAD, 4);
        namedBread.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Special bread"));
        items.setItem(0, namedBread);
        items.setItem(1, new ItemStack(Items.BREAD, 3));
        Colony colony = new Colony(0, CultureType.ROMAN, pos);
        var storage = colony.getWarehouseManager();
        if (!storage.registerStorage(helper.getLevel(), pos)) helper.fail("Could not designate storage");
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") != 3)
            helper.fail("Modified bread was counted as generic food");
        if (colony.consumeStock(helper.getLevel(), "minecraft:bread", 4)) helper.fail("Consumed named bread as generic stock");
        if (!colony.consumeStock(helper.getLevel(), "minecraft:bread", 3)) helper.fail("Failed to consume ordinary bread");
        if (storage.deposit(helper.getLevel(), pos, "minecraft:bread", 2) != 2) helper.fail("Could not deposit ordinary bread");
        if (items.getItem(0).getCount() != 4 || !items.getItem(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                || items.getItem(1).getCount() != 2 || items.getItem(1).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
            helper.fail("Named bread was consumed or generic food merged into it");
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") != 2) helper.fail("Stock counts became inconsistent");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void warehouseHubDesignationSurvivesReloadWithoutInventingOne(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.GREEK, center);
        var noHub = Colony.load(colony.save());
        if (noHub.getWarehouseManager().getConfiguredWarehouseCenter() != null
                || !noHub.getWarehouseManager().getWarehouseCenter().equals(center))
            helper.fail("Reload turned the display fallback into a designated hub");
        BlockPos hub = center.east(5);
        colony.setWarehouseCenter(hub);
        var restored = Colony.load(colony.save());
        if (!hub.equals(restored.getWarehouseManager().getConfiguredWarehouseCenter()))
            helper.fail("Designated warehouse hub did not survive reload");
        helper.succeed();
    }
}
