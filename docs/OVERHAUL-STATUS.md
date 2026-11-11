# Living-village overhaul: foundation (0.1.18)

The original goal is a self-sustaining, expanding village with five historical
cultures, meaningful trades, leadership, soldiers and diplomacy. This build is
an **incremental foundation**, not a claim that the whole goal is finished.
Test it in a new world and keep backups of existing worlds.

## What changed

* Starter plots seek clear, level, loaded ground instead of blindly overwriting
  nearby structures. If no site is found, the structure is skipped and a warning
  is logged. Irrigation sits at soil level and the house has a closed roof.
* Starter workers include a farmer, woodcutter and builder. Farmers find the
  actual farmland; starving colonies can recover when fed, and dead citizens
  are removed instead of being replaced forever. Colonies only tick in loaded
  overworld chunks. Chest scanning examines loaded block entities rather than
  scanning every block in the village.
* House blueprints use real block placements and consume oak logs incrementally;
  the construction queue persists over saves. Unsafe obstructions stop work.
  Only house construction is implemented: other blueprint buttons explicitly
  report that they are unavailable rather than building placeholder cubes.
* Colony menus receive an initial server snapshot, including on dedicated
  servers. Live updates after opening a menu are not yet implemented; reopen it
  to refresh.
* Natural discovery no longer creates colonies just because it finds water,
  chests or other ordinary blocks, nor generates random settlements around
  world spawn. Existing structure-set IDs remain available to `/locate`.

## Still needed

Historical structure templates and full buildings for all five cultures;
non-house building plans, actual beds and doors in builder-created houses;
reliable pathfinding and non-destructive gathering for all jobs; progression
and automatic job assignment for new adults; real trades, warfare, alliances,
and multiplayer menu live synchronization; comprehensive in-game testing.
Current worldgen uses vanilla jigsaw pools, not historical architecture.
The vanilla-village replacement configuration is not implemented. Do not treat
existing README feature claims as evidence these systems have been verified.

## Manual acceptance checks

1. In a new single-player overworld use `/colony spawn roman` on open level
   ground. Verify a complete roof and a planted farm with a narrow sunken water
   channel. Verify farmer, woodcutter and builder appear.
2. Check `/colony warehouse` after crops grow and trees are harvested. Observe
   food decreasing after a day, then exhaust and replenish food; confirm starvation
   eventually stops after resupply. Save and reload between checks.
3. Give the colony logs, queue a house in the Town Hall, observe actual blocks
   being placed. Save and reload partway; the builder must resume without charging
   twice. Check a nearby player structure is not overwritten.
4. Use `/locate structure simplecolonies:roman/village_center`, travel there,
   and verify a colony is discovered. Ordinary chests, water and vanilla farms
   away from these structures must not spawn colonies.
5. On a dedicated server, open each menu and check initial culture, citizens and
   marker data match the server. Reopen after making changes to refresh.

These checks require a Minecraft runtime and have not been automated yet.
