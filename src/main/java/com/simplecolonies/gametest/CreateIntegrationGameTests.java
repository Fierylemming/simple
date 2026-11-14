package com.simplecolonies.gametest;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.integration.CreateSchematicIntegration;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** These fixtures run with real Create only when the optional CI runtime is enabled. */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class CreateIntegrationGameTests {
    private static boolean createAvailable(GameTestHelper helper) {
        if (CreateSchematicIntegration.isCreateLoaded()) return true;
        if ("true".equals(System.getenv("SIMPLECOLONIES_REQUIRE_CREATE")))
            helper.fail("Create compatibility run did not load Create");
        else helper.succeed();
        return false;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack schematic(GameTestHelper helper, BlockPos source, Vec3i size, BlockPos destination) throws Exception {
        var owner = "simplecolonies-test-" + java.util.UUID.randomUUID();
        var file = "fixture.nbt";
        var base = (java.nio.file.Path) Class.forName("com.simibubi.create.foundation.utility.CreatePaths")
                .getField("UPLOADED_SCHEMATICS_DIR").get(null);
        var dir = base.resolve(owner);
        java.nio.file.Files.createDirectories(dir);
        var template = new StructureTemplate();
        template.fillFromWorld(helper.getLevel(), helper.absolutePos(source), size, false, Blocks.STRUCTURE_VOID);
        NbtIo.writeCompressed(template.save(new CompoundTag()), dir.resolve(file));
        var itemClass = Class.forName("com.simibubi.create.content.schematics.SchematicItem");
        var stack = (ItemStack) itemClass.getMethod("create", Level.class, String.class, String.class)
                .invoke(null, helper.getLevel(), file, owner);
        var components = Class.forName("com.simibubi.create.AllDataComponents");
        stack.set((DataComponentType<Boolean>) components.getField("SCHEMATIC_DEPLOYED").get(null), true);
        stack.set((DataComponentType<BlockPos>) components.getField("SCHEMATIC_ANCHOR").get(null), helper.absolutePos(destination));
        return stack;
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void deployedCreateSchematicQueuesOnceAndPreservesBlueprint(GameTestHelper helper) throws Exception {
        if (!createAvailable(helper)) return;
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var src = new BlockPos(2, 1, 2);
        helper.setBlock(src, Blocks.OAK_PLANKS); helper.setBlock(src.east(), Blocks.COBBLESTONE);
        var blueprint = schematic(helper, src, new Vec3i(2, 1, 1), new BlockPos(10, 1, 10));
        var chest = new BlockPos(6, 1, 6); helper.setBlock(chest, Blocks.CHEST);
        var inventory = (Container) helper.getBlockEntity(chest); inventory.setItem(0, blueprint.copy());
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.addCitizen(new CitizenData(java.util.UUID.randomUUID(), "Builder", JobType.BUILDER));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share schematic chest");
        var target = new BlockPos(10, 1, 10);
        helper.setBlock(target, Blocks.SHORT_GRASS);
        if (CreateSchematicIntegration.queue(colony, helper.getLevel()).task() != null
                || !helper.getBlockState(target).is(Blocks.SHORT_GRASS))
            helper.fail("Create import accepted or cleared a replaceable obstruction");
        helper.setBlock(target, Blocks.AIR);
        var result = CreateSchematicIntegration.queue(colony, helper.getLevel());
        if (result.task() == null) { helper.fail("Create import failed: " + result.message()); return; }
        var plan = result.task();
        if (plan.getRemainingPlacements().size() != 2 || !ItemStack.isSameItemSameComponents(inventory.getItem(0), blueprint)
                || plan.getRemainingPlacements().stream().noneMatch(p -> p.pos.equals(helper.absolutePos(new BlockPos(10, 1, 10))) && p.state.is(Blocks.OAK_PLANKS))
                || plan.getRemainingPlacements().stream().noneMatch(p -> p.pos.equals(helper.absolutePos(new BlockPos(11, 1, 10))) && p.state.is(Blocks.COBBLESTONE)))
            helper.fail("Create import changed the blueprint or produced wrong placements");
        var restored = Colony.load(colony.save());
        if (restored.getBuildings().size() != 1 || restored.getBuildings().getFirst().getRemainingPlacements().size() != 2)
            helper.fail("Create task did not survive a colony save");
        if (CreateSchematicIntegration.queue(colony, helper.getLevel()).task() != null || colony.getBuildings().size() != 1)
            helper.fail("Duplicated an already queued schematic");
        inventory.setItem(1, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        inventory.setItem(2, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        worker.setNoAi(true);
        var job = new com.simplecolonies.villager.job.BuilderJob(worker, restored);
        var restoredPlan = restored.getBuildings().getFirst();
        for (int tick = 1; tick < 700; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (restoredPlan.getState() != com.simplecolonies.building.BuildingState.COMPLETE
                    || !helper.getBlockState(target).is(Blocks.OAK_PLANKS) || !helper.getBlockState(target.east()).is(Blocks.COBBLESTONE)
                    || inventory.countItem(net.minecraft.world.item.Items.OAK_PLANKS) != 0
                    || inventory.countItem(net.minecraft.world.item.Items.COBBLESTONE) != 0
                    || !ItemStack.isSameItemSameComponents(inventory.getItem(0), blueprint))
                helper.fail("Restored Create plan did not physically build: " + restoredPlan.getState() + "/" + job.getState());
        });
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void rotatedCreateSchematicPreservesPositionsAndFacing(GameTestHelper helper) throws Exception {
        if (!createAvailable(helper)) return;
        var src = new BlockPos(2, 1, 2);
        helper.setBlock(src, Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH));
        helper.setBlock(src.east(), Blocks.COBBLESTONE);
        var blueprint = schematic(helper, src, new Vec3i(2, 1, 1), new BlockPos(10, 1, 10));
        var components = Class.forName("com.simibubi.create.AllDataComponents");
        blueprint.set((DataComponentType<net.minecraft.world.level.block.Rotation>) components.getField("SCHEMATIC_ROTATION").get(null),
                net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        var chest = new BlockPos(6, 1, 6); helper.setBlock(chest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(chest)).setItem(0, blueprint);
        var colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        colony.addCitizen(new CitizenData(java.util.UUID.randomUUID(), "Builder", JobType.BUILDER));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share schematic chest");
        var result = CreateSchematicIntegration.queue(colony, helper.getLevel());
        if (result.task() == null) { helper.fail("Rotated Create import failed: " + result.message()); return; }
        var blocks = result.task().getRemainingPlacements();
        if (blocks.size() != 2 || blocks.stream().noneMatch(p -> p.pos.equals(helper.absolutePos(new BlockPos(10, 1, 10)))
                    && p.state.is(Blocks.OAK_STAIRS) && p.state.getValue(net.minecraft.world.level.block.StairBlock.FACING) == net.minecraft.core.Direction.EAST)
                || blocks.stream().noneMatch(p -> p.pos.equals(helper.absolutePos(new BlockPos(10, 1, 11))) && p.state.is(Blocks.COBBLESTONE)))
            helper.fail("Create rotation lost the transformed position or stair facing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unsupportedCreateSchematicDoesNotConsumeOrQueue(GameTestHelper helper) throws Exception {
        if (!createAvailable(helper)) return;
        var src = new BlockPos(2, 1, 2); helper.setBlock(src, Blocks.CHEST);
        var blueprint = schematic(helper, src, new Vec3i(1, 1, 1), new BlockPos(10, 1, 10));
        var chest = new BlockPos(6, 1, 6); helper.setBlock(chest, Blocks.CHEST);
        var inventory = (Container) helper.getBlockEntity(chest); inventory.setItem(0, blueprint.copy());
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        colony.addCitizen(new CitizenData(java.util.UUID.randomUUID(), "Builder", JobType.BUILDER));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share schematic chest");
        var result = CreateSchematicIntegration.queue(colony, helper.getLevel());
        if (result.task() != null || !colony.getBuildings().isEmpty() || !ItemStack.isSameItemSameComponents(inventory.getItem(0), blueprint))
            helper.fail("Unsupported schematic was partially queued or consumed");
        helper.succeed();
    }
}
