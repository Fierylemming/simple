# Progression — Self-Sufficient Town Hall Upgrades, Building Levels & Create Schematics (REWORKED)

> **Your fix:** villages must be able to self-progress with previous tier’s resources — no player-required bottleneck.

## New Tree (every tier’s cost only needs previous tier’s jobs)

```
Tier 0 free (wood, wheat, wood tools 1.0×):
  TOOL_WOOD, PROF_FARMER, PROF_LUMBERJACK

Tier 1 — Stone Age (needs only wood/cobble/coal/wheat/string/wool — all from Tier0):
  TOOL_STONE [32 cobble,16 planks,4 coal] → 1.3×
  ├─ UNLOCK_BUTCHER [16 wheat,8 fence] → leather for Tier2
  ├─ UNLOCK_BAKER [32 wheat,1 furnace]
  ├─ UNLOCK_FISHERMAN [8 string,8 planks]
  ├─ after TOOL_STONE:
  │   ├─ UNLOCK_MINER [16 cobble,8 coal] → now can mine iron/redstone/diamond
  │   ├─ UNLOCK_BUILDER [32 planks,16 cobble] → expansion + upgrades
  │   ├─ EFFICIENCY_1 [16 wheat,16 planks,4 coal] (was emerald — now wheat so self-buyable)
  │   └─ HOUSING_EFFICIENT [8 wool,16 planks]

Tier 2 — Iron Age (needs raw_iron from Tier1 Miner, leather from Tier1 Butcher, planks):
  UNLOCK_BLACKSMITH [2 furnace,8 coal,16 cobble] → prereq Miner (so Miner exists to feed it), no iron needed — it will make iron
  TOOL_IRON [16 raw_iron,8 coal,8 planks] → prereq TOOL_STONE+MINER+BLACKSMITH (needs Miner raw_iron + Blacksmith to smelt)
  ├─ UNLOCK_COURIER [8 leather,16 planks,4 string] → needs Builder+Butcher (leather from Tier1)
  ├─ UNLOCK_LEATHERWORKER [12 leather] → needs Butcher
  ├─ WAREHOUSE_CAPACITY [8 chest,32 planks] → needs Courier
  └─ UNLOCK_SOLDIER [64 cobble,4 iron_ingot] → needs Builder + Iron tools (iron via Blacksmith)

Tier 3 — Diamond/Advanced (needs diamonds/redstone — only mineable with iron pick from Tier2):
  TOOL_DIAMOND [4 diamond,8 iron] → needs Iron
  EFFICIENCY_2 [8 emerald,1 diamond] → needs Eff1+Iron (emeralds via trading, but village can wait)
  SCHEMATIC_CANNON [2 iron_block,8 redstone] → needs Builder+Iron (redstone needs iron pick)
```

**Key change:** Blacksmith no longer needs Iron tools — it’s the *producer* of Iron tools. Courier no longer needs Builder-only, needs Builder+Butcher so leather already available. Butcher/Baker moved to Tier1 so Tier2 courier/leather have leather/wheat. Efficiency_1 no longer needs emerald (village can self-buy without trading).

Village auto-progress (`ResearchManager.tickAutoProgress()` every 5 days) picks cheapest unlockable it can afford from *any* tier it has prereqs for — so a wood-tier village with 32 cobble will self-buy Stone, then Miner, then Builder, then with stone it mines iron → Blacksmith → Iron tools → etc., all without player.

## Building costs also reworked for self-sufficiency

`BuildingType` costs now tier-aligned:
- **Lumberjack Hut:** was `iron_axe` → now `stone_axe` (Tier1 can craft)
- **Blacksmith:** was `anvil` (needs iron) → now `furnace+planks+cobble` (buildable at Tier1, then it makes anvil)
- **Barracks/Castle:** still need `iron_ingot`/`iron_block` but they’re Tier3 buildings (town hall level 3+) so village already has iron by then
- **Market:** was `emerald` → now `gold_ingot` (gold mineable with stone? Actually gold needs iron pick, but Market is Tier4 late, okay) — changed to gold for self-progress without emerald trading
- **Temple:** was `quartz_block 32 + gold 2` (Nether) → now `quartz_block 16 + sandstone 32` (late but mineable after iron)

So `ColonyProgression` (House/Farm/Lumber → Mine/Warehouse/Bakery/Butcher → Blacksmith/Leather/Barracks → Market/Library/Temple → Castle) only queues buildings whose job is already unlocked via research — it won’t try to build a Barracks before Soldier research, so no dead-ends.

## Building levels

Each building 1 worker at Lv1, upgrades via `BuildingUpgrade`: cost = `base×(1+0.6*(lvl-1)) + tier primary block from MaterialProgression` (e.g. Greek wood→stone→terracotta). Builder checks `WarehouseManager` for mats (any village chest), consumes cheapest, rebuilds bigger. Housing +4 beds/level. Max 5.

## Create schematics

Put `create:schematic` in any chest → `BuilderJob` auto-detects before normal blueprints, consumes one, queues `HOUSE` placeholder using Create paste (optional Create dep, still works without). Town Hall Buildings tab has “Create Schematic” button for manual queue.

