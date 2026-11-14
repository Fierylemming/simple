package com.simplecolonies.integration;

import com.simplecolonies.building.Building;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Optional, server-side bridge to Create's real schematic item and printer API.
 * The supported subset is deliberately conservative: all unsupported content is
 * rejected before queueing, never silently omitted or built for free. */
public final class CreateSchematicIntegration {
    private static final int MAX_VOLUME = 32768, MAX_BLOCKS = 16384;
    public record Result(Building task, String message) {}

    public static boolean isCreateLoaded() { return ModList.get().isLoaded("create"); }
    public static boolean isSchematic(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("create:schematic");
    }

    /** Search only loaded, explicitly shared storage, without consuming the blueprint.
     * A saved identity suppresses duplicate jobs while that blueprint remains in storage. */
    public static Result queue(Colony colony, ServerLevel level) { return queue(colony, level, false); }

    /** An explicit leader action may retry a previously cancelled schematic. */
    public static Result queue(Colony colony, ServerLevel level, boolean retryCancelled) {
        if (!isCreateLoaded()) return new Result(null, "Create is not installed");
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) return new Result(null, "Overworld required");
        if (colony.getCitizens().stream().noneMatch(c -> !c.isChild && c.job == com.simplecolonies.villager.job.JobType.BUILDER))
            return new Result(null, "Assign an adult builder first");
        if (colony.getBuildings().stream().anyMatch(b -> b.hasSupportedPlan() && b.getState() != com.simplecolonies.building.BuildingState.COMPLETE))
            return new Result(null, "Another construction plan is already queued");
        String reason = "Put a deployed Create schematic in shared storage";
        var warehouse = colony.getWarehouseManager();
        for (BlockPos pos : warehouse.courierStoragePositions(level).stream().sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList()) {
            var container = warehouse.courierContainer(level, pos);
            if (container == null) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!isSchematic(stack)) continue;
                var source = new CompoundTag();
                source.put("stack", stack.copyWithCount(1).save(level.registryAccess()));
                if (colony.getBuildings().stream().anyMatch(b -> b.hasImportedSource(source))) continue;
                if (!retryCancelled && colony.isCancelledSchematic(source)) continue;
                try {
                    var decoded = decode(level, stack);
                    String obstacle = validateSite(colony, level, decoded.blocks());
                    if (obstacle != null) { reason = obstacle; continue; }
                    Building task = Building.imported(decoded.anchor(), colony.getCulture(), source, decoded.blocks());
                    if (colony.queueImportedBuilding(task)) {
                        colony.clearCancelledSchematic(source);
                        return new Result(task, "Queued schematic; the blueprint remains in shared storage");
                    }
                    return new Result(null, "Construction queue changed");
                } catch (UnsupportedSchematic e) {
                    reason = e.getMessage();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    com.simplecolonies.SimpleColonies.LOGGER.warn("Could not read Create schematic", e);
                    reason = "Could not read schematic with this Create version; no items were consumed";
                }
            }
        }
        return new Result(null, reason);
    }

    /** Retained for callers of the original integration hook. */
    public static Building tryCreateSchematicTask(Colony colony, ServerLevel level, BlockPos builderPos) {
        return queue(colony, level).task();
    }

    private record Decoded(BlockPos anchor, List<Building.BlockPlacement> blocks) {}
    private static final class UnsupportedSchematic extends Exception {
        UnsupportedSchematic(String message) { super(message); }
    }

    @SuppressWarnings("unchecked")
    private static Decoded decode(ServerLevel level, ItemStack blueprint) throws ReflectiveOperationException, UnsupportedSchematic {
        Class<?> components = Class.forName("com.simibubi.create.AllDataComponents");
        var deployedType = (DataComponentType<Boolean>) components.getField("SCHEMATIC_DEPLOYED").get(null);
        var anchorType = (DataComponentType<BlockPos>) components.getField("SCHEMATIC_ANCHOR").get(null);
        BlockPos anchor = blueprint.get(anchorType);
        if (!Boolean.TRUE.equals(blueprint.get(deployedType)) || anchor == null)
            throw new UnsupportedSchematic("Deploy the schematic in the world before sharing it");
        Class<?> itemClass = Class.forName("com.simibubi.create.content.schematics.SchematicItem");
        var template = (StructureTemplate) itemClass.getMethod("loadSchematic", Level.class, ItemStack.class).invoke(null, level, blueprint);
        var size = template.getSize();
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0 || volume > MAX_VOLUME
                || size.getX() > 64 || size.getY() > 32 || size.getZ() > 64)
            throw new UnsupportedSchematic("Schematic file missing, empty, or too large");
        Class<?> printerClass = Class.forName("com.simibubi.create.content.schematics.SchematicPrinter");
        Object printer = printerClass.getConstructor().newInstance();
        printerClass.getMethod("loadSchematic", ItemStack.class, Level.class, boolean.class).invoke(printer, blueprint, level, true);
        if (!(boolean) printerClass.getMethod("isLoaded").invoke(printer) || (boolean) printerClass.getMethod("isErrored").invoke(printer))
            throw new UnsupportedSchematic("Create could not load this schematic");
        Class<?> blockHandler = Class.forName("com.simibubi.create.content.schematics.SchematicPrinter$BlockTargetHandler");
        Class<?> entityHandler = Class.forName("com.simibubi.create.content.schematics.SchematicPrinter$EntityTargetHandler");
        Object[] current = new Object[3];
        Object onBlock = Proxy.newProxyInstance(blockHandler.getClassLoader(), new Class<?>[]{blockHandler}, (p, method, args) -> {
            if (method.getName().equals("handle")) { current[0] = args[0]; current[1] = args[1]; current[2] = args[2]; }
            return null;
        });
        Object onEntity = Proxy.newProxyInstance(entityHandler.getClassLoader(), new Class<?>[]{entityHandler}, (p, method, args) -> {
            if (method.getName().equals("handle")) current[0] = args[1];
            return null;
        });
        var advance = printerClass.getMethod("advanceCurrentPos");
        var handle = printerClass.getMethod("handleCurrentTarget", blockHandler, entityHandler);
        var stage = printerClass.getMethod("getPrintStage");
        var requirementMethod = printerClass.getMethod("getCurrentRequirement");
        List<Building.BlockPlacement> plan = new ArrayList<>();
        Map<BlockPos, BlockState> upperDoors = new HashMap<>();
        Map<BlockPos, BlockState> lowerDoors = new HashMap<>();
        int inspected = 0;
        while ((boolean) advance.invoke(printer)) {
            if (++inspected > MAX_VOLUME * 2) throw new UnsupportedSchematic("Schematic has too many positions");
            current[0] = current[1] = current[2] = null;
            handle.invoke(printer, onBlock, onEntity);
            if (stage.invoke(printer).toString().equals("ENTITIES"))
                throw new UnsupportedSchematic("Schematic entities are not supported yet; nothing was queued");
            var pos = ((BlockPos) current[0]).immutable();
            var state = (BlockState) current[1];
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) continue;
            if (state.hasBlockEntity() || current[2] != null || !state.getFluidState().isEmpty())
                throw new UnsupportedSchematic("Schematic machines, block entities and fluids are not supported yet; nothing was queued");
            if (state.hasProperty(BlockStateProperties.BED_PART))
                throw new UnsupportedSchematic("Schematic beds and other multi-blocks are not supported yet");
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                if (!(state.getBlock() instanceof DoorBlock)) throw new UnsupportedSchematic("Schematic multi-blocks are not supported yet");
                if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) { upperDoors.put(pos, state); continue; }
                lowerDoors.put(pos, state);
            }
            Object requirement = requirementMethod.invoke(printer);
            Class<?> requirementType = requirement.getClass();
            if ((boolean) requirementType.getMethod("isInvalid").invoke(requirement)
                    || (boolean) requirementType.getMethod("isEmpty").invoke(requirement))
                throw new UnsupportedSchematic("Schematic contains a block with unsupported material requirements");
            var requirements = (List<?>) requirementType.getMethod("getRequiredItems").invoke(requirement);
            if (requirements.size() != 1) throw new UnsupportedSchematic("Schematic has a multi-item block requirement");
            Object required = requirements.getFirst();
            ItemStack item = (ItemStack) required.getClass().getField("stack").get(required);
            Object usage = required.getClass().getField("usage").get(required);
            if (!usage.toString().equals("CONSUME") || item.isEmpty() || item.getCount() != 1
                    || !ItemStack.isSameItemSameComponents(item, item.getItem().getDefaultInstance()))
                throw new UnsupportedSchematic("Schematic has a special or multi-item material requirement");
            plan.add(new Building.BlockPlacement(pos, state, BuiltInRegistries.ITEM.getKey(item.getItem()).toString()));
            if (plan.size() > MAX_BLOCKS) throw new UnsupportedSchematic("Schematic contains too many blocks");
        }
        if (plan.isEmpty()) throw new UnsupportedSchematic("Schematic contains no supported blocks");
        if (lowerDoors.size() != upperDoors.size()) throw new UnsupportedSchematic("Schematic has an incomplete door");
        for (var lower : lowerDoors.entrySet()) {
            var expected = lower.getValue().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            if (!expected.equals(upperDoors.get(lower.getKey().above())))
                throw new UnsupportedSchematic("Schematic has an incomplete or mismatched door");
        }
        return new Decoded(anchor.immutable(), List.copyOf(plan));
    }

    private static String validateSite(Colony colony, ServerLevel level, List<Building.BlockPlacement> plan) {
        for (var placement : plan) {
            BlockPos pos = placement.pos;
            if (!colony.isInside(pos) || !level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
                return "Schematic extends outside the loaded colony or world boundary";
            for (var building : colony.getBuildings()) {
                if (building.importedFootprintNear(pos, 8)) return "Schematic overlaps an existing colony building";
                for (BlockPos footprint : building.footprintSites())
                    if (Math.abs((long) pos.getX() - footprint.getX()) <= 8 && Math.abs((long) pos.getZ() - footprint.getZ()) <= 8)
                        return "Schematic overlaps an existing colony building";
            }
            var existing = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null || !existing.getFluidState().isEmpty()
                    || (!existing.equals(placement.state) && !existing.isAir() && !existing.canBeReplaced()))
                return "Schematic site contains a solid obstruction or protected block entity";
            if (placement.state.getBlock() instanceof DoorBlock) {
                var above = pos.above();
                if (!level.isLoaded(above) || !colony.isInside(above) || level.getBlockEntity(above) != null) return "Schematic door is blocked";
                var upper = level.getBlockState(above);
                if (!upper.equals(placement.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
                        && (!upper.getFluidState().isEmpty() || (!upper.isAir() && !upper.canBeReplaced()))) return "Schematic door is blocked";
            }
        }
        return null;
    }
    private CreateSchematicIntegration() {}
}
