# Session state (updated 2026-10-24)

Autonomous content build, branch `feat/giant-mole` on fork
`github.com/Devexity-fivem/OpenRune-Server` (origin=fork, upstream=OpenRune).

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
