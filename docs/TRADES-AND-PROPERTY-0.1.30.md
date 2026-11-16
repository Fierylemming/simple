# Trades, player property and walk reliability (0.1.30)

Back up your world before trying this update. Everything below still needs in-game
and dedicated-server validation; no release was published.

## Colony trades replace the borrowed vanilla lists

Every colony job now has its own small trade table (`ColonyTradeTable`). A worker
buys the goods its job produces for emeralds, or sells a finished product, food or
tool the settlement can actually make:

- **Farmer:** buys wheat, sells bread, pumpkin, pie and hay.
- **Woodcutter:** buys logs, sells planks, sticks and charcoal.
- **Miner:** buys cobblestone and coal, sells torches and raw iron.
- **Builder:** buys cobblestone, sells ladders, fences and scaffolding.
- **Butcher:** buys raw beef/pork, sells cooked meat.
- **Leatherworker:** buys leather, sells leather armour.
- **Blacksmith:** buys coal and iron ingots, sells iron sword, pickaxe and axe.
- **Baker:** sells bread and cookies, buys wheat.
- **Fisherman:** buys raw fish, sells cooked fish.
- **Courier:** sells barrels, chests and boats, buys planks.
- **Shepherd:** buys wool and mutton, sells shears and carpet.
- **Soldier/Guard:** buys arrows and string, sells a bow and shield.
- **Unemployed:** buys wheat, sells bread and apples.

Emeralds are the only currency and are never created from nothing: they are paid
for real colony goods. Rows unlock as the citizen's job level rises (1-5), and
every job offers at least one buy and one sell trade from level one, so a new
settlement is useful immediately. Reassigning a job closes any open trade and
rebuilds the list; an existing trade's remaining uses are saved with the villager
and survive a reload.

The **Trade** button in the villager menu now opens this list. A normal (unshifted)
right-click on an adult also opens it directly; children still refuse. Closing a
trade returns to normal play. Vanilla restock and levelling rules still apply, so
trading levels a citizen up and unlocks the next rows.

The old behaviour copied the matching vanilla profession's trade lists into each
colony profession. That is removed: colony workers no longer sell arbitrary
vanilla items that the colony never produced.

## Player-placed blocks are remembered

Natural-block detection could not tell player-placed stone, dirt or sand from
terrain, and the protected plot only covers one designated column. Colonies now
keep a bounded record of blocks a player physically places inside their radius:

- Miners refuse a block (and its immediate neighbours) if a player placed any of
  them, and record the reason in the work status.
- Woodcutters refuse trees whose soil, trunk or clearing area touches a player
  block.
- Farmers will not harvest or replant on a remembered block.
- Builders treat a remembered block, including a door's upper half, as an
  obstruction instead of overwriting it.
- New house, worksite and starter-plot planning rejects plots that would build
  over remembered blocks, and starter clearing skips them.

A block a player breaks stops protecting its position immediately, and claims for
blocks that no longer exist are pruned during normal colony ticks. The record is
saved with the colony and is capped per colony (oldest entries are evicted first),
so a large build cannot grow saved data forever.

Limits, deliberately unchanged: this is not a land claim. It only knows blocks
placed by a player while inside a loaded colony, so blocks placed before the
colony existed, blocks placed by commands, worldgen, pistons, other mods or other
entities are not covered. It does not restrict players, vanilla mobs or other
mods, and shared containers stay accessible. Keep valuable builds away from work
areas and still use **Protect plot** for terrain.

## Walks are bounded instead of retried forever

Workers now track a single walking attempt (`TravelWatch`): progress towards the
goal, a stall window, a timeout and a short cooldown before the same goal is tried
again. Walking to the colony centre and to a claimed workstation use it, and a
miner that cannot reach its chosen quarry step moves to the next site instead of
looping on the same route. This does not add doors, bridges or terrain-aware
routing; it makes failure visible and recoverable rather than silent.

## Vanilla village replacement

`replaceVanillaVillages` is now a working setting rather than a reserved one. When
enabled, a NeoForge structure modifier empties the biome set of the five vanilla
village structures, so new chunks contain only the mod's culture villages. Notes:

- The setting is read while structure data loads; **restart the world or server**
  after changing it. A config reload alone is not enough.
- Villages that already generated are kept; approaching one still founds a colony.
- The mod's own villages now use the vanilla town-centre template pool for their
  climate (desert, savanna or plains) instead of a lone house, so a generated
  village has a centre and expands like a village.

## Suggested checks

1. Found a colony, open a villager's **Trade** tab and buy and sell one item from
   each side. Reassign the job and confirm the list changes and the level resets.
   Save, reload and confirm remaining trade uses persist.
2. Place a small stone or cobblestone hut inside the colony, then let a miner and a
   woodcutter work nearby. Confirm neither damages it and that `/colony status`
   shows a player-property blocker when it stops one.
3. Break one of those player blocks and confirm the position is no longer
   protected after the next colony tick.
4. Queue a house next to the player hut and confirm the chosen site avoids it.
5. Walk a worker into a walled-off compound and check that it gives up and retries
   later instead of standing still forever.
6. Enable `replaceVanillaVillages` in a **new** world, restart, and travel at least
   1000 blocks in plains, desert and savanna. Vanilla villages should be absent and
   culture villages present. Disable it again in another new world and confirm
   vanilla villages return.
7. Record the colony id, coordinates, steps, screenshots and `logs/latest.log` for
   any problem. Do not run `/colony reset_citizens` on a world you care about.

## Still open

Natural village generation still reuses vanilla jigsaw pieces rather than
historical architecture, so generated buildings do not match the colony's culture
palette. Property memory is best effort, not a claim system. Navigation remains
plain pathfinding with bounded retries. Live single-player and dedicated-server
testing of all of the above is still required.
