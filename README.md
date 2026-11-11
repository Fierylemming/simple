# Simple Colonies — Ancient Villages
> A simpler **MineColonies**-inspired mod for **Minecraft Java 1.21.1 / NeoForge 21.1.265**, compatible with **Create Aeronautics**.

Villages that *actually* live: farmers feed everyone, woodcutters replant trees, miners quarry stone & ores, builders construct the town from real resources. Villages expand on their own, starve without food, and come in five historical flavors — **Egyptian, Roman, Greek, Persian, Mesopotamian** — placed by biome heat and building in their own style. Earn trust, become leader, then command builders and armies, wage war or forge alliances.

---

## Features (from your request)

| You asked | How it works |
|-----------|--------------|
| **Supply-all-villagers-with-food** AI | Daily colony food tick; starvation → Weakness → death if `villagersDieWithoutFood`. Farmers + butchers + bakers supply warehouse. |
| **Wood cutter plants trees nearby & harvests** | `WoodcutterJob` keeps 10-tree managed forest, plants saplings, harvests logs, deposits to warehouse. |
| **Miner collects stone & ores for building/blacksmithing** | `MinerJob` digs expanding quarry, needs pickaxe from blacksmith, supplies cobble/coal/iron/copper. |
| **Builder constructs buildings** | `BuilderJob` places blueprint blocks gradually (`maxBuilderBlocksPerTick=4`), consuming warehouse resources; shows missing list. |
| **Naturally expand & increase size/pop** | Every 5 days if food surplus + builder → new building from `ColonyProgression`; level 1-5 Hamlet→Metropolis, radius 32→112. |
| **5 village types in correct biomes by heat** | `CultureType` maps temp: Egyptian (hot desert) → Persian (savanna) → Mesopotamian (river/swamp) → Roman (plains) → Greek (coastal). Biome tags data-pack editable. |
| **Join village, trade normally, be leader to expand/command** | Vanilla trading kept; reputation 0-100 (trade/defend/deliver = +). At 75 → auto-elected leader → Town Hall GUI + `/colony` commands. |
| **Soldier villagers for war + alliances** | Barracks → soldiers/guards with armor from leatherworker/blacksmith. **Command Staff** rallies them. `Diplomacy` declares war (14d) / alliance / peace. |

Extra hardcore touch you wanted: *butcher → leatherworker → armor* chain via generic `SupplyChain` request board (not hard-coded, extensible to any item).

---

## Quick Start (Player)

1. Find a village — desert = Egyptian sandstone/pyramids, plains = Roman quartz columns, beach = Greek white marble, etc.
2. **Right-click Town Hall** to see colony id/culture/rep. Trade with villagers to gain rep.
3. Defend from raid / donate food via warehouse chest.
4. Hit **75 rep → Leader** → Town Hall now shows citizens, buildings, requests.
5. Order buildings, ensure warehouse stocked (farmer + woodcutter + miner keep it filled).
6. Craft **Command Staff** (`stick + iron + emerald` — recipe via data pack) → right-click soldiers to command, shift-right-click to rally, right-click enemy Town Hall to declare war (leader only).

See `docs/` for culture palettes and job flowcharts.

---

## Tech Stack

- **Loader:** NeoForge 21.1.265 (required — same as Create Aeronautics 1.3.2 on 1.21.1)
- **Java:** 21, Parchment 2024.11.17
- **Mod ID:** `simplecolonies`
- **Optional deps:** `create`, `create_aeronautics` (AFTER, not required)

Project started from [NeoForge MDK 1.21.1 ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) (Gradle 8, ModDevGradle 2.0.148).

---

## Build

```bash
# JDK 21 required
./gradlew build              # -> build/libs/simplecolonies-0.1.0.jar
./gradlew runClient          # test client
./gradlew runServer          # test server (--nogui)
./gradlew runData            # regen data if you change data gen
```

