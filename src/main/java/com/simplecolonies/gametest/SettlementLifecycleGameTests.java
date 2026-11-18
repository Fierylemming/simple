package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * The closest automated equivalent of simply playing: found a settlement the way a
 * player does, let several days pass through the real colony clock, and check that
 * the founders stay alive, fed and persistent.
 *
 * <p>Everything here goes through production code paths (natural starter plan,
 * {@code createNaturalColony}, the daily simulation), not fixtures.</p>
 */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class SettlementLifecycleGameTests {

    private static void prepareLand(GameTestHelper helper) {
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 5; y <= 8; y++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Two small oak stands so the woodcutter and the founding supplies have a
        // source without any test-only stock being created.
        for (int[] stand : new int[][]{{36, 40}, {72, 76}}) {
            for (int x = stand[0]; x <= stand[1]; x += 4) for (int z = stand[0]; z <= stand[1]; z += 4) {
                for (int y = 1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.OAK_LOG);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    helper.setBlock(x + dx, 4, z + dz, Blocks.OAK_LEAVES);
            }
        }
    }

    /** Tick the colony, cross a day boundary, tick again — the real day cursor. */
    private static void simulateOneDay(GameTestHelper helper, Colony colony, Runnable after) {
        helper.runAfterDelay(1, () -> colony.tick(helper.getLevel()));
        helper.runAfterDelay(120, () -> colony.tick(helper.getLevel()));
        helper.runAfterDelay(240, () -> colony.tick(helper.getLevel()));
        helper.runAfterDelay(340, () -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 24000));
        helper.runAfterDelay(560, () -> {
            colony.tick(helper.getLevel());
            after.run();
        });
    }

    @SuppressWarnings("unchecked")
    private static void forget(Colony colony) {
        if (colony == null) return;
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            ((Map<Integer, Colony>) field.get(ColonyManager.getInstance())).remove(colony.getId());
        } catch (ReflectiveOperationException ignored) { }
    }

    @GameTest(template = "isolated", timeoutTicks = 2400)
    public static void settlementSurvivesSeveralSimulatedDays(GameTestHelper helper) {
        prepareLand(helper);
        var manager = ColonyManager.getInstance();
        BlockPos anchor = new BlockPos(56, 1, 56);
        var plan = manager.findNaturalStarterPlan(helper.getLevel(), helper.absolutePos(anchor));
        if (plan == null) { helper.fail("No starter plan on flat land"); return; }
        Colony colony = manager.createNaturalColony(helper.getLevel(), CultureType.ROMAN, plan);
        if (colony == null) { helper.fail("Could not found the colony"); return; }
        try {
            int founders = colony.citizenCount();
            int breadAtStart = colony.getWarehouseCount(helper.getLevel(), "minecraft:bread");
            if (founders < 4) helper.fail("Settlement did not start with its founders: " + founders);
            if (breadAtStart < 4) helper.fail("Founding food is missing: " + breadAtStart);

            simulateOneDay(helper, colony, () ->
                simulateOneDay(helper, colony, () ->
                    simulateOneDay(helper, colony, () -> {
                        int days = colony.getSimulatedDays();
                        if (days < 3)
                            helper.fail("Only " + days + " simulated day(s) passed; the daily loop is not running");
                        if (colony.citizenCount() < founders)
                            helper.fail("Founders died: " + colony.citizenCount() + " of " + founders);
                        for (var citizen : colony.getCitizens())
                            if (helper.getLevel().getEntity(citizen.uuid) == null)
                                helper.fail("Citizen " + citizen.uuid + " is registered but no longer exists");
                        if (colony.getState() != com.simplecolonies.colony.ColonyState.ACTIVE)
                            helper.fail("Colony left its active state: " + colony.getState());

                        // A player reload must not lose people, buildings or progress.
                        Colony reloaded = Colony.load(colony.save());
                        if (reloaded.citizenCount() != colony.citizenCount()
                                || reloaded.getBuildings().size() != colony.getBuildings().size()
                                || reloaded.getSimulatedDays() != days
                                || reloaded.getHomeTimber() != colony.getHomeTimber()
                                || reloaded.getState() != colony.getState())
                            helper.fail("Settlement state changed across save/load: citizens "
                                    + reloaded.citizenCount() + "/" + colony.citizenCount()
                                    + " buildings " + reloaded.getBuildings().size() + "/" + colony.getBuildings().size()
                                    + " days " + reloaded.getSimulatedDays() + "/" + days);
                        helper.succeed();
                    })));
        } finally {
            for (var citizen : colony.getCitizens())
                if (helper.getLevel().getEntity(citizen.uuid) instanceof net.minecraft.world.entity.Entity entity)
                    entity.discard();
            forget(colony);
        }
    }
}
