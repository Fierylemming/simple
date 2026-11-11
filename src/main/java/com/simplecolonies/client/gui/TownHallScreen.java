package com.simplecolonies.client.gui;

import com.simplecolonies.building.Building;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.menu.TownHallMenu;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Town Hall Screen — redesigned for readability and polish.
 * 6 tabs, parchment + dark wood frame, high-contrast text, proper spacing.
 */
public class TownHallScreen extends AbstractContainerScreen<TownHallMenu> {
    // Palette — warm ancient parchment theme, much higher contrast
    private static final int COL_FRAME_DARK = 0xFF1A1207;
    private static final int COL_FRAME_MID = 0xFF3B2A14;
    private static final int COL_HEADER_BG = 0xFF2C1E0F;
    private static final int COL_HEADER_TEXT = 0xFFF5E6A3; // warm gold on dark
    private static final int COL_PARCHMENT = 0xFFFFF8E7; // brighter parchment for readability
    private static final int COL_PARCHMENT_SHADOW = 0xFFE8DCC6;
    private static final int COL_CONTENT_BG = 0xFFFFFDF0; // almost white for max contrast
    private static final int COL_TEXT_DARK = 0xFF2B1B0E; // very dark brown, near black
    private static final int COL_TEXT_MID = 0xFF5A3E2B;
    private static final int COL_TEXT_MUTED = 0xFF8B7355;
    private static final int COL_ACCENT_GOLD = 0xFFB8952A;
    private static final int COL_ACCENT_BLUE = 0xFF2E5A88;
    private static final int COL_SUCCESS = 0xFF1B6B2E;
    private static final int COL_WARN = 0xFFB91C1C;
    private static final int COL_DIVIDER = 0xFFD4C4A8;

    private int currentTab = 0;
    private int citizenScroll = 0;
    private int researchScroll = 0;
    private final int imageW = 280;
    private final int imageH = 250;

