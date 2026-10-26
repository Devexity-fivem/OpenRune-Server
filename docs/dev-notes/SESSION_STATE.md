# Session state (updated 2026-10-25)

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
  range; Look-inside op5 on molehills (op5 is the real slot — verified via
  default-event probe). Spec: docs/dev-notes/giant_mole_spec.md,
  note: giant_mole.md.
- Giant Mole follow-ups (2026-10-26) ALL VERIFIED LIVE: private instance via
  dbrow.instance_giant_mole (25k fee charged, rejoin flow, copied regions
  6992/6993, boss+baby spawns); insect darkness timer (15t warn, 30t swarm,
  1 typeless dmg/tick, cleared by light or any exit — no leak after teleport
  or death); static + instance rope exits both land at the well (2999,3377);
  Wyson exchange (1 bird_nest_cheapseeds per claw/skin, 5/immaculate — nests
  verified as item 7413; holy moleys combine verified).
- Crazy Archaeologist (content/bosses/crazy-archaeologist) — VERIFIED live
  (2977,3702): ranged book projectile (spotanim.crazy_archaeologist_book,
  1259) every 3t at range, "Rain of knowledge!" overhead + debris specials
  fire, keep-range extension now actually runs via include(external(...)).
- Chaos Fanatic (content/bosses/chaos-fanatic) — VERIFIED live (2979,3846):
  magic projectile spotanim.chaoselemental_spotanim_confusion_travel (554)
  every 2t, max 21; disarm moves worn weapon to a free inv slot + "knocked
  your weapon" SPAM message (weapon=27275 -> inv slot 6 seen live), later
  disarms correctly no-op; explosion debris x3 Typeless (telegraph
  spotanim.chaoselemental_spotanim_madness_travel = 551 = RuneLite
  CHAOS_FANATIC_AOE). Killed me with god mode off — real damage.
  RISK RESOLVED: fabricated spotanim.chaos_fanatic_rift replaced; no
  fanatic-named spotanims exist in the rev-241 cache.
- Hunter bird snaring (content/skills/hunter) — VERIFIED live in Feldip
  Hills (2585,2884): lay consumes snare + spawns armed loc, real
  hunting_bird_* NPCs path onto the trap 1 tile/cycle, state locs
  armed -> trapping_<biome> -> full_<biome> / failing -> broken, Check
  gives bones+meat+feathers+snare back + exact XP (Crimson swift +34),
  Dismantle returns snare, Reset re-arms broken and caught traps, owner
  standing on the trap suppresses triggering, logout clears armed traps.
- Hunter box trapping (same module, generalized to TrapKind SNARE/BOX) —
  VERIFIED live in Feldip Hills (2507,2885) on Carnivorous chinchompas:
  armed hunting_boxtrap_empty, chin escape -> hunting_boxtrap_failed,
  Reset re-arms free, catch -> "Shaking box" full loc, Check returns the
  box trap + captured-animal item + exact XP (+265 carnivorous).
  dbtable.hunter_box_traps rows: ferret 27/115, jerboa 39/137 (tail-only
  loot — animal escapes), grey chin 53/198.4, red chin 63/265, black chin
  73/315. Directional trapping locs (n/s/e/w) pick the entry side. Snares
  and box traps share one limit; +1 box trap inside the wilderness.
  Letvek locs exist but no map spawn — skipped. 15 integration tests.
- Hunter deadfall (TrapKind.DEADFALL, no trap item) — VERIFIED live on the
  static hunting_deadfall_boulder locs in Feldip Hills (2579,2885): Set-trap
  needs knife + valid log (75% preserved), arms a 2x1 deadfall loc, kebbit
  triggers it, Check on the full Boulder pays bones + part + meat + exact XP
  (barb-tailed +134 at 1.0), and the tile reverts to the boulder. Own cap of
  2 traps. Rows: wild 23/102.4, barb-tailed 33/134.4, prickly 37/147.2,
  sabre 51/160, pyre fox 57/177.6. 22 integration tests.
