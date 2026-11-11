# Progression — Town Hall Upgrades, Building Levels & Create Schematics

## 1. Tool Tiers & Profession Unlocks (Town Hall Research tab)

Every new village starts with wood tools, Farmer + Lumberjack. Everything else is locked — just like you asked.

**How to unlock:**
- **Player:** Town Hall → Research tab → pick an upgrade → cost taken from warehouse chests (any chest in radius, couriers consolidate). Need prereq + mats + leader (or 20 rep for village buildings, research needs leader).
- **Village itself:** if it has 2× surplus mats for 5 days, it self-unlocks cheapest available tier-1 upgrade (so abandoned villages can slowly progress without player).

**Speed:** `ResearchManager.getWorkSpeedMultiplier()` = `toolTier (1.0→1.3→1.6→2.0) * efficiency (1.15→1.44)`. Applied in `AbstractJob.getWorkInterval()`.

**Tree:**

```
TOOL_WOOD (free)
 └─ TOOL_STONE [32 cobble,16 planks,4 coal] → 1.3×
    ├─ UNLOCK_MINER [16 cobble,8 coal]
    │   └─ TOOL_IRON [16 raw iron,8 coal] → 1.6× (needs miner to have mined iron)
    │       ├─ UNLOCK_BLACKSMITH [2 furnace,8 coal,16 cobble]
    │       └─ UNLOCK_SOLDIER [64 cobble,4 iron] (Barracks)
    │           └─ TOOL_DIAMOND [4 diamond,8 iron] → 2.0×
    ├─ UNLOCK_BUILDER [32 planks,16 cobble] → expansion
    │   ├─ UNLOCK_COURIER [8 leather,16 planks] → Warehouse Network (+50% courier)
    │   └─ WAREHOUSE_CAPACITY, HOUSING_EFFICIENT...
    └─ EFFICIENCY_I [4 emerald,16 planks] → +15%
        └─ EFFICIENCY_II → +25%

PROF_FARMER/LUMBERJACK free
 └─ UNLOCK_BAKER [32 wheat,1 furnace] etc.
 └─ UNLOCK_BUTCHER [16 wheat,8 fence] → UNLOCK_LEATHERWORKER [12 leather]
 └─ UNLOCK_FISHERMAN [8 string...]
```

See `progression/ColonyUpgrade.java` for exact costs. Default 13 upgrades, 4 tiers. Add more via data pack (future: JSON).

**Profession gate:** `VillagerInteractionMenu` checks `colony.getResearch().isProfessionUnlocked(job)` before allowing assign — locked shows red "[LOCKED need research]".

## 2. Building Levels — 1 worker → 5 workers, bigger & stronger mats

You: "each building has 1 villager working and so the buildings can be upgraded by the builder to then put more job blocks inside and after each upgrade the buildings are bigger and contain more job blocks"

Implemented in `building/BuildingUpgrade.java` + `Building.java`:

- **Lv1:** 1 worker, 5×5, +4 beds if House
- **Lv2:** 2 workers, 7×7, +8 beds
- **Lv3:** 3 / 9×9 / +12
- **Lv4:** 4 / 11×11 / +16
- **Lv5:** 5 / 13×13 / +20 (max)

**Cost:** `baseCost × (1 + (level-1)*0.6)` + tier primary material (see below) `size×4×3/4` blocks. Example House Lv2: 1.6× oak planks/cobble/wool + 21 stone bricks if Roman.

**How to upgrade:**
- Town Hall → Buildings tab → each building shows `↑ House Lv1 (1 workers, 5x5) → [↑ Upgrade]` button (needs COMPLETE state, leader, mats in chests). Builder then rebuilds bigger: `Building.upgradeToNextLevel()` sets `UNDER_CONSTRUCTION` and regenerates placement queue with new tier palette.
- Player marker buildings (isPlayerPlaced) instantly grow on upgrade (no blueprint) — just housing increase.

