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
