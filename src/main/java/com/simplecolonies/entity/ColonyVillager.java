package com.simplecolonies.entity;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.menu.VillagerInteractionMenu;
import com.simplecolonies.villager.job.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerData;
import com.simplecolonies.villager.ColonyProfessions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Custom villager that belongs to a colony, has a job, needs food/housing,
 * and participates in supply chain + warfare.
 * NEW: shift-right-click opens custom job GUI (playable), normal right-click opens vanilla trades.
 */
public class ColonyVillager extends Villager {
    private int colonyId = -1;
    private JobType job = JobType.UNEMPLOYED;
    private AbstractJob jobInstance;
    // Saved as an identity, not an entity reference: never force-load a leader.
    private java.util.UUID followCommanderId;
    private boolean suppressVanillaBrainReset;
    // Physical job cargo is retained across chunk unloads and restarts.
    private net.minecraft.world.item.ItemStack courierCargo = net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack courierCargoExtra = net.minecraft.world.item.ItemStack.EMPTY;
    // Ingredients in transit are kept separate from vanilla villager AI inventory.
    private net.minecraft.world.item.ItemStack workSupplies = net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack plantingSeeds = net.minecraft.world.item.ItemStack.EMPTY;
    // Builder-only physical materials, separate from vanilla inventory and AI.
    private final net.minecraft.world.SimpleContainer builderMaterials = new net.minecraft.world.SimpleContainer(18);
    private CompoundTag minerMemory = new CompoundTag();
    private CompoundTag woodcutterMemory = new CompoundTag();
    private long nextConversationTime;
    private long nextShelterAttempt;
    // Session-local interior candidates; revalidated before redirecting a walk.
    private java.util.List<net.minecraft.core.BlockPos> shelterInterior = java.util.List.of();
    private net.minecraft.core.BlockPos shelterAnchor;
    // Doors remembered by the vanilla brain while approaching this shelter.
    // Entries expire and never persist across unloads or dimension changes.
    private final java.util.Map<net.minecraft.core.BlockPos, Long> shelterDoors = new java.util.HashMap<>();
    // A vanilla memory may outlive closure; never treat a later reopening as ours.
    private final java.util.Set<GlobalPos> observedDoorMemories = new java.util.HashSet<>();

    // Needs
    private int hungerTicks = 0; // increments daily if no food, leads to damage
    private int daysAlive = 0;

    public ColonyVillager(EntityType<? extends Villager> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        this.setCustomName(Component.literal(randomName()));
        this.setCustomNameVisible(false);
        // Try auto-assign to nearest colony if spawned via village structure
        var colonyOpt = ColonyManager.getInstance().getColonyAt(level().dimension(), blockPosition());
        if (colonyOpt.isPresent()) {
            setColony(colonyOpt.get());
            // Random job to make colony alive immediately
            JobType[] starters = {JobType.FARMER, JobType.LUMBERJACK, JobType.MINER, JobType.BUILDER, JobType.UNEMPLOYED};
            setJob(starters[level.getRandom().nextInt(starters.length)]);
            // Register citizen properly with name
            setCustomName(Component.literal(citizenNameForCulture(colonyOpt.get())));
        }
        return data;
    }

    private String randomName() {
        String[] eg = {"Ahmose","Nefer","Khepri","Bastet","Ramses","Cleopatra","Imhotep"};
        return eg[level().random.nextInt(eg.length)];
    }

    private String citizenNameForCulture(Colony colony) {
        return switch (colony.getCulture()) {
            case EGYPTIAN -> new String[]{"Ahmose","Nefer","Khepri","Bastet","Ramses","Imhotep","Anhk"}[random.nextInt(7)];
            case ROMAN -> new String[]{"Marcus","Livia","Augustus","Caesar","Lucia","Titus","Aurelia"}[random.nextInt(7)];
            case GREEK -> new String[]{"Alexandros","Helene","Pericles","Socrates","Athena","Leonidas","Kallias"}[random.nextInt(7)];
            case PERSIAN -> new String[]{"Cyrus","Darius","Xerxes","Roxana","Artemis","Bahar","Kaveh"}[random.nextInt(7)];
            case MESOPOTAMIAN -> new String[]{"Gilgamesh","Enkidu","Sargon","Hammurabi","Ishtar","Nabu","Utu"}[random.nextInt(7)];
        };
    }

