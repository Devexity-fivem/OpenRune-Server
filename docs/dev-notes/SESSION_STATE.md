# Session state (updated 2026-10-24)

Autonomous content build on fork `github.com/Devexity-fivem/OpenRune-Server`
(origin=fork, upstream=OpenRune). Branch model (consolidated 2026-10-25):
**`dev` = active development/testing** (work here), **`main` = finished code**
(merge dev -> main when a batch is verified). All other branches deleted.
NOTE: `main` currently contains unverified compiled-only boss code
(archaeologists, chaos fanatic, scorpia) merged as baseline — verify before
treating main as "finished".

## Done + pushed

- Giant Mole — VERIFIED in game (dig entry 1759,5190; rope exit
  loc.mole_rope_01/02 op1 -> 2999,3377; burrow 1/4 on player hits while HP
  5-50% excl magic splashes; anims + 2t wait + spotanims; darkness on entry).
  Plus: dirt extinguish of open light sources (candle/torch/oil lamp) in melee
  range; Look-inside op2 on molehills. Spec: docs/dev-notes/giant_mole_spec.md,
  note: giant_mole.md.
- Crazy Archaeologist (content/bosses/crazy-archaeologist) — compiled only,
  NOT verified in game. Melee 0-14 requires WithinMeleeRange, ranged book
  projectile 0-14 (spotanim.crazy_archaeologist_book), Rain of knowledge! =
  debris x3 then debris x2 (0-24 Typeless). attackRate=3. Spawn npc at
  2977,3702.
- Chaos Fanatic (content/bosses/chaos-fanatic) — compiled only. Magic
  projectile 0-21, attackRate=2; specials: disarm (moves worn slot 3 weapon
  to inv if space, message) + explosion debris x3 (0-31 Typeless,
  center=CurrentTarget). Spawn npc.chaos_fanatic at 2979,3846.
  RISK: "spotanim.chaos_fanatic_rift" table guess may fail at boot —
  check for Missing mapping in run log.

## Gotchas

- ALWAYS run `gradlew run`/`jar` with `--no-configuration-cache` after adding
  a module — config cache silently drops new subprojects.
- Never `>NUL` in bash on Windows (creates literal file NUL).
- InvObj needs ItemServerType ctor (Int ctor blocked by type-safety check).
- BossPluginScript(deps): pass deps, do NOT redeclare `private val deps`.
- Spotanim/seq/loc symbol tables are strict — wrong prefix crashes startup.
- devMode=true -> all logins get ADMINISTRATOR; cmds ::tele ::item ::npc
  ::master ::mypos. RSProx modulus = .data/client.key content.

## Next up (user asleep, autonomous)

- Scorpia (drop table exists; melee + healing offspring minions)
- Scorpia committed (content/bosses/scorpia) — melee stab 0-16 4t, poison(20), guardian summon on first hit below 50% via AbilityUsed once-guard. GAP: guardian heal not implemented; seq.scorpion_attack symbol guessed (verify boot).
  deranged archaeologist, skotizo, dad, venenatis, callisto...)
- Test suite + spotlessCheck before pushing each module.

## Skills deep-dive (in progress)

14 skill modules exist and load: cooking, crafting(+pack), firemaking, fishing(+pack),
herblore, magic(alchemy/arceuus/spell-attacks/teleports/altars), mining, prayer,
runecrafting, slayer, smithing, thieving, utils, woodcutting. Combat skills live in
api/combat. PROGRESS.md says 20/23 — missing: AGILITY, FLETCHING, HUNTER.
Construction/Farming only partial (scattered code in content/areas/city).

Foundations already in place:
- `player.agilityLvl` / `player.statAdd(stat, constant, percent)` in
  api/player/stat/PlayerStatExtensions.kt (XP-grant helper still to confirm — look
  for addXp/statLevelUp fn).
- Obstacle pattern: `onApLoc1("loc.x") { apCross(it.loc) }` -> level check ->
  `exactMove(coords, dest, delay1, delay2, dir)` + `anim("seq.human_spot_jump",
  delay=n)` + `soundSynth("synth.x", delay=n)` + `delay(ticks)`.
  Reference: content/areas/city/lumbridge/.../LumbridgeSteppingStone.kt.
- `agility_*` loc/var symbols exist in binary gamevals (gnome course obstacles
  included — need to find table + exact loc names for gnome stronghold course:
  log balance, net, tree branch, balancing rope, tree branch down, obstacle pipe).
- Hunter: `npc.scorpion` etc exist; bird snares/box traps = loc+obj state machine —
  start with simplest (bird snare or impling?) if context runs short.
- Fletching: needs item-on-item knife/log->cut + bowstring->unstrung loops; check
  Jagex dbtable for recipe data before hand-listing.

PLAN for base setup (all new modules under content/skills/<skill>/):
1. Agility: gnome stronghold course loop (all 7 obstacles), level 1, xp/lap.
2. Fletching: knife+log -> shafts/shortbow/unstrung; string->bow; feathers->arrows.
3. Hunter: bird snare -> catch bird -> xp (or defer to next pass).
Each: new dir + build.gradle.kts (same dep set as firemaking), PluginScript,
gamevals.toml only if custom symbols needed; commit + push after each.

## Dev-tools MCP setup (client-side)

