package com.simplecolonies.villager.job;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class PhysicalBuilderMaterialsGameTests {
    @GameTest(template = "empty")
    public static void fullInventoryDoesNotSpendCraftingIngredients(GameTestHelper helper) {
        BlockPos table = new BlockPos(8, 1, 8), origin = helper.absolutePos(table);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        Colony colony = new Colony(0, CultureType.ROMAN, origin);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, origin, CultureType.ROMAN, true));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8));
        var inventory = worker.getBuilderMaterials();
        inventory.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        for (int i = 1; i < inventory.getContainerSize(); i++) inventory.setItem(i, new ItemStack(Items.DIAMOND, 64));
        var supplies = new PhysicalBuilderMaterials(worker, colony);
        if (supplies.prepare(helper.getLevel(), "minecraft:oak_stairs") != PhysicalBuilderMaterials.Result.BLOCKED
                || inventory.countItem(Items.OAK_LOG) != 2 || inventory.countItem(Items.OAK_STAIRS) != 0
                || inventory.countItem(Items.DIAMOND) != 17 * 64)
            helper.fail("Failed craft spent ingredients or changed inventory");
        inventory.setItem(16, ItemStack.EMPTY);
        inventory.setItem(17, ItemStack.EMPTY);
        if (supplies.prepare(helper.getLevel(), "minecraft:oak_stairs") != PhysicalBuilderMaterials.Result.WORKING
                || inventory.countItem(Items.OAK_LOG) != 0 || inventory.countItem(Items.OAK_PLANKS) != 2
                || inventory.countItem(Items.OAK_STAIRS) != 4 || inventory.countItem(Items.DIAMOND) != 15 * 64)
            helper.fail("Craft did not preserve its vanilla outputs and leftovers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void builderMaterialsDropOnProfessionChange(GameTestHelper helper) {
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        worker.setJob(JobType.BUILDER);
        worker.getBuilderMaterials().setItem(0, new ItemStack(Items.OAK_LOG, 12));
        worker.getBuilderMaterials().setItem(1, new ItemStack(Items.COBBLESTONE, 9));
        worker.setJob(JobType.FARMER);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, worker.getBoundingBox().inflate(2), e -> true);
        int logs = drops.stream().filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum();
        int stone = drops.stream().filter(e -> e.getItem().is(Items.COBBLESTONE)).mapToInt(e -> e.getItem().getCount()).sum();
        if (!worker.getBuilderMaterials().isEmpty() || logs != 12 || stone != 9)
            helper.fail("Profession change lost or retained physical building materials");
        helper.succeed();
    }
}
