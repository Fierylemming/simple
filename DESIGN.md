# Simple Colonies — Ancient Villages — Design Document

> A simpler MineColonies for Minecraft 1.21.1 NeoForge, compatible with Create Aeronautics.

## 1. Vision
MineColonies is amazing but heavy. Simple Colonies keeps the fantasy — living villages that farm, chop, mine, build and fight — but strips the complexity to 5 jobs + supply board + cultural biomes + leader & war.

Player story:
1. Wander desert, find Egyptian mud-brick hamlet with farmers.
2. Trade, defend from zombies, gain reputation.
3. Become leader at 75 rep → open Town Hall → order new lumberjack hut.
4. Watch builder fetch logs/stone from warehouse and place blocks over minutes (not instant).
5. Colony grows to town, unlocks blacksmith, barracks.
6. Recruit 6 soldiers with Command Staff, sail/drive airship (Create Aeronautics) to attack Persian savanna colony, or ally.

---

## 2. Core Loops

### 2.1 Survival Needs
Every **in-game day** each citizen consumes `FOOD_CONSUMPTION_MULTIPLIER` food (default 1). Warehouse is the shared stock.
- Food counted: bread, wheat, carrot, potato, cooked meats, stew, apple.
- If `available >= needed` → consume, `daysWithoutFood=0`.
- Else → eat remainder, `daysWithoutFood++`, broadcast starving.
- After `DAYS_WITHOUT_FOOD_BEFORE_STARVATION` (3) → villagers get Weakness, then starve damage if `VILLAGERS_DIE_WITHOUT_FOOD`.
- Housing: each `House` gives 4 beds × level. Overcrowded → broadcast, no new children, slower rep.

This forces player to secure farmer first.

### 2.2 Jobs & Supply Chain
| Job | Workplace | Consumes | Produces | Supplies To |
|-----|-----------|----------|----------|-------------|
| Farmer | Farm | seeds, water | wheat/carrots | Everyone (food), Butcher (wheat), Baker |
| Woodcutter | Lumberjack Hut | axe | logs | Builder, Warehouse, Blacksmith (fuel) |
| Miner | Mine | pickaxe | cobble, coal, raw iron/copper | Blacksmith, Builder |
| Builder | any blueprint | all building mats | completed buildings | Everyone (houses) |
| Butcher | Butcher Hut | wheat | beef/pork, **leather** | Leatherworker, colony food |
| Leatherworker | Leather Shop | **leather** (from butcher) | leather armor | Soldiers, Guards |
| Blacksmith | Forge | ores + coal | ingots, tools, swords | Miner, Woodcutter, Soldiers |
| Soldier/Guard | Barracks/Castle | armor, swords | defense, war | Player armies |
| Baker | Bakery | wheat | bread (2× food value) | Warehouse |

**Supply board:** `villager.ai.SupplyChain` is a global pending-request map per colony. When `requestFromColony(item, amount)` fails, it posts `SupplyChain.request()`. Producers scan board each tick: butcher sees `leather:4` and fulfills. No hard-coded links except emergent preference.

Result: butcher→leatherworker→armorer chain the user asked for works naturally, extendable to any mod item via data pack.

### 2.3 Builder & Blueprints
- Culture-specific NBTs: `data/simplecolonies/structures/egyptian/town_hall_1.nbt` etc.
- Palette swapped per culture: Egyptian sandstone/terracotta, Roman quartz/brick red, Greek white concrete/calcite, Persian glazed terracotta, Mesopotamian mud bricks.
- On placement `Building` loads schematic → queue of `BlockPlacement`.
- `BuilderJob` places `MAX_BUILDER_BLOCKS_PER_TICK` (4 default) after verifying warehouse has resources; if missing, sets `NEEDS_RESOURCES` and posts supply requests.
- Player can see ghost outline (client particles) and missing-items list in Town Hall GUI.

This is the “schematic_blueprint” option voted in clarification — gradual, resource-consuming, visible.

### 2.4 Natural Expansion
Every `EXPANSION_CHECK_DAYS` (5) if:
- food surplus ≥ 2× daily need
- at least 1 builder exists
- pop < max per colony
Then `ColonyProgression.getNextBuilding()` picks next unlock for current level. It looks for free flat site in expanding ring `findBuildSite()` and creates `Building(BLUEPRINT)`. If all buildings for level done, colony **levels up**: radius +16, unlocks next tier.

Example progression (from `ColonyProgression.java`):
- Lv1 Hamlet: House, Farm, Lumberjack
- Lv2 Village: Mine, Warehouse, Bakery
- Lv3 Town: Blacksmith, Barracks, Town Hall upgrade
- Lv4 City: Market, Library, Temple
- Lv5 Metropolis: Castle, Walls, Harbor

Duplicate houses/farms allowed up to `level*2`.

### 2.5 Culture & Biomes
`CultureType.java` defines 5 cultures. Placement is two-fold:
1. **Biome tag** (`tags/worldgen/biome/has_egyptian_villages.json`): explicit list, data-pack editable, allows modded biomes.
2. **Heat fallback** `fromBiomeTemperature(temp)`: for procedurally generated or untagged biomes, map Mojang temperature:
   - ≥1.6 → Egyptian (desert/badlands)
   - ≥1.0 → Persian (savanna)
   - ≥0.7 humid → Mesopotamian (river/swamp)
   - ≥0.6 → Roman (plains/forest)
   - else → Greek (beach/ocean hills)

