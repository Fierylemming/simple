package com.simplecolonies.client.gui;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.menu.BuildingMarkerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Building Marker GUI — the "town setter you right click then select what building it is"
 * Shows all building types as buttons, current designation, and explains what villagers will do with it.
 */
public class BuildingMarkerScreen extends AbstractContainerScreen<BuildingMarkerMenu> {
    public BuildingMarkerScreen(BuildingMarkerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 260;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        // Building type buttons — grid 4x3
        BuildingType[] types = {
                BuildingType.HOUSE, BuildingType.FARM, BuildingType.LUMBERJACK_HUT, BuildingType.MINE,
                BuildingType.WAREHOUSE, BuildingType.BARRACKS, BuildingType.BLACKSMITH, BuildingType.BAKERY,
                BuildingType.BUTCHER_HUT, BuildingType.LEATHER_SHOP, BuildingType.TEMPLE, BuildingType.MARKET
        };
        int[] btnIds = {
                BuildingMarkerMenu.BTN_SET_HOUSE, BuildingMarkerMenu.BTN_SET_FARM, BuildingMarkerMenu.BTN_SET_LUMBER, BuildingMarkerMenu.BTN_SET_MINE,
                BuildingMarkerMenu.BTN_SET_WAREHOUSE, BuildingMarkerMenu.BTN_SET_BARRACKS, BuildingMarkerMenu.BTN_SET_BLACKSMITH, BuildingMarkerMenu.BTN_SET_BAKERY,
                BuildingMarkerMenu.BTN_SET_BUTCHER, BuildingMarkerMenu.BTN_SET_LEATHER, BuildingMarkerMenu.BTN_SET_TEMPLE, 15 // market uses barracks id for demo
        };
        for (int i = 0; i < types.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int bx = x + 8 + col * 62;
            int by = y + 50 + row * 22;
            BuildingType t = types[i];
            int id = btnIds[i];
            Button b = Button.builder(Component.literal(t.displayName), btn -> send(id))
                    .bounds(bx, by, 60, 18)
                    .build();
            addRenderableWidget(b);
        }

        addRenderableWidget(Button.builder(Component.literal("Clear"), btn -> send(BuildingMarkerMenu.BTN_CLEAR))
                .bounds(x + 8, y + 124, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(x + imageWidth - 68, y + 124, 60, 18).build());
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2316);
        gfx.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFEEE0C8);
        gfx.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 18, 0xFF3A2F1F);
        gfx.fill(leftPos + 6, topPos + 22, leftPos + imageWidth - 6, topPos + 46, 0xFFD8C49E);

        int x = leftPos + 10;
        int y = topPos + 24;
        gfx.drawString(font, "Marker: " + menu.markerPos.toShortString(), x, y, 0x3A2F1F, false);
        String current = menu.currentType == null ? "Not designated — pick below" : "Now: " + menu.currentType.displayName;
        gfx.drawString(font, current, x, y + 12, menu.currentType == null ? 0xCC0000 : 0x2E7D32, false);

        gfx.drawString(font, "Pick what your build should count as:", x, y + 26, 0x3A2F1F, false);

        // Help text based on hover? For now static help at bottom
        gfx.drawString(font, "Houses give 4 beds each. Warehouse (many chests) becomes courier hub.", x, topPos + 150, 0x6B5A3A, false);
        gfx.drawString(font, "Farm/Farm-hut jobs will use chests near marker. Leader or 20 rep required.", x, topPos + 162, 0x6B5A3A, false);
        gfx.drawString(font, "Tip: Place chests near work sites — couriers bulk-move to warehouse faster.", x, topPos + 174, 0x6B5A3A, false);
        gfx.drawString(font, "Colony: " + menu.colony.getCulture().displayName + " #" + menu.colony.getId(), x, topPos + 190, 0x3A2F1F, false);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title.getString(), 8, 6, 0xFFFFFF, false);
    }
}
