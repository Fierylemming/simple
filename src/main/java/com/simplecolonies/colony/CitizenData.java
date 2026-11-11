package com.simplecolonies.colony;

import com.simplecolonies.villager.job.JobType;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Persistent data for one villager in a colony.
 * Stored inside Colony, not the entity itself, so colony survives villager death/respawn.
 */
public class CitizenData {
    public final UUID uuid; // villager entity UUID
    public String name;
    public JobType job;
    public int level; // job level 1-5 (apprentice -> master)
    public int health; // 0-20 tracked for starvation logic
    public int daysEmployed;
    public boolean isSoldier;
    public boolean isChild;

    public CitizenData(UUID uuid, String name, JobType job) {
        this.uuid = uuid;
        this.name = name;
        this.job = job;
        this.level = 1;
        this.health = 20;
        this.daysEmployed = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        tag.putString("name", name);
        tag.putString("job", job.name());
        tag.putInt("level", level);
        tag.putInt("health", health);
        tag.putInt("daysEmployed", daysEmployed);
        tag.putBoolean("isSoldier", isSoldier);
        tag.putBoolean("isChild", isChild);
        return tag;
    }

    public static CitizenData load(CompoundTag tag) {
        UUID uuid = tag.getUUID("uuid");
        String name = tag.getString("name");
        JobType job = JobType.valueOf(tag.getString("job"));
        CitizenData d = new CitizenData(uuid, name, job);
        d.level = tag.getInt("level");
        d.health = tag.contains("health") ? tag.getInt("health") : 20;
        d.daysEmployed = tag.getInt("daysEmployed");
        d.isSoldier = tag.getBoolean("isSoldier");
        d.isChild = tag.getBoolean("isChild");
        return d;
    }
}
