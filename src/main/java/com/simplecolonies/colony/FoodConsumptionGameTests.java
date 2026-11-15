package com.simplecolonies.colony;

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
public class FoodConsumptionGameTests {
    @GameTest(template = "empty")
    public static void preparedFoodIsUsedBeforeStoredPlantingProduce(GameTestHelper helper) {
        BlockPos chest = new BlockPos(8, 1, 8);
        helper.setBlock(chest, Blocks.CHEST);
        Container inventory = (Container) helper.getBlockEntity(chest);
        inventory.setItem(0, new ItemStack(Items.CARROT, 3));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(chest));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest)))
            helper.fail("Could not share test chest");
        colony.depositToWarehouse("minecraft:bread", 2);
        colony.consumeFood(helper.getLevel(), 2);
        if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") != 0 || inventory.getItem(0).getCount() != 3)
            helper.fail("Consumed carrots before virtual bread");
        colony.consumeFood(helper.getLevel(), 2);
        if (inventory.getItem(0).getCount() != 1 || colony.getFoodCount(helper.getLevel()) != 1)
            helper.fail("Did not consume actual produce when no preferred food remained");
        helper.succeed();
    }
}
