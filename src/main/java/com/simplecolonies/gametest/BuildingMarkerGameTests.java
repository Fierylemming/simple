package com.simplecolonies.gametest;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.menu.BuildingMarkerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class BuildingMarkerGameTests {
    @SuppressWarnings("unchecked")
    @GameTest(template = "empty")
    public static void breakingLinkedMarkerRemovesOnlyItsRecord(GameTestHelper helper) {
        // Register a fixture colony without invoking natural world generation.
        var manager = com.simplecolonies.colony.ColonyManager.getInstance();
        java.util.Map<Integer, Colony> colonies;
        try {
            var field = com.simplecolonies.colony.ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            colonies = (java.util.Map<Integer, Colony>) field.get(manager);
        } catch (ReflectiveOperationException e) { helper.fail("Cannot install test colony: " + e); return; }
        BlockPos pos = new BlockPos(8, 1, 8), absolute = helper.absolutePos(pos);
        int id = -1200000000;
        if (colonies.containsKey(id)) { helper.fail("Fixture colony id already in use"); return; }
        var colony = new Colony(id, CultureType.ROMAN, absolute);
        colonies.put(id, colony);
        try {
            helper.setBlock(pos, ModBlocks.BUILDING_MARKER.get());
            var marker = (BuildingMarkerBlockEntity) helper.getBlockEntity(pos);
            if (!colony.updateMarkerBuilding(absolute, false, BuildingType.WAREHOUSE)) helper.fail("Could not designate warehouse");
            marker.setBuildingType(BuildingType.WAREHOUSE); marker.setLinkedBuildingPos(absolute);
            if (!absolute.equals(colony.getWarehouseManager().getConfiguredWarehouseCenter())) helper.fail("Hub not set");
            helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
            if (!colony.getBuildings().isEmpty() || colony.getWarehouseManager().getConfiguredWarehouseCenter() != null)
                helper.fail("Breaking the linked marker left a phantom building or hub");
            if (!Colony.load(colony.save()).getBuildings().isEmpty()) helper.fail("Removed marker record returned after reload");
            helper.setBlock(pos, ModBlocks.BUILDING_MARKER.get());
            marker = (BuildingMarkerBlockEntity) helper.getBlockEntity(pos);
            if (!colony.updateMarkerBuilding(absolute, false, BuildingType.HOUSE)) helper.fail("Could not designate house");
            marker.setBuildingType(BuildingType.HOUSE); marker.setLinkedBuildingPos(absolute);
            var house = colony.getBuildings().getFirst();
            if (!house.beginWorksiteWing(absolute.east(4))) helper.fail("Could not start marker wing");
            var first = house.getNextPlacement();
            helper.getLevel().setBlock(first.pos, first.state, 3); house.markPlaced(first);
            helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
            if (!colony.getBuildings().isEmpty() || !helper.getLevel().getBlockState(first.pos).equals(first.state))
                helper.fail("Breaking a marker during construction left a phantom or deleted blocks");
        } finally { colonies.remove(id); manager.saveAll(); }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty")
    public static void plotProtectionAppliesAcrossColoniesAndEndsWhenMarkerBreaks(GameTestHelper helper) {
        var manager = com.simplecolonies.colony.ColonyManager.getInstance();
        java.util.Map<Integer, Colony> colonies;
        try {
            var field = com.simplecolonies.colony.ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            colonies = (java.util.Map<Integer, Colony>) field.get(manager);
        } catch (ReflectiveOperationException e) { helper.fail("Cannot install test colony: " + e); return; }
        var relative = new BlockPos(8, 1, 8); var absolute = helper.absolutePos(relative);
        int id = GameTestColonyIds.next();
        var owner = new Colony(id, CultureType.ROMAN, absolute);
        var neighbour = new Colony(GameTestColonyIds.next(), CultureType.ROMAN, absolute.east(18));
        colonies.put(id, owner);
        try {
            helper.setBlock(relative, ModBlocks.BUILDING_MARKER.get());
            var marker = (BuildingMarkerBlockEntity) helper.getBlockEntity(relative);
            if (!owner.updateMarkerBuilding(absolute, false, BuildingType.PROTECTED_PLOT)) helper.fail("Could not protect plot");
            marker.setBuildingType(BuildingType.PROTECTED_PLOT); marker.setLinkedBuildingPos(absolute);
            if (!neighbour.isProtectedPlot(absolute.east(8))) helper.fail("Neighbouring colony ignored protected plot");
            helper.setBlock(relative, net.minecraft.world.level.block.Blocks.AIR);
            if (neighbour.isProtectedPlot(absolute.east(8)) || !owner.getBuildings().isEmpty())
                helper.fail("Broken marker left plot protection behind");
        } finally { colonies.remove(id); manager.saveAll(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markerChangesAndClearsColonyBuilding(GameTestHelper helper) {
        BlockPos relative = new BlockPos(8, 1, 8), absolute = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.BUILDING_MARKER.get());
        var marker = (BuildingMarkerBlockEntity) helper.getBlockEntity(relative);
        Colony colony = new Colony(22, CultureType.ROMAN, absolute);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        var menu = new BuildingMarkerMenu(1, player.getInventory(), colony, absolute, null);
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_HOUSE);
        if (!colony.getBuildings().isEmpty()) helper.fail("Unprivileged player designated building");
        colony.addReputation(player, 25, "test");
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_HOUSE);
        if (colony.getBuildings().size() != 1 || colony.getBuildings().getFirst().getType() != BuildingType.HOUSE
                || !colony.getBuildings().getFirst().isPlayerPlaced || marker.getBuildingType() != BuildingType.HOUSE)
            helper.fail("House designation failed");
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_WAREHOUSE);
        if (colony.getBuildings().size() != 1 || colony.getBuildings().getFirst().getType() != BuildingType.WAREHOUSE
                || !absolute.equals(colony.getWarehouseManager().getConfiguredWarehouseCenter()))
            helper.fail("Marker did not replace house with warehouse");
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_MARKET);
        if (colony.getBuildings().getFirst().getType() != BuildingType.MARKET
                || colony.getWarehouseManager().getConfiguredWarehouseCenter() != null)
            helper.fail("Market selection or hub cleanup failed");
        Colony loaded = Colony.load(colony.save());
        if (loaded.getBuildings().size() != 1 || loaded.getBuildings().getFirst().getType() != BuildingType.MARKET)
            helper.fail("Marker building did not survive reload");
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_PASTURE);
        if (colony.getBuildings().size() != 1 || colony.getBuildings().getFirst().getType() != BuildingType.PASTURE
                || marker.getBuildingType() != BuildingType.PASTURE || !absolute.equals(marker.getLinkedBuildingPos()))
            helper.fail("Pasture designation failed");
        var savedPasture = Colony.load(colony.save());
        if (savedPasture.getBuildings().getFirst().getType() != BuildingType.PASTURE)
            helper.fail("Pasture designation did not survive reload");
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_PROTECTED_PLOT);
        if (marker.getBuildingType() != BuildingType.PROTECTED_PLOT || !colony.isProtectedPlot(absolute.offset(8, 100, -8))
                || colony.isProtectedPlot(absolute.offset(9, 0, 0))) helper.fail("Protected plot boundaries are incorrect");
        var savedProtection = Colony.load(colony.save());
        if (!savedProtection.isProtectedPlot(absolute.offset(-8, -100, 8))) helper.fail("Protected plot did not survive reload");
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_CLEAR);
        if (colony.isProtectedPlot(absolute)) helper.fail("Clearing marker left plot protection");
        if (!colony.getBuildings().isEmpty() || marker.getBuildingType() != null || marker.getLinkedBuildingPos() != null)
            helper.fail("Clear left a phantom building");
        var generated = new Building(BuildingType.HOUSE, absolute, CultureType.ROMAN);
        colony.registerPlayerBuilding(generated);
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_FARM);
        if (colony.getBuildings().size() != 1 || colony.getBuildings().getFirst() != generated)
            helper.fail("Marker overwrote an unrelated blueprint");
        // Even a forged or stale marker link cannot take over a completed generated house.
        generated.setState(com.simplecolonies.building.BuildingState.COMPLETE);
        marker.setBuildingType(BuildingType.HOUSE); marker.setLinkedBuildingPos(absolute);
        menu.clickMenuButton(player, BuildingMarkerMenu.BTN_SET_FARM);
        if (colony.getBuildings().size() != 1 || colony.getBuildings().getFirst() != generated)
            helper.fail("Stale marker overwrote a generated building");
        helper.succeed();
    }
}
