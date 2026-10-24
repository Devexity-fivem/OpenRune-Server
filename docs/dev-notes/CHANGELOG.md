# Dev-notes changelog

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
