package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.colony.ColonyState;
import com.simplecolonies.config.ColonyConfig;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Starvation is the one failure state a player reaches by doing nothing, and
 * nothing used to prove it. This empties every container and the legacy
 * stockpile of a fresh settlement, crosses real day boundaries through
 * {@link Colony#tick}, and checks the state machine reacts exactly once the
 * configured grace period is over.
 */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class StarvationGameTests {

    private static void prepareLand(GameTestHelper helper) {
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            for (int y = 5; y <= 8; y++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Keep the founders on the platform: without a boundary they wander off
        // the isolated test structure and vanish, which is a test artefact, not
        // a colony bug.
        for (int x = 31; x <= 81; x++) for (int z = 31; z <= 81; z++) {
            boolean edge = x == 31 || x == 81 || z == 31 || z == 81;
            if (!edge) continue;
            for (int y = 1; y <= 4; y++) helper.setBlock(x, y, z, Blocks.BARRIER);
        }
    }

    /** Strip every item from every container near the colony, and the virtual stock. */
    private static void clearAllStock(GameTestHelper helper, Colony colony) {
        BlockPos center = colony.getCenter();
        for (int dx = -8; dx <= 8; dx++) for (int dy = -2; dy <= 4; dy++) for (int dz = -8; dz <= 8; dz++) {
            BlockPos pos = center.offset(dx, dy, dz);
            if (!helper.getLevel().isLoaded(pos)) continue;
            if (helper.getLevel().getBlockEntity(pos) instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++)
                    container.setItem(slot, ItemStack.EMPTY);
                container.setChanged();
            }
        }
        try {
            var field = Colony.class.getDeclaredField("warehouse");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Integer> stock = (Map<String, Integer>) field.get(colony);
            stock.clear();
        } catch (ReflectiveOperationException ignored) { }
    }

    private static void starveDay(GameTestHelper helper, Colony colony, int remaining, Runnable done) {
        if (remaining <= 0) { done.run(); return; }
        clearAllStock(helper, colony);
        colony.tick(helper.getLevel());
        helper.runAfterDelay(150, () -> {
            helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 24000L);
            helper.runAfterDelay(20, () -> {
                clearAllStock(helper, colony);
                colony.tick(helper.getLevel());
                starveDay(helper, colony, remaining - 1, done);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static void forget(Colony colony) {
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            ((Map<Integer, Colony>) field.get(ColonyManager.getInstance())).remove(colony.getId());
        } catch (ReflectiveOperationException ignored) { }
    }

    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void emptySettlementReachesStarvationAfterTheGracePeriod(GameTestHelper helper) {
        prepareLand(helper);
        var manager = ColonyManager.getInstance();
        var plan = manager.findNaturalStarterPlan(helper.getLevel(), helper.absolutePos(new BlockPos(56, 1, 56)));
        if (plan == null) { helper.fail("No starter plan on flat land"); return; }
        Colony colony = manager.createNaturalColony(helper.getLevel(), CultureType.ROMAN, plan);
        if (colony == null) { helper.fail("Could not found the colony"); return; }
        try {
            clearAllStock(helper, colony);
            int food = colony.getFoodCount(helper.getLevel());
            if (food != 0) { helper.fail("Could not empty the settlement's storage: " + food + " food left"); return; }

            int grace = Math.max(1, ColonyConfig.DAYS_WITHOUT_FOOD_BEFORE_STARVATION.get());
            starveDay(helper, colony, grace, () -> {
                try {
                    if (colony.getFoodCount(helper.getLevel()) != 0)
                        helper.fail("Food appeared from nothing in an emptied settlement");
                    if (!ColonyConfig.VILLAGERS_DIE_WITHOUT_FOOD.get()) {
                        // With deaths disabled the colony only reports hunger; the
                        // important part is that it never invents food or recovers.
                        if (colony.getState() == ColonyState.STARVING)
                            helper.fail("Colony starves although starvation deaths are disabled");
                    } else if (colony.getState() != ColonyState.STARVING) {
                        helper.fail("Still " + colony.getState().name() + " after " + grace
                                + " day(s) without food (grace period is " + grace + ")");
                    }
                    // The state must survive a reload, or a starving colony would
                    // silently look healthy after the player restarts.
                    Colony reloaded = Colony.load(colony.save());
                    if (reloaded.getState() != colony.getState())
                        helper.fail("Starvation state lost across save/load: "
                                + reloaded.getState().name() + " vs " + colony.getState().name());
                } finally {
                    cleanUp(helper, colony);
                }
                helper.succeed();
            });
        } catch (RuntimeException e) {
            cleanUp(helper, colony);
            throw e;
        }
    }

    private static void cleanUp(GameTestHelper helper, Colony colony) {
        for (var citizen : colony.getCitizens())
            if (helper.getLevel().getEntity(citizen.uuid) instanceof net.minecraft.world.entity.Entity entity)
                entity.discard();
        forget(colony);
    }
}
