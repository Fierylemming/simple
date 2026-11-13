# Overhaul preview: installation and test checklist

This is an **unfinished test build**, not the completed overhaul. Back up any existing world and preferably use a new disposable Overworld; do not use it on a valued world or server. Construction, farming, mining and tree cutting change blocks, and natural-block heuristics cannot reliably distinguish all player-built structures from terrain.

## Get the build

1. Open [CI run 43455390293](https://github.com/Fierylemming/simple/actions/runs/43455390293) and download the **simplecolonies-prism-jar** artifact at the bottom of the page (GitHub sign-in may be required). It is built from commit `f774df3`, version `0.1.29`. The build and GameTests passed, but the fishing integration has failed intermittently in other runs and still needs in-game verification. Extract the zip.
2. In Prism Launcher, create a **Minecraft 1.21.1, NeoForge 21.1.265, Java 21** instance. Place the extracted SimpleColonies jar in that instance's `mods` folder; remove any older SimpleColonies jar first. Do not install it alongside another copy. For a server, install the same version on both client and server.
3. Start a new world with cheats enabled, in the Overworld. No public overhaul release has been made.

## Initial checks

- On open, reasonably flat, loaded land away from structures and other colonies (natural stone, dirt, sand or gravel can be used), use `/colony spawn roman` as an operator. Find the starter hall and farm, and check the initial villagers and workstations. The starter-plot search can fail on unsuitable terrain; check chat and try a different open area.
- Use `/colony list`, `/colony info`, `/colony status`, `/colony warehouse`, and `/colony research` to see colony state, work blockers, stock and upgrades. For a quick resource test, `/colony give minecraft:oak_log 128` adds virtual stock while standing inside the colony. This is an operator-only debug shortcut, not physical gathering. `/colony unlock`, `/colony tickprogress` and `/colony tp` also require operator permission; spending colony stock via `/colony buy` requires the colony leader or an operator.
- Let the world run normally while watching workers and construction. Save, quit and reload, then check the status and inventories again. Avoid large `/time` jumps: daily food consumption catches up but physical work, crops and animal growth do not, so villagers can starve.
- Assign jobs through the villager interaction menu (reputation and research may gate choices). Worksites require a claimed, loaded workstation, a clear site and sufficient materials; an unreachable or obstructed site should leave a blocker in status rather than guarantee progress. A farmer needs a completed designated farm near its composter; the mod's harvest search is limited to five blocks horizontally from the farm marker.

## Storage and buildings

- To share a chest, barrel or shulker box, **sneak-use the Building Tool** on it within the colony. You need leader status or at least 20 reputation. Repeat to unshare. Each half of a double chest must be shared separately. Only loaded, designated containers in the Overworld are included; moving or replacing one requires a new designation. Existing virtual stock stays separate. Verify that an unshared chest remains untouched.
- A Building Marker lets a sufficiently trusted player designate a player-built building. Test changing its type and clearing it; clearing should remove that linked designation. **Breaking a marker does not currently unregister it**, and old phantom records are not automatically cleaned up. A warehouse marker sets a courier hub but does not automatically share containers.
- Expansion can build houses and a limited set of worksite shells around claimed workstations. A courier's warehouse shell requires its barrel to be shared first; barracks use a defender's claimed workstation. These plans do not create the workstation for you. Older in-progress/unsupported plan versions retain their prior layouts.

## Known gaps to watch for

- Fishing-to-shore pathfinding has had intermittent automated failures, despite passing the latest run. Fishing currently gathers excess wild fish by hand, not by rod; unmarked player fish cannot always be distinguished from wild fish. Keep valuable fish away from fishing sites.
- Tree and mining recognition is heuristic; keep player structures and valuables away from worksites. Shelter is a preliminary enclosure check, not a guarantee against open doors, hostile door breaking or villagers wandering out at night.
- Other than the courier's carried stacks, several jobs still access shared stock remotely; not all professions, buildings, armor, social behaviors or physical supply chains are complete. Starter structures and natural-world navigation need further in-game validation.
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

The subsequent [CI run 43421640302](https://github.com/Fierylemming/simple/actions/runs/43421640302) passed the build and complete GameTest suite at commit `50c6558`, including the physical birch-axe test. This does not resolve the intermittent shelter failure or replace real-world testing.

### Latest safety and defender checks

A following defender may now cross the colony boundary while its valid leader remains in the same dimension; dismissing it restores the ordinary return-to-colony behavior. Follow orders remain session-local, and chunks are not force-loaded. Builders pause if fluid appears in a queued placement or the upper half of a door. Butchers no longer tend or attack animals outside the colony or through solid walls. Unnamed player-owned livestock inside the designated work area are still indistinguishable from livestock intended for the butcher; keep them away. These changes passed the build and GameTests in [CI run 43436978482](https://github.com/Fierylemming/simple/actions/runs/43436978482). This is still an unfinished preview, not the requested final release.

### Timber and shelter follow-up

New version-12 houses, supported workshops and civic halls choose one affordable timber family from oak, spruce, birch, jungle, acacia, dark oak, mangrove and cherry. Builders craft matching planks, stairs, slabs and doors from that family's real logs; no other species becomes oak. The selected family and completed placement prefix are saved. Version-11 and older saved plans still regenerate their original oak layouts. The build and full suite passed [CI run 43439814794](https://github.com/Fierylemming/simple/actions/runs/43439814794), including physical birch-house and spruce-smithy construction. Foresters still cannot harvest many natural tree shapes; supplying logs does not make full tree support complete.

Shelter path selection now retries promptly after landing rather than waiting a full interval if the worker is airborne during a search. The previous [CI run 43437407960](https://github.com/Fierylemming/simple/actions/runs/43437407960) failed the intermittent hall-shelter test; later runs passed, but this change is not proof of reliable full-night shelter or a guaranteed fix.

### Compact two-by-two trees

Foresters can now cut a rooted, unbranched two-by-two dark oak, spruce or jungle trunk with 3–6 complete log layers, provided the canopy is natural, the surrounding area is safe and loaded, and all four upper logs are reachable from existing natural ground. They cut one log at a time using a real axe and save partial progress. They plant four matching saplings only when four are in stock and the original ground remains clear; if fewer are available, none are consumed. The [build and full GameTest suite passed](https://github.com/Fierylemming/simple/actions/runs/43441397364), including reload, obstruction, tool wear, resource, height and three-species checks. Many natural branched, uneven or taller trees remain unsupported, and natural-looking player-built logs cannot be reliably distinguished from trees. Keep valuable builds away from worksites. This is still an unfinished preview.

### Library and research progression

A level-three settlement with a completed civic hall, enough housing, a builder, suitable loaded terrain and sufficient materials may queue a compact version-13 library. The builder installs two real bookshelves from stock; bookshelves are not crafted automatically. Completed libraries, including player-designated ones, shorten the automatic-research retry interval from six daily ticks to three after the next attempt. An unfinished plan has no effect. Old unsupported library plans remain unchanged. Automatic research requires twice its full cost in stock, spends only the normal cost and retains the rest; manual research may use the exact cost. Daily research still catches up during time skips.

Tight Housing research costs eight **white wool** and sixteen oak planks after Stone Tools and adds two logical shelter slots per completed house, without adding beds or enlarging its shell. Existing unlocks and assignments persist. The unimplemented schematic-cannon research cannot be purchased or automatically unlocked; old saved unlocks are retained. Warehouse Network now grants two-stack courier capacity as described below. [CI run 43443381337](https://github.com/Fierylemming/simple/actions/runs/43443381337) passed the build and full GameTests, including library construction, automatic planning, research costs, housing and reload tests. This is still a preview, not the completed overhaul.

### Safety follow-up

The unimplemented Create schematic action now refuses without consuming an item or queuing a fake house, including for older saves with its research already unlocked. Donating food ignores customized bread rather than converting it to generic stock. Administrative spawn, grant, force-unlock, progress and teleport commands are operator-only, and research purchases require the leader or an operator. This does not provide complete player-property protection or implement schematic building.

### Courier capacity research

Warehouse Network research now lets a courier collect up to two real stacks from a shared source during each trip. Cargo keeps item components and is saved with the villager; if the hub is full, the courier retains the undelivered items. A job change or death drops both stacks. Existing saved unlocks retain their effect; new purchases require their prerequisites and real stock. Other jobs still use shared stock remotely, so this does not complete physical supply chains.

### Crooked-tree follow-up

Foresters can also cut some small crooked oak, spruce, birch, jungle, acacia and cherry trees with vertical logs. A candidate must have one rooted column, at most 32 connected logs within a bounded area, a matching natural canopy, no nearby construction or foreign logs, and reachable cutting positions for every log. They use an actual axe, save the original log positions, recheck the area before every cut and replant only with a real sapling. Unit tests cover invalid shapes and construction; physical GameTests cover acacia reload, oak, jungle, cherry, obstruction and an unreachable tree in [CI run 43453950821](https://github.com/Fierylemming/simple/actions/runs/43453950821). The obstruction test failed in [the preceding run](https://github.com/Fierylemming/simple/actions/runs/43453464436) and was revised to allow a longer approach while still requiring it to stop for new construction. The tests use synthetic trees: horizontal-log branches, many tall or wide trees, mangrove roots and other shapes remain unsupported. Player-made logs can still resemble natural trees, so keep builds away from the work area.

Connected sideways X/Z branches are now supported in those bounded trees when they align with neighboring logs. Log orientations are saved and rechecked before every cut, so changing a remaining log's axis stops the job; old vertical-only partial-tree saves still load. A synthetic sideways-cherry harvest across reload and the full suite passed [CI run 43455390293](https://github.com/Fierylemming/simple/actions/runs/43455390293). Wide, tall, multi-root and other shapes remain unsupported; this is not full natural-world tree coverage.
