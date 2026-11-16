# Changelog
## 0.1.31 — Creeper-proof colonies, physical meals and trade routes
- Colonies are creeper-proof: mob explosions (creeper, ghast, wither and their projectiles) no longer break colony buildings, wings, hall shells, protected plots or player blocks. Entity damage still applies and player-caused explosions (TNT, beds, crystals) still destroy blocks, so sieges and demolition stay possible. Config `protectColoniesFromMobExplosions`.
- Pistons cannot push, pull or break colony property. Config `protectColoniesFromPistons`.
- Daily meals are physical: a citizen walks to shared storage in the morning, takes a real food item and eats it. Uncollected rations are settled from storage before the next simulated day, so unloaded colonies still consume food. Config `enablePhysicalMeals` restores the old remote drawer when disabled.
- Completed harbors run a trade visit that sells surplus goods for emeralds; completed markets order missing construction materials with those emeralds. Reserves and the colony's own food are protected, and `/colony status` shows the last visit. Config `enableTradeRoutes`.
- A completed temple slowly raises existing player reputation (capped at 60, below the leadership threshold) and ignores hostile players.
- Overlapping colonies no longer hide each other's protection: a block is protected when *any* colony that covers it owns it.
- `Schematic Cannon` is a real research: builders accept a Create blueprint twice as large (32k blocks, 64k volume) once it is unlocked instead of being permanently unavailable.
- `Building` keeps and saves the bounding box of its plan, so finished buildings stay protected without regenerating thousands of placements.
- New unit tests for protected boxes, trade routes and temple standing, plus GameTests for creeper/piston protection, harbor exports, market orders and physical meals. Fire spread remains unprotected and is documented as a limit.

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

