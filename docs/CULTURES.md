# Cultures — Building Style Guide

Each culture's palette is defined in `CultureType.java` and its structures in `data/simplecolonies/structures/<culture>/`.

## Egyptian — Hot Arid (desert/badlands)
- Blocks: sandstone family, terracotta, yellow glaze, acacia
- Architecture: flat roofs, open courtyards, pyramid cap on town hall, sphinx gate
- Villager names: Egyptian pool, linen robes (texture placeholder)

## Persian — Hot Dry Savanna
- Blocks: red/orange terracotta, glazed, bricks, chiseled stone
- Architecture: grand iwan arches, garden terraces, wind towers

## Mesopotamian — Warm Humid Rivers/Swamps
- Blocks: mud bricks, packed mud, mangrove/bamboo, reed roofs
- Architecture: ziggurat town hall, canals, stilt houses

## Roman — Temperate Plains/Forest
- Blocks: quartz, quartz pillar, bricks, red terracotta tile, oak
- Architecture: forum columns, aqueduct, domus with atrium

## Greek — Mediterranean Coastal
- Blocks: white concrete, calcite, smooth quartz, cherry/birch
- Architecture: hilltop temple with marble columns, blue domes, agora stalls

### Adding a new building for a culture
1. In creative, build it with that palette.
2. `/gamerule structure` + Structure Block → save nbt to `src/main/resources/data/simplecolonies/structures/<culture>/house_2.nbt`
3. Register in `BuildingType` baseCost or data gen.

