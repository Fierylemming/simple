# Playtesting — Simple Colonies 0.3.0 (Chests + Courier + Building Marker)

## New: Distributed Chests, Warehouse Hub, Courier, Player Buildings

### The change
Previously warehouse was an abstract number. **Now villagers interact with real chests/bearrels/shulkers in village radius.**
- **Any chest** inside colony radius counts. Farmers dump to nearest farm chest, miners to mine chest, etc.
- **Warehouse building** = many chests clustered (e.g. 3x3 wall). Designate it via **Building Marker → Warehouse** and it becomes the hub.
- **Courier job** — bulk-moves 3 stacks/tick at 1.2 speed (normal villagers 0.6), consolidates scattered chests → warehouse hub. Without courier, villagers still work but slower as they walk to far chests.

### Building Marker — turn your own builds into colony buildings
You asked for "blocks a player can place and interact with to make one of their structures into a building":

1. Build anything: house, farm, tower — any blocks/style you want.
2. Place **Building Marker** (yellow checker, Creative tab) inside it.
3. Right-click marker → GUI: pick BuildingType (`House` (+4 beds), `Farm`, `Lumber Hut`, `Mine`, `Warehouse` (hub), `Barracks`, `Blacksmith`, `Bakery`, `Butcher Hut`, `Leather Shop`, `Temple`).
4. Colony instantly registers it: housing counts, jobs use that location as workplace, Warehouse sets courier hub.
5. Remove marker? Building stays (prevents accidental housing loss). Use "Clear" in GUI to deregister.

Also have **Building Tool** (iron stick) — right-click in colony for hint; main flow is marker block.

### How to test

```bash
./gradlew runClient
# Creative tab "Simple Colonies" has Town Hall, Building Marker, Building Tool, Warehouse Chest, Command Staff
```

**Quick setup (5 min):**
1. Place **Town Hall** → creates colony (e.g. Roman in plains). Note id.
2. Place 4 chests near town hall + 2 near where you'll farm. Fill one with 5 bread to avoid starve.
3. Place **Building Marker** inside those chests cluster → set to **Warehouse** → chat: "Warehouse hub set..." → this is now courier target.
4. Spawn a Farmer: `/summon simplecolonies:colony_villager ~ ~ ~` → shift-click → set to Farmer. Watch: it farms near marker, deposits wheat/bread into nearest farm chest (open chest to see items appear). Check Town Hall → Warehouse tab shows counts from chests.
5. Spawn **Courier**: summon another villager → set to Courier → watch chest counts: scattered farm chests drain, warehouse hub chests fill (bulk 3 stacks per courier tick).
6. Build your own house: 7×7 oak + 2 beds, place Building Marker inside → set to **House** → Town Hall → Overview housing +4, Citizens housing warning clears.
7. Queue blueprint: Town Hall → Buildings → `Queue House` still works for blueprints (places stone dummy blocks consuming mats from chests via WarehouseManager.extract). Player marker houses are instant-complete.
8. War: need leader (donate bread to 75 rep → Claim Leader) → then Building Marker Barracks, assign soldiers, Command Staff rally.

**Vanilla AI?** See `docs/VANILLA_AI.md` — vanilla villagers had fake work animations; all `ColonyVillager` jobs now do real block breaking/placing and chest interaction. Vanilla villagers outside colonies untouched for compat.

### Debug
- Town Hall Warehouse tab now shows `chestCount` + aggregated types.
- Warehouse chest right-click: sets hub (leader) + previews count.
- Builder consumes from chests first, then abstract (via `WarehouseManager.extract`).
- Courier visible: `colony broadcast` every 20 trips.

