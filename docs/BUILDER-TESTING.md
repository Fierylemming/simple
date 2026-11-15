# Builder testing (0.1.22)

The builder is a free starter profession, including for existing colonies.
In 0.1.21 an assigned builder could work despite the Town Hall claiming that the
profession was locked. A builder does nothing until construction is queued.

Natural expansion checks every `expansionCheckDays` (default 5 Minecraft days)
while the colony is loaded. It needs an assigned builder, food of at least twice
its population, no unfinished buildings and a clear, flat site. Only house
construction has a real plan. A plan needs oak logs; the worker places blocks
without walking to the build site, so this is not yet complete villager AI.

Use `/colony status` inside the colony to see builder status, food, logs,
time to the next expansion check, and unfinished construction and the next
block position. Reopen the Town Hall to refresh its snapshot. For a quick test
with cheats/operator permissions, `/colony give oak_log 150`, then `/colony
build house` queues one house without waiting for natural expansion. The command
does not grant leadership and cannot queue a second house while another project
is unfinished. If no safe site exists, try a flatter clearing in a fresh test
world. `/colony status` reports `BLOCKED` if a solid block obstructs the plan;
remove that obstruction to let the builder retry. The builder does not replace
solid obstructions. Save and reload during a build to check it resumes.

Automated tests verify starter research defaults, but these instructions still
need to be exercised in a Minecraft world; a successful Gradle build alone does
not validate world generation, building or villager behaviour.
