package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Harvest reachable, recognizable, unbuilt trees; never use nearby leaves alone as proof. */
public class WoodcutterJob extends AbstractJob {
    private static final int MAX_GROUND_TRUNK = 6;
    private static final int MAX_REACHABLE_TRUNK = 10;
    private static final double TOOL_REACH_SQ = 20.25;
    private record Species(net.minecraft.world.level.block.Block log, net.minecraft.world.level.block.Block leaves,
                           net.minecraft.world.level.block.Block sapling) {}
    private static final java.util.List<Species> SPECIES = java.util.List.of(
            new Species(Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.OAK_SAPLING),
            new Species(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_SAPLING),
            new Species(Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, Blocks.BIRCH_SAPLING),
            new Species(Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_SAPLING),
            new Species(Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES, Blocks.ACACIA_SAPLING),
            new Species(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, Blocks.DARK_OAK_SAPLING),
            new Species(Blocks.MANGROVE_LOG, Blocks.MANGROVE_LEAVES, Blocks.MANGROVE_PROPAGULE),
            new Species(Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, Blocks.CHERRY_SAPLING));

    private static Species species(BlockState state) {
        return SPECIES.stream().filter(s -> state.is(s.log())).findFirst().orElse(null);
    }

    /** Only rooted single-column species; multi-root dark oak and mangrove
     * have different growth and replanting rules. */
    private static boolean supportsBranched(Species species) {
        return species.log() == Blocks.OAK_LOG || species.log() == Blocks.SPRUCE_LOG
                || species.log() == Blocks.BIRCH_LOG || species.log() == Blocks.JUNGLE_LOG
                || species.log() == Blocks.ACACIA_LOG || species.log() == Blocks.CHERRY_LOG;
    }

    private BlockPos treeBase;
    private Species treeSpecies;
    private java.util.List<BlockPos> tree = java.util.List.of();
    private int remaining;
    private boolean squareTree;
    private boolean branchedTree;
    private int[] branchAxes = new int[0];
    private boolean restored;
    private boolean saplingsChecked;
    private net.minecraft.world.level.pathfinder.Path approachPath;
    private BlockPos approachLog;
    private double approachBestDistance;
    private long approachProgressTime;
    private final java.util.Map<BlockPos, Long> blockedStands = new java.util.HashMap<>();
    private final PhysicalJobCargo cargo;
    private final PhysicalToolPickup toolPickup;
    private final PhysicalBuilderMaterials materials;
    private static final net.minecraft.world.item.Item[] AXES = { Items.NETHERITE_AXE, Items.DIAMOND_AXE,
            Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE, Items.GOLDEN_AXE };

