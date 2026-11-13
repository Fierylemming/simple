# Overhaul preview: installation and test checklist

This is an **unfinished test build**, not the completed overhaul. Back up any existing world and preferably use a new disposable Overworld; do not use it on a valued world or server. Construction, farming, mining and tree cutting change blocks, and natural-block heuristics cannot reliably distinguish all player-built structures from terrain.

## Get the build

1. Open [CI run 43416447511](https://github.com/Fierylemming/simple/actions/runs/43416447511) and download the **simplecolonies-prism-jar** artifact at the bottom of the page (GitHub sign-in may be required). It is built from commit `6350ab0`, version `0.1.29`. The build and GameTests passed, but the fishing integration has failed intermittently in other runs and still needs in-game verification. Extract the zip.
2. In Prism Launcher, create a **Minecraft 1.21.1, NeoForge 21.1.265, Java 21** instance. Place the extracted SimpleColonies jar in that instance's `mods` folder; remove any older SimpleColonies jar first. Do not install it alongside another copy. For a server, install the same version on both client and server.
3. Start a new world with cheats enabled, in the Overworld. No public overhaul release has been made.

## Initial checks

- On open, reasonably flat, loaded land away from structures and other colonies (natural stone, dirt, sand or gravel can be used), use `/colony spawn roman`. Find the starter hall and farm, and check the initial villagers and workstations. The starter-plot search can fail on unsuitable terrain; check chat and try a different open area.
- Use `/colony list`, `/colony info`, `/colony status`, `/colony warehouse`, and `/colony research` to see colony state, work blockers, stock and upgrades. For a quick resource test, `/colony give minecraft:oak_log 128` adds virtual stock while standing inside the colony. This is a debug shortcut, not physical gathering.
- Let the world run normally while watching workers and construction. Save, quit and reload, then check the status and inventories again. Avoid large `/time` jumps: daily food consumption catches up but physical work, crops and animal growth do not, so villagers can starve.
- Assign jobs through the villager interaction menu (reputation and research may gate choices). Worksites require a claimed, loaded workstation, a clear site and sufficient materials; an unreachable or obstructed site should leave a blocker in status rather than guarantee progress. A farmer needs a completed designated farm near its composter; the mod's harvest search is limited to five blocks horizontally from the farm marker.

## Storage and buildings

- To share a chest, barrel or shulker box, **sneak-use the Building Tool** on it within the colony. You need leader status or at least 20 reputation. Repeat to unshare. Each half of a double chest must be shared separately. Only loaded, designated containers in the Overworld are included; moving or replacing one requires a new designation. Existing virtual stock stays separate. Verify that an unshared chest remains untouched.
- A Building Marker lets a sufficiently trusted player designate a player-built building. Test changing its type and clearing it; clearing should remove that linked designation. **Breaking a marker does not currently unregister it**, and old phantom records are not automatically cleaned up. A warehouse marker sets a courier hub but does not automatically share containers.
- Expansion can build houses and a limited set of worksite shells around claimed workstations. A courier's warehouse shell requires its barrel to be shared first; barracks use a defender's claimed workstation. These plans do not create the workstation for you. Older in-progress/unsupported plan versions retain their prior layouts.

## Known gaps to watch for

- Fishing-to-shore pathfinding has had intermittent automated failures, despite passing the latest run. Fishing currently gathers excess wild fish by hand, not by rod; unmarked player fish cannot always be distinguished from wild fish. Keep valuable fish away from fishing sites.
- Tree and mining recognition is heuristic; keep player structures and valuables away from worksites. Shelter is a preliminary enclosure check, not a guarantee against open doors, hostile door breaking or villagers wandering out at night.
- Other than the courier's carried stack, several jobs still access shared stock remotely; not all professions, buildings, armor, social behaviors or physical supply chains are complete. Starter structures and natural-world navigation need further in-game validation.
- Test storage, marker changes, jobs, housing, nighttime shelter, save/reload and dedicated-server behavior. Note the colony ID, coordinates, reproduction steps, screenshots and `logs/latest.log` for any issue. Do not run `/colony reset_citizens` on a world you care about; it can remove citizen records, including unloaded villagers.

## Tool replacement

New colonies start with a stone pickaxe and axe in colony stock. The forester now requires an axe instead of harvesting by hand. Miners and foresters first use a suitable tool from stock; if none is available, they can walk to a crafting table near a completed hall or house and craft a stone replacement from three cobblestone and two sticks (sticks can be crafted from real mixed planks or matching oak, spruce, birch, jungle, acacia, dark oak, mangrove or cherry logs). If no table or ingredients are available, they wait rather than create a free tool. A blacksmith can also make replacement stone or iron tools. Stock withdrawal is still remote; a physical trip to collect every ingredient or tool is not implemented. Existing saves do not receive free replacement tools.

Natural stone and player-placed stone are indistinguishable to the mod; do not test settlement creation on a valuable stone structure. Steep, obstructed, unloaded or water-covered plots may still be rejected.

### Housing and daily-life follow-up

- Completed houses assign shared homes up to their capacity, including to miners; assignments persist and the hall remains a fallback shelter. There are no automatically provided beds. Check `/colony status`, reload, and check that assignments remain stable. If a house is removed or no longer complete, occupants can be reassigned; a marker is not proof a player-built house is safe.
- Births now require nearby, loaded, eligible adults, spare shelter and real surplus food. A multi-day catch-up consumes daily food and applies health but defers births; it does not fast-forward physical jobs, crop growth or child aging. A long time skip can still cause starvation.
- Compact house variants check their actual footprint rather than requiring an entirely empty seven-by-seven square. An obstacle in the footprint remains protected; builders do not terraform slopes. Farmer replanting uses a harvested seed or a real seed from colony stock. These are preliminary and need testing in real worlds.
- At night, citizens attempt to reach a safe enclosed home or hall, without interrupting panic, sleep or trading. They may still wander out, and doors may not remain closed. The experimental idle-wander suppression was removed after intermittent door-closure failures.

### Blocked plans and storage safety

- `/colony status` lists unfinished plans and their exact origin. An operator can use `/colony cancel_building <colony-id> <x> <y> <z>` to forget a unique unfinished generated plan. Completed or player-designated buildings are refused. **The command leaves all blocks and already-spent materials in place**; it does not demolish or refund them. Partly constructed blocks may need manual attention before another site can be chosen. Builders may temporarily work another existing plan while an obstructed plan waits; they retry without automatically removing obstacles.
- Generic ID-only food and construction stock counts ignore stacks with custom components and do not merge normal items into them. Specialized tool withdrawal can still take an actual damaged or enchanted tool from shared storage, and couriers may move actual customized stacks; shared storage remains an explicit opt-in, not a private chest. Keep irreplaceable items in unshared containers.
- Only house markers have a capacity-only upgrade; it charges materials but does not build a larger shell, add beds, or install workstations. Other marker upgrades are disabled until they have real effects; generated-building visual upgrades remain unimplemented. Existing saved levels are preserved.
- When the clock jumps forward across even one day boundary, births are deferred for the catch-up, while food/health still advance. Detection begins after the colony has observed the clocks (legacy saves start observing on their next tick), and ordinary physical work is never fast-forwarded.

### Civic growth and starter safety

Version 11 adds a builder-constructed cultural civic hall after level two, subject to housing, materials and safe-site checks. It provides a meeting destination, not trading. Citizens may attempt to meet there late in the day if idle and the hall is enclosed and reachable. Shelter movement uses vanilla brain navigation to support door AI, but full-night safety remains unverified. Starter spawn no longer builds fallback dirt platforms; if no safe location exists, a founder may be missing. Cave space beneath the checked farm foundation is left alone.

### Recent forester work

Foresters can harvest recognizable straight trunks up to ten logs only when they can physically reach each log from the ground or a reachable existing natural slope. They do not build scaffolding, and branched, multi-trunk and inaccessible tall trees remain unsupported. Replacement-tool crafting now accepts the timber species listed above and keeps correctly matched surplus. These changes passed [CI run 43421105592](https://github.com/Fierylemming/simple/actions/runs/43421105592). A subsequent physical birch-axe integration test passed, but its overall [CI run 43421133434](https://github.com/Fierylemming/simple/actions/runs/43421133434) failed an intermittent bedless-hall shelter test; shelter reliability remains unresolved. Neither run is a release.
