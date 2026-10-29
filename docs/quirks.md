## TODO
- Explain types' computeIdentityHash.
- Nested EnumTypes in EnumProvider. Due to type-erasure, it is extremely tedious
to even begin to try and make the inner EnumTypes type-safe (checked during
boot-up). For now, we will just trust that the developers are careful (BAD!),
but solely because the other choice is to load them via reflection and try to
do comparisons that way, which may have a significant impact in future
scalability when it comes to boot-up performance.
- InvTransactions uses a cached singleton. It would become annoying for coders
to have to constantly get access to the required "lookup" maps in order to
perform an inv transaction. We willfully allow this antipattern for ease-of-use.
- Private type reference subclasses not allowed. Must be internal or public.
- .local in symbols directory being loaded by default and overrides any possible
colliding symbols from the root symbols.
- All game-related randoms should be generated through `GameRandom` functions.
- The great teleport/telejump tragedy of 24' where we had to choose between
anti-patterns that would blow a huge hit to testability or jeopardizing
user-friendliness by requiring the what-should-be simple teleport functions to
take a [CollisionFlagMap] instance arg. We went with the latter. The biggest
weakness of this approach is increasing the barrier-to-entry when a developer
wants to perform simple tasks. Teleporting _should_ be a simple task, but alas,
if we want to emulate core mechanics, teleporting needs to _instantly_ update
the collision flag map. We weighed our options from using singletons of the
collision map, to passing the collision map to each player/npc, to having a pub
sub system strictly for map changes - though at that point it'd be practically
the same thing as passing the game map pointer to the entities in question.
The solution of taking in the collision flag map as an arg is something that
will, *eventually*, solve itself by way of adding API-friendly wrapper classes
for systems such as "fade out" teleports, teleporting after interacting with a
loc, and so on. Though admittedly, it's a cumbersome ask for beginners to have
to deal with until those systems are in place.
- Decision to not make ClientScript arguments type-safe. Just not worth it,
especially if you consider that the arguments would have to be converted to
their primitive form dynamically during runtime.
- `delay` functions, AKA `p_delay`, do _not_ follow the "official" convention.
This means that calling `delay(1)` will delay the player by 1 tick, not 2.
`delay(0)` will not delay the player at all.
- Mention the purpose of each api submodule (in a README within api module).
- Inconsistency in OpLocHandler debug message vs. LocUInteractions debug
message. This comes down to the packet handler not having the detailed view of
the multiloc based on the player's vars. Meanwhile, OpLocU (as well as OpNpcU)
are handled at the "content-level" due to how they were changed from being
individual packets. The content-level scripts are exposed to the "visual" type
for these entities.
- Tests that involve obj transactions ([org.rsmod.objtx]) are not thread-safe
and the test class should be marked with `@Execution(ExecutionMode.SAME_THREAD)`
- `LocRegistry.findAll(zoneKey)` performed within a region's zone will not
return locs that did not originally belong to the copied normal zone. This is
intentional by design to prevent inconsistencies and unexpected behavior. This
scenario is rare but can occur under specific conditions - when a loc's angle,
dimensions, and the requested region rotation cause it to shift into an
unexpected region zone. This is an extremely niche case.
- Similarly, `findAll` performed in a region will skip any locs that have
shifted into a different zone.
- Any interaction on locs in region areas that are outside the region's working
area are discarded/ignored.
- If multiple zones in a region are copies of the same zone,
`Region.normal[...]` will resolve to the last one of these registered zones.
Since regions register zones column by column, from bottom to top, this means
the zone closest to the north-east boundary takes precedence.
- Zone update processing occurs in a fixed order, from the bottom-left build
area zone to the top-right zone. This means all `ZoneProt` updates are sent in
a predictable order, rather than in the order the updates occurred. For
example, if `zone(0, 1, 0)` receives a `soundarea` update first and
`zone(0, 0, 0)` receives one afterward, the latter will still be sent first due
to fixed iteration order; whereas the official game would send `zone(0, 1, 0)`
first since it was the earliest update.

## Tutorial Island

Known deviations from live OSRS:

- Script-granted skill XP (fishing, woodcutting, firemaking, cooking, mining,
  smelting) is capped at base level 3 via `tutorialXp`, matching the official
  level-3 cap. **Combat XP is not capped**: rats/chickens award normal XP past
  level 3 because the engine has no cancellable XP event to gate it.