    public WoodcutterJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.LUMBERJACK);
        ColonyVillager worker = villager instanceof ColonyVillager cv ? cv : null;
        cargo = worker == null ? null : new PhysicalJobCargo(worker, colony);
        toolPickup = worker == null ? null : new PhysicalToolPickup(worker, colony);
        materials = worker == null ? null : new PhysicalBuilderMaterials(worker, colony);
    }

    @Override protected void onTick(ServerLevel level) {
        if (approachPath == null) return;
        var brain = villager.getBrain();
        var pathMemory = net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH;
        boolean panic = brain.isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                || brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE)
                || brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY);
        if (!panic && state == JobState.GOING_TO_WORK && villager.getNavigation().getPath() == approachPath && !approachPath.isDone()) {
            var end = approachPath.getEndNode();
            if (end != null) {
                double distance = end.asBlockPos().distToCenterSqr(villager.position());
                if (distance < approachBestDistance - 0.1) {
                    approachBestDistance = distance;
                    approachProgressTime = level.getGameTime();
                } else if (level.getGameTime() - approachProgressTime >= 160) {
                    // A valid path can still be physically blocked. Try another
                    // natural standing spot rather than holding it forever.
                    blockedStands.put(end.asBlockPos(), level.getGameTime() + 200);
                    clearApproach();
                    state = JobState.STUCK;
                    ticksUntilNextWork = 0;
                    return;
                }
            }
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            brain.setMemory(pathMemory, approachPath);
        } else {
            if (brain.getMemory(pathMemory).orElse(null) == approachPath) brain.eraseMemory(pathMemory);
            approachPath = null; approachLog = null;
        }
    }

    private void followApproach(net.minecraft.world.level.pathfinder.Path path, BlockPos log) {
        if (villager.getNavigation().moveTo(path, .7)) {
            approachPath = path; approachLog = log;
            var end = path.getEndNode();
            approachBestDistance = end == null ? Double.POSITIVE_INFINITY : end.asBlockPos().distToCenterSqr(villager.position());
            approachProgressTime = villager.level().getGameTime();
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH, path);
        }
    }

    private void clearApproach() {
        if (approachPath == null) return;
        if (villager.getNavigation().getPath() == approachPath) villager.getNavigation().stop();
        var memory = net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH;
        if (villager.getBrain().getMemory(memory).orElse(null) == approachPath) villager.getBrain().eraseMemory(memory);
        approachPath = null; approachLog = null;
    }

    @Override
    protected void doWork(ServerLevel level) {
        if (!restored) { readMemory(); restored = true; }
        if (cargo != null && (treeBase == null || cargoFull())) {
            var delivery = cargo.deliver(level);
            if (delivery != PhysicalJobCargo.Delivery.NONE) {
                state = delivery == PhysicalJobCargo.Delivery.MOVING ? JobState.GOING_TO_WORK
                        : delivery == PhysicalJobCargo.Delivery.DELIVERED ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
                ticksUntilNextWork = delivery == PhysicalJobCargo.Delivery.MOVING ? 10 : 40; return;
            }
        }
        if (treeBase == null && materials != null) {
            var result = materials.returnSurplus(level);
            if (result != PhysicalBuilderMaterials.Result.READY) {
                state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                        : result == PhysicalBuilderMaterials.Result.WORKING ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
                ticksUntilNextWork = result == PhysicalBuilderMaterials.Result.MOVING ? 10 : 40; return;
            }
        }
        if (treeBase == null) {
            BlockPos center = villager.blockPosition();
            for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 8, 16))) {
                BlockPos base = mutable.immutable();
                if (!level.isLoaded(base)) continue;
                Species species = species(level.getBlockState(base));
                if (species == null) continue;
                var candidate = new TreeView(level, species);
                if (!candidate.trunk(base) || !candidate.loaded(base.below()) || !candidate.soil(base.below())) continue;
                var found = SafeOakTree.trunk(candidate, base);
                boolean square = false;
                if (found.isEmpty() && (species.log() == Blocks.DARK_OAK_LOG || species.log() == Blocks.SPRUCE_LOG
                        || species.log() == Blocks.JUNGLE_LOG)) {
                    found = SafeOakTree.squareTrunk(candidate, base);
                    square = !found.isEmpty();
                }
                boolean branched = false;
                if (found.isEmpty() && supportsBranched(species)) {
                    found = SafeBranchedTree.find(candidate, base);
                    branched = !found.isEmpty();
                }
                if (found.isEmpty() || (!square && !branched && found.size() > MAX_REACHABLE_TRUNK)) continue;
                // A square tree is accepted only if every top log is reachable
                // from an existing natural stand; never start an unreachable trunk.
                boolean accessible = true;
                int firstTop = branched ? 0 : square ? found.size() - 4 : found.size() - 1;
                if (branched || square || found.size() > MAX_GROUND_TRUNK) {
                    for (int i = firstTop; i < found.size(); i++) {
                        BlockPos top = found.get(i);
                        if (!inToolReach(top) && findTallApproach(level, base, top) == null) { accessible = false; break; }
                    }
                }
                if (!accessible) continue;
                treeBase = base;
                saplingsChecked = false;
                treeSpecies = species;
                tree = found;
                squareTree = square;
                branchedTree = branched;
                branchAxes = branched ? found.stream().mapToInt(candidate::branchAxis).toArray() : new int[0];
                remaining = tree.size();
                writeMemory();
                break;
            }
        }
        if (treeBase == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (!level.isLoaded(treeBase)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        Species species = species(level.getBlockState(treeBase));
        if (species == null || (treeSpecies != null && treeSpecies != species)) {
            clearTree(); state = JobState.STUCK; ticksUntilNextWork = 60; return;
        }
        // Old saved partial oak trees did not have a species field.
        if (treeSpecies == null) treeSpecies = species;
        var view = new TreeView(level, species);
        // A chunk unload is not a safety violation: retain the partially cut tree
        // and retry without loading chunks. Actual construction in the area aborts it.
        int height = squareTree ? tree.size() / 4 : tree.size();
        if (branchedTree) {
            if (!SafeBranchedTree.areaLoaded(view, treeBase)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        } else {
            for (int dx = -3; dx <= (squareTree ? 4 : 3); dx++) for (int dz = -3; dz <= (squareTree ? 4 : 3); dz++)
                for (int dy = -1; dy <= height + 2; dy++) {
                    if (!view.loaded(treeBase.offset(dx, dy, dz))) {
                        state = JobState.STUCK; ticksUntilNextWork = 100; return;
                    }
                }
        }
        boolean safe = branchedTree ? SafeBranchedTree.safePartial(view, treeBase, tree, remaining, branchAxes)
                : squareTree ? SafeOakTree.safePartialSquare(view, treeBase, tree, remaining)
                : SafeOakTree.safePartialTrunk(view, treeBase, tree, remaining);
        if (!safe) {
            clearTree(); state = JobState.STUCK; ticksUntilNextWork = 60; return;
        }
        // Resolve a missing axe before approaching the tree; otherwise the worker
        // alternates between walking to the tree and back to the crafting table.
        if (!(villager.getMainHandItem().getItem() instanceof AxeItem)) {
            if (!villager.getMainHandItem().isEmpty()) { state = JobState.NEEDS_TOOL; ticksUntilNextWork = 100; return; }
            if (toolPickup == null) { if (!equipAxeIfAvailable(level)) craftStoneReplacement(level, "axe"); return; }
            var picked = toolPickup.equip(level, AXES);
            if (picked == PhysicalToolPickup.Result.EQUIPPED) { state = JobState.WORKING; ticksUntilNextWork = 20; return; }
            if (picked == PhysicalToolPickup.Result.MOVING || picked == PhysicalToolPickup.Result.BLOCKED) {
                state = picked == PhysicalToolPickup.Result.MOVING ? JobState.GOING_TO_WORK : JobState.STUCK;
                ticksUntilNextWork = picked == PhysicalToolPickup.Result.MOVING ? 10 : 60; return;
            }
            var result = materials.prepare(level, "minecraft:stone_axe");
            if ((result == PhysicalBuilderMaterials.Result.READY || result == PhysicalBuilderMaterials.Result.WORKING)
                    && materials.takePrepared("minecraft:stone_axe")) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.STONE_AXE));
                state = JobState.WORKING; ticksUntilNextWork = 20;
            } else {
                state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                        : result == PhysicalBuilderMaterials.Result.WORKING ? JobState.WORKING
                        : result == PhysicalBuilderMaterials.Result.MISSING ? JobState.NEEDS_TOOL : JobState.STUCK;
                ticksUntilNextWork = result == PhysicalBuilderMaterials.Result.MOVING ? 10 : 40;
            }
            return;
        }
        if (materials != null && !saplingsChecked) {
            if (!squareTree && species.sapling() == Blocks.DARK_OAK_SAPLING) saplingsChecked = true;
            else {
                String sapling = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(species.sapling().asItem()).toString();
                var result = materials.prepareCount(level, sapling, squareTree ? 4 : 1);
                if (result == PhysicalBuilderMaterials.Result.MOVING || result == PhysicalBuilderMaterials.Result.WORKING) {
                    state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK : JobState.WORKING;
                    ticksUntilNextWork = 10; return;
                }
                // An unavailable sapling does not conjure one or prevent harvesting.
                // Unreachable or full storage waits rather than stranding reserved items.
                if (result == PhysicalBuilderMaterials.Result.BLOCKED) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
                saplingsChecked = true;
            }
            writeMemory();
        }
        BlockPos log = tree.get(remaining - 1);
        if (!inToolReach(log)) {
            if (approachPath != null && log.equals(approachLog)
                    && villager.getNavigation().getPath() == approachPath && !approachPath.isDone()) {
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
            if (!villager.onGround() && !villager.isInLiquid() && !villager.isPassenger()) {
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 0; return;
            }
            if (branchedTree || squareTree || tree.size() > MAX_GROUND_TRUNK) {
                var approach = findTallApproach(level, treeBase, log);
                if (approach == null) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
                followApproach(approach, log);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
            net.minecraft.world.level.pathfinder.Path path = null;
            var candidates = new java.util.ArrayList<BlockPos>();
            for (Direction d : Direction.Plane.HORIZONTAL) candidates.add(treeBase.relative(d));
            candidates.sort(java.util.Comparator.comparingDouble(p -> villager.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
            blockedStands.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
            for (BlockPos candidate : candidates) {
                if (blockedStands.containsKey(candidate) || !level.isLoaded(candidate) || !level.isLoaded(candidate.above()) || !level.isLoaded(candidate.below())) continue;
                if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                        || !level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                        || !level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)) continue;
                var attempt = villager.getNavigation().createPath(candidate, 0);
                if (attempt != null && attempt.canReach()) { path = attempt; break; }
            }
            if (path == null) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            followApproach(path, log);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        clearApproach();
        var block = level.getBlockState(log);
        var drops = net.minecraft.world.level.block.Block.getDrops(block, level, log, null, villager, villager.getMainHandItem());
        if (!level.destroyBlock(log, false)) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
        if (villager.getMainHandItem().getItem() instanceof AxeItem)
            villager.getMainHandItem().hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (var drop : drops) if (!drop.isEmpty()) {
            if (cargo == null) depositToColony(level, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(), drop.getCount());
            else {
                cargo.collectStack(drop);
                if (!drop.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, log, drop);
            }
        }
        remaining--;
        if (remaining == 0) {
            // No free saplings: replant only from real colony stock, at the tree's original base.
            String sapling = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(species.sapling().asItem()).toString();
            if (squareTree) {
                boolean canPlant = true;
                for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                    BlockPos p = treeBase.offset(x, 0, z);
                    if (!level.isLoaded(p) || !level.isLoaded(p.below()) || view.protectedPosition(p)
                            || !level.getBlockState(p).isAir() || !view.soil(p.below())) canPlant = false;
                }
                // Consume actual saplings only after validating all four spots.
                if (canPlant && (materials == null ? colony.consumeStock(level, sapling, 4) : materials.countPrepared(sapling) >= 4)) {
                    for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                        if (materials != null && !materials.takePrepared(sapling)) break;
                        if (!level.setBlock(treeBase.offset(x, 0, z), species.sapling().defaultBlockState(), 3)) {
                            if (materials == null) colony.depositToWarehouse(sapling, 1); else materials.refund(sapling);
                        }
                    }
                }
            } else if (species.sapling() != Blocks.DARK_OAK_SAPLING && level.getBlockState(treeBase).isAir()
                    && view.soil(treeBase.below()) && (materials == null ? colony.consumeStock(level, sapling, 1) : materials.takePrepared(sapling))) {
                if (!level.setBlock(treeBase, species.sapling().defaultBlockState(), 3)) {
                    if (materials == null) colony.depositToWarehouse(sapling, 1); else materials.refund(sapling);
                }
            }
            clearTree();
        } else writeMemory();
        state = JobState.WORKING;
        ticksUntilNextWork = 30;
    }

    private boolean cargoFull() {
        var worker = (ColonyVillager) villager;
        var first = worker.getCourierCargo(); var second = worker.getCourierCargoExtra();
        return !first.isEmpty() && !second.isEmpty()
                && first.getCount() >= first.getMaxStackSize() && second.getCount() >= second.getMaxStackSize();
    }

    private boolean inToolReach(BlockPos log) {
        return villager.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(log)) <= TOOL_REACH_SQ;
    }

    /** Only use loaded, dry, solid existing terrain that can put the worker's
     * eyes within real tool reach. This does not alter the hillside. */
    private net.minecraft.world.level.pathfinder.Path findTallApproach(ServerLevel level, BlockPos base, BlockPos log) {
        var candidates = new java.util.ArrayList<BlockPos>();
        blockedStands.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 5; dy++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos stand = base.offset(dx, dy, dz);
            if (blockedStands.containsKey(stand) || !colony.isInside(stand) || !level.isLoaded(stand) || !level.isLoaded(stand.above()) || !level.isLoaded(stand.below())) continue;
            var floor = level.getBlockState(stand.below());
            if (!(floor.is(BlockTags.DIRT) || floor.is(BlockTags.BASE_STONE_OVERWORLD))
                    || !floor.isFaceSturdy(level, stand.below(), Direction.UP)
                    || !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                    || !level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()
                    || !level.getFluidState(stand).isEmpty() || !level.getFluidState(stand.above()).isEmpty()) continue;
            double x = stand.getX() + 0.5 - (log.getX() + 0.5);
            double y = stand.getY() + villager.getEyeHeight() - (log.getY() + 0.5);
            double z = stand.getZ() + 0.5 - (log.getZ() + 0.5);
            if (x * x + y * y + z * z <= TOOL_REACH_SQ) candidates.add(stand);
        }
        candidates.sort(java.util.Comparator.comparingDouble(p -> villager.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
        for (BlockPos stand : candidates) {
            var path = villager.getNavigation().createPath(stand, 0);
            if (path != null && path.canReach()) return path;
        }
        return null;
    }

    /** Never harvest bare-handed; a missing axe must be supplied or crafted. */
    private boolean equipAxeIfAvailable(ServerLevel level) {
        if (villager.getMainHandItem().getItem() instanceof AxeItem) return true;
        if (!villager.getMainHandItem().isEmpty()) return false;
        for (var item : new net.minecraft.world.item.Item[]{Items.NETHERITE_AXE, Items.DIAMOND_AXE,
                Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE, Items.GOLDEN_AXE}) {
            var tool = takeTool(level, item);
            if (!tool.isEmpty()) { villager.setItemSlot(EquipmentSlot.MAINHAND, tool); return true; }
        }
        return false;
    }

    private void clearTree() {
        clearApproach();
        treeBase = null; treeSpecies = null; tree = java.util.List.of(); remaining = 0; squareTree = false; branchedTree = false; saplingsChecked = false; branchAxes = new int[0]; writeMemory();
    }
    private void writeMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = new CompoundTag();
        if (treeBase != null) {
            tag.putLong("base", treeBase.asLong());
            if (treeSpecies != null) tag.putString("species", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(treeSpecies.log()).toString());
            if (branchedTree) {
                tag.putLongArray("branchLogs", tree.stream().mapToLong(BlockPos::asLong).toArray());
                tag.putIntArray("branchAxes", branchAxes);
            }
            else tag.putInt("height", squareTree ? tree.size() / 4 : tree.size());
            if (squareTree) tag.putBoolean("square", true);
            tag.putInt("remaining", remaining);
            tag.putBoolean("saplingsChecked", saplingsChecked);
        }
        cv.setWoodcutterMemory(tag);
    }
    private void readMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = cv.getWoodcutterMemory();
        int height = tag.getInt("height"), count = tag.getInt("remaining");
        boolean square = tag.getBoolean("square"), branched = tag.contains("branchLogs");
        long[] savedLogs = branched ? tag.getLongArray("branchLogs") : new long[0];
        int size = branched ? savedLogs.length : square ? height * 4 : height;
        if (!tag.contains("base") || (branched && (square || size < 3 || size > 32))
                || (!branched && (height < 3 || height > (square ? 6 : MAX_REACHABLE_TRUNK)))
                || count < 1 || count > size) return;
        BlockPos base = BlockPos.of(tag.getLong("base"));
        if (!colony.isInside(base)) return;
        var logs = new java.util.ArrayList<BlockPos>();
        if (branched) {
            for (long value : savedLogs) logs.add(BlockPos.of(value));
        } else for (int y = 0; y < height; y++) {
            if (square) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) logs.add(base.offset(x, y, z));
            else logs.add(base.above(y));
        }
        String savedSpecies = tag.getString("species");
        if (!savedSpecies.isEmpty()) {
            treeSpecies = SPECIES.stream().filter(s -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.log()).toString().equals(savedSpecies)).findFirst().orElse(null);
            if (treeSpecies == null) return;
        }
        if (square && (treeSpecies == null || (treeSpecies.log() != Blocks.DARK_OAK_LOG
                && treeSpecies.log() != Blocks.SPRUCE_LOG && treeSpecies.log() != Blocks.JUNGLE_LOG))) return;
        if (branched && (treeSpecies == null || !supportsBranched(treeSpecies))) return;
        int[] savedAxes = branched && tag.contains("branchAxes") ? tag.getIntArray("branchAxes") : new int[size];
        if (branched && savedAxes.length != size) return;
        branchAxes = branched ? savedAxes : new int[0];
        treeBase = base; tree = java.util.List.copyOf(logs); remaining = count; squareTree = square; branchedTree = branched;
        saplingsChecked = tag.getBoolean("saplingsChecked");
    }

    private final class TreeView implements SafeOakTree.View {
        private final ServerLevel level;
        private final Species species;
        TreeView(ServerLevel level, Species species) { this.level = level; this.species = species; }
        public boolean loaded(BlockPos pos) { return level.isLoaded(pos); }
        public boolean soil(BlockPos pos) { return level.getBlockState(pos).is(BlockTags.DIRT); }
        public boolean trunk(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(species.log()) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
        }
        public int branchAxis(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(species.log())) return -1;
            return switch (state.getValue(RotatedPillarBlock.AXIS)) {
                case Y -> 0;
                case X -> 1;
                case Z -> 2;
            };
        }
        public boolean naturalLeaf(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(species.leaves()) && !state.getValue(LeavesBlock.PERSISTENT);
        }
        public boolean clearOrNatural(BlockPos pos, boolean belowBase) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;
            if (state.is(BlockTags.LEAVES)) return state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
            if (state.is(BlockTags.LOGS)) return false;
            return state.isAir() || state.canBeReplaced() || state.is(BlockTags.DIRT)
                    || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                    || (belowBase && state.is(BlockTags.BASE_STONE_OVERWORLD));
        }
        public boolean protectedPosition(BlockPos pos) {
            if (!colony.isInside(pos)) return true;
            // Use a generous horizontal buffer, including incomplete buildings and the larger starter farm.
            if (near(pos, colony.getCenter(), 9)) return true;
            for (var building : colony.getBuildings()) {
                int margin = Math.max(building.getType() == BuildingType.FARM ? 10 : 8, building.getSize() / 2 + 5);
                if (building.importedFootprintNear(pos, margin)) return true;
                for (BlockPos site : building.footprintSites()) if (near(pos, site, margin)) return true;
            }
            // Generated structures may not be registered as colony buildings.
            for (var structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
                if (level.structureManager().getStructureAt(pos, structure).isValid()) return true;
            }
            return false;
        }
        private boolean near(BlockPos a, BlockPos b, int radius) {
            return Math.abs((long) a.getX() - b.getX()) <= radius && Math.abs((long) a.getZ() - b.getZ()) <= radius;
        }
    }
}
