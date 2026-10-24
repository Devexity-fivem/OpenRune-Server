# Architecture notes

Read against `main` @ `5c4c9c106`, rev 241. Sources: `AGENTS.md`, `README.md`,
`PROGRESS.md`, `docs/`, `openwiki/`, `.github/workflows/`, and the modules below.

## Module layout

- `engine/` — game-loop plumbing: `game` (entities, hit, inv types), `module`
  (plugin module base), `coroutine` (tick-driven GameCoroutine), `events`
  (EventBus), `interact`, `map`, `routefinder`, `plugin`, small utils. Content
  basically never touches this.
- `api/` — ~60 submodules of DI-managed domain services + the DSL content code
  calls: `api/script` (op-handler DSL: `onOpNpc1..5`, `onOpLoc1..5`,
  `onOpHeld*`, `onItemOnItem`, `onIfOpen/Close`, `onUnimplementedOpNpcN`),
  `api/bosses` (declarative boss framework), `api/shops`, `api/combat*`,
  `api/player` (ProtectedAccess, dialogue), `api/repo` (loc/npc repositories),
  `api/testing` (test harness), `api/generated` (gitignored, produced by the
  cache build: typed dbtable-row and enum accessors).
- `content/` — gameplay plugins by category (`areas`, `bosses`, `drops`,
  `events`, `interfaces`, `minigames`, `quest`, `skills`, `travel`, `other`,
  `generic`). Each plugin is a Gradle submodule; a `*-pack` sibling submodule
  holds only cache resources (dbtables, enums, varps, gamevals.toml) so the
  cache build never compiles game scripts.
- `server/` — `app` (entry `GameServerKt`, `GameService`), `install` (RSA keygen,
  config copy), `logging`, `services` (embedded Postgres, central/HTTP),
  `shared` (plugin classpath scan + Guice wiring).
- `or-cache/` — cache builder, main `dev.openrune.CacheToolsKt`; tasks
  `freshCache` (download + full build), `buildCache`, `cleanCs2`,
  `mergePluginGamevals`.
- `tools/` — `osrs-mcp`, `wiki-dumping` (generates drop tables), progress
  generator. `example-plugin/` shows the external-plugin layout.
- `osrs-dumps/` — reference dumps (CS2 `.cs2`, interface `.if3`) for looking up
  how Jagex built a script/interface.

## Plugin/content loading

No manual registration. At boot, `server/shared` scans the classpath:
`PluginModule` subclasses become Guice modules (for DI and set-bindings like
`addSetBinding<XpMod>(...)`), `PluginScript` subclasses are constructed via
`@Inject` and their `startup()` binds handlers. Boot log on this machine:
`Loaded 62 plugin module(s)`, `Loaded 623 plugin script(s)`. World-type-scoped
scripts can be filtered out (`Skipped 2 plugin script(s) scoped to other world
types`).

## How each content kind is defined

- **Skill/activity** — a `content/skills/<name>` module: `PluginScript`s binding
  op handlers; skill tables (XP, levels, recipes) live in the cache as dbtables
  defined in a `-pack` module or in `or-cache` `tables/` (e.g.
  `Firemaking.logs()`, `Smithing.bars()`), read at runtime through generated
  `api/generated` accessors. Reference: `content/skills/crafting`,
  `content/skills/firemaking` (small), `docs/` per-system.
- **NPC/boss encounter** — `content/bosses/<name>` with a `BossPluginScript`
  subclass declaring `spec = boss("npc.x") { stats(); ability(){...};
  phase(){...} }` (`api/bosses`, documented in `docs/boss-dsl.md`). The spec is
  validated at startup. Non-DSL logic plugs in via `external(...)` handlers.
  Simple reference: `content/bosses/kbd` (2 files); richer:
  `content/bosses/amoxliatl`, `brutus` (custom boss w/ tests).
- **Shop** — no special module; an NPC script calls
  `shops.open(player, npc, "Title", "inv.<symbol>")` where the `inv.*` inventory
  contents are cache data (`api/shops` + TOML invs packed by `-pack` modules).
  Reference: `content/areas/city/lumbridge/.../npcs/Bob.kt`.
