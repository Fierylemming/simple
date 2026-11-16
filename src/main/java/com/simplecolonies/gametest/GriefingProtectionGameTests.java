package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Creeper-proof colonies, while player-caused destruction and sieges stay possible. */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class GriefingProtectionGameTests {
    private static final int COLONY_ID = -1200000500;

    private static Colony install(GameTestHelper helper, BlockPos center, BuildingType type) {
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var colonies = (Map<Integer, Colony>) field.get(ColonyManager.getInstance());
            var colony = new Colony(COLONY_ID, CultureType.ROMAN, center);
            colonies.put(COLONY_ID, colony);
            if (type != null) colony.registerPlayerBuilding(new Building(type, center, CultureType.ROMAN, true));
            return colony;
        } catch (ReflectiveOperationException e) {
            helper.fail("Cannot install test colony: " + e);
            return null;
        }
    }

    private static void uninstall() {
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var colonies = (Map<Integer, Colony>) field.get(ColonyManager.getInstance());
            colonies.remove(COLONY_ID);
        } catch (ReflectiveOperationException ignored) { }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creeperExplosionCannotBreakAColonyBuilding(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = install(helper, center, BuildingType.TOWN_HALL);
        if (colony == null) return;
        try {
            helper.setBlock(new BlockPos(8, 1, 8), Blocks.STONE_BRICKS);
            var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(8, 1, 8));
            creeper.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 1.5);
            helper.getLevel().explode(creeper, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 1.5,
                    3.0f, Level.ExplosionInteraction.MOB);
            if (!helper.getBlockState(new BlockPos(8, 1, 8)).is(Blocks.STONE_BRICKS))
                helper.fail("A creeper broke a colony building");
            helper.succeed();
        } finally { uninstall(); }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void playerTntStillBreaksColonyBuildings(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = install(helper, center, BuildingType.TOWN_HALL);
        if (colony == null) return;
        helper.setBlock(new BlockPos(8, 1, 8), Blocks.STONE_BRICKS);
        var tnt = new PrimedTnt(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5, null);
        tnt.setFuse(1);
        helper.getLevel().addFreshEntity(tnt);
        helper.runAfterDelay(60, () -> {
            try {
                if (helper.getBlockState(new BlockPos(8, 1, 8)).is(Blocks.STONE_BRICKS))
                    helper.fail("Player TNT no longer breaks colony blocks; sieges must stay possible");
                helper.succeed();
            } finally { uninstall(); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pistonCannotMoveAColonyBlock(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = install(helper, center, BuildingType.TOWN_HALL);
        if (colony == null) return;
        try {
            var target = helper.absolutePos(new BlockPos(8, 1, 8));
            if (!ColonyManager.getInstance().protectsStructure(helper.getLevel(), target))
                helper.fail("Fixture is not protected, so the piston test proves nothing: direct="
                        + colony.protectsStructure(target) + " dim=" + helper.getLevel().dimension()
                        + " colonies=" + ColonyManager.getInstance().getAll().size());
            helper.setBlock(new BlockPos(8, 1, 8), Blocks.STONE_BRICKS);
            helper.setBlock(new BlockPos(7, 1, 8), Blocks.PISTON.defaultBlockState()
                    .setValue(DirectionalBlock.FACING, Direction.EAST));
            helper.setBlock(new BlockPos(6, 1, 8), Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(60, () -> {
                try {
                    if (!helper.getBlockState(new BlockPos(8, 1, 8)).is(Blocks.STONE_BRICKS))
                        helper.fail("A piston moved a colony block: direct=" + colony.protectsStructure(target)
                                + " manager=" + ColonyManager.getInstance().protectsStructure(helper.getLevel(), target)
                                + " piston=" + helper.getBlockState(new BlockPos(7, 1, 8)));
                    if (helper.getBlockState(new BlockPos(7, 1, 8)).getValue(PistonBaseBlock.EXTENDED))
                        helper.fail("A protected piston still extended");
                    helper.succeed();
                } finally { uninstall(); }
            });
        } catch (RuntimeException e) { uninstall(); throw e; }
    }
}
