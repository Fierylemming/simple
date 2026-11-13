package com.simplecolonies.entity;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.menu.VillagerInteractionMenu;
import com.simplecolonies.villager.job.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.GlobalPos;
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
    private boolean suppressVanillaBrainReset;
    // The courier carries this actual stack across chunk unloads and restarts.
    private net.minecraft.world.item.ItemStack courierCargo = net.minecraft.world.item.ItemStack.EMPTY;
    private net.minecraft.world.item.ItemStack courierCargoExtra = net.minecraft.world.item.ItemStack.EMPTY;
    private CompoundTag minerMemory = new CompoundTag();
    private CompoundTag woodcutterMemory = new CompoundTag();
    private long nextConversationTime;
    private long nextShelterAttempt;

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
        var colonyOpt = ColonyManager.getInstance().getColonyAt(blockPosition());
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
    public net.minecraft.world.item.ItemStack getCourierCargo() { return courierCargo; }
    public void setCourierCargo(net.minecraft.world.item.ItemStack cargo) { courierCargo = cargo; }
    public net.minecraft.world.item.ItemStack getCourierCargoExtra() { return courierCargoExtra; }
    public void setCourierCargoExtra(net.minecraft.world.item.ItemStack cargo) { courierCargoExtra = cargo; }

    private void dropCourierCargo() {
        if (!courierCargo.isEmpty()) spawnAtLocation(courierCargo);
        if (!courierCargoExtra.isEmpty()) spawnAtLocation(courierCargoExtra);
        courierCargo = net.minecraft.world.item.ItemStack.EMPTY;
        courierCargoExtra = net.minecraft.world.item.ItemStack.EMPTY;
    }
    public CompoundTag getMinerMemory() { return minerMemory.copy(); }
    public void setMinerMemory(CompoundTag memory) { minerMemory = memory.copy(); }
    public CompoundTag getWoodcutterMemory() { return woodcutterMemory.copy(); }
    public void setWoodcutterMemory(CompoundTag memory) { woodcutterMemory = memory.copy(); }

    public void setColony(Colony colony) {
        if (this.colonyId != -1 && this.colonyId != colony.getId()) {
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
            // Keep real job tools (including their damage/components) when switching
            // professions, instead of leaving an incompatible tool in the main hand.
            var held = getMainHandItem();
            boolean oldTool = (job == JobType.LUMBERJACK && held.getItem() instanceof net.minecraft.world.item.AxeItem)
                    || (job == JobType.MINER && held.getItem() instanceof net.minecraft.world.item.PickaxeItem)
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
            default -> null;
        };
    }

    /** Leader-only, session-local follow command; does not force-load citizens. */
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
        return jobInstance instanceof SoldierJob soldier && soldier.isFollowing();
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

    @Override
    public void tick() {
        if (!level().isClientSide && level() instanceof ServerLevel sl && colonyId != -1) {
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

            // Non-combat citizens seek shelter at night, even when there are no beds.
            if (sl.isNight() && !job.isCombat && !isTrading()) {
                seekShelter(sl, colony);
            } else if (!isBaby() && jobInstance != null && (job.isCombat || updateWorkstation(sl, colony))) {
                jobInstance.tick(sl);
            }

            if (!sl.isNight()) trySocialize(sl);
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
                || getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)
                || getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)) return;
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
                || getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)
                || getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)) return false;
        return jobInstance == null || (jobInstance.getState() != AbstractJob.JobState.WORKING
                && jobInstance.getState() != AbstractJob.JobState.GOING_TO_WORK);
    }

    void seekShelter(ServerLevel level, Colony colony) {
        // Do not interrupt panic, trading or sleep with shelter navigation.
        if (isTrading() || isSleeping() || getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)
                || getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)) return;
        if (level.getGameTime() < nextShelterAttempt) return;
        // Ground navigation cannot create a path while airborne. Do not burn
        // a whole retry interval just because this tick coincided with a jump.
        if (!onGround() && !isInLiquid() && !isPassenger()) return;
        nextShelterAttempt = level.getGameTime() + 60;
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanPassDoors(true);
        }
        // Markers can refer to destroyed, obstructed or inaccessible buildings. Try other
        // completed shelters instead of repeatedly walking toward the nearest marker.
        var record = colony.getCitizens().stream().filter(c -> c.uuid.equals(getUUID())).findFirst().orElse(null);
        var home = record == null ? null : record.home;
        var shelters = colony.getBuildings().stream()
                .filter(b -> b.getState() == com.simplecolonies.building.BuildingState.COMPLETE)
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL
                        || b.getType() == com.simplecolonies.building.BuildingType.HOUSE)
                .filter(b -> level.isLoaded(b.getPos()))
                .sorted(java.util.Comparator.<com.simplecolonies.building.Building>comparingInt(b -> b.getPos().equals(home) ? 0 : 1)
                        .thenComparingDouble(b -> b.getPos().distSqr(blockPosition())))
                .toList();
        for (var shelter : shelters) {
            int y = shelter.isPlayerPlaced ? shelter.getPos().getY() : shelter.constructionWalkY();
            var candidates = new java.util.ArrayList<net.minecraft.core.BlockPos>();
            // The central 3x3 is inside all generated shells, including old houses.
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var pos = new net.minecraft.core.BlockPos(shelter.getPos().getX() + x, y, shelter.getPos().getZ() + z);
                if (usableShelterSpot(level, pos) && ShelterSafety.enclosed(level, pos)) candidates.add(pos);
            }
            candidates.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(blockPosition())));
            // Do not replace a still-valid shelter walk every 60 ticks. Let the
            // vanilla brain own its PATH so its door behavior can open and close.
            var existing = getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            if (existing.isPresent() && candidates.contains(existing.get().getTarget().currentBlockPosition())
                    && !getNavigation().isDone()) return;
            boolean alreadySheltered = candidates.contains(blockPosition());
            boolean leaving = existing.isPresent() && !candidates.contains(existing.get().getTarget().currentBlockPosition());
            for (var target : candidates) {
                // A vanilla wander target can still point outside after arrival.
                // Let the brain walk to another indoor spot instead of treating
                // the current position as proof that this walk will stay safe.
                if (blockPosition().equals(target)) {
                    if (!leaving) return;
                    continue;
                }
                var path = getNavigation().createPath(target, 0);
                if (path != null && path.canReach()) {
                    getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new net.minecraft.world.entity.ai.memory.WalkTarget(target, 0.7f, 0));
                    return;
                }
            }
            if (alreadySheltered && leaving) {
                // If no alternative indoor path exists, ask the vanilla brain
                // to stop at the current safe spot rather than leave the shelter.
                getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new net.minecraft.world.entity.ai.memory.WalkTarget(blockPosition(), 0.7f, 0));
                return;
            }
        }
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
            daysAlive++;
            if (starving != 0) {
                hungerTicks++;
                if (hungerTicks > 3) {
                    hurt(damageSources().starve(), 2.0f);
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
        tag.putInt("hungerTicks", hungerTicks);
        tag.putInt("daysAlive", daysAlive);
        if (!courierCargo.isEmpty()) tag.put("courierCargo", courierCargo.save(registryAccess()));
        if (!courierCargoExtra.isEmpty()) tag.put("courierCargoExtra", courierCargoExtra.save(registryAccess()));
        if (!minerMemory.isEmpty()) tag.put("minerMemory", minerMemory.copy());
        if (!woodcutterMemory.isEmpty()) tag.put("woodcutterMemory", woodcutterMemory.copy());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        colonyId = tag.getInt("colonyId");
        if (tag.contains("colonyJob")) job = JobType.valueOf(tag.getString("colonyJob"));
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
