# Dev-notes changelog

## 2026-10-25 — Gnome course lap progress + Marks of Grace (`dev`)

- Lap completion now tracked per-obstacle in temp varp
  `varp.agility_course_progress` (63739): the 50 XP lap bonus fires only when
  obstacles 1-7 complete in order; out-of-order use resets progress, and
  repeat pipe squeezes award no bonus (verified live: isolated pipe +1125,
  full lap +16575 at 150x).
- New shared `MarkOfGrace` service (`MarkOfGrace.kt`): per-lap 2/6 roll
  (gnome's exact rate is unpublished — chosen value documented in source),
  3-minute game-time cooldown via permanent varp `agility_mark_last_minute`
  (63740, survives logout), 80% rate reduction when unboosted Agility is 20+
  over the course requirement, private `obj.grace` spawn on the level-2
  platform (2484,3418,2) with 10-minute despawn/reveal.
- New `agility-pack` module (`AgilityPluginPack` + `agility_vars.toml`)
  registers both varps; ids moved to 63739/63740 after discovering the
  65400-65535 block is fully occupied (`crafting_state` collision).
- Gnome trainer (`npc.gnometrainer`): obstacle completions trigger the
  nearest trainer within 15 tiles to `npc.say` wiki-verbatim barks; Talk-to
  now opens the actual dialogue ("training area, not pointless conversation
  area") — verified live.
- 9 integration tests in `GnomeAgilityCourseGameTest` (GameTestExtension)
  cover ordered progression, per-obstacle XP, single bonus, repeat-pipe
  anti-farming, out-of-order reset, mark spawn/cooldown/level-penalty.
  Gotcha: private objects need `player.observerUUID` — test players must set
  it manually (login applier is not run in tests).
- Live: trainer dialogue verified; mark not observed across 2 laps (roll
  failure, ~56% likely) — deterministic test coverage stands in.

## 2026-10-25 — Gnome Stronghold Agility Course (`feat/giant-mole`)

- Rewrote `GnomeAgilityCourse.kt` into the full 7-obstacle course: log balance
  (10), obstacle net up (10), tree branch up (6.5), balancing rope (10), tree
  branch down (6.5), obstacle net vault (10), obstacle pipe (7.5 + 50 lap
  bonus) = 110.5 XP/lap.
- Correct coords/levels verified live over 2 full laps (log N/S both ways,
  net2 -> (x,3424,1), branch -> (2473,3420,2), rope -> (2483,3420,2),
  branch-down -> (2486,3419,0), net3 vault -> (x,3427,0), pipe south-row only
  -> two-hop crawl to z=3437).
- Fixed a real crash found live: `seq.human_tightrope` does not exist — the
  tightrope walk anim is `seq.myq3_human_tightrope` (4772).
- Added `GnomeAgilityCourseTest`: cache-backed regression for every gameval
  symbol the script resolves (catches the tightrope class of crash) plus
  obstacle loc placement in map square 9781. `MapLocListDecoder` reports a
  level one higher than the render plane, so the test asserts (x,z) only.
- Removed temp diagnostic `LocDump.kt` and stray `NUL` artifact.
- Still open: lap-bonus gating by per-obstacle progress (enum 3990-series
  exists for ordering), Marks of Grace spawns, gnome trainer barks,
  tick-exact timings. See `docs/development/feature-coverage-roadmap.md`.

## 2026-10-24 — Giant Mole encounter (`feat/giant-mole`)

- New module `content/bosses/giant-mole`: melee spec (4-tick, 0–21 crush) +
  1-in-4 burrow on player hits while HP is 5%–50%, magic splashes excluded.
  Burrow plays `seq.mole_burrow_down` + `spotanim.mole_mud_splat_spotanim`,
  waits 2 ticks, teleports to a random resurface spot, then
  `seq.mole_burrow_up` + `spotanim.mole_mud_hole_up_spotanim`.
- Spade-dig entry on Falador Park molehills (`loc.mole_hill` /
  `loc.mole_hill_spade` within 3 tiles, neighbouring zones scanned) drops the
  player at 1759,5190 and applies the Lumbridge-style darkness plumbing when
  no light source is carried. Rope exit (`loc.mole_rope_01`/`02`) returns to
  the surface well at 2999,3377 and clears darkness.
- Verified in game (dig entry, burrow visuals, exit rope). Not implemented:
  light-extinguish dirt throw, insect warning timer, private instance,
  Look-inside, Wyson exchange.
- Gotcha worth remembering: after adding a new content module you must run
  Gradle with `--no-configuration-cache` once — the cached configuration
  predates the new subproject and silently omits it from the runtime classpath.
