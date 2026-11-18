package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.colony.ColonyState;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ColonyVillager;
import com.simplecolonies.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
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

    /** Founder entities as objects, so a vanished one can report why it went. */
    private static final List<Entity> FOUNDERS = new ArrayList<>();

    private static void prepareLand(GameTestHelper helper) {
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 5; y <= 8; y++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Two small oak stands so a woodcutter and the founding supplies have a
        // source without any test-only stock being created.
        for (int[] stand : new int[][]{{36, 40}, {72, 76}}) {
            for (int x = stand[0]; x <= stand[1]; x += 4) for (int z = stand[0]; z <= stand[1]; z += 4) {
                for (int y = 1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.OAK_LOG);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    helper.setBlock(x + dx, 4, z + dz, Blocks.OAK_LEAVES);
            }
        }
        // Keep the founders on the platform: without a boundary they would walk
        // off the isolated structure.
        for (int x = 31; x <= 81; x++) for (int z = 31; z <= 81; z++) {
            if (x != 31 && x != 81 && z != 31 && z != 81) continue;
            for (int y = 1; y <= 4; y++) helper.setBlock(x, y, z, Blocks.BARRIER);
        }
    }

    @GameTest(template = "isolated", timeoutTicks = 2400)
    public static void settlementSurvivesSeveralSimulatedDays(GameTestHelper helper) {
        prepareLand(helper);
        // A villager the test spawns itself, to tell a colony problem apart from a
        // GameTest world problem.
        ColonyVillager control = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(40, 1, 40));
        control.setPersistenceRequired();
        // Founding on the first tick of the test is not representative: the test
        // world tears down entities left over from the previous test on its first
        // tick. Found a moment later, like a player would.
        helper.runAfterDelay(3, () -> foundAndSimulate(helper, control));
    }

    private static void foundAndSimulate(GameTestHelper helper, ColonyVillager control) {
        var manager = ColonyManager.getInstance();
        var plan = manager.findNaturalStarterPlan(helper.getLevel(), helper.absolutePos(new BlockPos(56, 1, 56)));
        if (plan == null) { helper.fail("No starter plan on flat land"); return; }
        Colony colony = manager.createNaturalColony(helper.getLevel(), CultureType.ROMAN, plan);
        if (colony == null) { helper.fail("Could not found the colony"); return; }
        try {
            FOUNDERS.clear();
            for (var citizen : colony.getCitizens()) {
                var entity = helper.getLevel().getEntity(citizen.uuid);
                FOUNDERS.add(entity);
                if (entity instanceof Mob mob) mob.setPersistenceRequired();
            }
            int founders = colony.citizenCount();
            int breadAtStart = colony.getWarehouseCount(helper.getLevel(), "minecraft:bread");
            if (founders < 4) helper.fail("Settlement did not start with its founders: " + founders);
            if (breadAtStart < 4) helper.fail("Founding food is missing: " + breadAtStart);

            List<String> log = new ArrayList<>();
            simulateOneDay(helper, colony, control, 1, log, () ->
                simulateOneDay(helper, colony, control, 2, log, () ->
                    simulateOneDay(helper, colony, control, 3, log, () -> {
                        try {
                            int days = colony.getSimulatedDays();
                            if (days < 3)
                                helper.fail("Only " + days + " simulated day(s) passed; the daily loop is not "
                                        + "running. Timeline: " + String.join(" ", log));
                            if (colony.citizenCount() < founders)
                                helper.fail("Founders died: " + colony.citizenCount() + " of " + founders
                                        + ". Timeline: " + String.join(" ", log));
                            var missing = colony.getCitizens().stream()
                                    .filter(citizen -> helper.getLevel().getEntity(citizen.uuid) == null)
                                    .toList();
                            if (!missing.isEmpty()) {
                                var nearby = helper.getLevel().getEntitiesOfClass(ColonyVillager.class,
                                        new AABB(colony.getCenter()).inflate(72));
                                helper.fail("Citizens registered but not in the world: " + missing.size() + "/"
                                        + colony.citizenCount()
                                        + " (jobs " + missing.stream().map(c -> c.job.name()).toList() + ")"
                                        + "; nearby villagers=" + nearby.size()
                                        + "; control=" + status(control)
                                        + "; state=" + colony.getState().name()
                                        + "; timeline=" + String.join(" ", log));
                            }
                            if (colony.getState() != ColonyState.ACTIVE)
                                helper.fail("Colony left its active state: " + colony.getState()
                                        + ". Timeline: " + String.join(" ", log));

                            // A player reload must not lose people, buildings or progress.
                            Colony reloaded = Colony.load(colony.save());
                            if (reloaded.citizenCount() != colony.citizenCount()
                                    || reloaded.getBuildings().size() != colony.getBuildings().size()
                                    || reloaded.getSimulatedDays() != days
                                    || reloaded.getHomeTimber() != colony.getHomeTimber()
                                    || reloaded.getState() != colony.getState())
                                helper.fail("Settlement state changed across save/load: citizens "
                                        + reloaded.citizenCount() + "/" + colony.citizenCount()
                                        + " buildings " + reloaded.getBuildings().size() + "/"
                                        + colony.getBuildings().size()
                                        + " days " + reloaded.getSimulatedDays() + "/" + days);
                        } finally {
                            cleanUp(helper, colony);
                        }
                        helper.succeed();
                    })));
        } catch (RuntimeException e) {
            cleanUp(helper, colony);
            throw e;
        }
    }

    /** Tick the colony, cross a day boundary, tick again — the real day cursor. */
    private static void simulateOneDay(GameTestHelper helper, Colony colony, ColonyVillager control,
                                       int day, List<String> log, Runnable after) {
        sample(helper, colony, control, "d" + day + "-start", log);
        helper.runAfterDelay(1, () -> { colony.tick(helper.getLevel()); sample(helper, colony, control, "d" + day + "-t1", log); });
        helper.runAfterDelay(120, () -> { colony.tick(helper.getLevel()); sample(helper, colony, control, "d" + day + "-t2", log); });
        helper.runAfterDelay(240, () -> { colony.tick(helper.getLevel()); sample(helper, colony, control, "d" + day + "-t3", log); });
        helper.runAfterDelay(340, () -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 24000L));
        helper.runAfterDelay(560, () -> {
            colony.tick(helper.getLevel());
            sample(helper, colony, control, "d" + day + "-next", log);
            after.run();
        });
    }

    private static String status(Entity entity) {
        if (entity == null) return "null";
        if (entity.isRemoved()) return "removed(" + entity.getRemovalReason() + ")";
        return entity.isAlive() ? "alive" : "dead";
    }

    private static void sample(GameTestHelper helper, Colony colony, ColonyVillager control,
                               String label, List<String> log) {
        long live = colony.getCitizens().stream()
                .filter(citizen -> helper.getLevel().getEntity(citizen.uuid) != null)
                .count();
        StringBuilder founders = new StringBuilder();
        for (var entity : FOUNDERS) founders.append('.').append(status(entity));
        log.add(label + "[live=" + live + "/" + colony.citizenCount()
                + ",days=" + colony.getSimulatedDays() + ",founders=" + founders
                + ",control=" + status(control) + "]");
    }

    private static void cleanUp(GameTestHelper helper, Colony colony) {
        for (var citizen : colony.getCitizens())
            if (helper.getLevel().getEntity(citizen.uuid) instanceof Entity entity) entity.discard();
        forget(colony);
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
}