    public int getColonyId() { return colonyId; }
    public JobType getColonyJob() { return job; }
    public String getWorkStatus() {
        if (isBaby()) return "CHILD";
        if (job != JobType.UNEMPLOYED && !job.isCombat && !getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return "WAITING_FOR_WORKSTATION";
        return jobInstance == null ? "IDLE" : jobInstance.getState().name();
    }
    public boolean isColonyCitizen() { return colonyId != -1; }
    /** Villager brains use the PANIC activity, not necessarily IS_PANICKING memory. */
    public boolean isInDanger() {
        return getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                || getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)
                || getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }
    public net.minecraft.world.item.ItemStack getCourierCargo() { return courierCargo; }
    public void setCourierCargo(net.minecraft.world.item.ItemStack cargo) { courierCargo = cargo; }
    public net.minecraft.world.item.ItemStack getCourierCargoExtra() { return courierCargoExtra; }
    public void setCourierCargoExtra(net.minecraft.world.item.ItemStack cargo) { courierCargoExtra = cargo; }
    public net.minecraft.world.item.ItemStack getWorkSupplies() { return workSupplies; }
    public void setWorkSupplies(net.minecraft.world.item.ItemStack supplies) { workSupplies = supplies; }
    public net.minecraft.world.item.ItemStack getPlantingSeeds() { return plantingSeeds; }
    public void setPlantingSeeds(net.minecraft.world.item.ItemStack seeds) { plantingSeeds = seeds; }
    public net.minecraft.world.SimpleContainer getBuilderMaterials() { return builderMaterials; }

    private void dropCourierCargo() {
        if (!courierCargo.isEmpty()) spawnAtLocation(courierCargo);
        if (!courierCargoExtra.isEmpty()) spawnAtLocation(courierCargoExtra);
        if (!workSupplies.isEmpty()) spawnAtLocation(workSupplies);
        if (!plantingSeeds.isEmpty()) spawnAtLocation(plantingSeeds);
        for (int i = 0; i < builderMaterials.getContainerSize(); i++) {
            var stack = builderMaterials.removeItemNoUpdate(i);
            if (!stack.isEmpty()) spawnAtLocation(stack);
        }
        plantingSeeds = net.minecraft.world.item.ItemStack.EMPTY;
        workSupplies = net.minecraft.world.item.ItemStack.EMPTY;
        courierCargo = net.minecraft.world.item.ItemStack.EMPTY;
        courierCargoExtra = net.minecraft.world.item.ItemStack.EMPTY;
    }
    public CompoundTag getMinerMemory() { return minerMemory.copy(); }
    public void setMinerMemory(CompoundTag memory) { minerMemory = memory.copy(); }
    public CompoundTag getWoodcutterMemory() { return woodcutterMemory.copy(); }
    public void setWoodcutterMemory(CompoundTag memory) { woodcutterMemory = memory.copy(); }

    public void setColony(Colony colony) {
        if (this.colonyId != -1 && this.colonyId != colony.getId()) {
            followCommanderId = null;
            jobInstance = null;
            ColonyManager.getInstance().getColony(this.colonyId).ifPresent(old -> old.removeCitizen(getUUID()));
        }
        this.colonyId = colony.getId();
        // Avoid duplicate citizen entry if already exists
        var citizen = colony.getCitizens().stream().filter(c -> c.uuid.equals(getUUID())).findFirst().orElse(null);
        if (citizen == null) {
            citizen = new CitizenData(getUUID(), getName().getString(), job);
            citizen.isChild = isBaby();
            colony.addCitizen(citizen);
        } else if (citizen.isChild != isBaby()) {
            citizen.isChild = isBaby();
            ColonyManager.getInstance().markDirty(colony);
        }
    }

