# Creeper-proof colonies, physical meals and trade routes (0.1.31)

Back up your world before trying this update. No release was published; the in-game
and dedicated-server checks below are still required.

## Colonies are creeper-proof, sieges are not

A creeper, ghast, wither or wither skull explosion no longer breaks anything a
colony owns:

- every planned block of a hall, house, workshop or upgrade wing (finished or
  still under construction),
- the shell of a player-marked hall or house (a small core box around the marker),
- protected plots and blocks players placed inside the colony.

Only the affected block list changes. The blast still happens, still hurts
entities and still drops nothing from protected blocks, so defending a village
from monsters is exactly as dangerous as before. Blocks a colony does not own —
terrain, crops, roads — still break.

Player-caused explosions are deliberately untouched: TNT, beds, respawn anchors,
end crystals and player-owned projectiles still destroy colony blocks, so a siege
or a player who wants to clear a building can still do it. Pistons are blocked
from pushing, pulling or breaking colony property, which closes the most common
griefing trick without adding a mixin.

Protection is looked up across every colony that covers a block, so two
overlapping villages cannot shadow each other's buildings.

Switches: `protectColoniesFromMobExplosions` and `protectColoniesFromPistons`
(both default `true`).

**Fire is still not protected.** Minecraft has no event for fire spread or
burning, and this mod ships no mixins, so a burning colony can still lose wooden
buildings. Do not store fire near a village.

## Physical daily meals

A simulated day still consumes one food item per citizen (times
`foodConsumptionMultiplier`), but the item is now taken physically:

1. The colony schedules the day's rations for its citizens.
2. In the morning (before 8000 ticks into the day) a citizen without a meal walks
   to the nearest shared container that holds plain food.
3. On arrival it takes one real item and eats it, gaining the vanilla food
   effects. The ration is marked served.
4. A ration nobody collected is eaten off-screen from storage before the next day
   is simulated, so unloaded colonies, busy workers and time skips cannot feed a
   village for free.

`enablePhysicalMeals=false` restores the old behaviour of drawing food from the
ledger directly. The starter hall's own chest is shared automatically, so a new
colony can eat immediately.

Birth consumption is unchanged: births still draw their food from the shared
ledger, as described in the README limits.

## Harbor and market trade routes

- A completed **harbor** runs one trade visit per simulated day. A trader buys the
  largest surplus above the export reserve (cod, salmon, cooked fish, wool, hay,
  charcoal, logs, planks) and pays one emerald. Food is never sold below the
  colony's own daily need plus a small buffer.
- A completed **market** spends emeralds on an order for a material a current
  building plan still needs (cobblestone, coal, logs, planks, glass, iron,
  bookshelves, quartz). It only spends emeralds it actually holds.
- Both exchanges are two-sided: the goods leave storage and the payment arrives,
  or nothing happens. `/colony status` shows the last visit
  ("Harbor: cod 8 -> emerald 1", "Market: emerald 2 -> oak_log 16").
- `enableTradeRoutes=false` disables both.

## Temple standing

A completed **temple** raises the reputation of players who already have some
standing by one point per simulated day, up to 60. That is deliberately below the
default leadership threshold (75), so an election still needs deeds. Players with
negative reputation gain nothing.

## What to check in a disposable world

1. Build a colony, then detonate a creeper next to a finished house, an unfinished
   house and a protected plot. Nothing colonial should break; a stone block on
   open colony ground outside those should.
2. Prime TNT against the same house. It must still destroy blocks.
3. Place a piston aimed at a colony wall and power it. The wall must not move.
4. Share a chest with bread, skip to morning and watch a citizen walk over and
   eat. Then unload the area, pass a day and confirm the bread is still consumed.
5. Build a harbor, stock shared storage with surplus cod and check
   `/colony status` after a day. Repeat with a market, emeralds and an unfinished
   house.
6. Build a temple, gain some reputation, pass days and confirm it stops at 60 and
   never rises while your reputation is negative.
