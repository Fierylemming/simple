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
    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void courierWalksAndCarriesRealPersistentStack(GameTestHelper helper) {
        for (int x = 45; x <= 67; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos source = new BlockPos(50, 1, 56), hub = new BlockPos(62, 1, 56);
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(hub, Blocks.CHEST);
        Container from = (Container) helper.getBlockEntity(source);
        Container to = (Container) helper.getBlockEntity(hub);
        from.setItem(0, new ItemStack(Items.WHEAT, 16));
        from.setChanged();
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(source));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(hub));
        colony.setWarehouseCenter(helper.absolutePos(hub));
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(55, 1, 56));
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
    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void researchedCourierPreservesBothStacksAcrossReloadAndFullHub(GameTestHelper helper) {
        for (int x = 45; x <= 67; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos source = new BlockPos(50, 1, 56), hub = new BlockPos(62, 1, 56);
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(hub, Blocks.CHEST);
        Container from = (Container) helper.getBlockEntity(source);
        Container to = (Container) helper.getBlockEntity(hub);
        from.setItem(0, new ItemStack(Items.WHEAT, 16));
        ItemStack keepsake = new ItemStack(Items.BREAD, 3);
        keepsake.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keepsake"));
        from.setItem(1, keepsake.copy());
        for (int i = 0; i < to.getContainerSize(); i++) to.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        to.setItem(0, new ItemStack(Items.WHEAT, 48));
        from.setChanged(); to.setChanged();
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(source));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(hub));
        colony.setWarehouseCenter(helper.absolutePos(hub));
        var research = colony.getResearch().save();
        var unlocked = research.getList("unlocked", net.minecraft.nbt.Tag.TAG_STRING);
        unlocked.add(net.minecraft.nbt.StringTag.valueOf("WAREHOUSE_CAPACITY"));
        research.put("unlocked", unlocked);
        colony.getResearch().load(research);
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(55, 1, 56));
        var job = new CourierJob(villager, colony);
        boolean[] pickedUp = {false}, sawFullHub = {false};
        for (int tick = 1; tick < 1400; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!pickedUp[0] && !villager.getCourierCargoExtra().isEmpty()) {
                if (villager.distanceToSqr(helper.absolutePos(source).getX() + 0.5,
                        helper.absolutePos(source).getY() + 0.5, helper.absolutePos(source).getZ() + 0.5) > 9)
                    helper.fail("Courier collected second stack remotely");
                if (!villager.getCourierCargo().is(Items.WHEAT) || villager.getCourierCargo().getCount() != 16
                        || !ItemStack.isSameItemSameComponents(villager.getCourierCargoExtra(), keepsake)
                        || villager.getCourierCargoExtra().getCount() != 3) helper.fail("Courier did not take two real stacks");
                CompoundTag data = new CompoundTag();
                villager.addAdditionalSaveData(data);
                var restored = ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
                if (restored == null) { helper.fail("Could not restore courier"); return; }
                restored.readAdditionalSaveData(data);
                if (!restored.getCourierCargo().is(Items.WHEAT) || restored.getCourierCargo().getCount() != 16
                        || !ItemStack.isSameItemSameComponents(restored.getCourierCargoExtra(), keepsake)
                        || restored.getCourierCargoExtra().getCount() != 3) helper.fail("Both stacks did not survive reload");
                pickedUp[0] = true;
            }
            if (pickedUp[0] && !sawFullHub[0] && to.getItem(0).getCount() == 64
                    && villager.getCourierCargo().isEmpty() && !villager.getCourierCargoExtra().isEmpty()) {
                if (villager.distanceToSqr(helper.absolutePos(hub).getX() + 0.5,
                        helper.absolutePos(hub).getY() + 0.5, helper.absolutePos(hub).getZ() + 0.5) > 9)
                    helper.fail("Courier delivered first stack remotely");
                sawFullHub[0] = true;
                to.setItem(1, ItemStack.EMPTY); to.setChanged();
            }
        });
        helper.succeedWhen(() -> {
            if (!pickedUp[0] || !sawFullHub[0] || !from.getItem(0).isEmpty() || !from.getItem(1).isEmpty()
                    || !villager.getCourierCargo().isEmpty() || !villager.getCourierCargoExtra().isEmpty()
                    || !ItemStack.isSameItemSameComponents(to.getItem(1), keepsake) || to.getItem(1).getCount() != 3)
                helper.fail("Second stack was not retained and delivered: " + job.getState());
        });
    }

}
