# Empty colonies from 0.1.19

Back up the world before repairing it. Version 0.1.19 could register citizen
records and then fail while assigning a job, before adding the villager to the
world. A missing entity-attribute registration and renderer also affected custom
villagers. An overly strict flat-site search could skip starter buildings.

Install 0.1.20, visit the colony's center and run `/colony list` to get its ID.
In a cheats-enabled world (or as a server operator), run `/colony repair <id>`.
This tries to add a missing starter house and farm on safe terrain, and creates
starter villagers only if there are no citizen records. It does not delete the
colony or its warehouse. It will not overwrite existing registered buildings.
If no suitable land is found, the server log says which starter could not be
placed. Travel back and try again after nearby chunks have loaded.

If the repair reports citizen records but you are certain none of those
villagers actually exist, `/colony repair <id> reset_citizens` explicitly removes
all those records and creates starters. It refuses if any recorded citizen is
currently loaded. **It cannot detect villagers in unloaded chunks**; using it
when citizens are merely elsewhere can create duplicates or orphan their old
records. Do not use it for a colony with real villagers. Preserve a backup.

For new discoveries, the code verifies the actual structure bounds, selects dry
loaded land with space for both starters, and reports the colony's center,
citizen count and building count. If there is no suitable land nearby, it says
so instead of claiming a fully discovered colony. Greek villages no longer
select open-ocean biomes in newly generated chunks. Actual historical templates
remain unfinished; this change doesn't turn the vanilla jigsaw pieces into
historical villages.
