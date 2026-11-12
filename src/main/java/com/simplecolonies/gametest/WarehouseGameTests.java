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
        colony.getWarehouseManager().scan(helper.getLevel());
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:diamond") < 1) helper.fail("Chest not scanned");
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:nether_star") != 0
                || colony.consumeStock(helper.getLevel(), "minecraft:nether_star", 1)
                || !machine.getItem(0).is(Items.NETHER_STAR)) helper.fail("Machine inventory was used as warehouse stock");
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