Put the jar plus `create-1.21.1-6.x.jar` + `sable` + `create_aeronautics-1.21.1-1.3.2.jar` in `mods/` to run with Aeronautics. Without them the mod still works (they're optional).

---

## Config

`config/simplecolonies-common.toml` (server):

```toml
[colony]
  maxCitizensPerColony = 60
  villagersNeedHousing = true
  villagersDieWithoutFood = true
  daysWithoutFoodBeforeStarvation = 3
  foodConsumptionMultiplier = 1.0
  enableSupplyChain = true
  enableNaturalExpansion = true
  expansionCheckDays = 5
  enableWarfare = true
  replaceVanillaVillages = false
  reputationNeededToLead = 75.0

[cultures]
  enableEgyptian = true
  # ... roman/greek/persian/mesopotamian toggles

[performance]
  maxBuilderBlocksPerTick = 4
```

Client: `simplecolonies-client.toml` — border particles, job icons.

---

## Worldgen / Cultures

Biome tags in data pack (`data/simplecolonies/tags/worldgen/biome/has_*_villages.json`) define exact spawns; edit to add modded biomes. Fallback temp logic in `CultureType.fromBiomeTemperature()` handles untagged biomes gracefully.

Structure NBTs go in `data/simplecolonies/structures/<culture>/town_hall_1.nbt` etc and are referenced by `structure_set/<culture>_village.json` (random_spread, spacing 32-34). Replace vanilla villages by setting `replaceVanillaVillages=true` (adds biome modifier removing `minecraft:village_*`).

Palettes:
- **Egyptian:** sandstone, cut sandstone, terracotta, acacia
- **Roman:** quartz, quartz pillar, bricks, red terracotta, oak/dark oak
- **Greek:** white concrete, calcite, smooth quartz, cherry/birch
- **Persian:** terracotta variants, bricks, acacia/dark oak
- **Mesopotamian:** mud bricks, packed mud, mangrove/bamboo

Contribute new cultural buildings: export with Structure Block → `.nbt` → PR.

---

## Commands (admin + leader debug)

```
/colony list                    # all colonies
/colony info                    # rep/leader for colony you're in
/colony tp <id>                 # teleport to center
/colony delete <id>             # op only
```

Leader Town Hall also does diplomacy/assign jobs/buildings in GUI (WIP screen in `client/`).

---

## Project Structure

```
src/main/java/com/simplecolonies/
  SimpleColonies.java          # mod entry
  colony/                      # Colony, ColonyManager (SavedData), Progression, Diplomacy, Commands
  culture/CultureType.java     # 5 cultures + heat mapping
  villager/job/*               # Farmer, Woodcutter, Miner, Builder, Butcher/Leatherworker/Blacksmith, Soldier
  villager/ai/SupplyChain.java
  building/Building*           # type, state, placement queue
  block/TownHallBlock etc.
  item/CommandStaffItem
  entity/ColonyVillager        # Villager subclass with hunger/housing logic
  worldgen/ModWorldGen
  config/ColonyConfig
docs/DESIGN.md                 # full design doc with balancing
```

See `DESIGN.md` for deep dive on survival tick, supply board, builder flow, progression, and war logic.

---

## Roadmap

- [x] Colony core + persistence + heat-based cultures + biome tags + structure sets
- [x] 8 jobs with supply chain (incl. butcher→leatherworker)
- [x] Builder blueprint & natural expansion
- [x] Reputation → leader election + Command Staff + Diplomacy (war/alliance)
- [ ] Town Hall Screen GUI (tabs: citizens, warehouse, buildings, diplomacy)
- [ ] Visual ghost outlines & resource holograms for blueprints
- [ ] JEI trade viewer, Create conveyor → warehouse driver
- [ ] Advancements + recipes + culture NBT pack
- [ ] Airship-borne soldier transport (Create Aeronautics API)

PRs welcome! Please test on NeoForge 1.21.1 with and without Create Aeronautics.

---

## License

MIT — see `TEMPLATE_LICENSE.txt`. Ancient architecture research via community; no asset rips.

## Credits

Inspired by MineColonies. Built by FieryLemming & contributors. Thanks to NeoForged MDK, Parchment, and ryanhcode (Create Aeronautics) for compatibility baseline.
