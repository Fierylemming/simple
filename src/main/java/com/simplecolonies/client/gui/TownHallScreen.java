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
 * Town Hall Screen — v3 readable layout: no overlap, parchment cards,
 * taller content area so buttons never cover text.
 */
public class TownHallScreen extends AbstractContainerScreen<TownHallMenu> {
    private static final int COL_FRAME_DARK = 0xFF1A1207;
    private static final int COL_FRAME_MID = 0xFF3B2A14;
    private static final int COL_HEADER_BG = 0xFF2C1E0F;
    private static final int COL_HEADER_TEXT = 0xFFF5E6A3;
    private static final int COL_PARCHMENT = 0xFFFFF8E7;
    private static final int COL_PARCHMENT_SHADOW = 0xFFE8DCC6;
    private static final int COL_CONTENT_BG = 0xFFFFFDF0;
    private static final int COL_TEXT_DARK = 0xFF2B1B0E;
    private static final int COL_TEXT_MID = 0xFF5A3E2B;
    private static final int COL_TEXT_MUTED = 0xFF8B7355;
    private static final int COL_ACCENT_GOLD = 0xFFB8952A;
    private static final int COL_SUCCESS = 0xFF1B6B2E;
    private static final int COL_WARN = 0xFFB91C1C;
    private static final int COL_DIVIDER = 0xFFD4C4A8;

    private int currentTab = 0;
    private int citizenScroll = 0;
    private int researchScroll = 0;
    // Wider + taller so nothing truncates
    private final int imageW = 320;
    private final int imageH = 270;

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

