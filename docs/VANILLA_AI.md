# Vanilla AI — Have we updated it?

**Short answer: Yes, all colony villagers now do real work.** Vanilla Minecraft villagers have fake AI that just *looks* busy.

## What vanilla does (before mod)
- Villager gets a POI job site (composter for farmer, blast furnace for armorer...)
- On schedule (2000-9000 tick work window) they pathfind to POI, play animation, occasionally replenish 2 trades
- They never harvest crops, chop trees, mine ores, or produce items. Food is magically restocked via `VillagerTrades`.
- Lumberjack/miner/blacksmith/soldier don't exist in vanilla.

## What Simple Colonies replaces
Every `ColonyVillager` (new entity `simplecolonies:colony_villager`) overrides tick with **real jobs**:

| Job | Vanilla behavior | Our real behavior |
|-----|------------------|-------------------|
| Farmer | bonemeals nearest crop rarely | Scans 9×9 farmland, harvests mature crops, replants, deposits wheat/bread to nearest chest in radius |
| Lumberjack | *no vanilla equivalent* | Manages 10-tree forest, chops logs within 10 blocks, plants saplings |
| Miner | *no* | Digs quarry 6×6 shaft downwards, consumes pickaxe, deposits cobble/coal/raw iron to chests |
| Builder | *no* | Takes blueprint queue (or player marker), checks warehouse/courier chest stock, places 4 blocks/tick |
| Butcher → Leatherworker | butcher only trades, leatherworker not linked | Butcher breeds/slaughters → leather to chests; Leatherworker pulls 4 leather → chestplate (supply chain board) |
| Blacksmith | armorer trades only | Smelts raw iron+coal from chests → ingots → pickaxe/sword for miners/soldiers |
| Courier | *no* | Bulk-moves 3 stacks/tick at 2.5× speed from scattered farm chests to warehouse hub |
| Soldier/Guard | iron golem spawns, not villager | Follows leader via `Command Staff`, attacks leader's target, patrols walls |

Implementation: `villager/job/AbstractJob` is ticked every job interval (10-200 ticks) inside `ColonyVillager.tick()` if inside colony radius. It uses `WarehouseManager` to find nearest chest (`Container` BE), not an abstract number. `CourierJob` consolidates.

## What about vanilla villagers?
- Vanilla `minecraft:villager` outside colonies keeps vanilla AI for compatibility/performance.
- Inside colony radius, on spawn (`FinalizeSpawnEvent` + `ColonyEventHandler`) a `ColonyVillager` is assigned a colony and given a random starter job; vanilla villagers that wander in can be converted via `/summon` replacement or by right-clicking with spawn egg of colony type.
- If you want **all** vanilla villagers replaced: set config `replaceVanillaVillages=true` (removes vanilla village structures via biome modifier) so only ancient colonies spawn (our entity). Or use command `/colony convert` (planned) to mass-convert.

## Proof it's real
- Watch `WarehouseManager.scan()` logs: chest counts change after farmer tick.
- Starve colony (remove farmer, empty chests): after 3 days villagers get `WEAKNESS` then `starve` damage → they die if `villagersDieWithoutFood`.
- Break chests: warehouse count drops, builder stalls with `NEEDS_RESOURCES`, posts request board that butcher/miner see.
- Place 8 chests together, mark as **Warehouse** via Building Marker → hub set, courier speed test: farm chests empty faster.

## Next: deeper vanilla overrides (optional)
If you want iron-golem-style guard AI on vanilla villagers too, we can add a Mixin to `Villager` that injects at `aiStep` when `isInSimpleColony()` — currently not needed because we have separate entity which is cleaner for Create Aeronautics compatibility (separate renderer, no conflict with Sable mixins).

