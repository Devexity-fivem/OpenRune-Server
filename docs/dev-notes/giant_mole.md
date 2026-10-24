# Giant Mole — implementation note

Branch `feat/giant-mole`. New module `content/bosses/giant-mole` (auto-included
by `settings.gradle.kts`).

## What was added

- `GiantMole.kt` — `BossPluginScript` spec for `npc.mole_giant`: 4-tick melee
  attack (0..21 crush), plus the burrow mechanic. An `onIncomingHit` reaction
  fires while HP is in (5%, 50%] and the hit isn't a magic splash (magic hit
  with 0 damage), and a `chance(4, ...)` gate implements the documented 1-in-4
  roll. The burrow plays `seq.mole_burrow_down`, teleports the NPC to a random
  resurface spot, plays `seq.mole_burrow_up`, then `interrupt()`s to drop the
  in-flight attack. `mole_attack`/`mole_burrow_*` are real cache seq symbols.
- `GiantMoleLair.kt` — `GiantMoleDigHook` (a `SpadeDigHook`, bound in
  `GiantMoleModule`) claims digging within 1 tile of a `loc.mole_hill` on the
  surface and drops the player into the lair, applying the swamp-cave darkness
  plumbing (`varbit.darkness_level`, minimap hidden, `interface.darkness_dark`)
  when no light source is carried/worn. `onOpLoc1("loc.mole_mud_hole_up")` exits
  to the surface and clears darkness.
- `GiantMoleSpecTest.kt` — spec builds without throwing.

## OSRS sources

- Giant Mole (fight overview, infobox): attack rate 4, max hit 21, non-
  aggressive, 25% burrow chance while HP between 5% and 50%, 0-damage hits
  count except magic splashes, poison never triggers it.
- Mole Hole: spade entry on Falador Park molehills, rope exit south end →
  well south of White Knights' Castle, darkness/light-source behaviour.
- `npcs.toml` already carries the 50% earth-spell weakness.

## Known gaps / verify in game

- **Resurface spots** are a hand-picked list of walkable tiles (baby-mole
  spawn area spread). Real resurface target selection is undocumented —
  check she lands on sensible tiles and never inside walls.
- **Entry/exit tiles** (`LAIR_ENTRY` 1759,5233; `SURFACE_EXIT` 2956,3363) are
  approximations — verify with `::mypos` and adjust.
- **Exit loc op index** assumed `op1` ("Climb-up"); if nothing happens, the
  op may be a different index — report and I'll move the handler.
- **Dirt-throw light extinguish** is NOT implemented (needs player-side
  inventory edits from a hit-reaction context; documented in spec).
- **Insect "skittering" warning** not wired (reusing `timer.swamp_cave_insects`
  would collide with the Lumbridge handler registration).
- Private instance, Look-inside op, Wyson exchange — out of scope per spec.

## In-game test script

1. `::tele 0 2987 3378` (Falador Park), `::item spade` if needed.
2. Stand next to a mole hill and use the spade → you should drop into the
   Mole Hole; with no light source the screen darkens and minimap hides.
3. Attack the Giant Mole: melee hits up to 21, attack every ~2.4s.
4. Get her below half HP: she should periodically dive into the ground and
   resurface elsewhere in the lair (about 1 in 4 hits in the window).
5. Below ~5% HP she should stop burrowing entirely.
6. Kill her → confirm drops (mole claw + 1–3 mole skins guaranteed).
7. Climb the rope/rocks at the south end to exit; darkness clears.
