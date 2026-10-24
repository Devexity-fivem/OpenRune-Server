# Giant Mole — spec

Task: implement the Giant Mole encounter (`content/bosses/giant-mole`).

## OSRS facts

Sources: [Giant Mole](https://oldschool.runescape.wiki/w/Giant_Mole) and
[Mole Hole](https://oldschool.runescape.wiki/w/Mole_Hole) (OSRS Wiki, fetched
2026-10-23), the packed rev-241 cache, and `.data/raw-cache` TOMLs.

### The boss

| Fact | Value | Source |
|---|---|---|
| Combat level | 230 | wiki infobox |
| Hitpoints | 200 | wiki + `dump.npc` (`hitpoints=200`) |
| Attack/Strength/Defence/Magic | 200/200/200/200 | `dump.npc` (`[mole_giant]` block) |
| Size | 3x3 | wiki + npc def |
| Attack style | Melee (crush), max hit **21**, no special attacks | wiki fight overview |
| Attack speed | 4 ticks | wiki infobox |
| Aggressive | No | wiki |
| XP bonus | +7.5% | wiki |
| Elemental weakness | 50% to earth spells (since Aug 2025 update) | wiki changelog; **already in cache data** — `npcs.toml` sets `param.elemental_weakness_type=2`, `percent=50` on `npc.mole_giant`, so combat formulas handle it |
| Respawn | 16 ticks (9.6 s), at the **centre of the Mole Hole** | wiki infobox + fight overview |
| Slayer | Boss category, 215 xp | wiki |

### The burrow mechanic (the whole point of the fight)

- While her HP is **between 50% and 5%** of max, **every player attack has a
  25% (1/4) chance** of making her burrow to another spot in the lair —
  including hits that roll 0. Exception: **magic attacks that splash do not
  trigger it**, and **poison damage never triggers it**.
  (Wiki fight overview; Mod Ash tweet cited by the wiki, 18 Jun 2019:
  "1/4 when its health is between 5% and 50%".)
- On burrowing she loses her target/aggression and reappears elsewhere in the
  lair; players chase her down again.
- **Uncertain — exact resurface spots.** The wiki does not enumerate landing
  tiles. Plan: pick a random walkable tile inside the lair polygon (below);
  verify in-game against reference videos/wiki map whether real resurface
  points are a fixed set (e.g. at the `loc.mole_dugupsoil_*` mounds).
- On burrowing there is an additional chance (rate not stated on the wiki —
  **uncertain**) that she throws dirt, extinguishing **open** light sources
  (candle, torch, oil lamp) of players in melee range. Closed sources
  (bullseye lantern) are unaffected.
- Below 5% HP she stops burrowing entirely (2016-02-18 changelog).

### The lair (Mole Hole)

- Entrance: **dig with a spade** on the mole hills in Falador Park → drop into
  the lair. `loc.mole_hill` (12202) and `loc.mole_hill_spade` symbols exist in
  the cache.
- Multiway combat area `x 1728–1791, z 5120–5239` (levels 0–3) — already in
  `.data/raw-cache/map/area/multiway.toml` as "Giant mole".
- Mole respawn/anchor point `0_27_81_31_5` = (1759, 5189); 30
  `npc.mole_baby_*` spawns already in `.data/raw-cache/map/npcs/giant_mole_lair.toml`.
- Darkness: a light source is needed to avoid darkness; without one the
  `varbit.darkness_level` darkness overlay applies and insects skitter
  (project already implements this for Lumbridge Swamp Caves —
  `LumbridgeSwampLocs`). Whether darkness **damages** the player in the Mole
  Hole specifically is **uncertain** — wiki says light is "required to
  navigate without taking damage"; OSRS dark-cave insect bites exist. To
  verify in-game.
- Exit: rope at the **south end** via climbing rocks → well south of White
  Knights' Castle. Exit loc candidates in cache: `loc.mole_mud_hole_up` /
  `mole_mud_hole`.
- Burrow/resurface visuals exist in cache: `spotanim.mole_mud_hole_up_spotanim`,
  `mole_mud_splat_spotanim`, `mole_mud_cloud_spotanim`, `mole_mude_hole_spotanim`
  (exact ids to confirm at implementation).

### Explicitly out of scope for v1

- Private instance for 25,000 gp via the NW spade hill (`mole_hill_spade`;
  12,500 after Hard CA; halved on ironman) — needs `api/instances` + payment.
- `Look-inside` op on mole hills (player count in lair) — easy follow-up.
- Fire of Eternal Light (permanent light; needs Making Friends With My Arm).
- Wyson the gardener mole-skin/claw → bird's nest exchange — separate small
  feature, worth its own commit if quick.

## What already exists in the project

| Prereq | Status |
|---|---|
| `npc.mole_giant` def + full combat stats + earth weakness | ✅ cache + npcs.toml |
| Spawn (`0_27_81_31_5`) + baby moles + multiway bounds | ✅ `giant_mole_lair.toml` / `multiway.toml` |
| Drop table | ✅ `content/drops/.../giant_mole.toml` (binds `npc.mole_giant`; guaranteed mole claw + 1–3 skins, brimstone roll) |
| Spade digging | ✅ `SpadeScript` + `SpadeDigHook` extension point (`claims()`/`beforeDig()`/`dig()`), used by Barrows (`BarrowsDigHook`) |
| Darkness/light-source plumbing | ✅ `varbit.darkness_level`, `interface.darkness_dark`, insect timer, `LIGHT_SOURCES` check, `dbtable.light_sources` + `LightSourceEvents` (firemaking) |
| Boss DSL support for burrow | ✅ `onIncomingHit(ability, requires=...)`, `chance(n,...)`, `teleport(to)`, `randomFreeTiles`/`randomOf` tile sets, `external()` handlers |
| `loc.mole_hill` op handler | ❌ not registered anywhere — spade-dig hook needed |
| Burrow/resurface behaviour | ❌ to write |
| Light-extinguish on burrow | ❌ `external()` handler |
| Exit rope op | ❌ `onOpLoc` on `loc.mole_mud_hole_up` |

## Implementation plan

New module `content/bosses/giant-mole` (mirroring `content/bosses/kbd`):

- `build.gradle.kts` — `base-conventions` + `game-cache-test-conventions`;
  deps on `api.bosses`, `api.player`, `api.repo`, `engine.game`, `engine.map`.
- `GiantMole.kt` — `BossPluginScript` with `spec = boss("npc.mole_giant")`:
  `stats(attackRate = 4)`; single melee ability (damage 0..21, `Melee`);
  `onIncomingHit` reaction gated on
  `HpBelow(0.5) and !HpBelow(0.05) and !(damage==0-magic-splash)` running a
  `burrow` ability with `chance(4, ...)`. Burrow = spotanim (mud splat) +
  `interrupt()` + `teleport(randomOf(lairTiles))` + resurface spotanim +
  `external("giant_mole.extinguish")` for the dirt-throw.
  The 0-damage-magic-splash exclusion maps to the hit context — if the engine
  can't distinguish a legit magic 0 from a splash, use `!hitStyle(Magic)`
  restricted to 0-damage hits and note the approximation.
- `GiantMoleDigHook.kt` — `SpadeDigHook` registered via `PluginModule`
  `addSetBinding<SpadeDigHook>`: `claims()` = player standing on/adjacent to a
  `loc.mole_hill` in Falador Park (loc lookup via `LocRepository`); `dig()` =
  `telejump` to lair entry tile + apply `varbit.darkness_level` +
  `interface.darkness_dark` + minimap hide when no light source (reuse the
  swamp-cave pattern; entry tile to verify in-game).
- `GiantMoleLair.kt` — exit rope `onOpLoc("loc.mole_mud_hole_up")` → telejump to
  the surface well + clear darkness; entry-time darkness check shared.
- `gamevals.toml` only if new symbols are needed (likely none — all cache
  symbols exist). No `-pack` module unless a dbtable becomes useful.
- Tests: spec-validation test (pattern from `content/bosses/doom/DoomSpecTest`,
  `brutus` tests) + a unit test around the HP-window/25% burrow gate if it's
  factored testably; otherwise manual test script.

### Open questions to resolve during implementation

1. Exact dig-entry tile inside the lair (verify in game — dig on the park
   molehill and read coords, or locate `loc.mole_mud_hole*` in the packed map).
2. Resurface spot set — random walkable tile in the lair polygon vs. fixed
   mound list.
3. Dirt-throw chance and whether insect darkness damages the player in this
   lair specifically (existing swamp code only warns — mirror that unless the
   wiki/test shows damage).
4. `respawnRate` on `npc.mole_giant` — npcs.toml carries no override; check the
   default at runtime vs the wiki's 16 ticks.

Effort estimate: **M** as scoped — smaller if darkness/exit reuse is clean. No
architecture or public-API changes needed.
