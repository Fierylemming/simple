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
    // A shared house assignment; null means the citizen may shelter in the hall.
    public net.minecraft.core.BlockPos home;
    // Run-length encoded daily effects retain ordering while allowing large time
    // skips without storing one boxed byte per citizen per simulated day.
    private record HealthRun(byte starving, long days) {}
    private final java.util.Deque<HealthRun> pendingHealthDays = new java.util.ArrayDeque<>();

    public void queueDailyHealth(boolean starving) { addHealthDays((byte) (starving ? 1 : 0), 1); }
    private void addHealthDays(byte starving, long days) {
        if (days <= 0) return;
        starving = (byte) (starving == 0 ? 0 : 1);
        var last = pendingHealthDays.peekLast();
        if (last != null && last.starving == starving && last.days <= Long.MAX_VALUE - days) {
            pendingHealthDays.removeLast(); days += last.days;
        }
        pendingHealthDays.addLast(new HealthRun(starving, days));
    }
    /** Bound entity work after reload; unprocessed days remain queued and saved. */
    public java.util.List<Byte> takePendingHealthDays() {
        var result = new java.util.ArrayList<Byte>();
        while (result.size() < 64 && !pendingHealthDays.isEmpty()) {
            var run = pendingHealthDays.removeFirst();
            int count = (int) Math.min(run.days, 64 - result.size());
            for (int i = 0; i < count; i++) result.add(run.starving);
            if (run.days > count) pendingHealthDays.addFirst(new HealthRun(run.starving, run.days - count));
        }
        return result;
    }

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
        if (home != null) tag.putLong("home", home.asLong());
        var runs = new net.minecraft.nbt.ListTag();
        for (var run : pendingHealthDays) {
            var entry = new CompoundTag();
            entry.putBoolean("starving", run.starving != 0); entry.putLong("days", run.days);
            runs.add(entry);
        }
        tag.put("pendingHealthRuns", runs);
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
        if (tag.contains("home")) d.home = net.minecraft.core.BlockPos.of(tag.getLong("home"));
        if (tag.contains("pendingHealthRuns", net.minecraft.nbt.Tag.TAG_LIST)) {
            var runs = tag.getList("pendingHealthRuns", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < runs.size(); i++) {
                var run = runs.getCompound(i);
                d.addHealthDays((byte) (run.getBoolean("starving") ? 1 : 0), run.getLong("days"));
            }
        } else {
            // Old worlds used one byte per pending day.
            for (byte day : tag.getByteArray("pendingHealthDays")) d.addHealthDays(day, 1);
        }
        return d;
    }
}