- Tutorial doors and gates have no open variants or `next_loc_stage` params in
  the rev-241 cache (all read `content.shooting_star` with placeholder data).
  `TutorialIslandScript` swings the same loc type to a translated+rotated tile
  for 12 ticks instead of morphing to an open loc type. Double gates pair their
  halves by symbol (`DOOR_PAIRS`) since the cache carries no pairing metadata.
- Clicking a door mid-swing re-translates the temporary loc; it self-corrects
  when the timed locs despawn and the original respawns.
- The Ironman tutor offers standard/hardcore/ultimate and free re-selection
  while on the island, matching OSRS. Group Ironman is not offered; the mode is
  not supported.
- Stage progression is monotonic in `varp.tutorial`; item grants are idempotent
  (`TutorialInventory.supply` tops up missing counts across inventory+worn,
  `exchange` rolls back atomically) so interrupted dialogues cannot duplicate
  items and failed recipes cannot eat ingredients.

## Smoke devils

Known deviations from live OSRS:

- `SmokeDevilProtection` makes unmasked hits from `npc.smoke_devil` and
  `npc.superior_smoke_devil` queue an extra typeless hit (1-8) and drain
  combat stats. OSRS gates smoke devils behind **93 Slayer and an active
  Slayer task** — no Slayer task system exists yet, so that gate and the
  Slayer level check are not enforced.
- Exact choke damage/drain cadence is approximated; the unmasked outcome
  (rapid extra damage + stat drain) matches the documented mechanic.
- A second stat drain (~1-3 per smoke devil hit, across stats outside this
  plugin's drained set, e.g. Agility) was observed live on `devin ai` and is
  **not** produced by `SmokeDevilProtection`; its source is unattributed and
  needs investigation.

## Obor and Bryophyta

- Obor's ranged attack uses `projanim.godwars_bandos_ranged` (a thrown
  boulder) as the closest cache-verified projectile; his knockback uses a
  player-flinch anim (`seq.agilityarena_player_spikedback`) since no
  dedicated knockback sequence resolves in this revision.
- Bryophyta's immunity while growthlings live is implemented as an
  incoming-damage cap of 0 via `incoming { rule ... cap(0) }`, so hits show
  a 0 hit-splat rather than refusing the swing outright.
- Growthling damage is gated on the attacker wielding an axe or
  `obj.fairy_enchanted_secateurs` (magic secateurs), matching OSRS; the
  bronze-axe-only edge cases and poison-weapon interaction are not
  modelled.
- Bryophyta's summon cooldown uses a dedicated `varn.bryophyta_summon_cd`
  (20 ticks between re-summons once growthlings die), an approximation of
  the ~20-tick OSRS cooldown.

## Sarachnis

- `forceWhen(HpBelow(...))` did not fire her spawn summons mid-fight in
  live testing (no exceptions logged); the encounter instead uses
  `triggers { on(HpBelow) runs ... }`, which evaluates every combat tick.
  Whether `forceWhen` is broken for mid-fight conditions needs checking —
  Scorpia's guardian summon uses the same pattern and may have the same
  latent issue.
- Spawn-of-Sarachnis hits on the player should heal Sarachnis; that
  lifesteal link is not modelled.
- The ranged/web attacks reuse `spotanim.ice_burst_impact` as the closest
  cache-verified projectile graphic; the true web projectile has no named
  symbol in this revision.
- Web bind is a flat 10-tick freeze approximation.

## Kalphite Queen

- OSRS gives each form true protection prayers (ranged+magic on the grounded
  form, melee on the flying form). We model the reduction as
  `IncomingAction.ScalePercent(10)` — protected styles still chip ~10% damage
  instead of splashing outright. Verified live: magic hits on the grounded
  form landed for single digits while melee cut through normally.
- Her two-form mechanic is implemented as a hp floor (`NpcHitpointsFloorHook`
  keeps the grounded form at 1 hp) plus a `HpExact(1)` trigger that plays the
  emerge sequence and transitions to the flying phase — rather than OSRS's
  actual death-and-re-emerge, which the death pipeline cannot express.
- The magic lightning does not chain to nearby players and the spine volley
  does not splash nearby players (Kronos-184 implements both; needs
  multi-target `TargetExpr` support that the boss DSL lacks today).
- Worker rousing fires on a 6-tick 1-in-3 timer and only sets already-spawned
  wandering workers onto the queen's target; OSRS rolls per attack.
- Emerge anim/gfx reuse `seq.kalphite_queen_glow` + `spotanim.kalphite_queen_glow`
  (the dedicated emerge/transform sequence has no named symbol in this cache).

