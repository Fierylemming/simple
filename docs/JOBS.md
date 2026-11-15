# Jobs & Supply Flow

```
Farmer --wheat--> Butcher --leather--> Leatherworker --armor--> Soldier/Guard
   |wheat           |meat                |
   +---> Baker --bread--> Warehouse (food for all)
   
Woodcutter --logs--> Builder & Blacksmith
Miner --ores/coal/stone--> Blacksmith --tools/swords--> Miner/Woodcutter/Soldiers
                          Builder <--all mats-- Warehouse
```

Any job can `requestFromColony(item, amt)` → posts to SupplyChain pending map.
Producers peek pending each tick. This decouples links — adding a new `Tailor` that needs `wool` automatically works if you add a Shepherd that supplies wool.

### Starvation cascade
If farmer dead/no farm -> food 0 for 3 days -> weakness -> death -> less producers -> colony collapses unless player rescues with food donation (+rep).
Design goal: hardcore but recoverable.

