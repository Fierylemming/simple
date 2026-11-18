# Automated test coverage

This file exists because manual playtesting kept missing breakage. It states, plainly,
what CI proves on every push, what it only partly proves, and what it cannot prove at
all. Read it before assuming a green build means "the mod works".

Current numbers: **33 GameTest classes / 236 GameTest instances**, plus **31 JUnit
classes** (pure-logic tests such as `DailyCatchUpTest`, `GrowthBoundsTest`,
`ColonyPropertyMemoryTest`, `OverlappingColonyTest`, `StarterSiteSearchTest`,
`FoodRulesTest`).

## How the tests run

Every push to `arena/e0f7e936-simple` runs `.github/workflows/build.yml`:

1. `./gradlew build` — compiles the mod and runs the JUnit suite.
2. `runGameTestServer` — a headless integrated server loads a real world and runs the
   GameTests: real blocks, real items, real pathfinding, real furnace smelting, real
   entity saves.
3. A Create-compat job runs the same suite with Create installed.

A push whose commit message contains `[release]` additionally publishes
`v<mod_version>` with the jar attached. `workflow_dispatch` is not enabled for the
workflow token, so a fresh run is triggered by pushing a commit (an empty one is fine).

Locally: `./gradlew test` for the JUnit suite, `./gradlew runGameTestServer` for the
GameTests. A JDK 21 toolchain is required.

## What a green run proves, area by area