This satisfies “placed in correct biomes based on the heat then the buildings should be in that village style”.

Village structures themselves use culture palette + architecture: Egyptian pyramids/open courtyards, Roman arches/red roofs, Greek white columns/blue domes, Persian glazed archways, Mesopotamian ziggurats.

`worldgen/structure_set/*.json` spread placement so colonies don’t crowd. If `REPLACE_VANILLA_VILLAGES=true`, a biome modifier removes `minecraft:village_*`.

### 2.6 Player as Leader
Vanilla trading kept (`ColonyVillager extends Villager`). Added:
- **Reputation 0-100 per player per colony**. Gained by: trading (+1), killing raider near colony (+5), completing Town Hall “request board” deliveries (+3), donating food (+2). Lost by: stealing (-10), killing villager (-20), abandoning during war (-15).
- At `REPUTATION_NEEDED_TO_LEAD` (75) with no current leader → auto-elected, `setLeader()` broadcasts.
- Leader gets Town Hall extra tabs: citizen job assignment (shift-right-click villager), building queue reorder/cancel, warehouse stock + request board, diplomacy.
- Housing/job assignment also doable via `/colony` command fallback for headless servers.

Design vote was **reputation / quest unlock**, not instant claim — respects “hardcore but fair”.

### 2.7 Soldiers, War & Alliances
Config `ENABLE_WARFARE`. Jobs `SOLDIER` (follows leader) and `GUARD` (patrols).
- Recruit: Barracks building spawns guard; assign existing unemployed via Town Hall to soldier → they seek armor + sword from warehouse (supplied by blacksmith/leatherworker).
- **Command Staff** (`item/CommandStaffItem`): leader right-click soldier → `SoldierJob.setCommander(player, follow=true)`, now AI `NearestAttackableTargetGoal` targets player’s last hurt mob. Shift-right-click ground → rally all soldiers within colony radius pathfind to player.
- **Diplomacy** (`colony/Diplomacy`): per colony map `otherId → Relation(WAR/ALLIED/PEACE/NEUTRAL, days)`. Leader can at Town Hall: Declare War (14-day war), Propose Alliance (permanent until broken), Make Peace (30-day truce). War = soldiers of both colonies hostile to each other + `ColonyState.UNDER_ATTACK` triggers defense. Alliance = shared trader discounts, allied soldiers assist.
- Wars auto-expire to neutral. Logging to console for server admins.

Future: Create Aeronautics airship raids — soldiers could board contraptions (API hook noted).

---

## 3. Technical Architecture

```
colony/
  Colony.java           // central data + tick + NBT
  ColonyManager.java    // singleton SavedData per overworld, id alloc, world save
  ColonyState, ColonyLevel, ColonyProgression, Diplomacy
  ColonyEventHandler    // server tick trigger
  ColonyCommand         // /colony
culture/
  CultureType.java      // 5 cultures, palette, temp mapping, NBT path
villager/job/
  JobType, AbstractJob, FarmerJob, WoodcutterJob, MinerJob, BuilderJob, SoldierJob, ButcherJob, LeatherworkerJob, BlacksmithJob
villager/ai/
  SupplyChain.java      // request board
building/
  Building.java, BuildingType, BuildingState
block/
  TownHallBlock (creates colony), WarehouseBlock, ColonyBannerBlock
item/
  CommandStaffItem
entity/
  ColonyVillager (Villager subclass, hunger, job tick, NBT)
worldgen/
  ModWorldGen + data-driven structure_set + biome tags
config/
  ColonyConfig (common + client)
```

**Persistence:** `ColonyManager.SavedDataHolder` stored as `simplecolonies.dat` in dimension data storage. Also each `ColonyVillager` stores `colonyId` to reconnect after reload. Colony radius check `isInside()` uses stored center + level-derived radius, not chunk claims (simpler than MineColonies).

**Performance:** Colony ticks every `TICKS_BETWEEN_COLONY_TICK` (200 = 10 sec) plus daily heavy tick at 24000 boundary. Builder capped at 4 blocks/tick. Pathfind range capped 64. Supply chain is hash map, O(1).

**Compatibility:** Project targets NeoForge 21.1.265 / MC 1.21.1 (same as Create Aeronautics 1.3.2). Create marked `optional` dependency, loader AFTER to ensure ordering. No direct API calls yet, but hooks ready for `create_aeronautics` contraption boarding.

---

## 4. Balancing Defaults (hardcore fair)
- 60 pop max → needs ~6 houses + 2 farms to sustain.
- Farms feed ~10 each if farmer active; woodcutter plants to avoid deforestation.
- Miner depth loops after 50 blocks → needs relocation via rebuilding mine (prevents infinite void mining).
- Starvation 3-day grace + heal otherwise → player has time to rescue with food donation.
- Supply chain means if you neglect one job, dependents suffer — teaches interdependence.

---

## 5. Future Extensions
- GUI mockups (Town Hall with tabs) → add `client/screen/TownHallScreen.java` using NeoForge screens.
- Data gen for structure templates via Structure Blocks; community can submit culture .nbt packs.
- Integration: Create conveyor delivery to warehouse, Aeronautics airlifts, JEI trade viewer.
- Advancements: “First Colony”, “Leader Elected”, “First War Won”.

---

## 6. Setup for Contributors
See README build steps. Requires JDK 21, NeoForge 1.21.1 MDK template already applied.