    public void setJob(JobType newJob) {
        boolean changed = this.job != newJob;
        if (changed && !level().isClientSide) {
            followCommanderId = null;
            // Keep real job tools (including their damage/components) when switching
            // professions, instead of leaving an incompatible tool in the main hand.
            var held = getMainHandItem();
            boolean oldTool = (job == JobType.LUMBERJACK && held.getItem() instanceof net.minecraft.world.item.AxeItem)
                    || (job == JobType.MINER && held.getItem() instanceof net.minecraft.world.item.PickaxeItem)
                    || (job == JobType.SHEPHERD && held.is(net.minecraft.world.item.Items.SHEARS))
                    || (job.isCombat && !newJob.isCombat && held.getItem() instanceof net.minecraft.world.item.SwordItem);
            if (oldTool) {
                var remainder = getInventory().addItem(held);
                setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, remainder);
            }
            dropCourierCargo();
            if (getTradingPlayer() instanceof ServerPlayer player) player.closeContainer();
            setTradingPlayer(null);
            clearWorkMemories();
            this.offers = null;
            setVillagerXp(0);
        }
        this.job = newJob;
        this.jobInstance = createJobInstance(newJob);
        if (!level().isClientSide) syncProfession(changed);
        // Update citizen record
        Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
        if (colony != null) {
            for (CitizenData cd : colony.getCitizens()) {
                if (cd.uuid.equals(getUUID())) {
                    cd.job = newJob;
                    cd.isSoldier = newJob.isCombat;
                    break;
                }
            }
            ColonyManager.getInstance().markDirty(colony);
        }
        if (newJob == JobType.SOLDIER || newJob == JobType.GUARD) {
            SoldierJob.attachGoals(this);
        }
        // Update custom name to show job icon
        setCustomName(Component.literal(com.simplecolonies.villager.VillagerNames.withJob(getName().getString(), newJob.displayName)));
    }

    private AbstractJob createJobInstance(JobType type) {
        Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
        if (colony == null) return null;
        return switch (type) {
            case FARMER -> new FarmerJob(this, colony);
            case LUMBERJACK -> new WoodcutterJob(this, colony);
            case MINER -> new MinerJob(this, colony);
            case BUILDER -> new BuilderJob(this, colony);
            case SOLDIER, GUARD -> new SoldierJob(this, colony);
            case BUTCHER -> new ButcherJob(this, colony);
            case LEATHERWORKER -> new LeatherworkerJob(this, colony);
            case BLACKSMITH -> new BlacksmithJob(this, colony);
            case BAKER -> new BakerJob(this, colony);
            case FISHERMAN -> new FishermanJob(this, colony);
            case COURIER -> new CourierJob(this, colony);
            case SHEPHERD -> new ShepherdJob(this, colony);
            default -> null;
        };
    }

    /** Follow orders survive entity reload; an offline leader is never force-loaded. */
    public @Nullable java.util.UUID getFollowCommanderId() { return followCommanderId; }
    public void setFollowCommanderId(@Nullable java.util.UUID id) { followCommanderId = id; }

    /** Only the current leader can order a loaded defender. */
    public boolean commandFollow(Player player, boolean follow) {
        if (level().isClientSide || !isAlive() || !job.isCombat || player.level() != level()) return false;
        var colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
        if (colony == null || !colony.isLeader(player)) return false;
        if (jobInstance == null) jobInstance = createJobInstance(job);
        if (!(jobInstance instanceof SoldierJob soldier)) return false;
        soldier.setCommander(player, follow);
        return true;
    }

    public boolean isFollowingCommander() {
        return job.isCombat && followCommanderId != null;
    }

    // Vanilla ResetProfession/AssignProfessionFromJobSite must not overwrite a colony job.
    @Override
    public void setVillagerData(VillagerData data) {
        if (!level().isClientSide && colonyId != -1 && job != null
                && data.getProfession() != ColonyProfessions.profession(job)) {
            suppressVanillaBrainReset = true;
            return;
        }
        super.setVillagerData(data);
    }

    @Override
    public void refreshBrain(ServerLevel level) {
        if (suppressVanillaBrainReset) {
            suppressVanillaBrainReset = false;
            return;
        }
        super.refreshBrain(level);
    }

    private void clearWorkMemories() {
        releasePoi(MemoryModuleType.JOB_SITE);
        releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    private void syncProfession(boolean resetLevel) {
        var expected = ColonyProfessions.profession(job);
        if (getVillagerData().getProfession() != expected) {
            // Release a pre-existing vanilla ticket using its old profession first.
            clearWorkMemories();
            setVillagerXp(0);
            super.setVillagerData(getVillagerData().setProfession(expected).setLevel(1));
            suppressVanillaBrainReset = false;
            if (level() instanceof ServerLevel serverLevel) super.refreshBrain(serverLevel);
        } else if (resetLevel) {
            super.setVillagerData(getVillagerData().setLevel(1));
        }
    }

    private boolean updateWorkstation(ServerLevel level, Colony colony) {
        syncProfession(false);
        // Legacy potential sites may have been claimed before conversion.
        if (getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)) {
            releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
            getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        }
        if (job == JobType.UNEMPLOYED || isBaby()) return false;
        var site = getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isPresent()) {
            var pos = site.get();
            if (!pos.dimension().equals(level.dimension())) { clearWorkMemories(); return false; }
            if (!level.isLoaded(pos.pos())) return false; // Don't load chunks or discard a valid ticket.
            if (colony.isInside(pos.pos()) && level.getPoiManager().getType(pos.pos())
                    .filter(getVillagerData().getProfession().heldJobSite()).isPresent()) return true;
            clearWorkMemories();
        }
        if (level.getGameTime() % 40 != 0) return false;
        var found = level.getPoiManager().take(getVillagerData().getProfession().heldJobSite(),
                (holder, pos) -> level.isLoaded(pos) && colony.isInside(pos), colony.getCenter(), colony.getRadius());
        found.ifPresent(pos -> getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), pos)));
        return found.isPresent();
    }

    /** Colonists must not destroy their own or nearby player farmland just by
     * navigating across a field. This does not change player or animal rules. */
    @Override
    public boolean canTrample(net.minecraft.world.level.block.state.BlockState state,
                              net.minecraft.core.BlockPos pos, float fallDistance) {
        return false;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && level() instanceof ServerLevel sl && colonyId != -1
                && sl.dimension().equals(Level.OVERWORLD)) {
            ColonyManager.getInstance().getColony(colonyId).ifPresent(c -> updateWorkstation(sl, c));
        }
        super.tick();
        // Lazy recreate job instance after reload if null
        if (jobInstance == null && job != JobType.UNEMPLOYED) {
            jobInstance = createJobInstance(job);
        }
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
            if (colony == null) return;

            boolean homeDimension = sl.dimension().equals(Level.OVERWORLD);
            // Colony territory and workstations exist only in the Overworld.
            // A defender may still follow a valid commander in another dimension.
            if (homeDimension && sl.isNight() && !job.isCombat && !isTrading()) {
                seekShelter(sl, colony);
            } else if (!isBaby() && jobInstance != null) {
                if (job.isCombat) {
                    if (homeDimension || (jobInstance instanceof SoldierJob soldier && soldier.hasValidCommander(sl)))
                        jobInstance.tick(sl);
                } else if (homeDimension && !isInDanger()) {
                    if (updateWorkstation(sl, colony)) jobInstance.tick(sl);
                    else jobInstance.tickWithoutWorksite(sl);
                }
            }

            if (homeDimension && !sl.isNight()) trySocialize(sl);
            colony.getCitizens().stream().filter(c -> c.uuid.equals(getUUID())).findFirst()
                    .ifPresent(c -> {
                        if (c.isChild != isBaby()) {
                            c.isChild = isBaby();
                            ColonyManager.getInstance().markDirty(colony);
                        }
                        applyPendingDailyHealth(c);
                    });
            // Show job icon above head if client config enabled
            // (handled via custom renderer; placeholder is nameplate)
        }
    }

    /** Brief idle conversations use vanilla gossip rather than inventing reputation. */
    void trySocialize(ServerLevel level) {
        if (!availableForConversation(level)) return;
        var other = level.getEntitiesOfClass(ColonyVillager.class, getBoundingBox().inflate(8),
                v -> v != this && v.isAlive() && v.colonyId == colonyId && v.availableForConversation(level))
                .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        nextConversationTime = level.getGameTime() + 200;
        if (other == null || distanceToSqr(other) > 64) {
            // An optional late-afternoon gathering gives idle citizens a real
            // chance to meet; no one is teleported or pulled away from work.
            long hour = Math.floorMod(level.getDayTime(), 24000L);
            if (hour >= 9000 && hour < 11500)
                ColonyManager.getInstance().getColony(colonyId).ifPresent(c -> seekCivicMeeting(level, c));
            return;
        }
        if (distanceToSqr(other) > 9 || !hasLineOfSight(other)) {
            // Let the vanilla brain walk toward an idle neighbor, so door handling
            // and unreachable paths keep their ordinary Minecraft behavior.
            getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(other, 0.5f, 2));
            nextConversationTime = level.getGameTime() + 100;
            return;
        }
        nextConversationTime = other.nextConversationTime = level.getGameTime() + 1200;
        getLookControl().setLookAt(other);
        other.getLookControl().setLookAt(this);
        gossip(level, other, level.getGameTime());
        playSound(net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, 0.5f, 1.0f);
        other.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, 0.5f, 1.1f);
    }

    /** Walk to a reachable, enclosed civic hall; marker records alone are not proof of safety. */
    void seekCivicMeeting(ServerLevel level, Colony colony) {
        if (isBaby() || isTrading() || isSleeping() || !getNavigation().isDone()
                || getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                || isInDanger()) return;
        var halls = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.MARKET
                        && b.getState() == com.simplecolonies.building.BuildingState.COMPLETE
                        && level.isLoaded(b.getPos()) && b.getPos().distSqr(blockPosition()) <= 24 * 24)
                .sorted(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(blockPosition()))).toList();
        for (var hall : halls) {
            var target = hall.getPos();
            if (target.distSqr(blockPosition()) <= 9 || !usableShelterSpot(level, target) || !ShelterSafety.enclosed(level, target)) continue;
            if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
                ground.setCanOpenDoors(true); ground.setCanPassDoors(true);
            }
            var path = getNavigation().createPath(target, 0);
            if (path == null || !path.canReach()) continue;
            getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(target, 0.5f, 0));
            return;
        }
    }

    private boolean availableForConversation(ServerLevel level) {
        if (colonyId == -1 || isBaby() || isTrading() || isSleeping() || level.getGameTime() < nextConversationTime
                || !getNavigation().isDone() || getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                || isInDanger()) return false;
        return jobInstance == null || (jobInstance.getState() != AbstractJob.JobState.WORKING
                && jobInstance.getState() != AbstractJob.JobState.GOING_TO_WORK);
    }

    void seekShelter(ServerLevel level, Colony colony) {
        // Do not interrupt panic, trading or sleep with shelter navigation.
        if (isTrading() || isSleeping() || isInDanger()) {
            shelterDoors.clear();
            return;
        }
        rememberAndCloseShelterDoors(level, colony);
        if (level.getGameTime() < nextShelterAttempt) {
            redirectShelteredWander(level, colony);
            return;
        }
        // Ground navigation cannot create a path while airborne. Do not burn
        // a whole retry interval just because this tick coincided with a jump.
        if (!onGround() && !isInLiquid() && !isPassenger()) return;
        nextShelterAttempt = level.getGameTime() + 60;
        shelterInterior = java.util.List.of();
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanPassDoors(true);
        }
        // Markers can refer to destroyed, obstructed or inaccessible buildings. Try other
        // completed shelters instead of repeatedly walking toward the nearest marker.
        var record = colony.getCitizens().stream().filter(c -> c.uuid.equals(getUUID())).findFirst().orElse(null);
        var home = record == null ? null : record.home;
        var shelters = colony.getBuildings().stream()
                .filter(com.simplecolonies.building.Building::isOperational)
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL
                        || b.getType() == com.simplecolonies.building.BuildingType.HOUSE)
                .flatMap(b -> b.shelterSites().stream().map(site -> java.util.Map.entry(b, site)))
                .filter(entry -> level.isLoaded(entry.getValue()))
                .sorted(java.util.Comparator.<java.util.Map.Entry<com.simplecolonies.building.Building, BlockPos>>comparingInt(entry -> entry.getValue().equals(home) ? 0 : 1)
                        .thenComparingDouble(entry -> entry.getValue().distSqr(blockPosition())))
                .toList();
        for (var entry : shelters) {
            var shelter = entry.getKey();
            var anchor = entry.getValue();
            int y = shelter.isPlayerPlaced ? anchor.getY() : anchor.getY() + shelter.constructionWalkY() - shelter.getPos().getY();
            var candidates = new java.util.ArrayList<net.minecraft.core.BlockPos>();
            // The central 3x3 is inside all generated shells, including old houses.
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var pos = new net.minecraft.core.BlockPos(anchor.getX() + x, y, anchor.getZ() + z);
                if (usableShelterSpot(level, pos) && ShelterSafety.enclosed(level, pos)) candidates.add(pos);
            }
            candidates.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(blockPosition())));
            // Do not replace a still-valid shelter walk every 60 ticks. Let the
            // vanilla brain own its PATH so its door behavior can open and close.
            var existing = getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            if (existing.isPresent() && candidates.contains(existing.get().getTarget().currentBlockPosition())
                    && !getNavigation().isDone()) {
                setShelterCache(anchor, candidates);
                return;
            }
            boolean alreadySheltered = candidates.contains(blockPosition());
            boolean leaving = existing.isPresent() && !candidates.contains(existing.get().getTarget().currentBlockPosition());
            for (var target : candidates) {
                // A vanilla wander target can still point outside after arrival.
                // Let the brain walk to another indoor spot instead of treating
                // the current position as proof that this walk will stay safe.
                if (blockPosition().equals(target)) {
                    if (!leaving) {
                        setShelterCache(anchor, candidates);
                        return;
                    }
                    continue;
                }
                var path = getNavigation().createPath(target, 0);
                if (path != null && path.canReach()) {
                    setShelterCache(anchor, candidates);
                    getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new net.minecraft.world.entity.ai.memory.WalkTarget(target, 0.7f, 0));
                    return;
                }
            }
            if (alreadySheltered && leaving) {
                setShelterCache(anchor, candidates);
                // If no alternative indoor path exists, ask the vanilla brain
                // to stop at the current safe spot rather than leave the shelter.
                getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new net.minecraft.world.entity.ai.memory.WalkTarget(blockPosition(), 0.7f, 0));
                return;
            }
        }
        shelterAnchor = null;
        shelterDoors.clear();
    }

    boolean remembersShelterDoor(net.minecraft.core.BlockPos pos) { return shelterDoors.containsKey(pos); }

    String shelterDiagnostic() {
        return "anchor=" + shelterAnchor + "; remembered=" + shelterDoors
                + "; inside=" + shelterInterior.contains(blockPosition()) + "; danger=" + isInDanger();
    }

    private void setShelterCache(net.minecraft.core.BlockPos anchor, java.util.List<net.minecraft.core.BlockPos> candidates) {
        if (!java.util.Objects.equals(shelterAnchor, anchor)) shelterDoors.clear();
        shelterAnchor = anchor;
        shelterInterior = java.util.List.copyOf(candidates);
    }

    /** Close only a nearby wooden door that vanilla recorded this villager
     * opening on the way to its shelter. Never interfere with redstone,
     * nearby players, occupied doorways, panic, or an invalid enclosure. */
    private void rememberAndCloseShelterDoors(ServerLevel level, Colony colony) {
        long now = level.getGameTime();
        // Drop ownership if the door was closed, replaced, unloaded, or a
        // player approached it. Never adopt a later reopening while vanilla's
        // original memory is still present.
        shelterDoors.entrySet().removeIf(e -> {
            var p = e.getKey();
            if (e.getValue() < now || !level.isLoaded(p) || !level.isLoaded(p.above())) return true;
            var state = level.getBlockState(p);
            return !(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    || !state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)
                    || !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                    || state.getValue(net.minecraft.world.level.block.DoorBlock.POWERED)
                    || level.hasNeighborSignal(p) || level.hasNeighborSignal(p.above())
                    || !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                            new net.minecraft.world.phys.AABB(p).expandTowards(0, 1, 0).inflate(4)).isEmpty();
        });
        var doors = getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE).orElse(java.util.Set.of());
        observedDoorMemories.retainAll(doors);
        if (shelterAnchor == null) {
            observedDoorMemories.addAll(doors);
            return;
        }
        for (var door : doors) {
            var p = door.pos();
            if (!observedDoorMemories.add(door) || !door.dimension().equals(level.dimension())
                    || Math.abs(p.getX() - shelterAnchor.getX()) > 7
                    || Math.abs(p.getZ() - shelterAnchor.getZ()) > 7 || Math.abs(p.getY() - shelterAnchor.getY()) > 4
                    || !level.isLoaded(p) || !level.isLoaded(p.above())) continue;
            var state = level.getBlockState(p);
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)
                    && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                    && level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                            new net.minecraft.world.phys.AABB(p).expandTowards(0, 1, 0).inflate(4)).isEmpty())
                // A blocked or circuitous route can take more than 600 ticks.
                // Retain a single observed opening for at most two minutes;
                // closures, replacement, redstone and nearby players still revoke it.
                shelterDoors.put(p.immutable(), now + 2400);
        }
        if (shelterDoors.isEmpty() || !shelterInterior.contains(blockPosition())) return;
        boolean registered = colony.getBuildings().stream().anyMatch(b -> b.shelterSites().contains(shelterAnchor)
                && b.isOperational()
                && (b.getType() == com.simplecolonies.building.BuildingType.HOUSE
                    || b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL));
        if (!registered || !usableShelterSpot(level, blockPosition()) || !ShelterSafety.enclosed(level, blockPosition())) return;
        var iterator = shelterDoors.entrySet().iterator();
        while (iterator.hasNext()) {
            var p = iterator.next().getKey();
            if (!level.isLoaded(p) || !level.isLoaded(p.above())) continue;
            var state = level.getBlockState(p);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door)
                    || !state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) { iterator.remove(); continue; }
            if (!state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) { iterator.remove(); continue; }
            if (state.getValue(net.minecraft.world.level.block.DoorBlock.POWERED)
                    || level.hasNeighborSignal(p) || level.hasNeighborSignal(p.above())) continue;
            var doorway = new net.minecraft.world.phys.AABB(p).expandTowards(0, 1, 0);
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, doorway.inflate(1)).isEmpty()
                    || !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, doorway.inflate(4)).isEmpty()) continue;
            door.setOpen(this, level, state, p, false);
            iterator.remove();
        }
    }

    /** Avoid a 60-tick gap in which vanilla idle wandering can leave a shelter.
     * Never replace panic, trading or sleep; recheck the actual enclosure and
     * only use a neighboring safe interior spot or the villager's own position. */
    private void redirectShelteredWander(ServerLevel level, Colony colony) {
        var here = blockPosition();
        if (shelterAnchor == null || !shelterInterior.contains(here)) return;
        var walk = getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (walk.isEmpty() || shelterInterior.contains(walk.get().getTarget().currentBlockPosition())) return;
        boolean registered = colony.getBuildings().stream().anyMatch(b -> b.shelterSites().contains(shelterAnchor)
                && b.isOperational()
                && (b.getType() == com.simplecolonies.building.BuildingType.HOUSE
                    || b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL));
        if (!registered || !usableShelterSpot(level, here) || !ShelterSafety.enclosed(level, here)) return;
        var target = shelterInterior.stream().filter(p -> !p.equals(here)
                && Math.abs(p.getX() - here.getX()) + Math.abs(p.getY() - here.getY()) + Math.abs(p.getZ() - here.getZ()) == 1)
                .filter(p -> usableShelterSpot(level, p) && ShelterSafety.enclosed(level, p))
                .findFirst().orElse(here);
        getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new net.minecraft.world.entity.ai.memory.WalkTarget(target, 0.7f, 0));
    }

    private static boolean usableShelterSpot(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (!level.isLoaded(pos.below()) || !level.isLoaded(pos.above(5))) return false;
        var floor = level.getBlockState(pos.below());
        if (!floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                || floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return false;
        // A solid ceiling near the standing position is required; a bare marker or
        // a roof that has been removed is not shelter. This is not an enclosure proof.
        for (int dy = 2; dy <= 5; dy++) {
            var roof = pos.above(dy);
            if (level.getBlockState(roof).isFaceSturdy(level, roof, net.minecraft.core.Direction.DOWN)) return true;
        }
        return false;
    }

    /** Apply exactly the days simulated by the colony, even after a chunk reload. */
    public void applyPendingDailyHealth(CitizenData citizen) {
        var pending = citizen.takePendingHealthDays();
        if (pending.isEmpty()) return;
        for (byte starving : pending) {
            if (!isAlive()) break;
            if (daysAlive < Integer.MAX_VALUE) daysAlive++;
            if (starving != 0) {
                if (hungerTicks < Integer.MAX_VALUE) hungerTicks++;
                if (hungerTicks > 3) {
                    // Queued entries represent separate days, not simultaneous
                    // attacks. Vanilla hurt immunity must not collapse them into
                    // a single hit when an unloaded citizen is restored.
                    int previousImmunity = invulnerableTime;
                    invulnerableTime = 0;
                    try { hurt(damageSources().starve(), 2.0f); }
                    finally { invulnerableTime = Math.max(previousImmunity, invulnerableTime); }
                    addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 6000, 0));
                }
            } else {
                hungerTicks = 0;
                heal(1.0f);
            }
        }
        ColonyManager.getInstance().getColony(colonyId).ifPresent(ColonyManager.getInstance()::markDirty);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
        // Hold shift -> open custom villager GUI (playable job assign)
        if (player.isShiftKeyDown()) {
            if (level().isClientSide) return InteractionResult.SUCCESS;
            if (colony == null) {
                player.displayClientMessage(Component.literal("§7This villager is not in a colony (wanderer). Claim with Town Hall."), true);
                return InteractionResult.SUCCESS;
            }
            // Allow anyone with >0 rep to view, leader/high rep to assign
            CitizenData citizen = colony.getCitizens().stream().filter(c -> c.uuid.equals(getUUID())).findFirst().orElse(null);
            if (citizen == null) {
                // Create missing record
                citizen = new CitizenData(getUUID(), getName().getString(), job);
                colony.addCitizen(citizen);
            }
            final CitizenData fCitizen = citizen;
            final Colony fColony = colony;
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider((cid, inv, ply) -> new VillagerInteractionMenu(cid, inv, getId(), fColony, fCitizen),
                        Component.literal(fCitizen.name + " - " + job.displayName)),
                        buf -> {
                            buf.writeInt(getId());
                            buf.writeInt(fColony.getId());
                            buf.writeNbt(fColony.save());
                            buf.writeNbt(fCitizen.save());
                        });
            }
            return InteractionResult.SUCCESS;
        }
        // Normal right-click: keep vanilla trading if villager has trades (we give default trades per job)
        // If colony villager, still allow vanilla trade plus show hint
        if (!level().isClientSide && colony != null && player instanceof ServerPlayer sp) {
            // Give hint once
            // sp.displayClientMessage(Component.literal("§7Shift+right-click to manage job/commands"), true);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("colonyId", colonyId);
        tag.putString("colonyJob", job.name());
        if (job.isCombat && followCommanderId != null) tag.putUUID("followCommander", followCommanderId);
        tag.putInt("hungerTicks", hungerTicks);
        tag.putInt("daysAlive", daysAlive);
        if (!courierCargo.isEmpty()) tag.put("courierCargo", courierCargo.save(registryAccess()));
        if (!courierCargoExtra.isEmpty()) tag.put("courierCargoExtra", courierCargoExtra.save(registryAccess()));
        if (!workSupplies.isEmpty()) tag.put("workSupplies", workSupplies.save(registryAccess()));
        if (!plantingSeeds.isEmpty()) tag.put("plantingSeeds", plantingSeeds.save(registryAccess()));
        if (!builderMaterials.isEmpty()) tag.put("builderMaterials", builderMaterials.createTag(registryAccess()));
        if (!minerMemory.isEmpty()) tag.put("minerMemory", minerMemory.copy());
        if (!woodcutterMemory.isEmpty()) tag.put("woodcutterMemory", woodcutterMemory.copy());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        colonyId = tag.getInt("colonyId");
        if (tag.contains("colonyJob")) job = JobType.valueOf(tag.getString("colonyJob"));
        followCommanderId = job.isCombat && tag.hasUUID("followCommander") ? tag.getUUID("followCommander") : null;
        jobInstance = null;
        hungerTicks = tag.getInt("hungerTicks");
        daysAlive = tag.getInt("daysAlive");
        minerMemory = tag.getCompound("minerMemory").copy();
        woodcutterMemory = tag.getCompound("woodcutterMemory").copy();
        courierCargo = tag.contains("courierCargo")
                ? net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("courierCargo"))
                : net.minecraft.world.item.ItemStack.EMPTY;
        courierCargoExtra = tag.contains("courierCargoExtra")
                ? net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("courierCargoExtra"))
                : net.minecraft.world.item.ItemStack.EMPTY;
        workSupplies = tag.contains("workSupplies")
                ? net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("workSupplies"))
                : net.minecraft.world.item.ItemStack.EMPTY;
        plantingSeeds = tag.contains("plantingSeeds")
                ? net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("plantingSeeds"))
                : net.minecraft.world.item.ItemStack.EMPTY;
        builderMaterials.fromTag(tag.getList("builderMaterials", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide) {
            dropCourierCargo();
            ColonyManager.getInstance().getColony(colonyId).ifPresent(c -> c.removeCitizen(getUUID()));
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Colony villagers never despawn
        return colonyId == -1 && super.removeWhenFarAway(distanceToClosestPlayer);
    }
}
