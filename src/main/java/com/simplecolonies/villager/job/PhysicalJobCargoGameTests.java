package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class PhysicalJobCargoGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullAndUnsharedContainersRetainRealCargo(GameTestHelper helper) {
        BlockPos chest = new BlockPos(7, 1, 8), origin = helper.absolutePos(new BlockPos(8, 1, 8));
        helper.setBlock(chest, Blocks.CHEST);
        var container = (Container) helper.getBlockEntity(chest);
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        Colony colony = new Colony(0, CultureType.ROMAN, origin);
        BlockPos absoluteChest = helper.absolutePos(chest);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absoluteChest)) helper.fail("Could not share chest");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8)); worker.setNoAi(true);
        var cargo = new PhysicalJobCargo(worker, colony);
        var beef = new ItemEntity(helper.getLevel(), origin.getX(), origin.getY(), origin.getZ(), new ItemStack(Items.BEEF, 3));
        ItemStack named = new ItemStack(Items.LEATHER, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special hide"));
        var leather = new ItemEntity(helper.getLevel(), origin.getX(), origin.getY(), origin.getZ(), named.copy());
        var excess = new ItemEntity(helper.getLevel(), origin.getX(), origin.getY(), origin.getZ(), new ItemStack(Items.PORKCHOP, 1));
        helper.getLevel().addFreshEntity(beef); helper.getLevel().addFreshEntity(leather); helper.getLevel().addFreshEntity(excess);
        cargo.collect(beef); cargo.collect(leather); cargo.collect(excess);
        if (!beef.isRemoved() || !leather.isRemoved() || excess.isRemoved() || excess.getItem().getCount() != 1
                || cargo.deliver(helper.getLevel()) != PhysicalJobCargo.Delivery.WAITING)
            helper.fail("Cargo overflow or full storage lost actual items");
        container.setItem(0, ItemStack.EMPTY);
        if (cargo.deliver(helper.getLevel()) != PhysicalJobCargo.Delivery.DELIVERED
                || !container.getItem(0).is(Items.BEEF) || container.getItem(0).getCount() != 3
                || !ItemStack.isSameItemSameComponents(worker.getCourierCargoExtra(), named))
            helper.fail("Partial delivery lost remaining customized cargo");
        if (!colony.getWarehouseManager().unregisterStorage(helper.getLevel(), absoluteChest)) helper.fail("Could not unshare chest");
        container.setItem(1, ItemStack.EMPTY);
        if (cargo.deliver(helper.getLevel()) != PhysicalJobCargo.Delivery.WAITING || worker.getCourierCargoExtra().getCount() != 2)
            helper.fail("Unshared storage accepted cargo");
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absoluteChest)
                || cargo.deliver(helper.getLevel()) != PhysicalJobCargo.Delivery.DELIVERED
                || !ItemStack.isSameItemSameComponents(container.getItem(1), named)
                || container.getItem(1).getCount() != 2 || cargo.hasCargo())
            helper.fail("Could not deliver retained customized cargo to re-shared chest");
        if (excess.isRemoved() || !excess.getItem().is(Items.PORKCHOP)) helper.fail("Over-capacity world drop disappeared");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void builderCanCraftInACompletedWingDuringAnotherUpgrade(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 1, 8));
        var wing = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, origin);
        var house = new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.HOUSE, origin, CultureType.ROMAN);
        house.setState(com.simplecolonies.building.BuildingState.COMPLETE);
        if (!house.beginWorksiteWing(wing)) helper.fail("Could not queue first wing");
        while (house.getNextPlacement() != null) house.markPlaced(house.getNextPlacement());
        house.finishConstruction();
        if (!house.beginWorksiteWing(origin.west(7))) helper.fail("Could not queue second wing");
        colony.registerPlayerBuilding(house);
        helper.setBlock(new BlockPos(8, 1, 8), Blocks.CRAFTING_TABLE);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 9)); worker.setNoAi(true);
        worker.getBuilderMaterials().setItem(0, new ItemStack(Items.OAK_LOG, 1));
        var materials = new PhysicalBuilderMaterials(worker, colony);
        if (materials.prepare(helper.getLevel(), "minecraft:oak_planks") != PhysicalBuilderMaterials.Result.WORKING
                || worker.getBuilderMaterials().countItem(Items.OAK_PLANKS) != 4
                || worker.getBuilderMaterials().countItem(Items.OAK_LOG) != 0)
            helper.fail("Builder could not use completed wing table during another upgrade");
        helper.succeed();
    }

}
