# Simple Colonies — Ancient Villages

A Minecraft Java 1.21.1 / NeoForge colony mod in active development. Its goal is living, expanding settlements with five cultures, physical professions, leadership and diplomacy.

**The overhaul is not finished or released.** Back up your worlds and use a disposable world for preview testing. Automated tests do not replace single-player and dedicated-server testing. See [the current work-in-progress status](docs/OVERHAUL-WIP.md) and [preview instructions](docs/TEST-BUILD.md); older documentation records earlier milestones and may describe behavior that has since changed.

## Current preview

- Colonies track citizens, shelter, food, research and need-driven expansion. New settlements attempt to create a bedless hall and farm on suitable loaded land. Time skips catch up daily food and health, not physical work or births.
- Builders construct supported houses, workshops and physical upgrade wings, using carried supplies from explicitly shared storage. Other jobs gather or transport actual items, though research and food consumption still use the shared stock ledger remotely. Paths, construction sites and some professions remain limited.
- Only explicitly shared loaded containers are available to workers. Use the Building Tool to share storage; see the preview instructions for permissions and setup. Existing virtual stock is not automatically converted to physical items.
- A designated pasture authorizes work on eligible nearby unnamed livestock; ownership of unnamed animals and natural-looking terrain cannot be reliably inferred. Keep valuable animals and builds away from work areas.
- Loaded defenders can follow a leader, equip real equipment and defend against monsters. Basic colony relations include time-limited wars and peace; military combat targets eligible enemy defenders, not civilians. Diplomacy controls select a colony from a snapshot list. Player-led colonies must accept peace or alliance offers; leaderless colonies accept automatically.
- If Create 6.0.10 is installed, a builder can import a deployed schematic from shared storage. The blueprint remains in storage. Supported content is deliberately restricted to simple single-item blocks and paired doors; machines, block entities, fluids, entities, beds and other complex requirements are refused. Create is optional and not bundled.
- Natural structure generation, historical architecture, generalized property safety, dependable navigation, trades, and live-world and multiplayer validation are still incomplete. The `replaceVanillaVillages` setting is reserved and does not replace vanilla villages.

## Preview setup

Use Java 21 and NeoForge 21.1.265 for Minecraft 1.21.1. In a disposable overworld, an operator can use `/colony spawn roman` (or `egyptian`, `greek`, `persian`, `mesopotamian`) on suitable loaded land. Alternatively, place and use a Town Hall block to found a colony. Right-click the hall for management, shift-right-click a colonist for job options, and explicitly share storage with the Building Tool. See [docs/TEST-BUILD.md](docs/TEST-BUILD.md) for workflows and limitations.

Admin commands such as `/colony spawn`, `/colony give`, `/colony unlock`, and `/colony tp` require operator privileges. `/colony give` adds legacy virtual stock and is not a replacement for physically stocked containers. Some menu data is a snapshot: reopen the menu to refresh it.

## Build and test

```bash
./gradlew build
./gradlew runGameTestServer
```

The GameTest server needs an accepted EULA in `run/eula.txt`. CI also runs the tests with the optional Create runtime using `-PwithCreateTest`; production builds do not require Create. Build artifacts appear in `build/libs/`. Create Aeronautics compatibility and normal gameplay have not been fully validated.

## Source

The implementation lives under `src/main/java/com/simplecolonies/`; data and recipes are in `src/main/resources/data/simplecolonies/`. Unit tests live under `src/test`, and server GameTests are in the Java source tree under the `gametest`, `entity`, `colony`, and `villager/job` packages.
