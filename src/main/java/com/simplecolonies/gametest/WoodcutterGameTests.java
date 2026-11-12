package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ColonyVillager;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.WoodcutterJob;
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
public class WoodcutterGameTests {
    private static final BlockPos ROOT = new BlockPos(56, 1, 56);
    private static void makeTree(GameTestHelper helper) {
        for (int x = 35; x <= 75; x++) for (int z = 35; z <= 75; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        // The isolated fixture has an artificial barrier ceiling inside the tree's
        // safety envelope. Remove just this roof section, never the side barriers.
        for (int x = 50; x <= 62; x++) for (int z = 50; z <= 62; z++) for (int y = 5; y <= 8; y++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int y = 0; y < 4; y++) helper.setBlock(ROOT.above(y), Blocks.OAK_LOG);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) helper.setBlock(ROOT.offset(x, 3, z), Blocks.OAK_LEAVES);
    }
    private static Colony colony(GameTestHelper helper) {
        return new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(45, 1, 56)));
    }

    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void resumesPartialTreeAndWearsActualStockAxe(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:oak_sapling", 1);
        // A damaged tool in a real chest must not turn into a new axe on withdrawal.
        BlockPos chest = new BlockPos(43, 1, 56);
        helper.setBlock(chest, Blocks.CHEST);
        Container container = (Container) helper.getBlockEntity(chest);
        ItemStack axe = new ItemStack(Items.STONE_AXE);
        axe.setDamageValue(10);
        container.setItem(0, axe); container.setChanged();
        ColonyVillager[] worker = {helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(48, 1, 56))};
        WoodcutterJob[] job = {new WoodcutterJob(worker[0], colony)};
        boolean[] reloaded = {false};
        for (int tick = 1; tick < 1400; tick++) helper.runAtTickTime(tick, () -> {
            boolean[] before = new boolean[4];
            for (int y = 0; y < 4; y++) before[y] = helper.getBlockState(ROOT.above(y)).is(Blocks.OAK_LOG);
            job[0].tick(helper.getLevel());
            for (int y = 0; y < 4; y++) if (before[y] && !helper.getBlockState(ROOT.above(y)).is(Blocks.OAK_LOG)) {
                BlockPos cut = helper.absolutePos(ROOT.above(y));
                if (worker[0].distanceToSqr(cut.getX() + 0.5, cut.getY() + 0.5, cut.getZ() + 0.5) > 20.25)
                    helper.fail("Woodcutter cut from too far away");
            }
            if (!reloaded[0] && !helper.getBlockState(ROOT.above(3)).is(Blocks.OAK_LOG)) {
                CompoundTag saved = new CompoundTag();
                worker[0].saveWithoutId(saved);
                if (saved.getCompound("woodcutterMemory").getInt("remaining") != 3) helper.fail("Partial tree was not saved");
                var replacement = ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
                if (replacement == null) { helper.fail("Could not recreate worker"); return; }
                worker[0].discard();
                replacement.load(saved);
                helper.getLevel().addFreshEntity(replacement);
                worker[0] = replacement;
                job[0] = new WoodcutterJob(replacement, colony);
                reloaded[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!reloaded[0] || !helper.getBlockState(ROOT).is(Blocks.OAK_SAPLING)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_log") < 4)
                helper.fail("Tree not harvested and replanted: " + job[0].getState() + " at " + worker[0].blockPosition().toShortString()
                        + " reloaded=" + reloaded[0] + " root=" + helper.getBlockState(ROOT)
                        + " logs=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_log")
                        + " memory=" + worker[0].getWoodcutterMemory() + " axe=" + worker[0].getMainHandItem());
            if (!worker[0].getMainHandItem().is(Items.STONE_AXE) || worker[0].getMainHandItem().getDamageValue() != 14)
                helper.fail("Stock axe damage was not preserved and worn: " + worker[0].getMainHandItem().getDamageValue());
            if (!worker[0].getWoodcutterMemory().isEmpty()) helper.fail("Completed tree remained in worker memory");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 900)
    public static void resumedTreeStopsIfConstructionAppears(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        WoodcutterJob[] job = {new WoodcutterJob(worker, colony)};
        boolean[] blocked = {false};
        for (int tick = 1; tick < 900; tick++) helper.runAtTickTime(tick, () -> {
            job[0].tick(helper.getLevel());
            if (!blocked[0] && !helper.getBlockState(ROOT.above(3)).is(Blocks.OAK_LOG)) {
                helper.setBlock(ROOT.offset(2, 0, 0), Blocks.OAK_PLANKS);
                job[0] = new WoodcutterJob(worker, colony);
                blocked[0] = true;
            }
        });
        helper.runAtTickTime(850, () -> {
            if (!blocked[0] || !helper.getBlockState(ROOT).is(Blocks.OAK_LOG)
                    || !helper.getBlockState(ROOT.above(2)).is(Blocks.OAK_LOG)
                    || !helper.getBlockState(ROOT.offset(2, 0, 0)).is(Blocks.OAK_PLANKS))
                helper.fail("Worker continued cutting after construction appeared: blocked=" + blocked[0]
                        + " root=" + helper.getBlockState(ROOT) + " second=" + helper.getBlockState(ROOT.above(2))
                        + " top=" + helper.getBlockState(ROOT.above(3)) + " memory=" + worker.getWoodcutterMemory()
                        + " state=" + job[0].getState() + " at=" + worker.blockPosition().toShortString());
            helper.succeed();
        });
    }
}