## Corporeal Beast

- The dark energy core drains 5-13 typeless every 2 ticks from players in
  its 3x3 and feeds half to the beast, and hops onto a player (northernmost,
  then easternmost) on a 4-tick cooldown when nobody is adjacent — matching
  the wiki cadence. Remaining gaps vs OSRS: the core is not hidden during
  the hop and stays attackable mid-jump; a poisoned core doesn't slow its
  drain (emerald-bolts-e interaction unmodelled); the beast doesn't retarget
  to a strong off-target attacker (hit ≥15, 1/5 in Kronos).
- The powered magic projectile always rolls 30-65; OSRS reduces it to 10-35
  against Protect from Magic — the prayer-dependent max isn't modelled.
- The `npc.corp_beast` type's cached stats probe reports an unrelated "Woman"
  record in some tools paths, though the live spawn is the correct level-785
  beast — the RSCM/probe metadata discrepancy is unresolved.

## Level-up popups

`LevelUpScript` sets `varbit.busy` while `levelup_display` is open and clears
it through the interface close hook. Keep cleanup tied to the interface,
not the suspended dialogue: walking or a hit can cancel that dialogue.
Disabled popups do not set the flag.

## Pest Control reward exchange

Void Knights offer the native reward exchange through Exchange or the third Talk-to option.
Commendations persist in `varp.pest_control_points`; `varp.if1` is shared interface display
scratch and is refreshed whenever the reward shop opens.

Dodgy Squire refunds regular and elite Void equipment, including locked versions without
returning parchment. Talk-to selects the highest eligible inventory slot; item use selects
that exact item. The quoted refund is limited by the 4,000-point cap, including zero-point
offers. Acceptance consumes one item only if the original source and quoted payment remain
valid. Refunds do not update the shop display scratch until the shop next opens.

Normal rounds do not yet award commendations; landers, round gameplay, elite upgrades and
Void seal combat are outside this implementation. Capped-offer and locked-item packet parity
remain unverified against live.
Herb quantities use independent uniform draws within the documented inclusive ranges. Their
exact probability law, herb/mineral space-refusal packets and bulk XP packet/text parity remain
unverified.

## Types
```
engine
    └─ game
        └─ type
```
#### <u>_Internal `id` field_</u>
```kotlin
class ItemServerType(internal var id: Int?)
```
There was a conscious decision to allow for configuration types' "id" to be
mutable, and nullable.
#### What problem does this solve?
- Type-safety:
  - For years, frameworks used magic numbers for config types, whether it was
    for worn obj conditions, or to define a rewards table. This comes with
    obvious flaws (see [magic-numbers]). With time people moved onto static
    classes that held each individual id with a reasonably-named field. Again,
    this still comes with its problems. For example, you are still able to
    compare an obj to a constant that's not an obj:
    `if (player.righthand == Spotanims.iron_arrow_projectile)`.
- Consistency between references and configs in cache:
  - Another major flaw with the traditional approach, is that there was never a
    feasible way to make sure all references actually exist in the game cache.
    As the developer, you just had to trust that these constants were up-to-date
    and their naming stayed 100% true to what they originally meant to
    represent.
- Creating custom config types:
  - TODO:
#### What are the pros and cons?
- Cons
  - Can be abused and the `id` may not be accurate after runtime if a developer
    sees fit to modify it.
  - TODO:
- Pros
  - TODO:

## Player timer phases

Soft timers normally run before interactions. `softTimer(..., afterInteractions = true)`
opts a timer into the pass immediately after player interactions. Foundry work uses this
phase so a tool re-click can affect the work consumed on that same tick. Rescheduling
moves a timer between phases; clearing it removes either registration.

Global effects tied to player work use `StartCycle`, before the map clock advances,
so their animations and sounds share the player's work tick.

## Foundry item safety

`PlayerDeathPreDropHook` runs before carried items are captured for death drops,
including administrator deaths that bypass drops. Foundry stores the preform here,
before a grave or ground stack can claim it. Equipment replacements store displaced
preforms after the equipment transaction commits. Teleport validation does not
store a preform; a successful coordinate change does.

Sword hand-in and ore-pack conversion use all-or-nothing inventory transactions.
Failed payment leaves the sword, commission and reward state unchanged. Coins go
to the bank first, then inventory if necessary; Ultimate Ironmen use inventory only.

[magic-numbers]: https://en.wikipedia.org/wiki/Magic_number_(programming)
