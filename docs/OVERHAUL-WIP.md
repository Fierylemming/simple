# Overhaul in progress — not a release

The user requested a single completed release. This branch is a work in progress, not a distribution-ready mod or jar recommendation. Do not treat compilation as proof of in-game correctness. Back up worlds and use a disposable test world if testing the branch.

## Decisions

- `/time` catches up daily food and other daily effects, but does not fast-forward walking, gathering or building. Large skips can starve citizens.
- A new settlement begins with a town hall, food, a farm and four workstation blocks; there are no initial beds. Villagers should shelter in the hall or houses at night.
- Three level-one houses per culture; expansion should respond to needs and affordable materials rather than a fixed day timer.
- User requested a single completed release, including accurate physical work for every profession and social behaviours.

## Work started

Expansion now checks on normal colony ticks, reports blockers and does not stall on the previously unsupported next-building type. It queues an affordable house if shelter capacity is low. Construction can craft a small set of oak components and has preliminary movement, with an occupied-placement check. Legacy unsupported blueprints no longer block house expansion. Cached stock is refreshed against loaded inventories before consumption. House plans are versioned with three footprints and culture-dependent material patterns, preserving old plans on reload. New settlements attempt to use the starter structure as a bedless town hall; a preliminary nighttime shelter navigation behaviour and crop harvesting/replanting using actual drops were added. Woodcutters now approach and cut one block at a time for short recognizable trees, recheck safety between cuts, and use stock saplings rather than creating them. Workstation-based crafting jobs now approach the claimed POI; a basic stock-backed baker has been added. These are not yet verified in a running game.

## Required before release

- Verify/fix the startup town hall, compact farm, pathfinding, door placement, roofs, resource accounting and 15 house plans in-game, including reload and blocked/unloaded sites. The initial town hall and house designs are provisional and not historically complete.
- Implement job-specific buildings built around the forester, miner and builder workstations; current construction still only supports houses. Improve site planning and growth beyond house-only expansion.
- Finish and test physical tree harvesting (current short-tree-only implementation leaves partial trees if interrupted and does not use or wear an axe) and replace remote quarry mining with accessible physical work and real tools. Avoid damage to buildings or player property; natural-block recognition remains heuristic.
- Finish and test every profession's real supply-chain behaviour, including butcher, leatherworker, blacksmith, baker, fisherman, courier, soldier/guard and any required shepherd/wool system. Current nonstarter professions include placeholders and are not distribution-ready.
- Implement proper social interactions and robust day/night navigation; current shelter movement is provisional. Address beds and actual food production/cooking beyond wheat counts.
- Add integration/GameTests plus single-player and dedicated-server tests, including time skips, starvation, save migration, multiple workers, missing resources, construction sites and unreachable paths. Test resource reload and villager POI/trades, too.
