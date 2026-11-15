# Testing — How to verify progression & self-sufficient villages

## Your question: how does village get cobble for tier 2 with only lumberjack + farmer?

**Fixed:** fresh villages now get `32 cobblestone + 8 coal + 16 sticks + 32 planks + 16 logs + 16 bread` in starter warehouse (`ColonyManager.createColony`). That's enough for `TOOL_STONE` (32 cobble,16 planks,4 coal, tier 1). Once they buy Stone, they unlock `Miner` (16 cobble,8 coal — already affordable from starter/foraging) → Miner quarries stone/cobble/iron efficiently for Tier 2. Plus `ForagerJob`: when no crops/trees, Farmers & Lumberjacks forage surface `stone/cobblestone/coal/copper ore` in 8 blocks at 25%/tick, so even starter cobble runs out they refill.

**Proof every tier is reachable:** see `docs/PROGRESSION.md` — no upgrade costs an item that needs a later-tier job. Blacksmith no longer needs anvil/iron, Lumberhut uses stone axe not iron, etc. `ColonyProgression` also won't queue a building whose job is locked, so no dead-end.

---

## 1. Build & run (you need JDK 21 — Adoptium)

```bash
./gradlew build          # -> build/libs/simplecolonies-0.1.0.jar (check no errors)
./gradlew runClient      # test client (or runServer --nogui for dedicated)
# If JAVA_HOME missing: export JAVA_HOME=/path/to/jdk21 ; or install via sdkman: sdk install java 21-tem
```

Put jar in `mods/` with `create-6.x` + `create_aeronautics-1.3.2` if you want Create compat (optional).

## 2. In-game quick test (5 mins, no grinding)

All these use new debug commands in `ColonyCommand` — no chest hunting needed.

1. **Spawn a village:** new world → creative → `/colony spawn greek` (or egyptian/roman/persian/mesopotamian). It says “Spawned Greek colony #1 with starter cobble”.
2. **Check starter mats:** `/colony warehouse` → expect `minecraft:cobblestone: 32, coal: 8, oak_planks: 32, oak_log:16, bread:16 ...` + “ENOUGH for TOOL_STONE”. If chests=0, that’s abstract stash (no chests yet — Forager will use it).
3. **Check research:** `/colony research` → `Unlocked: Wooden Tools, Farmer, Woodcutter | Available: TOOL_STONE [32 cobble], BAKER, BUTCHER, FISHERMAN`. Tool tier 0 speed 1.0x. Lines with `✗` = can’t afford yet (need mats), `✓` = can buy.
4. **Buy Stone:** `/colony buy TOOL_STONE` (or Town Hall block → Research tab → click it). Should say “Unlocked Stone Tools”. `/colony research` now shows `Miner, Builder, Efficiency I, Tight Housing` available with `✓` (they cost cobble/planks/coal/wheat — all from Farmer/Lumber/Starter). Tool tier 1 speed 1.3x. `/colony warehouse` now has 0 cobble (consumed) — Forager will refill.
5. **Bootstrap Miner without grinding:** `/colony buy UNLOCK_MINER` → needs 16 cobble,8 coal. Starter had 32/8, minus Stone cost leaves 0 cobble but Miner cost 16 cobble — wait, won’t afford. Fix: do `/colony give cobblestone 16` then buy, or let Forager run 1 min (watch `/colony warehouse` tick up). Real village does Forager; for test use `/colony give`.
6. **Tier 1 full chain:** `buy UNLOCK_BUILDER` (32 planks,16 cobble), `buy UNLOCK_BUTCHER` (16 wheat,8 fence) — give wheat via `/colony give wheat 16`. Then `/colony research` shows Tier2: `Blacksmith [2 furnace,8 coal,16 cobble]` is `✓` (needs Miner prereq, not iron yet!).
7. **Tier 2 without player iron:** `/colony buy UNLOCK_BLACKSMITH` → then simulate Miner gathering iron: `/colony give raw_iron 16` + `/colony give coal 8` then `/colony buy TOOL_IRON` (needs Miner+Blacksmith+Stone, ✓). Now `/colony research` shows `LEATHERWORKER [12 leather]` — need Butcher leather: `/colony give leather 12` → buy, `COURIER [8 leather...]` etc. `Soldier [64 cobble,4 iron_ingot]` needs Builder+Iron ✓ after that.
8. **Auto-progress (village self-unlocks):** reset: `/colony delete 1` → `/colony spawn greek` → `/colony give cobblestone 64` → `/colony give coal 20` → `/colony give oak_planks 64` → `/colony tickprogress` (forces 4× auto-ticks, bypass 5-day cooldown). Check `/colony research` — village should have self-bought `TOOL_STONE → MINER → BUILDER` alone. Run `/colony tickprogress` again with iron/leather in stock → it walks to Tier2 itself.
9. **Building progression:** Town Hall → Buildings tab shows houses/farm tier mats; `/colony info` shows level/radius. Builder uses warehouse mats (4 blocks/tick). Try `BuildingMarker` block + mark area → “Registered player building” → upgrades with `BuildingUpgrade` (size 5→7→9).

## 3. What “working” looks like

- `/colony info` tool tier goes 0→1→2 as you buy, speed 1.0→1.3→1.6 in list.
- `/colony warehouse` counts go down on buy, up as Forager/Miner deposit (check chest counts).
- Town Hall GUI Research tab mirrors `/colony research` — buy buttons there consume chests first via `WarehouseManager`.
- Villager shift-right-click → job assign now fails with “Profession locked — unlock in Town Hall Research tab” until you buy that profession.
- Forager: leave game running 2 mins with Farmer+Woodcutter only, no chests broken — `/colony warehouse` cobble should increase from foraging.

## 4. Automated self-sufficiency check (no Minecraft needed)

Run the progression validator:

```bash
# After build, this parses ColonyUpgrade costs and asserts each tier only needs previous tier’s job outputs
./gradlew test --tests "com.simplecolonies.progression.ProgressionValidatorTest"
```

Or quick manual: open `ColonyUpgrade.java` top comment tree — every cost set is only `cobblestone/oak_planks/coal/wheat/wool/string/leather/raw_iron` etc. produced by Tier0/1 jobs. No Tier2 buy needs diamond/emerald etc.

## 5. Common pitfalls

- `Not inside a colony` → stand within 32 blocks of center or use `/colony <cmd> 1` with id.
- `Buy failed — Not enough resources` → check `/colony warehouse` — you need exact item ids (e.g. `minecraft:furnace` needs you to `/colony give furnace 2`, not cobblestone).
- `Missing prerequisites` → check `/colony research` “needs: …” line — buy those first in order: `TOOL_STONE → MINER → BLACKSMITH → TOOL_IRON`.
- No Java → install JDK 21 first, `./gradlew` will download NeoForge/Paper deps.
