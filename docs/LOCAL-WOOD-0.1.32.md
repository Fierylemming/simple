# Building with the wood that grows there (0.1.32)

Back up your world before testing. No release was published.

## The problem

Every house, worksite and research order used to be written in oak. A Roman
colony founded in a birch forest could never expand: its plans wanted oak planks,
its research wanted oak planks, and the only trees it could harvest were birch.
The settlement stalled on materials that do not exist in its biome.

## What changed

**Wooden costs name a family, not a species.** `#planks`, `#log`, `#fence`,
`#boat`, `#slab`, `#stairs`, `#door` and `#sapling` are resolved against the
colony's shared storage when the order is made. Exact item costs still work
exactly as before, so old saves and the Create schematic path are unchanged.

- **Research** (`ColonyUpgrade`): every wooden cost is a family. When an order is
  queued the colony resolves it to the species it holds most of and *saves that
  concrete list* (`pendingCost`). The builder then carries one exact species, and
  a later delivery cannot swap materials mid-order.
- **Generating plans** (`Building`, `CulturalHousePlans`, `LevelOneWorksites`)
  already substituted a chosen timber; the colony now chooses that timber from
  `WoodCosts.preferredTimbers(stock, homeTimber)` — most stored wood first. Stock
  order decides, so the plan follows the forest the villagers actually harvest.
- **Founding** (`ColonyManager.createColony`): `HouseTimber.dominantAround` reads
  logs (weight 3) and leaves (weight 1) within 24 blocks, and the starter hall's
  floor, roof and door plus the founding planks, logs and saplings use that
  species. A colony with no trees nearby still defaults to oak, so existing test
  worlds and arenas behave as before.
- **Adoption:** once a colony stores eight or more units of a different species,
  `homeTimber` switches, which steers later plans and trades.
- **Trade routes:** a harbor sells whichever species has the largest spare
  surplus above its reserve; a market buys the exact species a plan is missing.
- **Worker trades:** woodcutter, builder and courier offers are resolved to the
  colony's species when the merchant list is built.

Supported species: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry
— every wooden family is filtered against the item registry, so a species without
a given variant (for example a missing boat) is simply never asked for.

## What this does not fix

- The builder still needs the materials physically in shared storage; a colony
  with no woodcutter cannot conjure planks from scenery.
- Culture palettes for later tiers (stone, terracotta, quartz) are unchanged and
  still name their own blocks.
- The starter shell is a plain cobblestone and planks box; only its wood follows
  the biome.
- Pathfinding, site selection and mixed-biome edge cases still need a real world
  test; the automated coverage is a planted birch forest in a GameTest.

## Tests

- `WoodCostsTest` — family counting, deterministic resolution, spread across
  species, timber preference.
- `ColonyUpgradeCostTest` — no research may name a single wood species.
- `BiomeWoodGameTests` — a Roman colony founded in a planted birch forest: home
  timber, founding supplies, starter hall, house plan and research cost are all
  birch.
- `TradeRoutesTest` and `ColonyTradeTableTest` — wood families resolve to the
  colony's species for harbors, markets and worker trades.