- Hunter net traps (TrapKind.NET, rope+net on young trees) — VERIFIED live
  at Uzer (3406,3134): Set-trap on the static Young tree (8732) arms
  net_set (8731), orange salamanders trigger catching -> full (8734,
  Check/Reset), Check pays the wieldable salamander + rope + net + exact XP
  (+224 x2 at 1.0), tree restored. Prey is pinned by the tree loc;
  per-biome locs (net_set/catching/full/failing/failed). Tar bait
  (salamander_tar_*) usable on armed traps for +10% catch. Rows: swamp
  29/152, orange 47/224, red 59/272, black 67/319.2, tecu 79/344.
  27 integration tests.
- Hunter butterflies (Catch op on the 4 butterfly npcs) — VERIFIED live on
  Black warlocks in Feldip Hills (2534,2902): wielded butterfly net + empty
  jar -> jarred `butterfly_jar_warlock` + exact +54 XP at 1.0; Release is
  iop4 (onOpHeld4 — NOT onOpHeld1, that bug was caught live) and returns the
  empty jar + plays the spot anim; miss keeps the jar and teleports the npc
  a few tiles. Rows: ruby 15/24, glacialis 25/34, snowy 35/44, warlock
  45/54. 31 integration tests.
- Hunter implings (content/skills/hunter + content/drops/tables/items/
  ImplingJarDropTables.kt) — INTEGRATION-TESTED (43 tests), live verify
  pending (MCP bridge offline). Catch op1 on npc.ii_impling_type_1..11 +
  _maze variants + the 17 type_12 crystal gang; requirements+XP from the
  CURRENT wiki (OSRS rebalanced: baby needs 17 now — 17/22/28/36/42/50/58/
  65/74/80/83/89; Puro vs Gielinor XP e.g. baby 18/20, lucky 80/380).
  Barehanded = +10 levels; jar required when netted OR in Puro (maze npcs);
  barehanded-with-jar still jars the catch; barehanded-no-jar outside Puro
  pays loot directly. Catch chance = Ash formula: 12%->98% linear at lv1->99
  +8pp for barehanded/magic-net (injectable `implingCatchChance` for tests).
  anims: seq.hunting_impling_catch on player, seq.ii_impling_dodge on miss
  (NO teleport — implings don't flee per-catch), seq.ii_impling_caught on
  the npc. `Loot` (iop3/onOpHeld3) rolls RSDropTable per jarred obj — NEW:
  objs() binding + DropTableRegistry.forObj (api/drop-table[ -plugin]).
  9/10 jar return + clue tertiaries per table; gourmet pre-rolls grubby key
  1/500. `Destroy` (iop5) uses the real confirmDestroy dialog (params
  fallback text since jars carry no destroy_note params). Lucky jar loot is
  BLOCKED on Treasure Trails reward tables (table absent by design — Loot
  no-ops, jar kept). Dragon jar's MM2-gated dragon javelin tips roll
  unconditionally — MM2 varp doesn't exist yet (documented deviation).
  `ImplingSpawnManager` drives precursor lifecycle: ii_*_impling_precursor
  npcs (invisible, vislevel=0, 61 static spawns) manifest as a real impling
  (npc.transmog) after 100-300t invisible roam, stay ~3000t, revert; exact
  wiki tier pools (common 20/20/20/20/10/10, uncommon 10/10/20/37/20/2/1,
  rare /101, rare_maze /301, Prif crystal = uniform pick of the 17 gang).
  Catch a manifested impling -> precursor re-adds via Spawn event -> cycle
  restarts. NOTE for live verify: implings manifest ~1-3 min after spawn —
  tele to 2571,4305 (Puro maze) or stand near an overworld precursor and
  WAIT; they're invisible at first by design.
- Accounts cleaned up (2026-10-26): all ad-hoc test accounts deleted from
  main.accounts; kept Devexity + devin ai (login: devin ai / devinai).
  devin ai had stale xp_rate_in_hundreds=150 in character_progress — reset
  to 100. Use `devin ai` for all future live verification.
- Fletching (content/skills/fletching) — VERIFIED live: full make-x surface
  via pack dbtable; carve-all via subcomponent-0 fix in SkillingMenu,
  stringing, attach batches (28 client clamp), exact XP.
- SYSTEMIC DSL BUG found+fixed: a bare `external(...)` line inside an
  ability {} block compiles but is DISCARDED (external returns an Effect,
  not a statement). Correct idiom is `include(external("name"))`. Fixed at
  chaos-fanatic (disarm), scorpia (prayer drain), crazy-archaeologist
  (keep_range). Remaining `external(` call sites are all argument-position
  uses (ability("x", external(...))) which are fine.

## Gotchas

- ALWAYS run `gradlew run`/`jar` with `--no-configuration-cache` after adding
  a module — config cache silently drops new subprojects.
- Never `>NUL` in bash on Windows (creates literal file NUL).
- InvObj needs ItemServerType ctor (Int ctor blocked by type-safety check).
- BossPluginScript(deps): pass deps, do NOT redeclare `private val deps`.
- Spotanim/seq/loc symbol tables are strict — wrong prefix crashes startup.
- devMode=true -> all logins get ADMINISTRATOR; cmds ::tele ::item ::npc
  ::master ::mypos. RSProx modulus = .data/client.key content.
- LocRepository.del(loc) matches the EXACT entity (id+shape+angle). After
  locRepo.change(...) you must keep the NEW LocInfo (locRepo.add returns it)
  or deletes silently no-op and orphan the loc.
- Live test accounts: `fletchtest12` / `password` auto-registers fine;
  `type_chat send=false` fills login fields, ENTER submits.

## Next up

- Other wilderness bosses (deranged archaeologist, venenatis, callisto...).
- Powered-staff gap: only Tumeken's Shadow registered in WeaponRegistry —
  tridents/sceptres spam "fails to respond". Worn-slot gap: Rada's blessing,
  Ring of shadows unequippable.
- Test suite + spotlessCheck before pushing each module.

## Scorpia (2026-10-25) — VERIFIED LIVE

content/bosses/scorpia. Spawn ~3233,10341 (zone 0_50_161_33_37).
- FIXED crash: seq.scorpion_attack doesn't exist in rev-241 — real attack
  anim is seq.scorpion_update_attack_tail (6254). Seen live every 4t.
- Melee stab 0-16, attackRate 4.
- Prayer drain: include(external(PRAYER_DRAIN)) on the stab ability drains
  2 prayer points per attack while target has varbit.prayer_protectfrommelee
  (4118) — hit OR splash, per wiki. RE-VERIFIED live after discovering the
  original bare external() was silently discarded (earlier "verification"
  was natural drain — false positive): 99 -> 62 over ~12 swings with PFM on
  (god mode kept HP 99, so drains happened on splashes), then FLAT at 56
  for 33t with PFM off — correctly gated by the varbit.
- Guardians: forceWhen(HpBelow(0.5), summon, once=true) on the combat phase —
  fires on her next attack selection below half. VERIFIED live: exactly 2
  guardians (npcId 6617), curse projectiles (109) every 3t healing her,
  despawn when she dies, despawn ~25t after losing heal contact (>3 tiles),
  no re-summon on re-engage. Wiki says "next attack against her" — ours
  fires on her next attack tick; equivalent while she's engaged.
- Poison: poison(20, chance=1, outOf=4) at ABILITY level (not nested onHit —
  wiki: NPCs poison through protection prayers and on zero-damage hits;
  player poison needs a landed hit). 1/4 is an APPROXIMATION — no published
  Scorpia proc rate; matches the documented melee weapon-poison convention.
  Procs observed through god mode + Protect from Melee.
- Deterministic test: api/bosses .../BossEncounterForcedAbilityTest covers
  forceWhen+once+HpBelow selection (fires once below threshold, refires
  without once).
- Testing gotchas learned: `::god` exists (adminGodMode, wired into hit
  modifiers). Do NOT use ::maxhit — it skews threshold observation. Powered
  staves need WeaponRegistry entries — ONLY Tumeken's Shadow is registered
  (charge via `Charge` op with soul+chaos runes); Accursed sceptre/tridents
  just spam "Your staff fails to respond." Rada's blessing/Ring of shadows
  Equip clicks hit "Nothing interesting happens" — equip path gap, not
  wired to a worn slot. ::varbit takes SYMBOL names, not ids — numeric arg
  throws "Uncaught exception".

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
- Realm XP rates reset to 1:1 (2026-10-25, user request): baseXpRate=1.0,
  globalXpRate=1.0 — verified in the boot log's RealmConfig line.
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
