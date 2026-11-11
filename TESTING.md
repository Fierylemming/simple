# Playtesting Guide — Simple Colonies 0.2.0

You now have **fully interactive GUIs** — no more chat-only town hall.

## How to Test (NeoForge 1.21.1 + Create Aeronautics)

```bash
./gradlew runClient   # singleplayer with ColonyManager shared (easiest)
# or ./gradlew build -> build/libs/simplecolonies-0.2.0.jar -> drop into mods/ with create/sable/aeronautics
```

### 1. Find or make a colony
- `/colony list` — should be 0 at start
- Place a **Town Hall** (creative tab "Simple Colonies") in any biome. Right-click it:
  - First click creates colony, auto-detects culture by biome temp (desert=Egyptian, plains=Roman, beach=Greek, savanna=Persian, swamp/river=Mesopotamian). You get +10 rep.
  - Re-open → **Town Hall GUI** opens with 5 tabs.

### 2. Town Hall GUI (5 tabs)
- **Overview:** Culture, level, radius, pop/state, your rep, housing beds. Buttons: `Donate 5 Bread (+rep)` (needs bread in inv), `Claim Leader` (needs 75 rep), `Rally Soldiers`.
- **Citizens:** Scroll list of villagers (name, job, lvl, health). Shows up to 4 at a time with ▲/▼. Tip: shift-right-click villager in world for full job assign.
- **Buildings:** Shows all built/blueprint buildings + state (BLUEPRINT/UNDER_CONSTRUCTION/COMPLETE). Buttons to `Queue House/Farm/Lumber Hut/Mine/Barracks` — needs leader. Builder will place blocks gradually if warehouse has mats; otherwise `NEEDS_RESOURCES` + request board.
- **Warehouse:** Shared stockpile sorted by count. Shows food needed per day. Farmers/woodcutters/miners auto-deposit.
- **Diplomacy:** Neutral → `Declare War` (picks nearest other colony, 14d war) / `Propose Alliance` (permanent). Only leader.

### 3. Villager GUI
- Spawn naturally when colony creates villagers (via `ColonyVillager.finalizeSpawn`) or place via `/summon simplecolonies:colony_villager`.
- **Normal right-click:** vanilla trading (job gives default trades).
- **Shift+right-click:** opens **VillagerInteractionScreen**:
  - Top shows name, job, level, health, days, colony.
  - 8 job buttons: Farmer/Woodcutter/Miner/Builder/Soldier/Butcher/Leatherworker/Blacksmith (needs 30 rep, 50 for soldier).
  - `Trade (vanilla)` hints, `Command: Follow Me` (marks soldier), `Level Job` (cheat levels), `Close`.
  - Changing job updates `CitizenData` + live entity AI instantly.

### 4. Survival / Supply Chain test
- Place Town Hall → spawn 1 farmer via egg → check Warehouse: after ~40 ticks farmer deposits wheat. Donate bread → rep → claim leader → queue lumber hut → check Buildings tab → warehouse should show missing mats if not enough wood.
- Let colony run: colony ticks every 10s + daily food consumption at 24000 tick boundary. Starve it (remove farmer, set warehouse empty) → after 3 days `STARVING` + villagers get weakness/starve damage.

### 5. War test
- Make 2 colonies 100 blocks apart (`/colony list`, `/colony tp <id>`).
- Become leader of both (or use 2 players).
- In attacker Town Hall Diplomacy → Declare War → both mark WAR.
- Give both Barracks + Soldiers (assign unemployed to soldier, give sword via blacksmith chain). Rally with Command Staff (shift-right-click ground) — soldiers pathfind to you, attack your target.

### Known playable limits in this scaffold
- Warehouse is in-memory map, not backed by chest BE yet — donation via button, not chest inventory. Real BE warehouse chest coming.
- Builder places dummy stone blocks (shape from type.baseCost, not NBT templates) — place real NBTs in `data/simplecolonies/structures/<culture>/*.nbt` to enable cultural builds.
- Client colony sync uses shared ColonyManager in singleplayer; dedicated server needs `TownHallPayload` packet (stub in `network/payload` — next iteration).
- Textures: GUIs are code-drawn parchment, no png needed; block textures are placeholders.

### Debug
- Logs: `logs/latest.log` filter `SimpleColonies`
- `/colony delete <id>` cleanup
- Config `simplecolonies-common.toml` to tweak food/housing/war.

Enjoy — report rep gains / builder site finding issues in GitHub.

