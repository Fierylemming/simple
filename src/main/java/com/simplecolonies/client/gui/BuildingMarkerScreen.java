package com.simplecolonies.client.gui;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.menu.BuildingMarkerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Building Marker — v2 Parchment Edition
 * Same wood/parchment/gold palette as TownHall, so all GUIs feel like one ancient set.
 */
public class BuildingMarkerScreen extends AbstractContainerScreen<BuildingMarkerMenu> {
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

    public BuildingMarkerScreen(BuildingMarkerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 300;
        this.imageHeight = 235;
        this.inventoryLabelY = 999;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        BuildingType[] types = {BuildingType.HOUSE, BuildingType.FARM, BuildingType.LUMBERJACK_HUT, BuildingType.MINE, BuildingType.WAREHOUSE, BuildingType.BARRACKS, BuildingType.BLACKSMITH, BuildingType.BAKERY, BuildingType.BUTCHER_HUT, BuildingType.LEATHER_SHOP, BuildingType.TEMPLE, BuildingType.MARKET};
        int[] ids = {BuildingMarkerMenu.BTN_SET_HOUSE, BuildingMarkerMenu.BTN_SET_FARM, BuildingMarkerMenu.BTN_SET_LUMBER, BuildingMarkerMenu.BTN_SET_MINE, BuildingMarkerMenu.BTN_SET_WAREHOUSE, BuildingMarkerMenu.BTN_SET_BARRACKS, BuildingMarkerMenu.BTN_SET_BLACKSMITH, BuildingMarkerMenu.BTN_SET_BAKERY, BuildingMarkerMenu.BTN_SET_BUTCHER, BuildingMarkerMenu.BTN_SET_LEATHER, BuildingMarkerMenu.BTN_SET_TEMPLE, 15};
        for (int i=0;i<types.length;i++) {
            int col=i%4, row=i/4;
            int bx = x+8 + col*72;
            int by = y+68 + row*24;
            BuildingType t=types[i]; int id=ids[i];
            boolean sel = menu.currentType!=null && menu.currentType==t;
            Component lbl = Component.literal((sel?"◆ ":"◇ ")+icon(t)+" "+t.displayName);
            Button b = Button.builder(lbl, bt->send(id)).bounds(bx,by,70,18).build();
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("✕ Clear"), b->send(BuildingMarkerMenu.BTN_CLEAR)).bounds(x+8, y+142, 70,18).build());
        addRenderableWidget(Button.builder(Component.literal("✔ Done"), b->onClose()).bounds(x+imageWidth-78, y+142, 70,18).build());
    }
    private String icon(BuildingType t){ return switch(t){ case HOUSE->"⌂"; case FARM->"⚘"; case LUMBERJACK_HUT->"♠"; case MINE->"⛏"; case WAREHOUSE->"▣"; case BARRACKS->"⚔"; case BLACKSMITH->"⚒"; case BAKERY->"◯"; case BUTCHER_HUT->"B"; case LEATHER_SHOP->"L"; case TEMPLE->"☥"; case MARKET->"⚖"; case LIBRARY->"L"; case CASTLE->"C"; case WALLS->"W"; case HARBOR->"H"; case TOWN_HALL->"T"; }; }
    private void send(int id){ if(minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){
        int x0=leftPos, y0=topPos, x1=x0+imageWidth, y1=y0+imageHeight;
        g.fill(x0-1,y0-1,x1+1,y1+1, C_FRAME_DARK); g.fill(x0,y0,x1,y1, C_FRAME_MID);
        g.fill(x0+3,y0+3,x1-3,y1-3, C_PARCH); g.fillGradient(x0+3,y0+3,x1-3,y0+12, C_PARCH_SH, 0x00E8DCC6);
        g.fill(x0+3,y0+3,x1-3,y0+20, C_HEADER_BG); g.fill(x0+3,y0+20,x1-3,y0+22, C_GOLD);
        // info panel
        g.fill(x0+6,y0+24,x1-6,y0+64, C_PANEL); g.fill(x0+6,y0+24,x0+7,y0+64, C_DIV); g.fill(x1-7,y0+24,x1-6,y0+64, C_DIV); g.fill(x0+6,y0+64,x1-6,y0+65, C_DIV);
        // grid panel
        g.fill(x0+6,y0+66,y0+140==y0+66?y0+66:x1-6, y0+140, C_PANEL);
        // footer hints
        g.fill(x0+6,y0+164,x1-6,y1-14, C_PANEL);
        g.fill(x0+3,y1-14,x1-3,y1-3, 0xFFEBE2C8); g.fill(x0+3,y1-14,x1-3,y1-13, C_DIV);

        int x=x0+12, y=y0+26;
        g.drawString(font, "Marker "+menu.markerPos.toShortString(), x,y, C_TEXT,false);
        String cur = menu.currentType==null? "Not designated - pick a type below": "Now: "+menu.currentType.displayName+" OK";
        g.drawString(font, cur, x,y+10, menu.currentType==null?C_RED:C_GREEN,false);
        g.drawString(font, "Colony "+menu.markerPos.toShortString(), x,y+20, C_MUTED,false);
        g.drawString(font, "Choose what your build counts as - villagers use chests near marker.", x0+12, y0+50, C_MID,false);
        // help footer
        int fx=x0+12, fy=y0+168;
        g.drawString(font, "Houses: 4 beds each. Warehouse (many chests) = courier hub. Farm hut needs farmland.", fx,fy, C_MUTED,false);
        g.drawString(font, "Tip: Put chests near work sites - couriers bulk-move to warehouse faster.", fx,fy+10, C_MUTED,false);
        g.drawString(font, "Leader or 20 rep required to designate. Right-click again to change.", fx,fy+20, C_MUTED,false);
        // selected description card
        if(menu.currentType!=null){
            String desc = switch(menu.currentType){
                case HOUSE->"Housing: +4 beds, +1 pop. Build more houses to grow.";
                case FARM->"Farmer: tills, plants, harvests. Needs farmland + water.";
                case LUMBERJACK_HUT->"Woodcutter: fells trees, replants saplings.";
                case MINE->"Miner: digs stone/ore, needs pickaxe tier.";
                case WAREHOUSE->"Hub: all couriers deliver here. Many chests = faster.";
                case BARRACKS->"Soldier: guards & follows Command Staff when rallied.";
                case BLACKSMITH->"Smith: makes tools, needs ingots + coal.";
                case BAKERY->"Baker: turns wheat → bread for colony food.";
                default->"Designates area for job. Villagers path to marker.";
            };
            g.fill(x0+12, y0+186, x1-12, y0+206, 0xFFF7F0DE); g.fill(x0+12, y0+186, x0+14, y0+206, C_GOLD);
            g.drawString(font, icon(menu.currentType)+" "+menu.currentType.displayName+" - "+desc, x0+18, y0+192, C_TEXT,false);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){ g.drawString(font, "⧉ Designate Building", 12,6, 0xFFF5E6A3,true); }
}
