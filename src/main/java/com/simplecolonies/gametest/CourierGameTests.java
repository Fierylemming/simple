package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.CourierJob;
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
public class CourierGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void courierWalksAndCarriesRealPersistentStack(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos source = new BlockPos(2, 1, 8), hub = new BlockPos(14, 1, 8);
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(hub, Blocks.CHEST);
        Container from = (Container) helper.getBlockEntity(source);
        Container to = (Container) helper.getBlockEntity(hub);
        from.setItem(0, new ItemStack(Items.WHEAT, 16));
        from.setChanged();
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        colony.setWarehouseCenter(helper.absolutePos(hub));
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8));
        var job = new CourierJob(villager, colony);
        boolean[] sawCargo = {false};
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!villager.getCourierCargo().isEmpty()) {
                if (!sawCargo[0] && villager.distanceToSqr(helper.absolutePos(source).getX() + 0.5,
                        helper.absolutePos(source).getY() + 0.5, helper.absolutePos(source).getZ() + 0.5) > 9)
                    helper.fail("Courier picked up cargo remotely");
                CompoundTag data = new CompoundTag();
                villager.addAdditionalSaveData(data);
                ItemStack stored = ItemStack.parseOptional(helper.getLevel().registryAccess(), data.getCompound("courierCargo"));
                if (!stored.is(Items.WHEAT) || stored.getCount() != 16) helper.fail("Cargo did not save or came from another test: actual=" + villager.getCourierCargo() + ", saved=" + stored);
                sawCargo[0] = true;
            }
            if (!to.getItem(0).isEmpty() && villager.distanceToSqr(helper.absolutePos(hub).getX() + 0.5,
                    helper.absolutePos(hub).getY() + 0.5, helper.absolutePos(hub).getZ() + 0.5) > 9)
                helper.fail("Courier delivered cargo remotely");
        });
        helper.succeedWhen(() -> {
            if (!sawCargo[0] || !villager.getCourierCargo().isEmpty() || !from.getItem(0).isEmpty()
                    || !to.getItem(0).is(Items.WHEAT) || to.getItem(0).getCount() != 16)
                helper.fail("Cargo not delivered: " + job.getState() + " at " + villager.blockPosition().toShortString());
        });
    }
}
