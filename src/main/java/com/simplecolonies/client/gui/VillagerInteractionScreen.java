package com.simplecolonies.client.gui;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.menu.VillagerInteractionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Custom villager screen - complements vanilla trading.
 * Shows job, stats, assign job buttons (leader only), and Trade button that closes this and tells player to re-interact for vanilla trades.
 * Opened via shift-right-click on ColonyVillager, or via Town Hall citizens list click (future).
 */
public class VillagerInteractionScreen extends AbstractContainerScreen<VillagerInteractionMenu> {
    public VillagerInteractionScreen(VillagerInteractionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 240;
        this.imageHeight = 180;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        // Job assignment grid - 4x2
        String[] jobLabels = {"Farmer", "Woodcutter", "Miner", "Builder", "Soldier", "Butcher", "Leatherworker", "Blacksmith"};
        int[] jobBtns = {VillagerInteractionMenu.BTN_SET_FARMER, VillagerInteractionMenu.BTN_SET_LUMBERJACK, VillagerInteractionMenu.BTN_SET_MINER, VillagerInteractionMenu.BTN_SET_BUILDER,
                VillagerInteractionMenu.BTN_SET_SOLDIER, VillagerInteractionMenu.BTN_SET_BUTCHER, VillagerInteractionMenu.BTN_SET_LEATHERWORKER, VillagerInteractionMenu.BTN_SET_BLACKSMITH};
        for (int i = 0; i < jobLabels.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int bx = x + 8 + col * 57;
            int by = y + 70 + row * 22;
            int btnId = jobBtns[i];
            addRenderableWidget(Button.builder(Component.literal(jobLabels[i]), b -> sendBtn(btnId))
                    .bounds(bx, by, 55, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Trade (vanilla)"), b -> sendBtn(VillagerInteractionMenu.BTN_TRADE))
                .bounds(x + 8, y + 120, 110, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Command: Follow Me"), b -> sendBtn(VillagerInteractionMenu.BTN_COMMAND_FOLLOW))
                .bounds(x + 122, y + 120, 110, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Level Job"), b -> sendBtn(VillagerInteractionMenu.BTN_LEVEL_UP_JOB))
                .bounds(x + 8, y + 142, 110, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + 122, y + 142, 110, 18).build());
    }

    private void sendBtn(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2316);
        gfx.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFEEE0C8);
        gfx.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 18, 0xFF3A2F1F);
        gfx.fill(leftPos + 6, topPos + 22, leftPos + imageWidth - 6, topPos + 64, 0xFFD8C49E);

        int x = leftPos + 10;
        int y = topPos + 24;
        Colony c = menu.colony;
        String colonyInfo = c != null ? c.getCulture().displayName + " #" + c.getId() + "  Lv" + c.getLevel() : "No colony";
        gfx.drawString(font, menu.citizen.name + "  [" + menu.citizen.job.displayName + "]  Lv" + menu.citizen.level, x, y, 0x3A2F1F, false);
        gfx.drawString(font, "Health: " + menu.citizen.health + "/20  Days employed: " + menu.citizen.daysEmployed, x, y + 12, 0x3A2F1F, false);
        gfx.drawString(font, "Colony: " + colonyInfo, x, y + 24, 0x6B5A3A, false);
        gfx.drawString(font, "Assign job (need 30 rep, soldier needs 50):", x, y + 38, 0x3A2F1F, false);
        if (menu.citizen.isSoldier) gfx.drawString(font, "⚔ Soldier - will follow leader with Command Staff", x, y + 46, 0xCC0000, false);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title.getString(), 8, 6, 0xFFFFFF, false);
    }
}
