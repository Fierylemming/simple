package com.simplecolonies.colony;

public enum ColonyState {
    ACTIVE("Active - thriving"),
    STARVING("Starving - low food"),
    UNDER_ATTACK("Under attack"),
    ABANDONED("Abandoned - too many deaths"),
    DESTROYED("Destroyed in war");

    public final String description;
    ColonyState(String description) { this.description = description; }
}