| Area | Automated proof |
| --- | --- |
| Founding a colony | `StarterColonyGameTests` (hall, farm and supplies on soil and on stone), `SettlementLifecycleGameTests.naturalFoundingUsesItsOwnSupplies` (founding through the mod's own site search) |
| Owning a colony over time | `SettlementLifecycleGameTests.settlementSurvivesSeveralSimulatedDays` — founders, physical bread, three crossed day boundaries, no unsettled rations, save/load round-trip equality |
| Local wood species | `BiomeWoodGameTests.aRomanColonyInABirchForestUsesBirchThroughout`, `HousingGrowthGameTests` (birch/spruce variants, civic hall), `WoodCostsTest` |
| Growth and tiers | `ColonyLevelGameTests`, `HousingGrowthGameTests` (8), `WorksiteExpansionGameTests` (9), `GrowthBoundsTest`, `OverlappingColonyTest` |
| Construction | `ConstructionGameTests` (39 house plan cases), `PhysicalBuilderGameTests`, `PhysicalBuilderMaterialsGameTests`, `MarkerHouseWingGameTests` |
| Storage ownership | `WarehouseGameTests` (6: explicit ownership, revocation on reload, modified items, shulkers, furnaces excluded) |
| Food, meals, growth | `TradeAndMealGameTests` (real walk to storage and eating), `FoodConsumptionGameTests`, `VillagerGrowthGameTests` (pending health days, cancelled births keep food) |
| Shelter and night | `ShelterGameTests` (14), `SocialGameTests` (gossip, civic meeting, approach) |
| Woodcutter | `WoodcutterGameTests` (27: tall/dark-oak/birch forms, slope, resume after save, tool wear, unsafe sites) |
| Miner | `MinerGameTests` (walk + excavate with a real pickaxe, craft a pickaxe from real inputs, retain cargo when storage is full) |
| Miner site rotation | code path only (`step >= MAX_STEPS` → `resetSite()`, `siteIndex` cycles through `SITES`); **not** asserted by a test — see gaps |
| Farmer | `FarmerGameTests` (7), `FarmReplantGameTests` |
| Fisherman | `FishermanGameTests` (9: constructed harbour, shore routing, retries, school disappears) |
| Blacksmith and furnace | `BlacksmithGameTests` (6), `SmithFurnaceGameTests` (7: real furnace over time, batch ownership, occupied furnace, player changes input) |
| Butcher | `ButcherGameTests` (10: own drops only, breeding pair protected, cannot attack or feed through a wall) |
| Bakery / leatherworker | `PhysicalProductionGameTests` (baker carries wheat and bread, leatherworker carries leather and armour), `LeatherworkerGameTests` |
| Courier | `CourierGameTests` (5: full hub, unreachable source, nested shulker, real persistent stack) |
| Guard / soldier | `GuardGameTests` (5), `GuardEquipmentGameTests` (5), `DefenderDiplomacyGameTests`, `BlacksmithGameTests` guard sword supply |
| Tooling across job changes | `ToolReassignmentGameTests`, `ToolReplacementGameTests`, `PhysicalJobCargoGameTests` (4) |
| Research | `ResearchGameTests` (9: carried inputs, cancelled research returns inputs, no withdrawal through walls) |
| Trade and diplomacy | `TradeGameTests`, `TradeAndMealGameTests`, `TownHallSafetyGameTests`, `ColonyCommandGameTests`, `VillagerMenuGameTests` |
| Griefing / property | `GriefingProtectionGameTests` (creeper blast, player TNT, piston), `BuildingMarkerGameTests`, `TownHallRepairProtectionGameTests` |
| Create schematics | `CreateIntegrationGameTests` (4: queue once, double slab needs two real items, rotation, unsupported schematic consumes nothing) |
| Day skipping | `DailyCatchUpTest`, and the crossed day boundaries in `SettlementLifecycleGameTests` |

## Flakiness, measured

The suite is not perfectly deterministic; tick budgets and pathfinding in small
fixtures are the usual culprits. Observed this cycle:

| Test | Failure mode | Evidence |
| --- | --- | --- |
| `MinerGameTests.minerWalksAndExcavatesWithRealTool` | "Miner has not advanced" — long walk inside the fixture | Failed in runs 43917092197, 44096892633, 44097792338, 44099105612; green after the test's tick budget was widened to 12000 |
| `WoodcutterGameTests.harvestsSquaredDarkOakAndReplantsFourRealSaplings` | replant window too short | Failed in 43917908911; fixed by budget |
| `WoodcutterGameTests.harvestStalledStraightTreeFromNaturalSlope` | genuinely broken pre-fix | Failed in 43912867491; real bug, fixed |
| `FishermanGameTests` shore routing | intermittent path failure | Historically intermittent; two consecutive green runs after the water-avoidance change |
| `GriefingProtectionGameTests.pistonCannotMoveAColonyBlock` | genuinely broken pre-fix | 43912867491; real bug, fixed |

Rule of thumb: a single red run on one of the listed pathfinding tests is a flake, but
re-run before believing it — each fresh run needs its own push.

## What CI does *not* cover

These are the honest gaps; they are also why a manual pass can still find problems.

1. **Client side.** Screens, tooltips, sounds, labels, rendering of plans and markers.
   CI runs a headless server; no rendering happens.
2. **Navigation over real terrain.** GameTests use small flat fixtures. Long walks,
   dense forest, ravines, water edges and unloaded chunks are exercised only by the
   flakiest test in the suite (the miner), so "the villager walked into a tree and got
   stuck" can still reach a player first.
3. **Dedicated server.** `runGameTestServer` is a headless *integrated* server. A real
   dedicated-server run (branding, config, permissions, `/colony` command) is not
   automated; that is a runner choice, not a code limitation.
4. **Long-run behaviour.** Days are simulated by moving the clock, not by rendering
   thousands of ticks of villager AI. Slow drift (resource drain, stuck jobs after a
   week, entity growth) is not measured.
5. **Site exhaustion.** The miner's step cap and site rotation are code paths without a
   focused test; a full quarry would need a large fixture and a long budget, which the
   miner test is least able to afford today.
6. **Starvation.** The colony has no automated "all food gone" scenario; death and
   rationing behaviour is only partly modelled.
7. **Several workers competing.** Most tests run one worker at a time. Contention for
   one workstation, one chest or one door is exercised indirectly, not asserted.
8. **Balance and feel.** Whether the economy feels right over a session is not a test.
9. **Save migration.** Save/load round-trips are tested; loading a world saved by an
   older overhaul version is not.

## If CI goes red

1. `gh run list --branch arena/e0f7e936-simple --limit 3`, then
   `gh run view <id> --json jobs --jq '.jobs[]|{name,conclusion}'`.
2. Failing step is usually "Run GameTests"; the log names the test and the
   `helper.fail` message, which is written for a human.
3. Check the flake table above before changing code.
4. Re-run by pushing an empty commit (`git commit --allow-empty -m "Rerun"`).