        // Tabs: 6 * 50 = 300, fits in 320 with 6px margins — no clip
        String[] labels = {"Overview","Citizens","Buildings","Stores","Peace","Research"};
        // show count on Citizens tab
        labels[1] = "Citizens " + menu.colony.citizenCount();
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            boolean sel = i == currentTab;
            Component c = Component.literal((sel ? "◆ " : "◇ ") + labels[i]);
            Button b = Button.builder(c, bt -> { currentTab = idx; refreshWidgets(); })
                    .bounds(x + 6 + i * 52, y + 19, 50, 16).build();
            addRenderableWidget(b);
        }

        int btnX = x + 12;
        // Buttons live in their own strip at top of content, content below never overlaps
        int btnY = y + 38;

        switch (currentTab) {
            case 0 -> {
                boolean isLeader = menu.colony.isLeader(menu.player);
                addRenderableWidget(Button.builder(Component.literal("♥ Donate Bread"), b -> send(TownHallMenu.BTN_DONATE_FOOD)).bounds(btnX, btnY, 100, 16).build());
                addRenderableWidget(Button.builder(Component.literal(isLeader ? "★ Leader: You" : "Claim Leader ("+menu.colony.getReputation(menu.player)+"/100)"), b -> send(TownHallMenu.BTN_CLAIM_LEADER)).bounds(btnX+106, btnY, 150, 16).build());
                addRenderableWidget(Button.builder(Component.literal("⚔ Rally Soldiers"), b -> send(TownHallMenu.BTN_RALLY_SOLDIERS)).bounds(btnX, btnY+18, 100, 16).build());
            }
            case 1 -> {
                var citizens = menu.getCitizensSorted();
                if (citizens.size() > 4) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b -> { if (citizenScroll>0) citizenScroll--; refreshWidgets(); }).bounds(x+imageW-22, btnY+10, 14, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b -> { if (citizenScroll+4 < citizens.size()) citizenScroll++; refreshWidgets(); }).bounds(x+imageW-22, btnY+90, 14, 12).build());
                }
            }
            case 2 -> {
                addRenderableWidget(Button.builder(Component.literal("House"), b -> send(TownHallMenu.BTN_ADD_BUILDING_HOUSE)).bounds(btnX, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Farm"), b -> send(TownHallMenu.BTN_ADD_BUILDING_FARM)).bounds(btnX+54, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Lumber"), b -> send(TownHallMenu.BTN_ADD_BUILDING_LUMBER)).bounds(btnX+108, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Mine"), b -> send(TownHallMenu.BTN_ADD_BUILDING_MINE)).bounds(btnX+162, btnY, 50, 16).build());
                addRenderableWidget(Button.builder(Component.literal("Barracks"), b -> send(TownHallMenu.BTN_ADD_BUILDING_BARRACKS)).bounds(btnX+216, btnY, 50, 16).build());
                var buildings = menu.colony.getBuildings();
                for (int i=0;i<Math.min(buildings.size(),5);i++) {
                    Building bd = buildings.get(i);
                    int by = btnY+40 + i*16;
                    int idx=i;
                    String lbl = "↑ "+bd.getType().displayName+" Lv"+bd.getLevel()+" • "+bd.getWorkerSlots()+" workers"+(bd.getLevel()>=5?" ★ MAX":"");
                    Button up = Button.builder(Component.literal(lbl.length()>34?lbl.substring(0,34):lbl), bt->send(TownHallMenu.BTN_UPGRADE_BUILDING_BASE+idx)).bounds(btnX, by, 250, 14).build();
                    addRenderableWidget(up);
                }
            }
            case 3 -> {}
            case 4 -> {
                addRenderableWidget(Button.builder(Component.literal("⚔ Declare War"), b->send(TownHallMenu.BTN_DECLARE_WAR_NEXT)).bounds(btnX, btnY, 110, 16).build());
                addRenderableWidget(Button.builder(Component.literal("🤝 Propose Alliance"), b->send(TownHallMenu.BTN_PROPOSE_ALLIANCE)).bounds(btnX+126, btnY, 120, 16).build());
            }
            case 5 -> {
                var av = menu.colony.getResearch().getAvailableToBuy();
                int start = researchScroll*4;
                for (int i=0;i<Math.min(4, av.size()-start); i++) {
                    ColonyUpgrade up = av.get(start+i);
                    int by = btnY + 12 + i*28;
                    Button buy = Button.builder(Component.literal("Unlock: "+up.displayName), bt->send(TownHallMenu.BTN_RESEARCH_BASE+up.ordinal())).bounds(btnX, by, 250, 16).build();
                    addRenderableWidget(buy);
                }
                if (av.size()>4) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b->{ if(researchScroll>0) researchScroll--; refreshWidgets(); }).bounds(x+imageW-22, btnY+20,14,12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b->{ if((researchScroll+1)*4 < av.size()) researchScroll++; refreshWidgets(); }).bounds(x+imageW-22, btnY+110,14,12).build());
                }
            }
        }
    }

    private void send(int id) { if (minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override
    protected void renderBg(GuiGraphics gfx, float px, int mx, int my) {
        int x0=leftPos, y0=topPos, x1=x0+imageWidth, y1=y0+imageHeight;
        gfx.fill(x0-1,y0-1,x1+1,y1+1, COL_FRAME_DARK);
        gfx.fill(x0,y0,x1,y1, COL_FRAME_MID);
        gfx.fill(x0+3,y0+3,x1-3,y1-3, COL_PARCHMENT);
        gfx.fillGradient(x0+3,y0+3,x1-3,y0+10, COL_PARCHMENT_SHADOW, 0x00E8DCC6);
        gfx.fill(x0+3,y0+3,x1-3,y0+18, COL_HEADER_BG);
        gfx.fill(x0+3,y0+18,x1-3,y0+20, COL_ACCENT_GOLD);
        // content panel - lower so buttons have own area
        gfx.fill(x0+6,y0+36,x1-6,y1-10, COL_CONTENT_BG);
        gfx.fill(x0+6,y0+36,x1-6,y0+38, COL_DIVIDER);
        gfx.fill(x0+6,y0+36,x0+8,y1-10, COL_DIVIDER);
        gfx.fill(x1-8,y0+36,x1-6,y1-10, COL_DIVIDER);
        gfx.fill(x0+6,y1-12,x1-6,y1-10, COL_DIVIDER);

        // Buttons occupy y 38-58, so text starts at y 60+ (no overlap)
        int cx = x0+14;
        int cy = y0+62;
        var colony = menu.colony;
        var research = colony.getResearch();
        // In Overview we need extra offset because buttons take 2 rows
        if (currentTab==0) cy = y0+76;
        else if (currentTab==2) cy = y0+88;
        else if (currentTab==5) cy = y0+70;

        switch (currentTab) {
            case 0 -> drawOverview(gfx,cx,cy,colony,research);
            case 1 -> drawCitizens(gfx,cx,cy,colony,research);
            case 2 -> drawBuildings(gfx,cx,cy,colony);
            case 3 -> drawWarehouse(gfx,cx,cy,colony);
            case 4 -> drawDiplomacy(gfx,cx,cy,colony);
            case 5 -> drawResearch(gfx,cx,cy,research);
        }
        gfx.fill(x0+6,y1-16,x1-6,y1-10, 0xFFEBE2C8);
        gfx.drawString(font, getFooter(), x0+12, y1-14, COL_TEXT_MUTED,false);
    }

    private String getFooter(){
        return switch(currentTab){
            case 0->"Tip: Place Building Marker on your build to register it.";
            case 1->"Shift+right-click villager to change job (if researched).";
            case 2->"Upgrades add workers + culture materials (wood→stone→terracotta).";
            case 3->"Stores: Farmers deposit wheat, Couriers move 9 stacks.";
            case 4->"Peace/War: soldiers follow Command Staff.";
            case 5->"Village auto-buys when warehouse has surplus.";
            default->"";
        };
    }

    private void drawOverview(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c, com.simplecolonies.progression.ResearchManager r){
        g.drawString(font,"◆ "+c.getCulture().displayName+" — Lv "+c.getLevel()+" "+com.simplecolonies.colony.ColonyLevel.fromLevel(c.getLevel()).displayName, x,y, COL_TEXT_DARK,false); y+=11;
        g.drawString(font,"Center "+c.getCenter().toShortString()+"  Radius "+c.getRadius()+"  Pop "+c.citizenCount()+"/"+com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get(), x,y, COL_TEXT_MID,false); y+=11;
        boolean starv=c.getState()==com.simplecolonies.colony.ColonyState.STARVING;
        g.drawString(font,"State: "+c.getState()+"  Rep "+c.getReputation(menu.player)+"/100"+(c.isLeader(menu.player)?"  ★ LEADER":""), x,y, starv?COL_WARN:COL_TEXT_DARK,false); y+=13;
        g.fill(x,y,x+imageWidth-28,y+1, COL_DIVIDER); y+=5;
        g.drawString(font,"Tools: Tier "+r.getToolTier()+" (Wood→Stone→Iron→Diamond)  Speed ×"+String.format("%.2f",r.getWorkSpeedMultiplier()), x,y, COL_TEXT_DARK,false); y+=11;
        int housing=c.getBuildings().stream().filter(b->b.getType()==com.simplecolonies.building.BuildingType.HOUSE).mapToInt(b->b.getHousingCapacity()).sum();
        g.drawString(font,"Housing: "+housing+" beds  Slots: "+c.getBuildings().stream().mapToInt(b->b.getWorkerSlots()).sum()+"  Buildings: "+c.getBuildings().size(), x,y, COL_TEXT_MID,false); y+=14;
        drawInfoBox(g,x,y,"Unlocked: "+r.getUnlocked().size()+"/"+ColonyUpgrade.values().length+" • Farmer & Lumberjack free • See Research tab");
    }
    private void drawCitizens(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c, com.simplecolonies.progression.ResearchManager r){
        var citizens=menu.getCitizensSorted();
        if(citizens.isEmpty()){ drawInfoBox(g,x,y,"No citizens yet. Build House + Food >0 → villagers spawn when housed & fed."); return; }
        g.drawString(font,"Citizens — 1 per building start, upgrades add slots:", x,y, COL_TEXT_DARK,false); y+=12;
        for(int i=0;i<Math.min(4,citizens.size()-citizenScroll);i++){
            CitizenData cd=citizens.get(citizenScroll+i);
            boolean ok=r.isProfessionUnlocked(cd.job);
            g.fill(x,y-2,x+290,y+16, ok?0xFFF7F0DE:0xFFFFE8E8);
            g.fill(x,y-2,x+290,y-1, ok?COL_DIVIDER:COL_WARN);
            g.fill(x,y+15,x+290,y+16, ok?COL_DIVIDER:COL_WARN);
            g.drawString(font,cd.name, x+6,y, COL_TEXT_DARK,false);
            g.drawString(font,"["+cd.job.displayName+"] Lv"+cd.level, x+110,y, ok?COL_TEXT_MID:COL_WARN,false);
            if(!ok) g.drawString(font,"LOCKED — need research", x+110,y+9, COL_WARN,false); else g.drawString(font,"H:"+cd.health+" D:"+cd.daysEmployed, x+220,y,COL_TEXT_MUTED,false);
            y+=20;
        }
    }
    private void drawBuildings(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c){
        g.drawString(font,"Buildings ("+c.getBuildings().size()+") — marker on your build registers it:", x,y, COL_TEXT_DARK,false); y+=12;
        if(c.getBuildings().isEmpty()){ drawInfoBox(g,x,y,"None yet. Use buttons above or place Building Marker."); return; }
        for(var b: c.getBuildings()){
            if(y>topPos+170) break;
            boolean ok=b.getState()==com.simplecolonies.building.BuildingState.COMPLETE;
            g.fill(x,y-1,x+290,y+14, ok?0xFFE8F5E9:0xFFF7F0DE);
            String ti=com.simplecolonies.building.MaterialProgression.get(b.getCulture(),Math.min(b.getLevel(),5)).lore();
            g.drawString(font,b.getType().displayName+" Lv"+b.getLevel()+" ("+b.getWorkerSlots()+"w "+b.getSize()+"x"+b.getSize()+") ["+b.getState()+"]", x+4,y, ok?COL_SUCCESS:COL_TEXT_DARK,false);
            g.drawString(font,b.getPos().toShortString()+" "+ti, x+4,y+8, COL_TEXT_MUTED,false);
            y+=18;
        }
    }
    private void drawWarehouse(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c){
        g.drawString(font,"Hub: "+c.getWarehouseManager().getWarehouseCenter().toShortString()+" ("+c.getWarehouseManager().getChestCount()+" chests)", x,y, COL_TEXT_DARK,false); y+=12;
        var snap=c.getWarehouseSnapshot(menu.player.level() instanceof net.minecraft.server.level.ServerLevel sl?sl:null);
        if(snap.isEmpty()) drawInfoBox(g,x,y,"Empty — deposit to nearest chest, couriers consolidate.");
        else{
            var s=snap.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue()).toList();
            int col=0;
            for(var e:s){ g.drawString(font,e.getKey().replace("minecraft:","")+": "+e.getValue(), x+col*140,y, COL_TEXT_DARK,false); col=(col+1)%2; if(col==0) y+=10; if(y>topPos+160) break; }
            if(col==1) y+=10;
        }
        g.drawString(font,"Need "+(int)Math.ceil(c.citizenCount()*com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get())+" food/day. Courier 9 stacks.", x, Math.max(y+6, topPos+170), COL_TEXT_MID,false);
    }
    private void drawDiplomacy(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c){
        g.drawString(font,"Diplomacy:", x,y, COL_TEXT_DARK,false); y+=12;
        if(c.getDiplomacy().getAll().isEmpty()) drawInfoBox(g,x,y,"Neutral. Declare War / Propose Alliance (leader).");
        else for(var r: c.getDiplomacy().getAll()){ int col=r.relation==com.simplecolonies.colony.Diplomacy.Relation.WAR?COL_WARN: r.relation==com.simplecolonies.colony.Diplomacy.Relation.ALLIED?COL_SUCCESS:COL_TEXT_DARK; g.drawString(font,"#"+r.otherColonyId+": "+r.relation+(r.daysRemaining>0?" ("+r.daysRemaining+"d)":""), x+4,y, col,false); y+=10; }
    }
    private void drawResearch(GuiGraphics g,int x,int y, com.simplecolonies.progression.ResearchManager r){
        g.drawString(font,"Research — warehouse mats auto-buy when surplus:", x,y, COL_TEXT_DARK,false); y+=11;
        g.drawString(font,"Tier "+r.getToolTier()+" ×"+String.format("%.2f",r.getWorkSpeedMultiplier())+"  Unlocked "+r.getUnlocked().size()+"/"+ColonyUpgrade.values().length, x,y, COL_SUCCESS,false); y+=11;
        drawWrapped(g,"Unlocked: "+r.getUnlocked().stream().map(u->u.displayName).reduce((a,b)->a+", "+b).orElse("none"), x,y,280,COL_TEXT_MUTED); y+=12;
        g.fill(x,y,x+280,y+1, COL_DIVIDER); y+=4;
        g.drawString(font,"Available (leader):", x,y, COL_TEXT_DARK,false); y+=12;
        var av=r.getAvailableToBuy();
        if(av.isEmpty()) drawInfoBox(g,x,y,"All unlocked — next tier when surplus.");
        else{
            int st=researchScroll*4;
            for(int i=0;i<Math.min(4,av.size()-st);i++){
                var up=av.get(st+i); if(y>topPos+155) break;
                String cost=up.cost.entrySet().stream().map(e->e.getValue()+" "+e.getKey().replace("minecraft:","")).reduce((a,b)->a+", "+b).orElse("free");
                g.fill(x,y-1,x+280,y+22, up.isToolTier()?0xFFF0E6C2:0xFFFFFFFF);
                g.fill(x,y-1,x+2,y+22, up.isToolTier()?COL_ACCENT_GOLD:0xFF2E5A88);
                g.drawString(font,up.displayName, x+6,y, COL_TEXT_DARK,false);
                g.drawString(font,up.description, x+6,y+8, COL_TEXT_MID,false);
                g.drawString(font,"Cost: "+cost, x+6,y+16, COL_TEXT_MUTED,false);
                y+=26;
            }
        }
    }
    private void drawInfoBox(GuiGraphics g,int x,int y,String t){
        var w=font.split(Component.literal(t),270);
        int h=w.size()*10+8;
        g.fill(x,y,x+290,y+h,0xFFF3EAD1); g.fill(x,y,x+290,y+1,COL_ACCENT_GOLD);
        int ty=y+4; for(var l:w){ g.drawString(font,l,x+6,ty,COL_TEXT_MID,false); ty+=10; }
    }
    private void drawWrapped(GuiGraphics g,String t,int x,int y,int w,int col){
        for(var l: font.split(Component.literal(t),w)){ g.drawString(font,l,x,y,col,false); y+=10; }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title.getString(),titleLabelX,titleLabelY, COL_HEADER_TEXT,true);
        g.drawString(font,menu.colony.getCulture().displayName+" • "+menu.colony.getState(), titleLabelX+130,titleLabelY, 0xFFD4C4A8,false);
    }
}
