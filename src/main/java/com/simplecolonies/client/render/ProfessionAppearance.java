package com.simplecolonies.client.render;

/** Vanilla overlays reused for the custom colony professions. Null means no outfit. */
public final class ProfessionAppearance {
    public static String vanillaOverlay(String colonyJob) {
        return switch (colonyJob) {
            case "farmer", "baker" -> "farmer";
            case "lumberjack" -> "fletcher";
            case "miner", "builder" -> "mason";
            case "butcher" -> "butcher";
            case "leatherworker" -> "leatherworker";
            case "blacksmith" -> "toolsmith";
            case "soldier" -> "weaponsmith";
            case "guard" -> "armorer";
            case "fisherman" -> "fisherman";
            case "courier" -> "cartographer";
            default -> null;
        };
    }
    private ProfessionAppearance() {}
}
