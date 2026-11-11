package com.simplecolonies.client.gui;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.menu.VillagerInteractionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Villager — v2 Parchment Edition
 * Same wood/parchment/gold as TownHall so all GUIs feel like one ancient set.
 */
public class VillagerInteractionScreen extends AbstractContainerScreen<VillagerInteractionMenu> {
    private static final int C_FRAME_DARK = 0xFF1A1207;
    private static final int C_FRAME_MID  = 0xFF3B2A14;
    private static final int C_HEADER_BG  = 0xFF2C1E0F;
    private static final int C_GOLD       = 0xFFB8952A;
    private static final int C_GOLD_DIM   = 0xFFD9B86A;
    private static final int C_PARCH      = 0xFFFFF8E7;
    private static final int C_PARCH_SH   = 0xFFE8DCC6;
    private static final int C_PANEL      = 0xFFFFFDF0;
    private static final int C_DIV        = 0xFFD4C4A8;
    private static final int C_TEXT       = 0xFF2B1B0E;
    private static final int C_MID        = 0xFF5A3E2B;
    private static final int C_MUTED      = 0xFF8B7355;
    private static final int C_GREEN      = 0xFF1B6B2E;
    private static final int C_RED        = 0xFFB91C1C;
    private static final int C_BLUE       = 0xFF2E5A88;

    public VillagerInteractionScreen(VillagerInteractionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 280;
        this.imageHeight = 200;
        this.inventoryLabelY = 999;
    }

    @Override protected void init(){
        super.init();
        int x=leftPos, y=topPos;
        String[] labels={"Wheat Farmer","Lumberjack","Miner","Builder","Soldier","Butcher","Leather","Smith"};
        String[] icons = {"⚘","♠","⛏","⚒","⚔","B","⬢","H"};
        int[] ids={VillagerInteractionMenu.BTN_SET_FARMER,VillagerInteractionMenu.BTN_SET_LUMBERJACK,VillagerInteractionMenu.BTN_SET_MINER,VillagerInteractionMenu.BTN_SET_BUILDER,VillagerInteractionMenu.BTN_SET_SOLDIER,VillagerInteractionMenu.BTN_SET_BUTCHER,VillagerInteractionMenu.BTN_SET_LEATHERWORKER,VillagerInteractionMenu.BTN_SET_BLACKSMITH};
        for(int i=0;i<labels.length;i++){
            int col=i%4, row=i/4;
            int bx=x+8+col*66, by=y+78+row*22;
            Component lbl=Component.literal(icons[i]+" "+labels[i]);
            addRenderableWidget(Button.builder(lbl,b->sendBtn(ids[i])).bounds(bx,by,64,18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("◐ Trade (vanilla)"), b->sendBtn(VillagerInteractionMenu.BTN_TRADE)).bounds(x+8,y+126, 130,18).build());
        addRenderableWidget(Button.builder(Component.literal("⚑ Follow Me"), b->sendBtn(VillagerInteractionMenu.BTN_COMMAND_FOLLOW)).bounds(x+142,y+126, 130,18).build());
        addRenderableWidget(Button.builder(Component.literal("▲ Level Job"), b->sendBtn(VillagerInteractionMenu.BTN_LEVEL_UP_JOB)).bounds(x+8,y+148, 130,18).build());
        addRenderableWidget(Button.builder(Component.literal("✕ Close"), b->onClose()).bounds(x+142,y+148, 130,18).build());
    }
    private void sendBtn(int id){ if(minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){
        int x0=leftPos,y0=topPos,x1=x0+imageWidth,y1=y0+imageHeight;
        g.fill(x0-1,y0-1,x1+1,y1+1, C_FRAME_DARK); g.fill(x0,y0,x1,y1, C_FRAME_MID);
        g.fill(x0+3,y0+3,x1-3,y1-3, C_PARCH); g.fillGradient(x0+3,y0+3,x1-3,y0+12, C_PARCH_SH, 0x00E8DCC6);
        g.fill(x0+3,y0+3,x1-3,y0+20, C_HEADER_BG); g.fill(x0+3,y0+20,x1-3,y0+22, C_GOLD);
        // portrait + info panel
        g.fill(x0+6,y0+24,x1-6,y0+74, C_PANEL); g.fill(x0+6,y0+24,x0+7,y0+74, C_DIV); g.fill(x1-7,y0+24,x1-6,y0+74, C_DIV); g.fill(x0+6,y0+74,x1-6,y0+75, C_DIV);
        // job panel
        g.fill(x0+6,y0+76,y0+122==y0+76?y0+76:x1-6, y0+122, C_PANEL);
        g.fill(x0+3,y1-14,x1-3,y1-3, 0xFFEBE2C8); g.fill(x0+3,y1-14,x1-3,y1-13, C_DIV);

        int x=x0+50, y=y0+28;
        Colony c=menu.colony;
        String colInfo=c!=null? c.getCulture().displayName+" #"+c.getId()+" Lv"+c.getLevel() : "Wilderness";
        // avatar
        g.fill(x0+10,y0+26, x0+42,y0+58, C_PARCH_SH); g.fill(x0+11,y0+27, x0+41,y0+57, 0xFFE6EFFA); g.fill(x0+11,y0+27, x0+13,y0+57, C_BLUE);
        g.drawString(font, menu.citizen.name.substring(0,1), x0+19,y0+33, C_TEXT,false);
        g.drawString(font, menu.citizen.name, x,y, C_TEXT,false);
        g.drawString(font, "["+menu.citizen.job.displayName+"]  Lv"+menu.citizen.level, x,y+10, C_MID,false);
        int hp=menu.citizen.health; String hearts="♥".repeat(Math.max(0, Math.min(10, hp/2)));
        g.drawString(font, hearts+"  "+hp+"/20 HP  •  "+menu.citizen.daysEmployed+"d employed", x,y+20, C_MUTED,false);
        g.drawString(font, "Colony: "+colInfo, x,y+30, C_MUTED,false);
        if(menu.citizen.isSoldier) { g.fill(x0+50,y0+62, x1-10, y0+72, 0xFFFFE8E8); g.fill(x0+50,y0+62, x0+52,y0+72, C_RED); g.drawString(font,"⚔ Soldier — follows leader with Command Staff when rallied", x0+56,y0+64, C_RED,false); }

        g.drawString(font, "Assign job — needs 30 rep (soldier 50):", x0+12,y0+76, C_TEXT,false);
        g.drawString(font, "Jobs respect research: Farmer & Lumberjack free, others need unlocks via Town Hall.", x0+12,y1-10, C_MUTED,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){ g.drawString(font, "☺ "+title.getString(), 12,6, 0xFFF5E6A3,true); }
}
