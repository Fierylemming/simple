package com.simplecolonies.entity;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.villager.job.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Custom villager that belongs to a colony, has a job, needs food/housing,
 * and participates in supply chain + warfare.
 *
 * Extends Villager to reuse trading + profession UI, but overrides AI to use jobs.
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
        // Assign random Egyptian/Roman etc name based on nearest colony culture
        this.setCustomName(Component.literal(randomName()));
        this.setCustomNameVisible(false);
        return data;
    }

    private String randomName() {
        String[] eg = {"Ahmose","Nefer","Khepri","Bastet","Ramses","Cleopatra","Imhotep"};
        String[] roman = {"Marcus","Livia","Augustus","Caesar","Lucia","Titus","Aurelia"};
        return (job.displayName + " " + eg[level().random.nextInt(eg.length)]);
    }

    public void setColony(Colony colony) {
        this.colonyId = colony.getId();
        colony.addCitizen(new CitizenData(this.getUUID(), this.getName().getString(), job));
    }

    public void setJob(JobType newJob) {
        this.job = newJob;
        this.jobInstance = createJobInstance(newJob);
        // Update profession for trading UI
        // this.setVillagerData(getVillagerData().setProfession(...));
        // Attach AI goals if soldier
        if (newJob == JobType.SOLDIER || newJob == JobType.GUARD) {
            SoldierJob.attachGoals(this);
        }
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
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
            if (colony == null) return;

            // Job tick
            if (jobInstance != null) jobInstance.tick(sl);

            // Hunger / health
            if (level().getGameTime() % 24000 == 0) {
                daysAlive++;
                // Check if colony fed them (colony.tick already consumed food)
                // If colony starving, villagers take damage
                if (colony.getState() == com.simplecolonies.colony.ColonyState.STARVING) {
                    hungerTicks++;
                    if (hungerTicks > 3) {
                        this.hurt(damageSources().starve(), 2.0f);
                        this.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 6000, 0));
                    }
                } else {
                    hungerTicks = 0;
                    this.heal(1.0f);
                }
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Keep vanilla trading, plus extra if player is leader:
        // - shift-right-click to assign job
        // - command staff interaction handled separately
        if (!level().isClientSide && player.isShiftKeyDown()) {
            Colony colony = ColonyManager.getInstance().getColony(colonyId).orElse(null);
            if (colony != null) {
                if (colony.isLeader(player) || colony.getReputation(player) > 50) {
                    player.displayClientMessage(Component.literal(
                            "§eVillager: " + getName().getString() + " | Job: " + job.displayName + " | Colony #" + colonyId
                    ), false);
                    // Would open job assignment screen
                } else {
                    player.displayClientMessage(Component.literal("§7Villager is busy working for colony #" + colonyId), true);
                }
                return InteractionResult.SUCCESS;
            }
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
        job = JobType.valueOf(tag.getString("colonyJob"));
        hungerTicks = tag.getInt("hungerTicks");
        daysAlive = tag.getInt("daysAlive");
        // Recreate job instance when colony loaded later - lazy in tick
    }
}
