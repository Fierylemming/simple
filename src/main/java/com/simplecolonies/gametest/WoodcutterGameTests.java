package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ColonyVillager;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.WoodcutterJob;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
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
        for (int x = 50; x <= 62; x++) for (int z = 50; z <= 62; z++) for (int y = 5; y <= 10; y++) {
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

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void harvestsSixBlockBirchWithinActualToolReach(GameTestHelper helper) {
        makeTree(helper);
        for (int y = 0; y < 6; y++) helper.setBlock(ROOT.above(y), Blocks.BIRCH_LOG);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) helper.setBlock(ROOT.offset(x, 5, z), Blocks.BIRCH_LEAVES);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:birch_sapling", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(48, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        int[] cuts = {0};
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            boolean[] before = new boolean[6];
            for (int y = 0; y < 6; y++) before[y] = helper.getBlockState(ROOT.above(y)).is(Blocks.BIRCH_LOG);
            job.tick(helper.getLevel());
            for (int y = 0; y < 6; y++) if (before[y] && !helper.getBlockState(ROOT.above(y)).is(Blocks.BIRCH_LOG)) {
                if (worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(ROOT.above(y)))) > 20.25)
                    helper.fail("Worker cut a tall trunk out of reach");
                cuts[0]++;
            }
        });
        helper.succeedWhen(() -> {
            if (cuts[0] != 6 || !helper.getBlockState(ROOT).is(Blocks.BIRCH_SAPLING)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_log") != 6
                    || worker.getMainHandItem().getDamageValue() != 6)
                helper.fail("Six-log tree not harvested with a real axe: cuts=" + cuts[0] + " job=" + job.getState());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void harvestsTallerStraightTreeFromNaturalSlope(GameTestHelper helper) {
        makeTree(helper);
        for (int x = 50; x <= 62; x++) for (int z = 50; z <= 62; z++) for (int y = 5; y <= 15; y++) {
            BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
            if (helper.getLevel().getBlockState(p).is(Blocks.BARRIER)) helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int y = 0; y < 8; y++) helper.setBlock(ROOT.above(y), Blocks.BIRCH_LOG);
        // Remove the short oak canopy left by makeTree, then create a birch crown.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) {
            helper.setBlock(ROOT.offset(x, 3, z), Blocks.AIR);
            helper.setBlock(ROOT.offset(x, 7, z), Blocks.BIRCH_LEAVES);
        }
        // Two natural dirt steps place a worker close enough to the upper log.
        for (int z = -1; z <= 1; z++) {
            helper.setBlock(ROOT.offset(-2, 0, z), Blocks.DIRT);
            helper.setBlock(ROOT.offset(-1, 0, z), Blocks.DIRT);
            helper.setBlock(ROOT.offset(-1, 1, z), Blocks.DIRT);
        }
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:birch_sapling", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(49, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        int[] cuts = {0};
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> {
            boolean[] before = new boolean[8];
            for (int y = 0; y < 8; y++) before[y] = helper.getBlockState(ROOT.above(y)).is(Blocks.BIRCH_LOG);
            job.tick(helper.getLevel());
            for (int y = 0; y < 8; y++) if (before[y] && !helper.getBlockState(ROOT.above(y)).is(Blocks.BIRCH_LOG)) {
                var target = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(ROOT.above(y)));
                if (worker.getEyePosition().distanceToSqr(target) > 20.25) helper.fail("Worker cut out of reach");
                cuts[0]++;
            }
        });
        helper.succeedWhen(() -> {
            if (cuts[0] != 8 || !helper.getBlockState(ROOT).is(Blocks.BIRCH_SAPLING)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_log") != 8
                    || worker.getMainHandItem().getDamageValue() != 8)
                helper.fail("Tall reachable tree incomplete: cuts=" + cuts[0] + " state=" + job.getState()
                        + " position=" + worker.blockPosition().toShortString());
            if (!helper.getBlockState(ROOT.offset(-1, 1, 0)).is(Blocks.DIRT)) helper.fail("Worker changed the natural slope");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 500)
    public static void leavesTallTreeAloneWithoutReachableElevatedStand(GameTestHelper helper) {
        makeTree(helper);
        for (int x = 50; x <= 62; x++) for (int z = 50; z <= 62; z++) for (int y = 5; y <= 15; y++) {
            BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
            if (helper.getLevel().getBlockState(p).is(Blocks.BARRIER)) helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int y = 0; y < 8; y++) helper.setBlock(ROOT.above(y), Blocks.BIRCH_LOG);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) {
            helper.setBlock(ROOT.offset(x, 3, z), Blocks.AIR);
            helper.setBlock(ROOT.offset(x, 7, z), Blocks.BIRCH_LEAVES);
        }
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(50, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 500; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(400, () -> {
            for (int y = 0; y < 8; y++) if (!helper.getBlockState(ROOT.above(y)).is(Blocks.BIRCH_LOG))
                helper.fail("Cut an unreachable tall tree");
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_log") != 0)
                helper.fail("Unreachable tree created logs");
            helper.succeed();
        });
    }


    private static java.util.List<BlockPos> makeCrookedAcacia(GameTestHelper helper) {
        return makeCrookedTree(helper, Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES);
    }

    private static java.util.List<BlockPos> makeCrookedTree(GameTestHelper helper,
            net.minecraft.world.level.block.Block log, net.minecraft.world.level.block.Block leaves) {
        makeTree(helper);
        for (int x = 50; x <= 62; x++) for (int z = 50; z <= 62; z++) for (int y = 5; y <= 13; y++) {
            BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
            if (helper.getLevel().getBlockState(p).is(Blocks.BARRIER)) helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) helper.setBlock(ROOT.offset(x, 3, z), Blocks.AIR);
        var logs = java.util.List.of(ROOT, ROOT.above(), ROOT.above(2), ROOT.offset(1, 3, 0),
                ROOT.offset(2, 4, 0), ROOT.offset(2, 5, 0));
        helper.setBlock(ROOT.above(3), Blocks.AIR);
        for (var p : logs) helper.setBlock(p, log);
        for (int x = 1; x <= 3; x++) for (int z = -1; z <= 1; z++)
            if (x != 2 || z != 0) helper.setBlock(ROOT.offset(x, 5, z), leaves);
        return logs;
    }

    @GameTest(template = "isolated", timeoutTicks = 2000)
    public static void harvestsReachableCrookedAcaciaAfterReload(GameTestHelper helper) {
        var logs = makeCrookedAcacia(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:acacia_sapling", 1);
        ColonyVillager[] worker = {helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(50, 1, 56))};
        WoodcutterJob[] job = {new WoodcutterJob(worker[0], colony)};
        boolean[] reloaded = {false};
        int[] cuts = {0};
        for (int tick = 1; tick < 2000; tick++) helper.runAtTickTime(tick, () -> {
            boolean[] before = new boolean[logs.size()];
            for (int j = 0; j < logs.size(); j++) before[j] = helper.getBlockState(logs.get(j)).is(Blocks.ACACIA_LOG);
            job[0].tick(helper.getLevel());
            for (int j = 0; j < logs.size(); j++) if (before[j] && !helper.getBlockState(logs.get(j)).is(Blocks.ACACIA_LOG)) {
                if (worker[0].getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(logs.get(j)))) > 20.25)
                    helper.fail("Cut crooked branch beyond tool reach");
                cuts[0]++;
            }
            if (!reloaded[0] && cuts[0] == 1) {
                CompoundTag saved = new CompoundTag(); worker[0].saveWithoutId(saved);
                if (saved.getCompound("woodcutterMemory").getLongArray("branchLogs").length != logs.size())
                    helper.fail("Crooked log positions were not saved");
                // Preview saves made before axes were tracked contained vertical logs only.
                saved.getCompound("woodcutterMemory").remove("branchAxes");
                var replacement = ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
                if (replacement == null) { helper.fail("Could not reload worker"); return; }
                worker[0].discard(); replacement.load(saved); helper.getLevel().addFreshEntity(replacement);
                worker[0] = replacement; job[0] = new WoodcutterJob(replacement, colony); reloaded[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!reloaded[0] || cuts[0] != logs.size() || !helper.getBlockState(ROOT).is(Blocks.ACACIA_SAPLING)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:acacia_log") != logs.size()
                    || worker[0].getMainHandItem().getDamageValue() != logs.size())
                helper.fail("Crooked acacia incomplete: cuts=" + cuts[0] + " state=" + job[0].getState()
                        + " memory=" + worker[0].getWoodcutterMemory());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void harvestsReachableCrookedCherry(GameTestHelper helper) {
        harvestCrookedSpecies(helper, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, Blocks.CHERRY_SAPLING, "cherry");
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void harvestsReachableCrookedOak(GameTestHelper helper) {
        harvestCrookedSpecies(helper, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.OAK_SAPLING, "oak");
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void harvestsReachableCrookedJungle(GameTestHelper helper) {
        harvestCrookedSpecies(helper, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_SAPLING, "jungle");
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void harvestsReachableCrookedSpruce(GameTestHelper helper) {
        harvestCrookedSpecies(helper, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_SAPLING, "spruce");
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void harvestsReachableCrookedBirch(GameTestHelper helper) {
        harvestCrookedSpecies(helper, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, Blocks.BIRCH_SAPLING, "birch");
    }

    private static void harvestCrookedSpecies(GameTestHelper helper, net.minecraft.world.level.block.Block log,
            net.minecraft.world.level.block.Block leaves, net.minecraft.world.level.block.Block sapling, String name) {
        var logs = makeCrookedTree(helper, log, leaves);
        boolean sideways = name.equals("cherry");
        if (sideways) for (var p : java.util.List.of(ROOT.offset(1, 3, 0), ROOT.offset(2, 4, 0)))
            helper.setBlock(p, log.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    net.minecraft.core.Direction.Axis.X));
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:" + name + "_sapling", 1);
        ColonyVillager[] worker = {helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(50, 1, 56))};
        WoodcutterJob[] job = {new WoodcutterJob(worker[0], colony)};
        boolean[] reloaded = {!sideways};
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            job[0].tick(helper.getLevel());
            if (!reloaded[0] && colony.getWarehouseCount(helper.getLevel(), "minecraft:" + name + "_log") == 1) {
                CompoundTag saved = new CompoundTag(); worker[0].saveWithoutId(saved);
                int[] axes = saved.getCompound("woodcutterMemory").getIntArray("branchAxes");
                if (axes.length != logs.size() || java.util.Arrays.stream(axes).filter(a -> a == 1).count() != 2)
                    helper.fail("Sideways branch axes not saved");
                var replacement = ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
                if (replacement == null) { helper.fail("Could not reload worker"); return; }
                worker[0].discard(); replacement.load(saved); helper.getLevel().addFreshEntity(replacement);
                worker[0] = replacement; job[0] = new WoodcutterJob(replacement, colony); reloaded[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!reloaded[0] || !helper.getBlockState(ROOT).is(sapling)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:" + name + "_log") != logs.size()
                    || worker[0].getMainHandItem().getDamageValue() != logs.size())
                helper.fail("Crooked " + name + " incomplete: state=" + job[0].getState()
                        + "; reloaded=" + reloaded[0] + "; root=" + helper.getBlockState(ROOT)
                        + "; stock=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:" + name + "_log")
                        + "; expected=" + logs.size() + "; tool=" + worker[0].getMainHandItem()
                        + "; damage=" + worker[0].getMainHandItem().getDamageValue()
                        + "; position=" + worker[0].blockPosition().toShortString()
                        + "; memory=" + worker[0].getWoodcutterMemory());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 700)
    public static void leavesUnreachableCrookedAcaciaIntact(GameTestHelper helper) {
        var baseLogs = makeCrookedAcacia(helper);
        for (int x = 1; x <= 3; x++) for (int z = -1; z <= 1; z++) {
            if (x != 2 || z != 0) {
                helper.setBlock(ROOT.offset(x, 5, z), Blocks.AIR);
                helper.setBlock(ROOT.offset(x, 8, z), Blocks.ACACIA_LEAVES);
            }
        }
        for (int y = 6; y <= 8; y++) helper.setBlock(ROOT.offset(2, y, 0), Blocks.ACACIA_LOG);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(50, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 700; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(650, () -> {
            for (var p : baseLogs) if (!helper.getBlockState(p).is(Blocks.ACACIA_LOG))
                helper.fail("Started cutting a crooked tree with unreachable upper logs");
            for (int y = 6; y <= 8; y++) if (!helper.getBlockState(ROOT.offset(2, y, 0)).is(Blocks.ACACIA_LOG))
                helper.fail("Removed unreachable upper log");
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:acacia_log") != 0)
                helper.fail("Collected logs from an unreachable tree");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void crookedAcaciaStopsIfConstructionAppears(GameTestHelper helper) {
        makeCrookedAcacia(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(50, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        boolean[] blocked = {false};
        long[] blockedAt = {0};
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!blocked[0] && colony.getWarehouseCount(helper.getLevel(), "minecraft:acacia_log") == 1) {
                helper.setBlock(ROOT.offset(-4, 0, 0), Blocks.OAK_PLANKS); blocked[0] = true;
                blockedAt[0] = helper.getLevel().getGameTime();
            }
        });
        helper.succeedWhen(() -> {
            if (!blocked[0] || helper.getLevel().getGameTime() - blockedAt[0] < 200
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:acacia_log") != 1
                    || !helper.getBlockState(ROOT).is(Blocks.ACACIA_LOG)
                    || !helper.getBlockState(ROOT.offset(-4, 0, 0)).is(Blocks.OAK_PLANKS))
                helper.fail("Crooked-tree safety: blocked=" + blocked[0] + " state=" + job.getState()
                        + " stock=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:acacia_log")
                        + " worker=" + worker.blockPosition() + " root=" + helper.getBlockState(ROOT)
                        + " memory=" + worker.getWoodcutterMemory());
        });
    }

    private static void makeSquareDarkOak(GameTestHelper helper) {
        makeTree(helper);
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) {
            if (x < 0 || x > 1 || z < 0 || z > 1) helper.setBlock(ROOT.offset(x, 3, z), Blocks.DARK_OAK_LEAVES);
        }
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int y = 0; y < 4; y++)
            helper.setBlock(ROOT.offset(x, y, z), Blocks.DARK_OAK_LOG);
    }

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void harvestsSquareDarkOakAndReplantsFourRealSaplings(GameTestHelper helper) {
        makeSquareDarkOak(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:dark_oak_sapling", 4);
        ColonyVillager[] worker = {helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(50, 1, 56))};
        WoodcutterJob[] job = {new WoodcutterJob(worker[0], colony)};
        boolean[] reloaded = {false};
        int[] cuts = {0};
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> {
            boolean[] before = new boolean[16];
            for (int y = 0; y < 4; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                before[y * 4 + x * 2 + z] = helper.getBlockState(ROOT.offset(x, y, z)).is(Blocks.DARK_OAK_LOG);
            job[0].tick(helper.getLevel());
            for (int y = 0; y < 4; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                var p = ROOT.offset(x, y, z);
                if (before[y * 4 + x * 2 + z] && !helper.getBlockState(p).is(Blocks.DARK_OAK_LOG)) {
                    if (worker[0].getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(p))) > 20.25)
                        helper.fail("Square trunk cut out of reach");
                    cuts[0]++;
                }
            }
            if (!reloaded[0] && cuts[0] >= 2) {
                CompoundTag saved = new CompoundTag();
                worker[0].saveWithoutId(saved);
                if (!saved.getCompound("woodcutterMemory").getBoolean("square")) helper.fail("Square progress was not saved");
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
            if (!reloaded[0] || cuts[0] != 16 || colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_log") != 16
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_sapling") != 0
                    || worker[0].getMainHandItem().getDamageValue() != 16)
                helper.fail("Square tree incomplete: cuts=" + cuts[0] + " job=" + job[0].getState() + " memory=" + worker[0].getWoodcutterMemory());
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                if (!helper.getBlockState(ROOT.offset(x, 0, z)).is(Blocks.DARK_OAK_SAPLING)) helper.fail("Missing planted sapling");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void harvestsCompactSquareSpruce(GameTestHelper helper) {
        harvestCompactSquare(helper, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_SAPLING, "spruce");
    }

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void harvestsCompactSquareJungle(GameTestHelper helper) {
        harvestCompactSquare(helper, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_SAPLING, "jungle");
    }

    private static void harvestCompactSquare(GameTestHelper helper, net.minecraft.world.level.block.Block log,
            net.minecraft.world.level.block.Block leaves, net.minecraft.world.level.block.Block sapling, String species) {
        makeSquareDarkOak(helper);
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++)
            if (x < 0 || x > 1 || z < 0 || z > 1) helper.setBlock(ROOT.offset(x, 3, z), leaves);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int y = 0; y < 4; y++)
            helper.setBlock(ROOT.offset(x, y, z), log);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:" + species + "_sapling", 4);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(52, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:" + species + "_log") != 16
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:" + species + "_sapling") != 0
                    || worker.getMainHandItem().getDamageValue() != 16)
                helper.fail("Square " + species + " incomplete: " + job.getState());
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                if (!helper.getBlockState(ROOT.offset(x, 0, z)).is(sapling)) helper.fail("Square " + species + " not replanted");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 2200)
    public static void squareTreeDoesNotWasteThreeSaplings(GameTestHelper helper) {
        makeSquareDarkOak(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        colony.depositToWarehouse("minecraft:dark_oak_sapling", 3);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(52, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_log") != 16
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_sapling") != 3)
                helper.fail("Square tree was not harvested or saplings were wasted: " + job.getState());
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                if (!helper.getBlockState(ROOT.offset(x, 0, z)).isAir()) helper.fail("Incomplete dark oak sapling square planted");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 500)
    public static void leavesTooTallSquareTreeUntouched(GameTestHelper helper) {
        makeSquareDarkOak(helper);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int y = 4; y < 7; y++)
            helper.setBlock(ROOT.offset(x, y, z), Blocks.DARK_OAK_LOG);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(53, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 500; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(400, () -> {
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int y = 0; y < 7; y++)
                if (!helper.getBlockState(ROOT.offset(x, y, z)).is(Blocks.DARK_OAK_LOG))
                    helper.fail("Started an unsupported tall square tree");
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_log") != 0) helper.fail("Created logs from tall tree");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 800)
    public static void squareTreeStopsWhenNewConstructionAppears(GameTestHelper helper) {
        makeSquareDarkOak(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(53, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        boolean[] blocked = {false};
        for (int tick = 1; tick < 800; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!blocked[0] && colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_log") > 0) {
                helper.setBlock(ROOT.offset(3, 0, 0), Blocks.OAK_PLANKS);
                blocked[0] = true;
            }
        });
        helper.runAtTickTime(750, () -> {
            if (!blocked[0] || !helper.getBlockState(ROOT).is(Blocks.DARK_OAK_LOG)
                    || !helper.getBlockState(ROOT.offset(3, 0, 0)).is(Blocks.OAK_PLANKS)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:dark_oak_log") != 1)
                helper.fail("Square-tree safety check did not stop after construction appeared");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void harvestsAndReplantsShortSpruce(GameTestHelper helper) {
        harvestSpecies(helper, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_SAPLING, "spruce");
    }

    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void harvestsAndReplantsShortBirch(GameTestHelper helper) {
        harvestSpecies(helper, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, Blocks.BIRCH_SAPLING, "birch");
    }

    private static void harvestSpecies(GameTestHelper helper, net.minecraft.world.level.block.Block log,
            net.minecraft.world.level.block.Block leaves, net.minecraft.world.level.block.Block sapling, String name) {
        makeTree(helper);
        for (int y = 0; y < 4; y++) helper.setBlock(ROOT.above(y), log);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) helper.setBlock(ROOT.offset(x, 3, z), leaves);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:" + name + "_sapling", 1);
        colony.depositToWarehouse("minecraft:stone_axe", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(48, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 1400; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (!helper.getBlockState(ROOT).is(sapling)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:" + name + "_log") < 4)
                helper.fail("Did not harvest and replant " + name + ": " + job.getState());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void resumesPartialTreeAndWearsActualStockAxe(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:oak_sapling", 1);
        // A damaged tool in a real chest must not turn into a new axe on withdrawal.
        BlockPos chest = new BlockPos(43, 1, 56);
        helper.setBlock(chest, Blocks.CHEST);
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest));
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
                if (worker[0].getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(cut)) > 20.25)
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

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void craftsAxeAtHallTableBeforeHarvesting(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.TOWN_HALL,
                colony.getCenter(), CultureType.ROMAN, true));
        BlockPos table = new BlockPos(47, 1, 56);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        colony.depositToWarehouse("minecraft:cobblestone", 3);
        colony.depositToWarehouse("minecraft:stick", 2);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(48, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        boolean[] craftedAtTable = {false};
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            boolean hadAxe = worker.getMainHandItem().is(Items.STONE_AXE);
            job.tick(helper.getLevel());
            if (!hadAxe && worker.getMainHandItem().is(Items.STONE_AXE)) {
                BlockPos p = helper.absolutePos(table);
                if (worker.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > 9)
                    helper.fail("Woodcutter crafted an axe away from its table");
                craftedAtTable[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!craftedAtTable[0] || helper.getBlockState(ROOT).is(Blocks.OAK_LOG)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 0)
                helper.fail("Axe crafting or harvesting failed: " + job.getState() + " at " + worker.blockPosition());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void craftsReplacementAxeUsingBirchTimber(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.TOWN_HALL,
                colony.getCenter(), CultureType.ROMAN, true));
        BlockPos table = new BlockPos(47, 1, 56);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        colony.depositToWarehouse("minecraft:cobblestone", 3);
        colony.depositToWarehouse("minecraft:birch_log", 1);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(48, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        boolean[] craftedAtTable = {false};
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            boolean hadAxe = worker.getMainHandItem().is(Items.STONE_AXE);
            job.tick(helper.getLevel());
            if (!hadAxe && worker.getMainHandItem().is(Items.STONE_AXE)) {
                BlockPos p = helper.absolutePos(table);
                if (worker.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > 9)
                    helper.fail("Crafted the axe away from a real table");
                craftedAtTable[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!craftedAtTable[0] || helper.getBlockState(ROOT).is(Blocks.OAK_LOG)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_log") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:birch_planks") != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 0)
                helper.fail("Axe crafting did not consume and retain the correct timber: " + job.getState());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1000)
    public static void waitsForAxeBeforeHarvesting(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        var job = new WoodcutterJob(worker, colony);
        for (int tick = 1; tick < 1000; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(200, () -> {
            if (!helper.getBlockState(ROOT.above(3)).is(Blocks.OAK_LOG) || job.getState() != com.simplecolonies.villager.job.AbstractJob.JobState.NEEDS_TOOL)
                helper.fail("Woodcutter harvested without an axe or failed to report a missing tool");
            colony.depositToWarehouse("minecraft:stone_axe", 1);
        });
        helper.succeedWhen(() -> {
            if (helper.getBlockState(ROOT).is(Blocks.OAK_LOG) || !worker.getMainHandItem().is(Items.STONE_AXE))
                helper.fail("Woodcutter did not resume with a supplied axe: " + job.getState());
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 900)
    public static void resumedTreeStopsIfConstructionAppears(GameTestHelper helper) {
        makeTree(helper);
        Colony colony = colony(helper);
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        worker.setJob(JobType.LUMBERJACK);
        ItemStack axe = new ItemStack(Items.STONE_AXE); axe.setDamageValue(8);
        worker.setItemSlot(EquipmentSlot.MAINHAND, axe);
        worker.setJob(JobType.MINER);
        worker.setJob(JobType.LUMBERJACK);
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
            if (!worker.getMainHandItem().is(Items.STONE_AXE) || worker.getMainHandItem().getDamageValue() != 9)
                helper.fail("Worker did not reuse its stowed axe when returning to the job");
            helper.succeed();
        });
    }
}
