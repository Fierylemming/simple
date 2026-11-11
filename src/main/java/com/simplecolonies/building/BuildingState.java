package com.simplecolonies.building;

public enum BuildingState {
    BLUEPRINT("Blueprint placed, waiting for resources"),
    NEEDS_RESOURCES("Missing resources - check Town Hall"),
    BLOCKED("Construction obstructed - clear the next placement"),
    UNDER_CONSTRUCTION("Builder is working"),
    COMPLETE("Operational"),
    DAMAGED("Needs repair - builder will fix");

    public final String description;
    BuildingState(String description) { this.description = description; }
}
