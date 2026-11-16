# Structure templates

This folder currently holds only the server GameTest templates used by
`neoforge.enabledGameTestNamespaces`:

- `empty.nbt` — a flat, empty test arena.
- `isolated.nbt` — a small isolated platform for colony fixtures.
- `quarry.nbt` — the miner fixture.

They are referenced as `@GameTest(template = "empty")` and similar, not by
worldgen.

## Culture villages

Culture villages are data-driven jigsaw structures, not stored here:

- `data/simplecolonies/worldgen/structure/<culture>/village_center.json`
- `data/simplecolonies/worldgen/structure_set/<culture>_village.json`
- `data/simplecolonies/tags/worldgen/biome/has_<culture>_villages.json`

Each one starts from the vanilla town-centre pool that matches its climate
(desert, savanna or plains). Generated buildings are therefore still vanilla
jigsaw pieces; the mod's own culture walls and roofs are built by colony
builders around the generated hall, not by worldgen. Historical, culture-specific
generated architecture is still unfinished.

`CultureType.getStructure(String)` and `structureTemplatePrefix` are reserved for
that future work: the intended path is
`data/simplecolonies/structure/<culture>/<building>.nbt` once real templates
exist. Do not point worldgen at a template that has not been exported and tested.
