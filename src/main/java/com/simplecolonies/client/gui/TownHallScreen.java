package com.simplecolonies.client.gui;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingUpgrade;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.menu.TownHallMenu;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Town Hall Screen — now 6 tabs: Overview, Citizens, Buildings, Warehouse, Diplomacy, Research.
 * Progression: Research tab lets leader buy tool/profession upgrades; village self-progresses when it has mats.
 * Building upgrades: each building has 1 worker initially, upgrades add job blocks + size + material tier (Greek wood→stone→terracotta).
 * Create schematics: button in Buildings tab queues schematic if Create present.
 */
public class TownHallScreen extends AbstractContainerScreen<TownHallMenu> {
    private int currentTab = 0;
    private int citizenScroll = 0;
    private int researchScroll = 0;
    private final int imageW = 256;
    private final int imageH = 240;

    public TownHallScreen(TownHallMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = imageW;
        this.imageHeight = imageH;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = 999;
    }

    @Override
    protected void init() {
        super.init();
        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();
        int x = leftPos;
        int y = topPos;
        String[] tabs = {"Overview", "Citizens", "Buildings", "Warehouse", "Diplomacy", "Research"};
        for (int i = 0; i < tabs.length; i++) {
            int idx = i;
            String label = tabs[i];
            // Shorten for narrow tabs
            if (label.equals("Citizens")) label = "Citizens(" + menu.colony.citizenCount() + ")";
            Button tab = Button.builder(Component.literal(label), b -> { currentTab = idx; refreshWidgets(); })
                    .bounds(x + 4 + i * 42, y + 18, 40, 14)
                    .build();
            addRenderableWidget(tab);
        }
        int btnX = x + 8;
        int btnY = y + 38;

        switch (currentTab) {
            case 0 -> { // Overview
                boolean isLeader = menu.colony.isLeader(menu.player);
                addRenderableWidget(Button.builder(Component.literal("Donate 5 Bread"), b -> send(TownHallMenu.BTN_DONATE_FOOD)).bounds(btnX, btnY, 90, 16).build());
                addRenderableWidget(Button.builder(Component.literal(isLeader ? "Leader: You" : "Claim Leader " + menu.colony.getReputation(menu.player) + "/100"), b -> send(TownHallMenu.BTN_CLAIM_LEADER)).bounds(btnX + 95, btnY, 110, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Rally Soldiers"), b -> send(TownHallMenu.BTN_RALLY_SOLDIERS)).bounds(btnX, btnY + 18, 90, 16).build());
            }
            case 1 -> {
                var citizens = menu.getCitizensSorted();
                if (citizens.size() > 4) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b -> { if (citizenScroll > 0) citizenScroll--; refreshWidgets(); }).bounds(x + imageW - 22, btnY, 14, 14).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b -> { if (citizenScroll + 4 < citizens.size()) citizenScroll++; refreshWidgets(); }).bounds(x + imageW - 22, btnY + 50, 14, 14).build());
                }
            }
            case 2 -> {
                addRenderableWidget(Button.builder(Component.literal("House"), b -> send(TownHallMenu.BTN_ADD_BUILDING_HOUSE)).bounds(btnX, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Farm"), b -> send(TownHallMenu.BTN_ADD_BUILDING_FARM)).bounds(btnX + 52, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Lumber"), b -> send(TownHallMenu.BTN_ADD_BUILDING_LUMBER)).bounds(btnX + 104, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Mine"), b -> send(TownHallMenu.BTN_ADD_BUILDING_MINE)).bounds(btnX + 156, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Barracks"), b -> send(TownHallMenu.BTN_ADD_BUILDING_BARRACKS)).bounds(btnX, btnY + 18, 70, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Create Schematic"), b -> send(TownHallMenu.BTN_CREATE_SCHEMATIC)).bounds(btnX + 75, btnY + 18, 100, 16).build());
                // Upgrade buttons for each building (dynamic)
                var buildings = menu.colony.getBuildings();
                for (int i = 0; i < Math.min(buildings.size(), 6); i++) {
                    Building b = buildings.get(i);
                    int bx = btnX;
                    int by = btnY + 40 + i * 16;
                    int idx = i;
                    String label = "↑ " + b.getType().displayName + " Lv" + b.getLevel() + " (" + b.getWorkerSlots() + " workers)";
                    if (b.getLevel() >= 5) label += " MAX";
                    Button up = Button.builder(Component.literal(label.length() > 32 ? label.substring(0, 32) : label), btn -> send(TownHallMenu.BTN_UPGRADE_BUILDING_BASE + idx))
                            .bounds(bx, by, 200, 14).build();
                    addRenderableWidget(up);
                }
            }
            case 3 -> {}
            case 4 -> {
                addRenderableWidget(Button.builder(Component.literal("Declare War"), b -> send(TownHallMenu.BTN_DECLARE_WAR_NEXT)).bounds(btnX, btnY, 90, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Propose Alliance"), b -> send(TownHallMenu.BTN_PROPOSE_ALLIANCE)).bounds(btnX + 95, btnY, 110, 16).build());
            }
            case 5 -> {
                var available = menu.colony.getResearch().getAvailableToBuy();
                // Show 5 per page with scroll
                int start = researchScroll * 5;
                for (int i = 0; i < Math.min(5, available.size() - start); i++) {
                    ColonyUpgrade up = available.get(start + i);
                    int by = btnY + 20 + i * 22;
                    String costStr = up.cost.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().replace("minecraft:", "")).reduce((a,b) -> a + "," + b).orElse("free");
                    String label = up.displayName + (costStr.length() > 18 ? "" : " [" + costStr + "]");
                    Button buy = Button.builder(Component.literal(label), btn -> send(TownHallMenu.BTN_RESEARCH_BASE + up.ordinal())).bounds(btnX, by, 200, 18).build();
                    addRenderableWidget(buy);
                }
                if (available.size() > 5) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b -> { if (researchScroll > 0) researchScroll--; refreshWidgets(); }).bounds(x + imageW - 22, btnY, 14, 14).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b -> { if ((researchScroll+1)*5 < available.size()) researchScroll++; refreshWidgets(); }).bounds(x + imageW - 22, btnY + 70, 14, 14).build());
                }
            }
        }
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2316);
        gfx.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFEEE0C8);
        gfx.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 16, 0xFF3A2F1F);
        gfx.fill(leftPos + 6, topPos + 34, leftPos + imageWidth - 6, topPos + imageHeight - 10, 0xFFD8C49E);

        int x = leftPos + 10;
        int y = topPos + 36;
        var colony = menu.colony;
        var research = colony.getResearch();

        switch (currentTab) {
            case 0 -> {
                gfx.drawString(font, "Culture: " + colony.getCulture().displayName + "  Lv" + colony.getLevel() + " " + com.simplecolonies.colony.ColonyLevel.fromLevel(colony.getLevel()).displayName, x, y, 0x3A2F1F, false);
                gfx.drawString(font, "Center " + colony.getCenter().toShortString() + "  R=" + colony.getRadius() + "  Pop " + colony.citizenCount() + "/" + com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get(), x, y + 10, 0x3A2F1F, false);
                gfx.drawString(font, "State: " + colony.getState() + "  Rep " + colony.getReputation(menu.player) + "/100 " + (colony.isLeader(menu.player) ? " LEADER" : ""), x, y + 20, colony.getState() == com.simplecolonies.colony.ColonyState.STARVING ? 0xCC0000 : 0x3A2F1F, false);
                gfx.drawString(font, "Tool tier: " + research.getToolTier() + " (Wood0→Stone1→Iron2→Diamond3)  Speed ×" + String.format("%.2f", research.getWorkSpeedMultiplier()), x, y + 30, 0x3A2F1F, false);
                int housing = colony.getBuildings().stream().filter(b -> b.getType() == com.simplecolonies.building.BuildingType.HOUSE).mapToInt(b -> b.getHousingCapacity()).sum();
                gfx.drawString(font, "Housing: " + housing + " beds  Workers total slots: " + colony.getBuildings().stream().mapToInt(b -> b.getWorkerSlots()).sum(), x, y + 40, 0x3A2F1F, false);
                gfx.drawString(font, "Tip: Donate bread, do Research tab for tool upgrades.", x, y + 52, 0x6B5A3A, false);
                // Show unlocked count
                gfx.drawString(font, "Unlocked: " + research.getUnlocked().size() + " upgrades (farmer/lumberjack free)", x, y + 62, 0x6B5A3A, false);
            }
            case 1 -> {
                var citizens = menu.getCitizensSorted();
                if (citizens.isEmpty()) gfx.drawString(font, "No citizens. Village will spawn if housing+food.", x, y, 0x3A2F1F, false);
                else {
                    gfx.drawString(font, "Citizens — 1 per building initially, upgrades add slots:", x, y, 0x3A2F1F, false);
                    int ry = y + 12;
                    for (int i = 0; i < Math.min(4, citizens.size() - citizenScroll); i++) {
                        CitizenData c = citizens.get(citizenScroll + i);
                        int color = research.isProfessionUnlocked(c.job) ? 0x3A2F1F : 0xCC0000;
                        String locked = research.isProfessionUnlocked(c.job) ? "" : " [LOCKED need research]";
                        gfx.drawString(font, c.name + " [" + c.job.displayName + "] Lv" + c.level + locked, x, ry, color, false);
                        gfx.drawString(font, "H:" + c.health + " D:" + c.daysEmployed, x + 160, ry, 0x6B5A3A, false);
                        ry += 20;
                    }
                }
                gfx.drawString(font, "Shift+click villager in world to assign (if profession unlocked)", leftPos + 10, topPos + imageHeight - 14, 0x6B5A3A, false);
            }
            case 2 -> {
                gfx.drawString(font, "Buildings (" + colony.getBuildings().size() + ") — each has 1 worker, upgrade adds job blocks & size:", x, y, 0x3A2F1F, false);
                int ry = y + 12;
                if (colony.getBuildings().isEmpty()) gfx.drawString(font, "None. Queue below or place Building Marker on your build.", x, ry, 0x6B5A3A, false);
                else for (var b : colony.getBuildings()) {
                    String tierInfo = com.simplecolonies.building.MaterialProgression.get(b.getCulture(), Math.min(b.getLevel(),5)).lore();
                    gfx.drawString(font, b.getType().displayName + " Lv" + b.getLevel() + " (" + b.getWorkerSlots() + " workers, " + b.getSize() + "x" + b.getSize() + ") [" + b.getState() + "]", x, ry, b.getState() == com.simplecolonies.building.BuildingState.COMPLETE ? 0x2E7D32 : 0x8D6E63, false);
                    gfx.drawString(font, "  " + b.getPos().toShortString() + " " + tierInfo, x, ry + 10, 0x6B5A3A, false);
                    ry += 20;
                    if (ry > topPos + 140) break;
                }
                gfx.drawString(font, "↑ Upgrade buttons above — cost = base×level + tier mats (Greek wood→stone→terracotta).", x, topPos + imageHeight - 14, 0x6B5A3A, false);
                gfx.drawString(font, "Create schematic: put schematic in warehouse chest → button → builder pastes it.", x, topPos + imageHeight - 24, 0x3A5A6B, false);
            }
            case 3 -> {
                gfx.drawString(font, "Warehouse hub: " + colony.getWarehouseManager().getWarehouseCenter().toShortString() + " (" + colony.getWarehouseManager().getChestCount() + " chests)", x, y, 0x3A2F1F, false);
                var snap = colony.getWarehouseSnapshot(menu.player.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null);
                int ry = y + 12;
                if (snap.isEmpty()) gfx.drawString(font, "Empty — farmers/miners deposit to nearest village chest; couriers → hub.", x, ry, 0xCC0000, false);
                else {
                    var sorted = snap.entrySet().stream().sorted((a,b) -> b.getValue() - a.getValue()).toList();
                    int col = 0;
                    for (var e : sorted) {
                        gfx.drawString(font, e.getKey().replace("minecraft:", "") + ": " + e.getValue(), x + col * 110, ry, 0x3A2F1F, false);
                        col = (col+1)%2; if (col==0) ry+=10; if (ry > topPos+130) break;
                    }
                }
                gfx.drawString(font, "Need " + (int)Math.ceil(colony.citizenCount()*com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get()) + " food/day. Courier bulk 9 stacks/trip.", x, topPos+imageHeight-14, 0x6B5A3A, false);
            }
            case 4 -> {
                gfx.drawString(font, "Diplomacy:", x, y, 0x3A2F1F, false);
                int ry = y + 12;
                if (colony.getDiplomacy().getAll().isEmpty()) gfx.drawString(font, "Neutral to all.", x, ry, 0x6B5A3A, false);
                else for (var r : colony.getDiplomacy().getAll()) {
                    gfx.drawString(font, "#" + r.otherColonyId + ": " + r.relation + (r.daysRemaining>0?" ("+r.daysRemaining+"d)":""), x, ry, r.relation==com.simplecolonies.colony.Diplomacy.Relation.WAR?0xCC0000: r.relation==com.simplecolonies.colony.Diplomacy.Relation.ALLIED?0x2E7D32:0x3A2F1F, false); ry+=10;
                }
            }
            case 5 -> {
                gfx.drawString(font, "Research — buy upgrades with warehouse mats (village self-buys when surplus):", x, y, 0x3A2F1F, false);
                gfx.drawString(font, "Tool tier " + research.getToolTier() + " speed ×" + String.format("%.2f", research.getWorkSpeedMultiplier()) + "  Unlocked " + research.getUnlocked().size() + "/" + ColonyUpgrade.values().length, x, y + 10, 0x2E7D32, false);
                // List unlocked at top
                String unlockedStr = research.getUnlocked().stream().map(u -> u.displayName).reduce((a,b)->a+", "+b).orElse("none");
                gfx.drawString(font, "Unlocked: " + (unlockedStr.length()>52?unlockedStr.substring(0,52)+"…":unlockedStr), x, y + 20, 0x6B5A3A, false);
                gfx.drawString(font, "Available to buy (leader, mats in chests):", x, y + 30, 0x3A2F1F, false);
                var avail = research.getAvailableToBuy();
                if (avail.isEmpty()) gfx.drawString(font, "All tier-possible unlocked — village will self-buy next tier when it has surplus.", x, y + 42, 0x6B5A3A, false);
                else {
                    int ry = y + 42;
                    int start = researchScroll*5;
                    for (int i=0;i<Math.min(5, avail.size()-start);i++) {
                        var up = avail.get(start+i);
                        String cost = up.cost.entrySet().stream().map(e->e.getValue()+" "+e.getKey().replace("minecraft:","")).reduce((a,b)->a+", "+b).orElse("free");
                        gfx.drawString(font, up.displayName + " — " + up.description, x, ry, up.isToolTier()?0x6A4C2E:0x3A2F1F, false);
                        gfx.drawString(font, "  Cost: " + cost + "  Prereq: " + (up.prerequisites.isEmpty()?"none":up.prerequisites.stream().map(p->p.displayName).reduce((a,b)->a+", "+b).orElse("")), x, ry+8, 0x6B5A3A, false);
                        ry+=18;
                    }
                }
                gfx.drawString(font, "Default: Farmer+Lumberjack. Unlock Miner→Builder→Courier→Butcher→etc.", x, topPos+imageHeight-14, 0x6B5A3A, false);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title.getString(), titleLabelX, titleLabelY, 0xFFFFFF, false);
    }
}
