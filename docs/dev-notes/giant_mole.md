# Giant Mole — implementation note

Module `content/bosses/giant-mole` (auto-included by `settings.gradle.kts`).

## What was added

- `GiantMole.kt` — `BossPluginScript` spec for `npc.mole_giant`: 4-tick melee
  attack (0..21 crush), plus the burrow mechanic. An `onIncomingHit` reaction
  fires while HP is in (5%, 50%] and the hit isn't a magic splash (magic hit
  with 0 damage), and a `chance(4, ...)` gate implements the documented 1-in-4
  roll. The burrow plays `seq.mole_burrow_down`, teleports the NPC to a random
  resurface spot, plays `seq.mole_burrow_up`, then `interrupt()`s to drop the
  in-flight attack. `mole_attack`/`mole_burrow_*` are real cache seq symbols.
- `GiantMoleLair.kt` — `GiantMoleDigHook` (a `SpadeDigHook`) claims digging
  next to a `loc.mole_hill`/`loc.mole_hill_spade` in Falador Park and drops the
  player into the lair, applying the swamp-cave darkness plumbing
  (`varbit.darkness_level`, minimap hidden, `interface.darkness_dark`) when no
  light source is carried/worn. `GiantMoleLair` binds `onOpLoc5` on
  `loc.mole_hill` for Look-inside (the loc def stores it at op slot 5 — op1/op2
  both fall through to "Nothing interesting happens"; found via a
  `LocDefaultEvents` catch-all diagnostic). `GiantMoleInsects` runs a
  `WorldQueueList` 1-tick loop: after 15 dark ticks it warns ("You hear tiny
  insects skittering..."), from 30 ticks it swarms and deals 1 typeless damage
  per tick. It only tracks players inside the lair bounds or a `giant_mole`
  instance session, clears darkness when the player leaves or picks up a light
  source, and drops dead/offline players from tracking.
- `GiantMoleInstance.kt` — `InstanceScript` over `dbrow.instance_giant_mole`
  (added to `InstanceSettingsTable`): 25,000 gp fee, private 1-player
  instances, copies regions 6992/6993 (the lair), spawns the Giant Mole at its
  anchor plus all baby-mole spawns. `onExitObject` clears darkness then runs
  `defaultLeaveFlow()` for session members or telejumps static-lair climbers
  to the surface well; `onInstancePlayerJoin` applies darkness when the joiner
  has no light source.
- Wyson exchange (in `FaladorTownsfolkScript`): carrying any mole part
  (claw/skin, certed or not, immaculate skin) makes Talk-to / using the part
  on him offer the exchange. 1 `obj.bird_nest_cheapseeds` per claw/skin, 5 per
  immaculate skin (wiki). Also combines `obj.holy_sandals` + `obj.mole_slippers`
  into `obj.holy_moleys` when both are carried.

## OSRS sources

- Giant Mole (fight overview, infobox): attack rate 4, max hit 21, non-
  aggressive, 25% burrow chance while HP between 5% and 50%, 0-damage hits
  count except magic splashes, poison never triggers it.
- Mole Hole: spade entry on Falador Park molehills, rope exit south end →
  well south of White Knights' Castle, darkness/light-source behaviour,
  insects swarm unlit players (15t warning, damage from ~30t), private
  instance 25,000 gp via `loc.mole_hill_spade`.
- Wyson: nests for parts — always `bird_nest_cheapseeds`, 5 per immaculate
  skin; holy moleys combine.
- `npcs.toml` already carries the 50% earth-spell weakness.

## Verified in game

- Dig on a park molehill → drops into the lair at `LAIR_ENTRY` (1759, 5190).
- Burrow: plays `seq.mole_burrow_down` + mud-splat spotanim, waits 2 ticks,
  teleports, resurfaces with `seq.mole_burrow_up` + hole-up spotanim.
- Exit rope (`loc.mole_rope_02` at 1752,5136, Climb op1) → surface well at
  (2999, 3377) — both from the static lair and from inside a private instance
  (copied rope at instance coords ~10328,80; leave-flow ejects to the same well).
- Look-inside on `loc.mole_hill` → "Inside the mole hill there are N players."
- Private instance: Fight-privately on `loc.mole_hill_spade` → 25,000 gp fee
  charged (coins 75,000 remained from 100k), "Your Giant Mole instance is
  ready. Entering...", mapped space ~x10335, Giant Mole + baby moles spawned.
  Rejoin flow shows "Rejoining your instance...".
- Darkness: entering unlit hides the minimap + dark overlay; carrying a lit
  candle on dig entry or instance join leaves the minimap normal.
- Insects: 15t warning + skitter sound, 30t "Tiny biting insects swarm all
  over you!" then 1 dmg/tick (HP 28 → dead when ignored). Lit candle stops
  damage (HP regenerated 16→17). Teleporting out / dying clears the varbit —
  no damage continues on the surface (verified: HP stable at respawn).
- Wyson: 3 claws + 2 skins + 1 immaculate → exactly 10 nests; 2 claws →
  2 × bird_nest_cheapseeds (7413); sandals + slippers → holy moleys (33002).

## Known gaps / still to verify

- **Resurface spots** are a hand-picked list of walkable tiles (baby-mole
  spawn area spread). Real resurface target selection is undocumented.
- **Dirt-throw light extinguish** is NOT implemented (needs player-side
  inventory edits from a hit-reaction context; documented in spec).
- **Instance fee discounts** for Combat Achievements / ironmen are not wired —
  everyone pays 25,000.
- Fire of Eternal Light (permanent lair light) not implemented — needs the
  Making Friends with My Arm quest state.

## In-game test script

1. `::tele 0 2987 3378` (Falador Park), `::item spade` if needed.
2. Stand next to a mole hill and use the spade → you should drop into the
   Mole Hole; with no light source the screen darkens and minimap hides.
3. Attack the Giant Mole: melee hits up to 21, attack every ~2.4s.
4. Get her below half HP: she should periodically dive into the ground and
   resurface elsewhere in the lair (about 1 in 4 hits in the window).
5. Below ~5% HP she should stop burrowing entirely.
6. Kill her → confirm drops (mole claw + 1–3 mole skins guaranteed).
7. Climb the rope/rocks at the south end (1752,5136) to exit; darkness clears.
8. Fight-privately on the NW spade hill (2984,3387) → instance menu → 25k fee.

