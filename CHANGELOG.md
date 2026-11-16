# Changelog
## 0.1.30 — Trades, player property and bounded walks
- Curated per-job colony trade tables (buy job goods for emeralds, sell food/goods/tools); level unlocks, restock, saved uses, working Trade button and direct right-click trading. Replaces borrowed vanilla profession trades.
- Colonies remember blocks players place inside them: miners, woodcutters, farmers and builders avoid them, new plots avoid them, breaking a block clears the record. Bounded and saved per colony.
- Bounded worker walks (stall/timeout/retry) replace endless path re-requesting; unreachable miner quarry steps move to the next site.
- `replaceVanillaVillages` implemented through a NeoForge structure modifier; culture villages now start from the matching vanilla town-centre pool.
- Unit tests for property memory, trades and travel watch; GameTest for trade tables.
## 0.1.0 — Initial scaffold (NeoForge 1.21.1)
- Colony core with hunger/housing/expansion, 5 cultures by biome heat, biome tags & structure sets
- 8 jobs with SupplyChain (butcher -> leatherworker demo)
- Builder with schematic queue & resource consumption
- Town Hall block creates/inspects colony, reputation 0-100, leader election
- Command Staff + Diplomacy (war/alliance/peace)
- Config, commands (/colony), lang, recipes, worldgen registration, docs
- Create Aeronautics compatible (NeoForge 1.21.1, optional deps)