- **Drop table** — `docs/drops.md`. Simple tables are TOML under
  `content/drops/src/main/resources/drops/tables/monsters/*.toml` (stages:
  guaranteed / pre-roll / weighted main / tertiary, shared subtables like
  `gem`, clue/quest gates). Complex conditional tables are Kotlin
  `@RegisterDropTable` in `content/drops/.../tables/monsters/`. 556 TOML + 235
  Kotlin tables load at boot; wiki-dumper tool generates them.

## Tick / queue / interruption model

Single `game` thread, 600 ms ticks (`GameService` → `GameCycle.tick()`), fixed
order: world tick → npc/player pre-tick → input read → npc main → controllers →
player main → login/logout → `mapClock.tick()` → post-tick flush. Events go
through `EventBus` (`UnboundEvent` broadcast, `KeyedEvent` single handler,
`SuspendEvent` single suspending handler). Blocking flows (dialogue, anim
waits, skilling loops) are `suspend` functions on a hand-rolled tick-driven
coroutine under `ProtectedAccess` (which guarantees the entity still exists /
player still online at each resume point); `delay`, `arriveDelay`, `mapClock`,
`mes`, `chatNpc`, `choiceN` are the DSL. NPC queues/timers run through
`queue.com_retaliate_player`-style queue processor; scripts that freeze/stun
use engine states (`isFrozen`, timers) rather than ad-hoc locks.

## Cache, defs, GameVals

Two caches under `.data/cache/`: `LIVE` (full client cache, ~250 MB, downloaded
from OpenRS2 at the `game.example.yml` revision) and `SERVER` (stripped: JS5
responses + decoded defs the server needs). Every entity reference in code is a
gameval symbol (`"npc.bob"`, `"loc.winch"`, `"obj.*"`, `"varp.*"`,
`"dbtable.*"`) resolved through `GameValProvider` — symbols come from the
committed `.data/gamevals/*.rscm` plus generated `.data/gamevals-binary/*.dat`
(dumped from the cache during `freshCache`), plus per-plugin `gamevals.toml`
merged by `mergePluginGamevals`. Custom data ships in `-pack` modules
(`[[enum]]`/`dbTable{}`/varps), is packed into the cache, and is read back via
generated `api/generated` row/enum accessors. Rule: data lives in the cache,
code never hardcodes ids or lookup tables. CS2 sources are a versioned bundle
(`cdn.openrune.dev/cs2-bundles/<rev>`) unpacked to `%LOCALAPPDATA%`; recompiled
+ repacked into the cache each build — delete via `cleanCs2` if symbols break
after a revision bump.

## Testing + CI

- `src/test` — JUnit 5 unit tests (combat formulas, drop maths, item moves);
  content modules use `game-cache-test-conventions` for cache-backed tests
  (see `content/bosses/brutus/src/test`).
- `src/integration` — `integration-test-suite` JUnit suite wired to
  `api:testing`, working dir = repo root so it reads the built cache; covers
  game-process/NPC/player flows and content behaviour
  (`content/areas/city/lumbridge/.../GeneralStoreSaleTest.kt`).
- `.github/workflows/ci.yml` — `assemble test integration`, then boots the
  server and greps for `Server ready in` within 300 s. `format.yml` runs
  `spotlessCheck` (ktlint ratcheted to `origin/main` — only files you touched
  are checked; `./gradlew spotlessApply` fixes). Other workflows:
  `gameval-conflicts`, `content-progress`, `release-server`.

## How PRs get merged

Commits are small and single-purpose (`feat: Sheep Shearer quest`, `fix: climb
the Goblin Village lookout ladder`, `Map 11 shop invs from archived captures`).
A content PR = one `PluginScript`-holding module + its `-pack` resources +
`gamevals.toml`, nothing else (Sheep Shearer: 3 files, +343). Fixes stay in the
module they belong to. Maintainers coordinate on Discord; Trello is read-only
for non-maintainers. OSRS-accurate behaviour is expected — messages, XP rates
and drop tables get checked against the wiki/dumps.
