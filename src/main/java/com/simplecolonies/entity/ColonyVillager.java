package com.simplecolonies.entity;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.menu.VillagerInteractionMenu;
import com.simplecolonies.villager.job.*;
import net.minecraft.nbt.CompoundTag;
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
    public boolean isColonyCitizen() { return colonyId != -1; }

    public void setColony(Colony colony) {
        this.colonyId = colony.getId();
        // Avoid duplicate citizen entry if already exists
        boolean exists = colony.getCitizens().stream().anyMatch(c -> c.uuid.equals(getUUID()));
        if (!exists) {
            colony.addCitizen(new CitizenData(getUUID(), getName().getString(), job));
        }
    }

    public void setJob(JobType newJob) {
        this.job = newJob;
        this.jobInstance = createJobInstance(newJob);
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
        setCustomName(Component.literal(getName().getString().split(" \\[")[0] + " [" + newJob.displayName + "]"));
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
            default -> null;
        };
    }

    @Override
    public void tick() {
        super.tick();
        // Lazy recreate job instance after reload if null
        if (jobInstance == null && job != JobType.UNEMPLOYED) {
            jobInstance = createJobInstance(job);
        }
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
            if (colony == null) return;

            // Job tick
            if (jobInstance != null) jobInstance.tick(sl);

            // Hunger / health daily
            if (level().getGameTime() % 24000 == 0) {
                daysAlive++;
                if (colony.getState() == com.simplecolonies.colony.ColonyState.STARVING) {
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
            // Show job icon above head if client config enabled
            // (handled via custom renderer; placeholder is nameplate)
        }
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        colonyId = tag.getInt("colonyId");
        if (tag.contains("colonyJob")) job = JobType.valueOf(tag.getString("colonyJob"));
        hungerTicks = tag.getInt("hungerTicks");
        daysAlive = tag.getInt("daysAlive");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Colony villagers never despawn
        return colonyId == -1 && super.removeWhenFarAway(distanceToClosestPlayer);
    }
}
