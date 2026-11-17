# Simple Colonies — Ancient Villages

A Minecraft Java 1.21.1 / NeoForge colony mod in active development. Its goal is living, expanding settlements with five cultures, physical professions, leadership and diplomacy.

**The overhaul is not finished or released.** Back up your worlds and use a disposable world for preview testing. Automated tests do not replace single-player and dedicated-server testing. See [the current work-in-progress status](docs/OVERHAUL-WIP.md) and [preview instructions](docs/TEST-BUILD.md); older documentation records earlier milestones and may describe behavior that has since changed.

## Current preview

- Colonies track citizens, shelter, food, research and need-driven expansion. New settlements attempt to create a bedless hall and farm on suitable loaded land. Time skips catch up daily food and health, not physical work or births.
- Builders construct supported houses, workshops and physical upgrade wings, using carried supplies from explicitly shared storage. Research is queued for a builder to carry its inputs to a hall or library; legacy virtual stock cannot fund new research. Daily meals are physical: a citizen walks to shared storage in the morning, takes a real food item and eats it. A ration nobody collected is settled from storage before the next simulated day, so unloaded or busy colonies still consume food and nothing is created for free. Paths, construction sites and some professions remain limited.
- Only explicitly shared loaded containers are available to workers. Use the Building Tool to share storage; see the preview instructions for permissions and setup. Existing virtual stock is not automatically converted to physical items.
- A designated pasture authorizes work on eligible nearby unnamed livestock. Sneak-use the Building Tool on a cow, pig or sheep to protect it from colony livestock workers; only its protector or the colony leader can remove the mark. Unmarked animals cannot be identified as player property, so keep other animals away from work areas.
- Every profession has its own trade list (right-click an adult colonist, or use **Trade** in the job menu). Workers buy their job's goods for emeralds and sell food, goods and tools the settlement makes; rows unlock with job level 1-5. Vanilla restock and levelling still apply, and remaining uses survive a reload.
- Colonies remember blocks a player physically places inside them. Miners, woodcutters, farmers and builders leave those blocks alone, and new house, worksite and starter plots avoid them. Breaking the block removes the protection. This is best effort, not a claim: blocks placed before the colony existed, by commands, by worldgen or by other entities are unknown, and it does not restrict players, mobs or other mods. Keep valuable builds away from work areas and use **Protect plot** for terrain.
- Colonies build with the wood that grows around them. A settlement reads the local forest when it is founded and uses that species for its starter hall, its founding supplies and, by preference, every later house, worksite and research order; wooden costs ask for "any planks/log/fence/boat" and are resolved to the species actually in shared storage. All eight vanilla woods are supported, so a Roman colony in a birch or spruce biome is not stuck waiting for oak. A colony adopts a different species once it has stored enough of it.
- Colonies are creeper-proof. A creeper, ghast or wither explosion cannot break colony buildings (planned shells and wings, the town hall, protected plots and player blocks); the blast still damages entities, so fights in a village stay dangerous. Player-caused explosions (TNT, beds, crystals) and pistons are not blocked from player demolition, but pistons cannot shove colony structures either. Both protections are config switches. Sieges, defenders and wars are unaffected.
- A completed **harbor** sends a trade visit that buys surplus goods for emeralds, and a completed **market** spends emeralds on materials the colony still needs for construction. Both keep reserves, never sell the colony's own food, and report the last visit in `/colony status`. A completed **temple** slowly raises the reputation of players who already have some standing (up to 60, below the leadership threshold), and does nothing for hostile players.
- Loaded defenders can follow a leader, equip real equipment and defend against monsters. Basic colony relations include time-limited wars and peace; military combat targets eligible enemy defenders, not civilians. Diplomacy controls select a colony from a snapshot list. Player-led colonies must accept peace or alliance offers; leaderless colonies accept automatically.
- If Create 6.0.10 is installed, a builder can import a deployed schematic from shared storage. The blueprint remains in storage. Supported content is deliberately restricted to blocks with one plain consumed item type (including multi-count requirements such as double slabs) and paired doors; machines, block entities, fluids, entities, beds and other complex requirements are refused. Multi-count items must be supplied as finished items. Researching **Schematic Cannon** doubles the accepted size. Create is optional and not bundled.
- Worker walks are bounded: a stalled or unreachable walk is retried after a cooldown instead of being re-requested forever, and a miner whose quarry step cannot be reached moves to the next site. Pathfinding itself is still vanilla and does not build doors or bridges.
- `replaceVanillaVillages` now works: with it enabled, the five vanilla village structures are removed from world generation, leaving only the mod's culture villages. It is read while structure data loads, so restart the world; villages that already generated are kept and can still be colonised. Culture villages now use the matching vanilla town-centre template pool (desert, savanna or plains) rather than a lone house.
- Natural structure generation still reuses vanilla jigsaw buildings rather than historical architecture, so generated buildings do not match the culture palette. Fire spread is not protected: a burning colony can still lose wooden buildings, because Minecraft has no event for it and the mod uses no mixins. Property memory remains best effort, wider building coverage is unproven, and live-world and multiplayer validation has not been performed. No release has been published.

## Preview setup

Use Java 21 and NeoForge 21.1.265 for Minecraft 1.21.1. In a disposable overworld, an operator can use `/colony spawn roman` (or `egyptian`, `greek`, `persian`, `mesopotamian`) on suitable loaded land. Alternatively, place and use a Town Hall block to found a colony. Right-click the hall for management, shift-right-click a colonist for job options, right-click an adult colonist to trade, and explicitly share storage with the Building Tool. See [docs/TEST-BUILD.md](docs/TEST-BUILD.md) for workflows and limitations and [docs/TRADES-AND-PROPERTY-0.1.30.md](docs/TRADES-AND-PROPERTY-0.1.30.md) for the newest changes. See [docs/GRIEFING-MEALS-TRADE-0.1.31.md](docs/GRIEFING-MEALS-TRADE-0.1.31.md) and [docs/LOCAL-WOOD-0.1.32.md](docs/LOCAL-WOOD-0.1.32.md) for these updates.

Admin commands such as `/colony spawn`, `/colony give`, `/colony unlock`, and `/colony tp` require operator privileges. `/colony give` adds legacy virtual stock and is not a replacement for physically stocked containers. Some menu data is a snapshot: reopen the menu to refresh it.

## Build and test

```bash
./gradlew build
./gradlew runGameTestServer
```

The GameTest server needs an accepted EULA in `run/eula.txt`. CI also runs the tests with the optional Create runtime using `-PwithCreateTest`; production builds do not require Create. Build artifacts appear in `build/libs/`. Create Aeronautics compatibility and normal gameplay have not been fully validated.

## Source

The implementation lives under `src/main/java/com/simplecolonies/`; data and recipes are in `src/main/resources/data/simplecolonies/`. Unit tests live under `src/test`, and server GameTests are in the Java source tree under the `gametest`, `entity`, `colony`, and `villager/job` packages.
