package com.simplecolonies.villager;

/** Formats a villager name without treating player-visible names as regular expressions. */
public final class VillagerNames {
    private VillagerNames() {}

    public static String withJob(String currentName, String jobName) {
        int suffix = currentName.indexOf(" [");
        String base = suffix < 0 ? currentName : currentName.substring(0, suffix);
        return base + " [" + jobName + "]";
    }
}
