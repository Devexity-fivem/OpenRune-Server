# Dev-notes changelog

## 2026-10-26 — Equip verbs on any op: rings, blessings, iop4 items (`dev`)

- `ItemServerType.isEquipable` previously required `interfaceOptions[1]` to be
  exactly `Wield`/`Wear`, and only `opHeld2` had an equip fallback — so every
  item that wears on another op or verb printed "Nothing interesting happens":
  `iop1=Wear`/`Wield` (~411 items, e.g. Ring of shadows), `iop2=Equip` (~154,
  e.g. Rada's blessings), `iop4=Wear`/`Wield` (~53, e.g. Shayzien supply sets).
- `isEquipable` now checks `interfaceOptions.any { it in WEARABLE_OPS }`; new
  `hasWearableOp(op)` tests the *clicked* op. All held ops route through a
  shared `equipOrDefault` fallback — a non-wear op on an equipable item (e.g.
  the ring's `Teleport`) still falls to `dm_default` rather than equipping.
- Synthetic "equip this" call sites (equipment-stats side panel, bank-side
  Wear buttons in `BankInvScript`) now call `invEquip` directly instead of
  dispatching `opHeld2`, and print `HeldEquipResult.Fail` messages.
- Live-verified: Ring of shadows wears to the ring slot via iop1, Rada's
  blessing 1 to the quiver via `Equip`, ring Teleport prints the engine
  fallback, and a regular op2 `Wield` still works.
- Test: `ItemServerTypeTest` covers the verb set, per-op checks, and the
  wearpos requirement.

## 2026-10-26 — Powered staves: combat, charging, swaps (`dev`)

- `PoweredStaves.kt` registers every powered staff in the `WeaponMap` with the
  real formulas and visuals: seas trident `⌊M/3⌋-5`, swamp `+3`, Sanguinesti
  `+4`, warped sceptre `⌊(8M+96)/37⌋`, Thammaron's `⌊M/3⌋-8`, accursed
  `⌊M/3⌋-6`, bone staff `⌊M/3⌋+5` with a flat `+10` vs rats and 35 Magic xp
  on hit, Eye of Ayak = accursed formula at 3-tick speed, starter staff fixed
  8. Wilderness sceptres refuse to fire outside the Wilderness and burn 1000
  revenant ether as an activation deposit before usable charges count.
- `PoweredStavesCharging.kt`: Check/Charge/Uncharge ops on every charged and
  uncharged form, plus use-on charging from any recipe material. Seas trident
  treats `obj.tots` as an implicit full pool (firing converts it to
  `tots_charged` at 2499; charging `tots_charged` to max converts back to the
  tradeable `tots`). Uncharge confirms, converts to the uncharged variant and
  refunds exact remaining materials. Wilderness sceptres Swap between powered
  and autocast "(a)" forms preserving charges. Eye of Ayak keeps two mutually
  exclusive pools (`varobj.eye_of_ayak_tear_charges` /
  `varobj.eye_of_ayak_rune_charges`); each recipe is rejected while the other
  holds charges.
- HeldU dispatch pitfall found and documented by debug: `onOpHeldU(staff, mat)`
  loses when the player selects the material first, because the dispatch checks
  the *selected* item's single-arg default binding before trying the reversed
  pair — Zulrah's scales have a Herblore `onOpHeldU(item)` catch-all that
  swallowed scales→trident entirely. All powered-staff material bindings are
  now registered `(material, staff)` so the pair `Type` event wins the first
  combination. Verified live both directions on the toxic trident.
- `items.toml`: every charged-form powered staff now carries
  `charged_variant`/`uncharged_variant` params (`ObjChargeManager.addCharges`
  throws without `charged_variant` when converting a 0-charge obj — caused a
  live disconnect mid-charge). Eye of Ayak items and the deadman starter staff
  gained missing entries incl. `attack_range`.
- Live-verified: trident charge dialog + exact deductions (40 charges =
  40 scales/40 death/40 chaos/200 fire), Check counts, 2500→2499 conversion on
  cast, uncharge refunds, Sanguinesti (3 blood/charge, proj 1539), warped
  (2 chaos + 5 earth/charge, proj 2569), wilderness sceptre 1500-ether deposit
  semantics + (a) swap, dry-fire message.
- Tests: `PoweredStavesFormulaTest` (9 formula anchors vs wiki) and
  `PoweredStavesChargingTest` (10 integration tests driving
  `chargeStaff`/`uncharge`/`swap`/`chargeAyakPool` through
  `withProtectedAccess` + `resumeActiveCoroutine` dialog inputs).

## 2026-10-26 — Giant Mole complete: instance, insects, Look-inside, Wyson (`dev`)

- Private instance: `GiantMoleInstance` (InstanceScript) +
  `dbrow.instance_giant_mole` (25,000 gp fee, 1-player, copies lair regions
  6992/6993, Giant Mole + all baby-mole spawns). Fight-privately on
  `loc.mole_hill_spade` → menu → fee → mapped space; rejoin shows
  "Rejoining your instance...". Framework binds op1 on the row's
  enter/exit locs — rope exits route through `onExitObject` which clears
  darkness then `defaultLeaveFlow()` (in-session) or telejumps to the well
  (static-lair climbers). No duplicate op1 bindings.
- Darkness/insects (`GiantMoleInsects`, `WorldQueueList` 1-tick): entering the
  lair unlit sets `varbit.darkness_level=3` + hidden minimap + dark overlay;
  15 dark ticks → skitter warning, 30 → "Tiny biting insects swarm all over
  you!" + 1 typeless dmg/tick. Light source or leaving the lair clears it —
  verified no leak after teleport-out and after death.
- Look-inside fix: `loc.mole_hill` (12202) stores the option at **op slot 5**
  — `onOpLoc1`/`onOpLoc2` never dispatch; found via temporary
  `LocDefaultEvents.Op*` catch-alls that printed the real op index.
  `onOpLoc5("loc.mole_hill")` now reports the lair population.
- Wyson (`FaladorTownsfolkScript`): Talk-to / using a mole part on him offers
  the exchange when any mole part is carried — 1 `bird_nest_cheapseeds` per
  claw/skin (incl. certed), 5 per immaculate skin; dialogue + item movement
  verified live. Also combines holy sandals + mole slippers → holy moleys.
- Exits verified live: static rope 12230 (1752,5136) → (2999,3377) well;
  instance-copied rope → same well.

## 2026-10-25 — Chaos Fanatic + Crazy Archaeologist verified; bare-external DSL fix (`dev`)

- Found a systemic DSL pitfall: a bare `external(...)` statement inside an
  `ability {}` block evaluates to an `Effect` and is silently discarded —
  the extension never runs. Correct idiom: `include(external("name"))`.
  Affected sites fixed: chaos-fanatic disarm, scorpia prayer drain,
  crazy-archaeologist keep-range. Argument-position uses
  (`ability("x", external(...))`) are unaffected.
- Chaos Fanatic — replaced fabricated `spotanim.chaos_fanatic_rift` (does
  not exist in the rev-241 cache; crashed `PathingEntity.launch`) with
  verified symbols: normal magic projectile =
  `chaoselemental_spotanim_confusion_travel` (554), explosion telegraph =
  `chaoselemental_spotanim_madness_travel` (551 = RuneLite
  `CHAOS_FANATIC_AOE`). Verified live: 2-tick magic cadence, disarm moved
  equipped Tumeken's Shadow (27275) into a free inventory slot with the
  "knocked your weapon out of your hands" message and correctly no-ops once
  disarmed, explosion debris fires (real typeless damage confirmed — killed
  the test account with god mode off).
- Crazy Archaeologist — verified live: ranged book projectile (1259) on
  3-tick cadence at range, "Rain of knowledge!" + debris specials selected
  and executed, keep-range extension now dispatches.
- Scorpia prayer drain re-verified after the external() fix — earlier pass
  was a false positive (natural drain). Fresh evidence: 99 -> 62 prayer over
  ~12 swings with Protect from Melee up (god mode forced splashes, so drain
  applies through protection per wiki), then flat for 33 ticks with PFM off.

## 2026-10-25 — Scorpia verified + XP rates to 1x (`dev`)

- Realm XP rates reset to true 1x (`baseXpRate=1.0`, `globalXpRate=1.0`) at
  the user's request so server progression stays 1:1 with OSRS.
- Scorpia (`content/bosses/scorpia`) — fixed and verified live:
  - Attack anim: `seq.scorpion_update_attack_tail` (6254) replaces the
    nonexistent `seq.scorpion_attack`, which would have crashed on attack.
  - New: melee attacks drain 2 prayer points per swing whenever the target
    has Protect from Melee on — including splashes/protected hits (wiki).
  - New: guardians (npc.scorpia_guardian) summon once at <=99 HP — exactly
    two, via `forceWhen(HpBelow(0.5), …, once = true)` so the next attack
    below half summons them. Each guardian heals her 8 HP every 3 ticks
    within 3 tiles (spotanim.curse_travel projectile), and despawns after
    25 ticks without a heal or when she dies. Verified live: summon fired
    once, two guardians, heal projectiles on cadence, no re-summon on
    re-engage, out-of-range despawn observed.
  - Fixed poison rate: `poison(20)` defaulted to 1/1 (every attack). Now
    `poison(20, chance = 1, outOf = 4)` at ability level — NPCs poison on
    misses and through protection prayers (wiki), unlike player poison.
    1/4 is an approximation; no published Scorpia proc rate exists.
  - Added `BossEncounterForcedAbilityTest` covering forceWhen + once +
    HpBelow selection semantics deterministically.
- Tooling notes: `::god` toggles invincibility (already existed); powered
  staves need WeaponRegistry entries (only Tumeken's Shadow works, charge
  with soul+chaos runes); Rada's blessing/Ring of shadows can't be worn
  yet ("Nothing interesting happens" on equip — gap); `::varbit` needs
  symbol names.

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