    public TownHallScreen(TownHallMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = imageW;
        this.imageHeight = imageH;
        this.titleLabelX = 14;
        this.titleLabelY = 7;
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

        // Tabs — pill-shaped, selected has gold underline
        String[] tabs = {"Overview", "Citizens", "Buildings", "Stores", "Diplomacy", "Research"};
        String[] shortTabs = {"Overview", "People", "Build", "Stores", "Peace", "Research"};
        for (int i = 0; i < tabs.length; i++) {
            int idx = i;
            boolean selected = i == currentTab;
            String label = shortTabs[i];
            if (i == 1) label = "Citizens (" + menu.colony.citizenCount() + ")";
            Component comp = Component.literal((selected ? "◆ " : "◇ ") + label);
            Button tab = Button.builder(comp, b -> { currentTab = idx; refreshWidgets(); })
                    .bounds(x + 6 + i * 45, y + 19, 43, 16)
                    .build();
            addRenderableWidget(tab);
        }

        int btnX = x + 12;
        int btnY = y + 40;

        switch (currentTab) {
            case 0 -> {
                boolean isLeader = menu.colony.isLeader(menu.player);
                addRenderableWidget(Button.builder(Component.literal("♥ Donate Bread"), b -> send(TownHallMenu.BTN_DONATE_FOOD))
                        .bounds(btnX, btnY, 110, 18).build());
                addRenderableWidget(Button.builder(Component.literal(isLeader ? "★ Leader: You" : "Claim Leader (" + menu.colony.getReputation(menu.player) + "/100)"),
                        b -> send(TownHallMenu.BTN_CLAIM_LEADER)).bounds(btnX + 118, btnY, 140, 18).build());
                addRenderableWidget(Button.builder(Component.literal("⚔ Rally Soldiers"), b -> send(TownHallMenu.BTN_RALLY_SOLDIERS))
                        .bounds(btnX, btnY + 22, 110, 18).build());
            }
            case 1 -> {
                var citizens = menu.getCitizensSorted();
                if (citizens.size() > 4) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b -> { if (citizenScroll > 0) citizenScroll--; refreshWidgets(); })
                            .bounds(x + imageW - 24, btnY, 14, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b -> { if (citizenScroll + 4 < citizens.size()) citizenScroll++; refreshWidgets(); })
                            .bounds(x + imageW - 24, btnY + 68, 14, 12).build());
                }
            }
            case 2 -> {
                addRenderableWidget(Button.builder(Component.literal("⌂ House"), b -> send(TownHallMenu.BTN_ADD_BUILDING_HOUSE)).bounds(btnX, btnY, 54, 16).build());
                addRenderableWidget(Button.builder(Component.literal("🌾 Farm"), b -> send(TownHallMenu.BTN_ADD_BUILDING_FARM)).bounds(btnX + 58, btnY, 54, 16).build());
                addRenderableWidget(Button.builder(Component.literal("🌲 Lumber"), b -> send(TownHallMenu.BTN_ADD_BUILDING_LUMBER)).bounds(btnX + 116, btnY, 58, 16).build());
                addRenderableWidget(Button.builder(Component.literal("⛏ Mine"), b -> send(TownHallMenu.BTN_ADD_BUILDING_MINE)).bounds(btnX + 178, btnY, 54, 16).build());
                addRenderableWidget(Button.builder(Component.literal("⚔ Barracks"), b -> send(TownHallMenu.BTN_ADD_BUILDING_BARRACKS)).bounds(btnX, btnY + 20, 80, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Create Schematic"), b -> send(TownHallMenu.BTN_CREATE_SCHEMATIC)).bounds(btnX + 86, btnY + 20, 110, 16).build());
                var buildings = menu.colony.getBuildings();
                for (int i = 0; i < Math.min(buildings.size(), 6); i++) {
                    Building b = buildings.get(i);
                    int bx = btnX;
                    int by = btnY + 44 + i * 16;
                    int idx = i;
                    String label = "↑ " + b.getType().displayName + " Lv" + b.getLevel() + " • " + b.getWorkerSlots() + " workers" + (b.getLevel() >= 5 ? " ★ MAX" : "");
                    Button up = Button.builder(Component.literal(label.length() > 36 ? label.substring(0, 36) : label),
                            btn -> send(TownHallMenu.BTN_UPGRADE_BUILDING_BASE + idx)).bounds(bx, by, 220, 14).build();
                    addRenderableWidget(up);
                }
            }
            case 3 -> {}
            case 4 -> {
                addRenderableWidget(Button.builder(Component.literal("⚔ Declare War"), b -> send(TownHallMenu.BTN_DECLARE_WAR_NEXT)).bounds(btnX, btnY, 110, 18).build());
                addRenderableWidget(Button.builder(Component.literal("🤝 Propose Alliance"), b -> send(TownHallMenu.BTN_PROPOSE_ALLIANCE)).bounds(btnX + 118, btnY, 130, 18).build());
            }
            case 5 -> {
                var available = menu.colony.getResearch().getAvailableToBuy();
                int start = researchScroll * 5;
                for (int i = 0; i < Math.min(5, available.size() - start); i++) {
                    ColonyUpgrade up = available.get(start + i);
                    int by = btnY + 18 + i * 24;
                    String costStr = up.cost.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().replace("minecraft:", "")).reduce((a,b) -> a + ", " + b).orElse("free");
                    String label = up.displayName + (costStr.length() > 20 ? "" : " [" + costStr + "]");
                    Button buy = Button.builder(Component.literal("Unlock: " + label), btn -> send(TownHallMenu.BTN_RESEARCH_BASE + up.ordinal()))
                            .bounds(btnX, by, 240, 18).build();
                    addRenderableWidget(buy);
                }
                if (available.size() > 5) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b -> { if (researchScroll > 0) researchScroll--; refreshWidgets(); })
                            .bounds(x + imageW - 24, btnY + 10, 14, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b -> { if ((researchScroll+1)*5 < available.size()) researchScroll++; refreshWidgets(); })
                            .bounds(x + imageW - 24, btnY + 88, 14, 12).build());
                }
            }
        }
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        int x1 = leftPos + imageWidth;
        int y1 = topPos + imageHeight;

        // Outer dark wood frame with double border for depth
        gfx.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, COL_FRAME_DARK);
        gfx.fill(x0, y0, x1, y1, COL_FRAME_MID);
        // Inner parchment
        gfx.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, COL_PARCHMENT);
        // Subtle parchment shadow gradient at top
        gfx.fillGradient(x0 + 3, y0 + 3, x1 - 3, y0 + 10, COL_PARCHMENT_SHADOW, 0x00E8DCC6);
        // Header bar — solid dark with gold title
        gfx.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 18, COL_HEADER_BG);
        gfx.fill(x0 + 3, y0 + 18, x1 - 3, y0 + 20, COL_ACCENT_GOLD); // gold divider line

        // Tab bar background
        gfx.fill(x0 + 6, y0 + 38, x1 - 6, y1 - 10, COL_CONTENT_BG);
        gfx.fill(x0 + 6, y0 + 38, x1 - 6, y0 + 40, COL_DIVIDER);
        // Content panel border
        gfx.fill(x0 + 6, y0 + 38, x0 + 8, y1 - 10, COL_DIVIDER);
        gfx.fill(x1 - 8, y0 + 38, x1 - 6, y1 - 10, COL_DIVIDER);
        gfx.fill(x0 + 6, y1 - 12, x1 - 6, y1 - 10, COL_DIVIDER);

        // Header text is drawn in renderLabels, but we draw colony title with icon here for better control
        // Content area inset
        int cx = x0 + 14;
        int cy = y0 + 46;
        var colony = menu.colony;
        var research = colony.getResearch();

        switch (currentTab) {
            case 0 -> drawOverview(gfx, cx, cy, colony, research);
            case 1 -> drawCitizens(gfx, cx, cy, colony, research);
            case 2 -> drawBuildings(gfx, cx, cy, colony);
            case 3 -> drawWarehouse(gfx, cx, cy, colony);
            case 4 -> drawDiplomacy(gfx, cx, cy, colony);
            case 5 -> drawResearch(gfx, cx, cy, research);
        }

        // Footer hint bar
        gfx.fill(x0 + 6, y1 - 16, x1 - 6, y1 - 10, 0xFFEBE2C8);
        gfx.drawString(font, getFooterHint(), x0 + 12, y1 - 14, COL_TEXT_MUTED, false);
    }

    private String getFooterHint() {
        return switch (currentTab) {
            case 0 -> "Tip: Place Building Marker on your build to register it.";
            case 1 -> "Shift + right-click a villager to change job.";
            case 2 -> "Upgrade buildings for more workers and culture materials.";
            case 3 -> "Couriers move 9 stacks/trip. Keep chests near Town Hall.";
            case 4 -> "War: soldiers follow Command Staff. Alliance shares food.";
            case 5 -> "Village auto-buys research when warehouse has surplus.";
            default -> "";
        };
    }

    private void drawOverview(GuiGraphics gfx, int x, int y, com.simplecolonies.colony.Colony colony, com.simplecolonies.progression.ResearchManager research) {
        // Culture banner line — large, readable
        gfx.drawString(font, "◆ " + colony.getCulture().displayName + "  —  Lv " + colony.getLevel() + "  " + com.simplecolonies.colony.ColonyLevel.fromLevel(colony.getLevel()).displayName, x, y, COL_TEXT_DARK, false);
        y += 11;
        gfx.drawString(font, "Center " + colony.getCenter().toShortString() + "   Radius " + colony.getRadius() + "   Pop " + colony.citizenCount() + "/" + com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get(), x, y, COL_TEXT_MID, false);
        y += 11;
        boolean starving = colony.getState() == com.simplecolonies.colony.ColonyState.STARVING;
        gfx.drawString(font, "State: " + colony.getState() + "   Rep " + colony.getReputation(menu.player) + "/100" + (colony.isLeader(menu.player) ? "   ★ LEADER" : ""), x, y, starving ? COL_WARN : COL_TEXT_DARK, false);
        y += 12;
        // Divider
        gfx.fill(x, y, x + imageWidth - 28, y + 1, COL_DIVIDER);
        y += 4;
        gfx.drawString(font, "Tools: Tier " + research.getToolTier() + " (Wood 0 → Stone 1 → Iron 2 → Diamond 3)   Speed ×" + String.format("%.2f", research.getWorkSpeedMultiplier()), x, y, COL_TEXT_DARK, false);
        y += 11;
        int housing = colony.getBuildings().stream().filter(b -> b.getType() == com.simplecolonies.building.BuildingType.HOUSE).mapToInt(b -> b.getHousingCapacity()).sum();
        gfx.drawString(font, "Housing: " + housing + " beds   Worker slots: " + colony.getBuildings().stream().mapToInt(b -> b.getWorkerSlots()).sum() + "   Buildings: " + colony.getBuildings().size(), x, y, COL_TEXT_MID, false);
        y += 14;
        // Info box
        drawInfoBox(gfx, x, y, "Unlocked: " + research.getUnlocked().size() + " / " + ColonyUpgrade.values().length + "  •  Farmer & Lumberjack free  •  See Research tab");
    }

    private void drawCitizens(GuiGraphics gfx, int x, int y, com.simplecolonies.colony.Colony colony, com.simplecolonies.progression.ResearchManager research) {
        var citizens = menu.getCitizensSorted();
        if (citizens.isEmpty()) {
            drawInfoBox(gfx, x, y, "No citizens yet. Build a House and ensure Food > 0 — villagers spawn when housed & fed.");
            return;
        }
        gfx.drawString(font, "Citizens — 1 per building at start, upgrades add slots:", x, y, COL_TEXT_DARK, false);
        y += 12;
        for (int i = 0; i < Math.min(4, citizens.size() - citizenScroll); i++) {
            CitizenData c = citizens.get(citizenScroll + i);
            boolean unlocked = research.isProfessionUnlocked(c.job);
            int bg = unlocked ? 0xFFF7F0DE : 0xFFFFE8E8;
            int border = unlocked ? COL_DIVIDER : COL_WARN;
            gfx.fill(x, y - 2, x + 250, y + 16, bg);
            gfx.fill(x, y - 2, x + 250, y - 1, border);
            gfx.fill(x, y + 15, x + 250, y + 16, border);
            gfx.drawString(font, c.name, x + 6, y, COL_TEXT_DARK, false);
            gfx.drawString(font, "[" + c.job.displayName + "]  Lv" + c.level, x + 90, y, unlocked ? COL_TEXT_MID : COL_WARN, false);
            if (!unlocked) gfx.drawString(font, "LOCKED — research needed", x + 90, y + 9, COL_WARN, false);
            else gfx.drawString(font, "H:" + c.health + "  Days:" + c.daysEmployed, x + 180, y, COL_TEXT_MUTED, false);
            y += 20;
        }
    }

    private void drawBuildings(GuiGraphics gfx, int x, int y, com.simplecolonies.colony.Colony colony) {
        gfx.drawString(font, "Buildings (" + colony.getBuildings().size() + ") — place Building Marker on your build to register:", x, y, COL_TEXT_DARK, false);
        y += 12;
        if (colony.getBuildings().isEmpty()) {
            drawInfoBox(gfx, x, y, "None yet. Use buttons above or place a Building Marker block on any structure.");
            return;
        }
        for (var b : colony.getBuildings()) {
            if (y > topPos + 145) break;
            String tierInfo = com.simplecolonies.building.MaterialProgression.get(b.getCulture(), Math.min(b.getLevel(),5)).lore();
            boolean complete = b.getState() == com.simplecolonies.building.BuildingState.COMPLETE;
            gfx.fill(x, y - 1, x + 250, y + 18, complete ? 0xFFE8F5E9 : 0xFFF7F0DE);
            gfx.drawString(font, b.getType().displayName + "  Lv" + b.getLevel() + "  (" + b.getWorkerSlots() + " workers, " + b.getSize() + "×" + b.getSize() + ")  [" + b.getState() + "]", x + 4, y, complete ? COL_SUCCESS : COL_TEXT_DARK, false);
            gfx.drawString(font, b.getPos().toShortString() + "   " + tierInfo, x + 4, y + 9, COL_TEXT_MUTED, false);
            y += 20;
        }
    }

    private void drawWarehouse(GuiGraphics gfx, int x, int y, com.simplecolonies.colony.Colony colony) {
        gfx.drawString(font, "Warehouse hub: " + colony.getWarehouseManager().getWarehouseCenter().toShortString() + "  (" + colony.getWarehouseManager().getChestCount() + " chests)", x, y, COL_TEXT_DARK, false);
        y += 12;
        var snap = colony.getWarehouseSnapshot(menu.player.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null);
        if (snap.isEmpty()) {
            drawInfoBox(gfx, x, y, "Empty — farmers & miners deposit to nearest chest. Couriers consolidate to hub.");
        } else {
            var sorted = snap.entrySet().stream().sorted((a,b) -> b.getValue() - a.getValue()).toList();
            int col = 0;
            int startY = y;
            for (var e : sorted) {
                String name = e.getKey().replace("minecraft:", "");
                gfx.drawString(font, name + ": " + e.getValue(), x + col * 125, y, COL_TEXT_DARK, false);
                col = (col + 1) % 2;
                if (col == 0) y += 10;
                if (y > topPos + 125) break;
            }
            if (col == 1) y += 10;
        }
        // Food line at bottom of panel
        gfx.drawString(font, "Daily need: " + (int)Math.ceil(colony.citizenCount()*com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get()) + " food. Courier: 9 stacks/trip.", x, Math.max(y + 6, topPos + 132), COL_TEXT_MID, false);
    }

    private void drawDiplomacy(GuiGraphics gfx, int x, int y, com.simplecolonies.colony.Colony colony) {
        gfx.drawString(font, "Diplomacy — relations with nearby colonies:", x, y, COL_TEXT_DARK, false);
        y += 12;
        if (colony.getDiplomacy().getAll().isEmpty()) {
            drawInfoBox(gfx, x, y, "Neutral to all. Use buttons above to declare war or propose alliance (leader only).");
        } else {
            for (var r : colony.getDiplomacy().getAll()) {
                int col = r.relation == com.simplecolonies.colony.Diplomacy.Relation.WAR ? COL_WARN : r.relation == com.simplecolonies.colony.Diplomacy.Relation.ALLIED ? COL_SUCCESS : COL_TEXT_DARK;
                gfx.drawString(font, "Colony #" + r.otherColonyId + " — " + r.relation + (r.daysRemaining > 0 ? " (" + r.daysRemaining + "d)" : ""), x + 4, y, col, false);
                y += 10;
            }
        }
    }

    private void drawResearch(GuiGraphics gfx, int x, int y, com.simplecolonies.progression.ResearchManager research) {
        gfx.drawString(font, "Research — buy with warehouse materials (village auto-buys when surplus):", x, y, COL_TEXT_DARK, false);
        y += 11;
        gfx.drawString(font, "Tool Tier " + research.getToolTier() + "  Speed ×" + String.format("%.2f", research.getWorkSpeedMultiplier()) + "   Unlocked " + research.getUnlocked().size() + "/" + ColonyUpgrade.values().length, x, y, COL_SUCCESS, false);
        y += 11;
        String unlockedStr = research.getUnlocked().stream().map(u -> u.displayName).reduce((a,b)->a+", "+b).orElse("none");
        drawWrapped(gfx, "Unlocked: " + unlockedStr, x, y, 250, COL_TEXT_MUTED);
        y += 12;
        gfx.fill(x, y, x + 250, y + 1, COL_DIVIDER);
        y += 4;
        gfx.drawString(font, "Available to buy (leader only, costs in mats):", x, y, COL_TEXT_DARK, false);
        y += 12;
        var avail = research.getAvailableToBuy();
        if (avail.isEmpty()) {
            drawInfoBox(gfx, x, y, "All tier-possible unlocked — village will self-buy next tier when it has surplus.");
        } else {
            int start = researchScroll * 5;
            for (int i = 0; i < Math.min(5, avail.size() - start); i++) {
                var up = avail.get(start + i);
                if (y > topPos + 135) break;
                String cost = up.cost.entrySet().stream().map(e -> e.getValue() + " " + e.getKey().replace("minecraft:", "")).reduce((a,b)->a+", "+b).orElse("free");
                String prereq = up.prerequisites.isEmpty() ? "none" : up.prerequisites.stream().map(p -> p.displayName).reduce((a,b)->a+", "+b).orElse("");
                gfx.fill(x, y - 1, x + 250, y + 20, up.isToolTier() ? 0xFFF0E6C2 : 0xFFFFFFFF);
                gfx.fill(x, y - 1, x + 2, y + 20, up.isToolTier() ? COL_ACCENT_GOLD : COL_ACCENT_BLUE);
                gfx.drawString(font, up.displayName, x + 6, y, COL_TEXT_DARK, false);
                gfx.drawString(font, up.description, x + 6, y + 9, COL_TEXT_MID, false);
                // Cost line smaller
                gfx.drawString(font, "Cost: " + cost + "  •  Need: " + prereq, x + 6, y + 18, COL_TEXT_MUTED, false);
                // Inline prereq hidden for now, handled by button
                y += 22;
            }
        }
    }

    private void drawInfoBox(GuiGraphics gfx, int x, int y, String text) {
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text), 240);
        int h = wrapped.size() * 10 + 8;
        gfx.fill(x, y, x + 250, y + h, 0xFFF3EAD1);
        gfx.fill(x, y, x + 250, y + 1, COL_ACCENT_GOLD);
        int ty = y + 4;
        for (var line : wrapped) {
            gfx.drawString(font, line, x + 6, ty, COL_TEXT_MID, false);
            ty += 10;
        }
    }

    private void drawWrapped(GuiGraphics gfx, String text, int x, int y, int width, int color) {
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text), width);
        for (var line : wrapped) {
            gfx.drawString(font, line, x, y, color, false);
            y += 10;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title.getString(), titleLabelX, titleLabelY, COL_HEADER_TEXT, true);
        // Subtitle culture level
        var colony = menu.colony;
        gfx.drawString(font, colony.getCulture().displayName + " Colony  •  " + colony.getState(), titleLabelX + 110, titleLabelY, 0xFFD4C4A8, false);
    }
}
