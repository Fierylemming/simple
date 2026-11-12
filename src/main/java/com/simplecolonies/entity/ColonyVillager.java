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
    private CompoundTag minerMemory = new CompoundTag();
    private CompoundTag woodcutterMemory = new CompoundTag();
    private long nextConversationTime;

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
        if (job != JobType.UNEMPLOYED && !getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return "WAITING_FOR_WORKSTATION";
        return jobInstance == null ? "IDLE" : jobInstance.getState().name();
    }
    public boolean isColonyCitizen() { return colonyId != -1; }
    public net.minecraft.world.item.ItemStack getCourierCargo() { return courierCargo; }
    public void setCourierCargo(net.minecraft.world.item.ItemStack cargo) { courierCargo = cargo; }
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
        boolean exists = colony.getCitizens().stream().anyMatch(c -> c.uuid.equals(getUUID()));
        if (!exists) {
            colony.addCitizen(new CitizenData(getUUID(), getName().getString(), job));
        }
    }

    public void setJob(JobType newJob) {
        boolean changed = this.job != newJob;
        if (changed && !level().isClientSide) {
            if (!courierCargo.isEmpty()) {
                spawnAtLocation(courierCargo);
                courierCargo = net.minecraft.world.item.ItemStack.EMPTY;
            }
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
            case COURIER -> new CourierJob(this, colony);
            default -> null;
        };
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
            } else if (!isBaby() && jobInstance != null && updateWorkstation(sl, colony)) {
                jobInstance.tick(sl);
            }

            if (!sl.isNight()) trySocialize(sl);
            colony.getCitizens().stream().filter(c -> c.uuid.equals(getUUID())).findFirst()
                    .ifPresent(this::applyPendingDailyHealth);
            // Show job icon above head if client config enabled
            // (handled via custom renderer; placeholder is nameplate)
        }
    }

    /** Brief idle conversations use vanilla gossip rather than inventing reputation. */
    void trySocialize(ServerLevel level) {
        if (colonyId == -1 || isBaby() || isTrading() || isSleeping() || level.getGameTime() < nextConversationTime
                || !getNavigation().isDone() || (jobInstance != null && jobInstance.getState() == AbstractJob.JobState.WORKING)) return;
        var other = level.getEntitiesOfClass(ColonyVillager.class, getBoundingBox().inflate(3),
                v -> v != this && v.isAlive() && v.colonyId == colonyId && !v.isBaby() && !v.isTrading()
                        && !v.isSleeping() && v.getNavigation().isDone() && level.getGameTime() >= v.nextConversationTime
                        && (v.jobInstance == null || v.jobInstance.getState() != AbstractJob.JobState.WORKING))
                .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        nextConversationTime = level.getGameTime() + 200;
        if (other == null || distanceToSqr(other) > 9 || !hasLineOfSight(other)) return;
        nextConversationTime = other.nextConversationTime = level.getGameTime() + 1200;
        getLookControl().setLookAt(other);
        other.getLookControl().setLookAt(this);
        gossip(level, other, level.getGameTime());
        playSound(net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, 0.5f, 1.0f);
        other.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, 0.5f, 1.1f);
    }

    void seekShelter(ServerLevel level, Colony colony) {
        if (level.getGameTime() % 60 != 0) return;
        var shelter = colony.getBuildings().stream()
                .filter(b -> b.getState() == com.simplecolonies.building.BuildingState.COMPLETE)
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL
                        || b.getType() == com.simplecolonies.building.BuildingType.HOUSE)
                .filter(b -> level.isLoaded(b.getPos()))
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(blockPosition())))
                .orElse(null);
        if (shelter == null) return;
        var target = shelter.getPos().offset(1, shelter.isPlayerPlaced ? 0 : 1, 0);
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanPassDoors(true);
        }
        if (distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > 2.25) {
            getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.7);
        }
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
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide) {
            if (!courierCargo.isEmpty()) {
                spawnAtLocation(courierCargo);
                courierCargo = net.minecraft.world.item.ItemStack.EMPTY;
            }
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
