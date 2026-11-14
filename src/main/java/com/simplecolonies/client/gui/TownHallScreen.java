package com.simplecolonies.client.gui;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.ColonyLevel;
import com.simplecolonies.menu.TownHallMenu;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Town Hall — v4 Parchment Edition
 * Completely rebuilt: wood-frame + parchment, icon tabs, card UI, progress bars, parchment textures.
 * Same AI palette as all block textures (beige  #FFF8E7 / wood #2C1E0F / gold #B8952A) so GUI matches blocks.
 */
public class TownHallScreen extends AbstractContainerScreen<TownHallMenu> {
    // --- Palette — matches block textures (sandstone/beige + lapis + gold) ---
    private static final int C_BG_FRAME_DARK  = 0xFF1A1207;
    private static final int C_BG_FRAME_MID   = 0xFF3B2A14;
    private static final int C_BG_FRAME_HI    = 0xFF5A3E1A;
    private static final int C_HEADER_BG      = 0xFF2C1E0F;
    private static final int C_HEADER_GOLD    = 0xFFF5E6A3;
    private static final int C_HEADER_SUB     = 0xFFD4C4A8;
    private static final int C_PARCH          = 0xFFFFF8E7;
    private static final int C_PARCH_SHADOW   = 0xFFE8DCC6;
    private static final int C_PANEL          = 0xFFFFFDF0;
    private static final int C_DIV            = 0xFFD4C4A8;
    private static final int C_TEXT           = 0xFF2B1B0E;
    private static final int C_TEXT_MID       = 0xFF5A3E2B;
    private static final int C_TEXT_MUTED     = 0xFF8B7355;
    private static final int C_GOLD           = 0xFFB8952A;
    private static final int C_GOLD_DIM       = 0xFFD9B86A;
    private static final int C_GREEN          = 0xFF1B6B2E;
    private static final int C_GREEN_BG       = 0xFFE8F5E9;
    private static final int C_RED            = 0xFFB91C1C;
    private static final int C_RED_BG         = 0xFFFFE8E8;
    private static final int C_BLUE           = 0xFF2E5A88;
    private static final int C_BLUE_BG        = 0xFFE6EFFA;

    private int currentTab = 0;
    private int citizenScroll = 0;
    private int researchScroll = 0;
    private int buildingScroll = 0;
    private int diplomacyTarget = 0;

    private final int W = 340;
    private final int H = 285;

    // Icons — unicode, same style as block/item icons
    private static final String I_OVERVIEW = "◈";
    private static final String I_CITIZENS = "☺";
    private static final String I_BUILD    = "⚒";
    private static final String I_STORE    = "▣";
    private static final String I_DIPLO    = "☮";
    private static final String I_RESEARCH = "✧";

    public TownHallScreen(TownHallMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelX = 18;
        this.titleLabelY = 8;
        this.inventoryLabelY = 999;
    }

    @Override protected void init() { super.init(); rebuild(); }

    private void rebuild() {
        clearWidgets();
        int x = leftPos, y = topPos;
        String[] icons = {I_OVERVIEW,I_CITIZENS,I_BUILD,I_STORE,I_DIPLO,I_RESEARCH};
        String[] names  = {"Overview","Citizens","Buildings","Stores","Peace","Research"};
        names[1] = "Citizens "+menu.colony.citizenCount();
        int tabW = 54, gap = 2;
        int startX = x + 8;
        for (int i=0;i<6;i++) {
            int idx=i;
            boolean sel = i==currentTab;
            Component lbl = Component.literal((sel? "◆ ":"◇ ")+icons[i]+" "+names[i]);
            Button b = Button.builder(lbl, bt->{ currentTab=idx; rebuild(); })
                    .bounds(startX + i*(tabW+gap), y+22, tabW, 16).build();
            addRenderableWidget(b);
        }

        int btnX = x+12, btnY = y+44;
        boolean isLeader = menu.colony.isLeader(menu.player);
        switch (currentTab) {
            case 0 -> {
                addRenderableWidget(Button.builder(Component.literal("♥ Donate Bread"), b->send(TownHallMenu.BTN_DONATE_FOOD)).bounds(btnX, btnY, 98, 16).build());
                addRenderableWidget(Button.builder(Component.literal(isLeader?"★ Leader: You":"☆ Claim Leader "+menu.colony.getReputation(menu.player)+"/100"), b->send(TownHallMenu.BTN_CLAIM_LEADER)).bounds(btnX+104, btnY, 148, 16).build());
                addRenderableWidget(Button.builder(Component.literal("⚔ Rally"), b->send(TownHallMenu.BTN_RALLY_SOLDIERS)).bounds(btnX+258, btnY, 70, 16).build());
            }
            case 1 -> {
                var list = menu.getCitizensSorted();
                if (list.size()>4) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b->{ if(citizenScroll>0){citizenScroll--; rebuild();}}).bounds(x+W-22, btnY+6, 14, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b->{ if(citizenScroll+4 < list.size()){citizenScroll++; rebuild();}}).bounds(x+W-22, btnY+106, 14, 12).build());
                }
            }
            case 2 -> {
                String[] bt = {"House","Farm","Lumber","Mine","Barracks"};
                int[] ids2 = {TownHallMenu.BTN_ADD_BUILDING_HOUSE,TownHallMenu.BTN_ADD_BUILDING_FARM,TownHallMenu.BTN_ADD_BUILDING_LUMBER,TownHallMenu.BTN_ADD_BUILDING_MINE,TownHallMenu.BTN_ADD_BUILDING_BARRACKS};
                for (int i=0;i<bt.length;i++) { final int idx=i; addRenderableWidget(Button.builder(Component.literal(bt[idx]), b->send(ids2[idx])).bounds(btnX+idx*64, btnY, 60, 16).build()); }
                addRenderableWidget(Button.builder(Component.literal("Queue Create schematic"), b->send(TownHallMenu.BTN_CREATE_SCHEMATIC))
                        .bounds(x+12, y+211, 150, 16).build());
                var buildings = menu.colony.getBuildings();
                if (buildings.size() > 4) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b->{ if(buildingScroll>0){buildingScroll--; rebuild();}}).bounds(x+W-22, y+77, 14, 12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b->{ if(buildingScroll+4<buildings.size()){buildingScroll++; rebuild();}}).bounds(x+W-22, y+192, 14, 12).build());
                }
                for (int i=buildingScroll; i<Math.min(buildingScroll+4, Math.min(100, buildings.size())); i++) {
                    var building = buildings.get(i);
                    if (building.canBuildWorksiteWing() || (building.isPlayerPlaced && building.getType()==BuildingType.HOUSE && building.getLevel()<5)) {
                        final int index=i;
                        addRenderableWidget(Button.builder(Component.literal("Upgrade"), b->send(TownHallMenu.BTN_UPGRADE_BUILDING_BASE+index))
                                .bounds(x+244, y+78+(i-buildingScroll)*32, 58, 16).build());
                    }
                }
            }
            case 3 -> {}
            case 4 -> {
                var targets = menu.getDiplomacyTargets();
                if (!targets.isEmpty()) {
                    diplomacyTarget = Math.min(diplomacyTarget, targets.size()-1);
                    addRenderableWidget(Button.builder(Component.literal("<"), b->{ diplomacyTarget=(diplomacyTarget+targets.size()-1)%targets.size(); rebuild(); }).bounds(btnX, btnY, 22, 16).build());
                    addRenderableWidget(Button.builder(Component.literal(">"), b->{ diplomacyTarget=(diplomacyTarget+1)%targets.size(); rebuild(); }).bounds(btnX+258, btnY, 22, 16).build());
                    final int selected = diplomacyTarget;
                    addRenderableWidget(Button.builder(Component.literal("War"), b->send(TownHallMenu.BTN_WAR_TARGET_BASE+selected)).bounds(btnX, btnY+20, 70, 16).build());
                    addRenderableWidget(Button.builder(Component.literal("Ally"), b->send(TownHallMenu.BTN_ALLY_TARGET_BASE+selected)).bounds(btnX+74, btnY+20, 70, 16).build());
                    addRenderableWidget(Button.builder(Component.literal("Peace"), b->send(TownHallMenu.BTN_PEACE_TARGET_BASE+selected)).bounds(btnX+148, btnY+20, 70, 16).build());
                    addRenderableWidget(Button.builder(Component.literal("Decline"), b->send(TownHallMenu.BTN_DECLINE_TARGET_BASE+selected)).bounds(btnX+222, btnY+20, 70, 16).build());
                }
            }
            case 5 -> {
                var av = menu.colony.getResearch().getAvailableToBuy();
                int start = researchScroll*3;
                for (int i=0;i<Math.min(3, av.size()-start);i++) {
                    ColonyUpgrade up = av.get(start+i);
                    int by = btnY+10 + i*34;
                    Button buy = Button.builder(Component.literal("Unlock "+up.displayName), bt->send(TownHallMenu.BTN_RESEARCH_BASE+up.ordinal())).bounds(btnX, by, 234, 16).build();
                    addRenderableWidget(buy);
                    // price handled in draw
                }
                if (av.size()>3) {
                    addRenderableWidget(Button.builder(Component.literal("▲"), b->{ if(researchScroll>0){researchScroll--; rebuild();}}).bounds(x+W-22, btnY+14,14,12).build());
                    addRenderableWidget(Button.builder(Component.literal("▼"), b->{ if((researchScroll+1)*3 < av.size()){researchScroll++; rebuild();}}).bounds(x+W-22, btnY+110,14,12).build());
                }
            }
        }
    }

    private void send(int id){ if(minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my){
        int x0=leftPos, y0=topPos, x1=x0+W, y1=y0+H;
        // outer wood frame with rivets
        g.fill(x0-2,y0-2,x1+2,y1+2, C_BG_FRAME_DARK);
        g.fill(x0,y0,x1,y1, C_BG_FRAME_MID);
        g.fillGradient(x0,y0,x1,y0+6, C_BG_FRAME_HI, C_BG_FRAME_MID);
        // rivets corners
        for(int rx: new int[]{x0+4,x1-6}) for(int ry:new int[]{y0+4,y0+18,y1-8}) g.fill(rx,ry,rx+2,ry+2, C_GOLD_DIM);
        // parchment
        g.fill(x0+3,y0+3,x1-3,y1-3, C_PARCH);
        g.fillGradient(x0+3,y0+3,x1-3,y0+12, C_PARCH_SHADOW, 0x00E8DCC6);
        // header
        g.fill(x0+3,y0+3,x1-3,y0+20, C_HEADER_BG);
        g.fill(x0+3,y0+20,x1-3,y0+22, C_GOLD);
        g.fill(x0+3,y0+22,x1-3,y0+23, C_GOLD_DIM);
        // header text is in renderLabels
        // tab underline
        g.fill(x0+3,y0+40,x1-3,y0+42, C_DIV);
        // content well
        g.fill(x0+8,y0+42,x1-8,y1-14, C_PANEL);
        // inner border
        g.fill(x0+8,y0+42,x0+9,y1-14, C_DIV);
        g.fill(x1-9,y0+42,x1-8,y1-14, C_DIV);
        g.fill(x0+8,y1-14,x1-8,y1-13, C_DIV);
        // footer
        g.fill(x0+3,y1-14,x1-3,y1-3, 0xFFEBE2C8);
        g.fill(x0+3,y1-14,x1-3,y1-13, C_DIV);

        int cx = x0+14, cy = y0+62;
        var col = menu.colony;
        var res = col.getResearch();
        if(currentTab==0) cy=y0+68;
        else if(currentTab==2) cy=y0+66;
        else if(currentTab==5) cy=y0+66;
        else if(currentTab==4) cy=y0+86;
        switch(currentTab){
            case 0 -> drawOverview(g,cx,cy,col,res);
            case 1 -> drawCitizens(g,cx,cy,col,res);
            case 2 -> drawBuildings(g,cx,cy,col);
            case 3 -> drawWarehouse(g,cx,cy,col);
            case 4 -> drawDiplomacy(g,cx,cy,col);
            case 5 -> drawResearch(g,cx,cy,res);
        }
        // footer hint
        g.drawString(font, footer(), x0+12, y1-10, C_TEXT_MUTED, false);
    }

    private String footer(){
        return switch(currentTab){
            case 0->"Tip: Shift-use the Building Tool on containers to share colony storage.";
            case 1->"Shift+right-click villager to reassign job (if researched).";
            case 2->"Completed shelters count as capacity; beds are separate.";
            case 3->"Only explicitly shared storage is available to workers.";
            case 4->"Offers expire after 7 days; leaderless colonies accept.";
            case 5->"Research auto-buys when warehouse has surplus mats.";
            default->"";
        };
    }

    // ── Overview ──
    private void drawOverview(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c, com.simplecolonies.progression.ResearchManager r){
        // banner line
        g.fill(x,y-4,x+316,y+12, C_BLUE_BG); g.fill(x,y-4,x+2,y+12, C_BLUE);
        g.drawString(font, "◆ "+c.getCulture().displayName+" — "+ColonyLevel.fromLevel(c.getLevel()).displayName+"  Lv"+c.getLevel(), x+6,y, C_BLUE,false);
        g.drawString(font, c.getCenter().toShortString()+"  R"+c.getRadius(), x+210,y, C_TEXT_MUTED,false); y+=16;

        // top stats cards row
        drawStatCard(g,x,y, 76, "Pop", c.citizenCount()+"/"+com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get(), popPct(c), C_BLUE);
        drawStatCard(g,x+80,y, 76, "Housing", housing(c)+" slots", housingPct(c), C_GREEN);
        drawStatCard(g,x+160,y, 76, "Food/day", needFood(c)+" need", foodPct(c), needFood(c)>bread(c)?C_RED:C_GREEN);
        drawStatCard(g,x+240,y, 76, "Rep", c.getReputation(menu.player)+"/100", c.getReputation(menu.player)/100f, C_GOLD);
        y+=28;

        boolean starv = c.getState()==com.simplecolonies.colony.ColonyState.STARVING;
        int stateCol = starv?C_RED: C_GREEN;
        int stateBg = starv?C_RED_BG: C_GREEN_BG;
        g.fill(x,y,x+316,y+14, stateBg); g.fill(x,y,x+2,y+14, stateCol);
        g.drawString(font, "State: "+c.getState() + (c.isLeader(menu.player)? "  ★ LEADER":""), x+6,y+3, stateCol,false);
        g.drawString(font, "• Center "+c.getCenter().toShortString(), x+180,y+3, C_TEXT_MID,false); y+=18;

        // tool + speed
        g.fill(x,y,x+316,y+14, 0xFFF7F0DE); g.fill(x,y,x+2,y+14, C_GOLD);
        g.drawString(font, "Tools Tier "+r.getToolTier()+"  Wood→Stone→Iron→Diamond  ×"+String.format("%.2f",r.getWorkSpeedMultiplier())+" speed", x+6,y+3, C_TEXT,false); y+=18;

        // quick bars
        drawBar(g,x,y, 150, "Housing fill", housing(c), Math.max(housing(c), c.citizenCount()+2), C_GREEN);
        drawBar(g,x+164,y, 150, "Bread stock", bread(c), Math.max(needFood(c)*3, 8), bread(c)>=needFood(c)?C_GREEN:C_RED);
        y+=14;

        drawHintBox(g,x,y, "Unlocked "+r.getUnlocked().size()+"/"+ColonyUpgrade.values().length+" • Farmer, Lumberjack & Builder free. Open Research to progress. Place Town Hall in new biomes to found villages.");
    }

    // ── Citizens ──
    private void drawCitizens(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c, com.simplecolonies.progression.ResearchManager r){
        var list = menu.getCitizensSorted();
        if(list.isEmpty()){ drawHintBox(g,x,y,"No citizens yet. Births need nearby adults, shelter and food; beds are separate."); return; }
        g.drawString(font, "Citizens — assignments and jobs", x,y, C_TEXT,false); y+=10;
        g.drawString(font, list.size()+" villagers  •  showing "+(citizenScroll+1)+"-"+Math.min(citizenScroll+4,list.size()), x,y, C_TEXT_MUTED,false); y+=8;
        for(int i=0;i<Math.min(4, list.size()-citizenScroll);i++){
            CitizenData cd = list.get(citizenScroll+i);
            boolean ok = r.isProfessionUnlocked(cd.job);
            int cardY=y;
            drawCitizenCard(g,x,cardY, cd, ok);
            y+=38;
        }
    }
    private void drawCitizenCard(GuiGraphics g,int x,int y, CitizenData cd, boolean ok){
        int w=296;
        g.fill(x,y,x+w,y+34, ok?0xFFFFFFFF: C_RED_BG);
        g.fill(x,y,x+w,y+1, ok?C_DIV: C_RED);
        g.fill(x,y+33,x+w,y+34, ok?C_DIV: C_RED);
        g.fill(x,y,x+2,y+34, ok?C_BLUE: C_RED);
        // avatar
        g.fill(x+4,y+4, x+20,y+20, C_PARCH_SHADOW); g.fill(x+5,y+5, x+19,y+19, ok?C_BLUE_BG:C_RED_BG);
        g.drawString(font, cd.name.substring(0,1), x+9,y+7, C_TEXT,false);
        // name + job
        g.drawString(font, cd.name, x+24,y+2, C_TEXT,false);
        g.drawString(font, "["+cd.job.displayName+"] Lv"+cd.level, x+24,y+12, ok?C_TEXT_MID:C_RED,false);
        g.drawString(font, "♥"+cd.health+"  \uD83D\uDD52 "+cd.daysEmployed+"d", x+170,y+4, C_TEXT_MUTED,false);
        if(!ok) g.drawString(font, "LOCKED — research "+cd.job.displayName, x+24,y+22, C_RED,false);
        else g.drawString(font, "Assigned (see /colony status)", x+24,y+22, C_TEXT_MUTED,false);
    }

    // ── Buildings ──
    private void drawBuildings(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c){
        g.drawString(font, "Buildings ("+c.getBuildings().size()+") — place Building Marker on your build to register", x,y, C_TEXT,false); y+=10;
        if(c.getBuildings().isEmpty()){ drawHintBox(g,x,y,"None yet. Buttons above queue a site, or place Building Marker and choose House/Farm/Lumber/Mine/Barracks."); return; }
        var buildings = c.getBuildings();
        for (int i=buildingScroll; i<Math.min(buildingScroll+4, buildings.size()); i++) {
            var b = buildings.get(i);
            boolean done = b.getState()==com.simplecolonies.building.BuildingState.COMPLETE;
            drawBuildingCard(g,x,y,b,done);
            y+=32;
        }
    }
    private void drawBuildingCard(GuiGraphics g,int x,int y, Building b, boolean done){
        int w=300;
        g.fill(x,y,x+w,y+28, done?C_GREEN_BG:0xFFF7F0DE); g.fill(x,y,x+w,y+1, done?C_GREEN:C_GOLD); g.fill(x,y+27,x+w,y+28, C_DIV); g.fill(x,y,x+2,y+28, done?C_GREEN:C_GOLD);
        // icon
        g.fill(x+4,y+4, x+20,y+20, C_PARCH_SHADOW); g.drawString(font, BuildingIcon(b.getType()), x+7,y+6, C_TEXT,false);
        g.drawString(font, b.getType().displayName+" Lv"+b.getLevel()+"  ["+b.getState()+"]", x+24,y+2, done?C_GREEN: C_TEXT,false);
        g.drawString(font, b.getPos().toShortString(), x+24,y+13, C_TEXT_MUTED,false);
        // upgrade hint
        String up = b.isPlayerPlaced ? (b.getType()==BuildingType.HOUSE ? "Marker: "+menu.colony.houseShelterCapacity(b)+" shelter slots; no beds" : "Player-designated building") : (b.isUpgrading()?"Wing under construction":b.canBuildWorksiteWing()?"Upgrade: build an additional wing":"Generated plan; no upgrade available");
        g.drawString(font, up, x+24,y+22, b.getLevel()>=5?C_GOLD:C_TEXT_MID,false);
    }
    private String BuildingIcon(BuildingType t){ return switch(t){ case HOUSE->"⌂"; case FARM->"⚘"; case LUMBERJACK_HUT->"♠"; case MINE->"⛏"; case WAREHOUSE->"▣"; case BARRACKS->"⚔"; case BLACKSMITH->"⚒"; case BAKERY->"◯"; default->"⬢"; }; }

    // ── Warehouse ──
    private void drawWarehouse(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c){
        g.fill(x,y-2,x+316,y+12, C_BLUE_BG); g.fill(x,y-2,x+2,y+12, C_BLUE);
        g.drawString(font, "Hub "+c.getWarehouseManager().getWarehouseCenter().toShortString()+"  "+c.getWarehouseManager().getChestCount()+" chests", x+6,y, C_BLUE,false); y+=14;
        var snap=c.getWarehouseSnapshot(menu.player.level() instanceof net.minecraft.server.level.ServerLevel sl?sl:null);
        if(snap.isEmpty()) drawHintBox(g,x,y,"Empty — designate storage with the Building Tool. Couriers carry one stack to the hub.");
        else{
            g.drawString(font, "Stock (top by count):", x,y, C_TEXT,false); y+=10;
            var sorted=snap.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue()).toList();
            int col=0;
            for(var e: sorted){
                if(y>topPos+190) break;
                String n=e.getKey().replace("minecraft:",""); int v=e.getValue();
                g.fill(x+col*158,y,x+col*158+154,y+12, 0xFFFFFFFF); g.fill(x+col*158,y,x+col*158+2,y+12, C_DIV);
                g.drawString(font, n, x+col*158+6,y+2, C_TEXT,false);
                g.drawString(font, String.valueOf(v), x+col*158+120,y+2, C_TEXT,false);
                col=(col+1)%2; if(col==0) y+=14;
            } if(col==1) y+=14; y+=2;
        }
        int need=(int)Math.ceil(c.citizenCount()*com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get());
        g.fill(x,y,x+316,y+12, need<=bread(c)?C_GREEN_BG:C_RED_BG); g.fill(x,y,x+2,y+12, need<=bread(c)?C_GREEN:C_RED);
        int courierStacks = c.getResearch().isUnlocked(ColonyUpgrade.WAREHOUSE_CAPACITY) ? 2 : 1;
        g.drawString(font, "Need "+need+" food/day  •  bread "+bread(c)+"  •  courier "+courierStacks+" stack"+(courierStacks == 1 ? "" : "s"), x+6,y+2, need<=bread(c)?C_GREEN:C_RED,false);
    }

    // ── Diplomacy ──
    private void drawDiplomacy(GuiGraphics g,int x,int y, com.simplecolonies.colony.Colony c){
        var targets = menu.getDiplomacyTargets();
        if (targets.isEmpty()) { drawHintBox(g,x,y,"No other colonies. Reopen to refresh the list."); return; }
        var target = targets.get(Math.min(diplomacyTarget, targets.size()-1));
        g.drawString(font, "Target #"+target.id()+"  "+target.culture()+"  ("+(diplomacyTarget+1)+"/"+targets.size()+")", x+27,y-37,C_TEXT,false);
        var relation = c.getDiplomacy().getAll().stream().filter(r -> r.otherColonyId == target.id()).findFirst().orElse(null);
        g.drawString(font, "Relation: "+(relation == null ? "NEUTRAL" : relation.relation+(relation.daysRemaining>0?" ("+relation.daysRemaining+"d)":"")), x,y,C_TEXT,false); y+=18;
        var incoming = c.getDiplomacy().incomingOffer(target.id(), Math.floorDiv(menu.player.level().getDayTime(), 24000L));
        if (incoming != null) {
            g.drawString(font, "Incoming "+incoming+" offer: choose matching action to accept.", x,y,C_GREEN,false); y+=18;
        }
        drawHintBox(g,x,y,"Offers to player leaders need acceptance; reopen to refresh.");
    }

    // ── Research ──
    private void drawResearch(GuiGraphics g,int x,int y, com.simplecolonies.progression.ResearchManager r){
        g.fill(x,y,x+316,y+12, 0xFFF0E6C2); g.fill(x,y,x+2,y+12, C_GOLD);
        g.drawString(font, "Research  Tier "+r.getToolTier()+"  ×"+String.format("%.2f",r.getWorkSpeedMultiplier())+"  "+r.getUnlocked().size()+"/"+ColonyUpgrade.values().length+" unlocked", x+6,y+2, C_TEXT,false); y+=16;
        String unlocked = r.getUnlocked().stream().map(u->u.displayName).reduce((a,b)->a+", "+b).orElse("none");
        drawWrapped(g, "Unlocked: "+unlocked, x,y,316, C_TEXT_MID); y+=10;
        g.fill(x,y,x+316,y+1, C_DIV); y+=6;
        g.drawString(font, "Available (leader — auto-buys when surplus):", x,y, C_TEXT,false); y+=10;
        var av=r.getAvailableToBuy();
        if(av.isEmpty()) drawHintBox(g,x,y,"All research done — build more to raise tier.");
        else{
            int st=researchScroll*3;
            for(int i=0;i<Math.min(3, av.size()-st);i++){
                var up=av.get(st+i); if(y>topPos+210) break;
                drawResearchCard(g,x,y, up); y+=34;
            }
            if(av.size()>3) g.drawString(font, "Showing "+(st+1)+"-"+Math.min(st+3, av.size())+" of "+av.size()+" — scroll ▼/▲", x,y, C_TEXT_MUTED,false);
        }
    }
    private void drawResearchCard(GuiGraphics g,int x,int y, ColonyUpgrade up){
        String cost = up.cost.entrySet().stream().map(e->e.getValue()+" "+e.getKey().replace("minecraft:","")).reduce((a,b)->a+", "+b).orElse("free");
        boolean tier = up.isToolTier();
        g.fill(x,y,x+316,y+30, tier?0xFFF0E6C2:0xFFFFFFFF); g.fill(x,y,x+2,y+30, tier?C_GOLD:C_BLUE); g.fill(x,y,x+316,y+1, C_DIV); g.fill(x,y+29,x+316,y+30, C_DIV);
        g.drawString(font, (tier?"◆ ":"✧ ")+up.displayName, x+6,y+3, C_TEXT,false);
        g.drawString(font, up.description, x+6,y+12, C_TEXT_MID,false);
        g.drawString(font, "Cost: "+cost, x+6,y+20, C_TEXT_MUTED,false);
    }

    // ── helpers ──
    private void drawStatCard(GuiGraphics g,int x,int y,int w, String label, String val, float pct, int col){
        g.fill(x,y,x+w,y+22, 0xFFFFFFFF); g.fill(x,y,x+w,y+1, col); g.fill(x,y,x+2,y+22, col);
        g.drawString(font, label, x+6,y+2, C_TEXT_MUTED,false);
        g.drawString(font, val, x+6,y+11, col,false);
        // tiny bar
        g.fill(x+4,y+20,x+w-4,y+22, 0xFFE8DCC6);
        g.fill(x+4,y+20,x+4+(int)((w-8)*Math.clamp(pct,0,1)),y+22, col);
    }
    private void drawBar(GuiGraphics g,int x,int y,int w, String label, int cur, int max, int col){
        float pct = max==0?0: (float)cur/max;
        g.drawString(font, label+" "+cur+"/"+max, x,y, C_TEXT_MID,false);
        g.fill(x,y+10,x+w,y+14, 0xFFE8DCC6);
        g.fill(x,y+10,x+(int)(w*Math.clamp(pct,0,1)),y+14, col);
        g.fill(x,y+10,x+w,y+11, 0x55FFFFFF);
    }
    private void drawHintBox(GuiGraphics g,int x,int y,String t){
        var lines=font.split(Component.literal(t), 300);
        int h=lines.size()*10+10;
        g.fill(x,y,x+316,y+h, 0xFFF3EAD1); g.fill(x,y,x+316,y+2, C_GOLD);
        int ty=y+5; for(var l:lines){ g.drawString(font,l,x+8,ty, C_TEXT_MID,false); ty+=10; }
    }
    private void drawWrapped(GuiGraphics g,String t,int x,int y,int w,int col){
        for(var l: font.split(Component.literal(t),w)){ g.drawString(font,l,x,y,col,false); y+=10; }
    }

    // getters for bars
    private int housing(com.simplecolonies.colony.Colony c){ return c.getShelterCapacity(); }
    private float housingPct(com.simplecolonies.colony.Colony c){ int h=housing(c); return h==0?0: Math.min(1f, (float)c.citizenCount()/h); }
    private float popPct(com.simplecolonies.colony.Colony c){ int max=com.simplecolonies.config.ColonyConfig.MAX_CITIZENS_PER_COLONY.get(); return max==0?0: (float)c.citizenCount()/max; }
    private int needFood(com.simplecolonies.colony.Colony c){ return (int)Math.ceil(c.citizenCount()*com.simplecolonies.config.ColonyConfig.FOOD_CONSUMPTION_MULTIPLIER.get()); }
    private int bread(com.simplecolonies.colony.Colony c){ var s=c.getWarehouseSnapshot(menu.player.level() instanceof net.minecraft.server.level.ServerLevel sl?sl:null); return s.getOrDefault("minecraft:bread",0); }
    private float foodPct(com.simplecolonies.colony.Colony c){ int need=needFood(c); return need==0?1: Math.min(1f, (float)bread(c)/need); }

    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font, "⌖ "+title.getString(), titleLabelX, titleLabelY, C_HEADER_GOLD, true);
        var col = menu.colony;
        g.drawString(font, col.getCulture().displayName+" • "+col.getState(), titleLabelX+140, titleLabelY, C_HEADER_SUB,false);
        // level badge
        String lvl="Lv"+col.getLevel(); int lx=leftPos+W-46, ly=topPos+6;
        g.fill(lx,ly,lx+32,ly+12, C_GOLD); g.fill(lx+1,ly+1,lx+31,ly+11, 0xFFFDF0D5);
        g.drawString(font,lvl, lx+6,ly+2, C_TEXT,false);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        if(currentTab==1){
            var sz=menu.getCitizensSorted().size();
            if(sy<0 && citizenScroll+4 < sz){ citizenScroll++; rebuild(); return true;}
            if(sy>0 && citizenScroll>0){ citizenScroll--; rebuild(); return true;}
        }
        if(currentTab==5){
            var sz=menu.colony.getResearch().getAvailableToBuy().size();
            if(sy<0 && (researchScroll+1)*3 < sz){ researchScroll++; rebuild(); return true;}
            if(sy>0 && researchScroll>0){ researchScroll--; rebuild(); return true;}
        }
        return super.mouseScrolled(mx,my,sx,sy);
    }
}