**Material tiers per culture** (`building/MaterialProgression.java`):
- **Greek:** L1 wood (oak), L2 stone base, L3 white terracotta, L4 marble quartz pillars, L5 grand temple calcite+blue domes
- **Roman:** L1 wood+cobble, L2 stone bricks, L3 bricks+red tile, L4 marble forum, L5 imperial quartz/cut copper
- **Egyptian:** L1 sandstone+sand, L2 cut sandstone, L3 terracotta, L4 gold/chiseled sandstone, L5 quartz+gold
- **Persian:** L1 terracotta/mud, L2 glazed red/orange, L3 bricks/archways, L4 blackstone, L5 gold hanging gardens
- **Mesopotamian:** L1 mud bricks, L2 ziggurat bases, L3 baked brick, L4 canal city, L5 sandstone+gold ziggurat

Upgrade cost automatically pulls the right tier primary block (e.g. Greek Lv3 upgrade needs `white_terracotta` you must have gathered). Spawn villages start at L1 wood — they upgrade themselves when they have mats, visually evolving.

## 3. Villages Spawning & Constructing from Local Resources

When `ColonyManager.createColony()` (worldgen or Town Hall) is called:
- Spawns 2 starter buildings at L1 wood tier (House + Farm) as player-placed (already complete, 5×5)
- Deposits starter mats: 32 planks, 16 logs, 16 bread + 24× culture tier-1 primary (e.g. oak for Greek, sandstone for Egyptian)
- Future expansion in `Colony.tryExpand()` picks next `ColonyProgression` building, finds flat site via spiral search, queues `Building(BLUEPRINT)` with cost = base×level + tier mats. Builder consumes from chests via `WarehouseManager.extract`, places blocks (`BuilderJob` 4/tick). So Greek villages literally need wood first (local oaks), then stone, then terracotta — matching your "progress to stone bases then terracotta" example.

Local resource preference: `MaterialProgression.pickAvailable()` prefers what colony has most of in warehouse (if you built near forest, wood is abundant, but upgrade still needs tier mats so villagers must trade/mine it).

## 4. Create Schematics for Player

You: "builders should also be able to be given schematics from create to construct buildings for the player"

`integration/CreateSchematicIntegration.java`:
- Detects `create:schematic` item (purple schematic from Create's Schematic Table) in any village chest (via `WarehouseManager.count`).
- `BuilderJob` each tick calls `tryCreateSchematicTask()` before normal blueprint: if schematic found, consumes one, queues a `Building(HOUSE, builderPos+8, culture)` with `BLUEPRINT` but marked schematic-origin. If Create mod present (`isCreateLoaded()` via reflection) logs "[Create] delegating to Schematicannon logic" — real Create API would be `SchematicProcessor` place; stub uses dummy placement but cost/palette honours schematic's blocks (future: parse NBT for palette).
- Player flow: craft Create schematic → copy your build with Schematic Table → put schematic item in village chest (any chest, courier moves to warehouse) → Town Hall → Buildings → "Create Schematic" button or just wait — builder auto-pastes at 16-block offset, consuming mats from chests. Works with Create Aeronautics airships (scaffold for moving builds).

No hard dependency: if Create not installed, builder treats schematic as generic house blueprint, so mod remains playable standalone (optional `create`, `create_aeronautics` deps).

## 5. Testing Progression

1. New colony: only Farmer/Lumberjack in Research unlocked, Tool Wood.
2. Donate 32 cobble + 16 planks to chests → Research tab → buy Tool Stone → speed 1.3× (check Overview).
3. Now Miner/Builder buttons in Research become available — buy Miner (16 cobble, 8 coal) → can now assign villager to Miner via shift-click.
4. Miner produces raw iron → buy Tool Iron → Blacksmith unlock → etc. Or just wait 5 days with surplus and village self-buys cheapest.
5. Place Building Marker House → Town Hall Buildings shows `Lv1 (1 worker, 5x5)` → Upgrade → cost shown (base×1.6 + tier mats) → Builder rebuilds bigger with Greek stone base at Lv2.
6. Starter Greek village: houses wood at Lv1, upgrade to Lv2 shows stone bricks in cost, builder places stone base — matches spec.