- OpenRune-Developer-Tools cloned to C:\Users\pablo\OneDrive\Desktop\OpenRune-Developer-Tools,
  jar built via `gradlew jar`. Deployed to .runelite/plugins/ (the real scan dir for the
  RSProx-patched RuneLite 1.13.3 client — sideloaded-plugins dir is NOT scanned by it),
  plus .runelite/sideloaded-plugins/ and .rsprox/sideloaded-plugins/ for safety.
- Enable key written: runelite.openrunedevtoolsplugin=true in
  ~/.runelite/profiles2/default-669783123302300.properties
- MCP registered in %APPDATA%\devin\mcp_config.json as `osrs-dev-tools` ->
  http://127.0.0.1:7780/mcp. Endpoint dead until client restarts with plugin loaded.
- Client runs with --developer-mode already (RSProx launcher does it).
- If plugin still absent after restart, check ~/.runelite/logs/client.log for
  "Loading external plugin"/OpenRuneDevTools lines; patched client may need
  -PclientJar=<patched client jar> at build time.

## Agility progress

- XP call: player.statAdvance("stat.agility", xp); level gate: player.agilityLvl.
- Obstacle pattern (ChampionsGuildSteppingStone.kt): onApLoc1("loc.x")
  { apX(it.loc) } -> `if (isWithinApRange(loc, N)) action()` — NEVER call
  apRange() unconditionally: it sets apRangeCalled synchronously and instant
  returns (level fail, wrong side) refire the handler every tick.
- Realm baseXpRate=150.0 on dev world — per-obstacle XP deltas are 150x.
- GNOME STRONGHOLD COURSE: fully implemented + VERIFIED LIVE end-to-end
  (3+ laps) in GnomeAgilityCourse.kt. 110.5 XP/lap (10+10+6.5+10+6.5+10+7.5
  +50 bonus on pipe). All seven obstacles with correct coords/levels/anims/msgs.
- Lap-progress gating DONE (2026-10-25): temp varp 63739 tracks in-order
  obstacle progress; bonus only on genuine ordered lap; repeats/out-of-order
  can't farm it. Verified live (isolated pipe +1125 no bonus; lap +16575).
- Marks of Grace DONE: shared MarkOfGrace service — 2/6 per-lap roll
  (approximation, gnome rate unpublished), 3-min game-time cooldown in
  permanent varp 63740 (persists logout), lvl+20 penalty (denominator x5),
  private obj.grace at (2484,3418,2), 10-min despawn. Not observed live
  (roll failed both laps); 9 integration tests cover it deterministically.
- Trainer barks + Talk-to dialogue DONE + verified live (wiki-verbatim lines).
- Tests: GnomeAgilityCourseTest (cache-backed symbols/geometry) +
  GnomeAgilityCourseGameTest (9 GameTestExtension integration tests).
  Test players need `observerUUID` set manually for private-obj tests.
- Test account for live work: user `devin ai`, password `devinai`
  (auto-registered; requireRegistration=false).

## Gnome course reference (VERIFIED via live MCP play)

Course loop (all coords confirmed in game):
1. log_balance (23145/23142/23143, x2474 z3433-35, plane0): N->S lands
   (2474,3429,0), S->N lands (2474,3436,0). seq.human_walk_logbalance, ~9t.
   Msgs "You walk carefully across the slippery log..." / "... and make it
   safely to the other side."
2. obstical_net2 (23134, z3425 facing S): climb-over from ground north side ->
   (player.x, 3424, level1). seq.human_climbing. "You climb the netting."
3. climbing_branch (23559, 2473,3422,level1): -> (2473,3420,level2).
   "You climb the tree..." / "... to the platform above."
4. balancing_rope/(_mid) (23557-58, x2478-82 z3420 level2): walk east ->
   (2483,3420,2); west back -> (2477,3420,2). seq.myq3_human_tightrope.
   "You carefully cross the tightrope."
5. climbing_tree(+2) (23560/23561, ~2486,3419,level2): Climb-down ->
   (2486,3419,level0). seq.human_climbing_down. "You land on the ground."
6. obstical_net3 (23135, x2483-87 z3426 facing N): ground vault S->N ->
   (player.x,3427,0); also works N->S -> (x,3425,0). "You climb the netting."
7. obstical_pipe3_1/3_2 (23138/23139): ONLY the z=3431 row is enterable
   (z=3435 row is inert scenery — guard on loc.z). Enter from south mouth
   (loc.x,3430); two-hops exactMove +3 -> z3433, +4 -> z3437 exit.
   seq.human_pipesqueeze x2. +7.5 XP + 50 lap bonus.
   "You pull yourself through the pipes."

Deviations/gaps still open:
- Timing constants (cycles/ticks per obstacle) are eyeballed, not
  tick-exact vs real OSRS.
- myq3_human_tightrope is a quest-named seq id 4772 — resolves fine and
  looks right for a tightrope walk, but it may not be the exact gnome seq.
- Mark-of-Grace live observation pending (probabilistic; tested in harness).
- "Total Laps" HUD increments per pipe completion — existing engine/counter
  behavior, not gated on ordered progress; cosmetic.

## Course map-data quirk

MapLocListDecoder reports locs' packed level one higher than the client
plane (log stored level1, renders plane0). The test asserts (x,z) only.
