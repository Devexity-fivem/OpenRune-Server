package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.spawnOwnedNpc
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.bosses.spec.VarExpr
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onPlayerHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.LineValidator

/**
 * Nex (npc.nex) in her instanced Ancient Prison chamber. Five HP-gated phases —
 * Smoke, Shadow, Blood, Ice and Zaros — each ended by slaying the corner mage
 * (Fumus, Umbra, Cruor, Glacies) she calls at the 80/60/40/20% thresholds. The
 * mages are invulnerable until called. Deviations: the ice prison shatters on
 * its timer rather than needing to be attacked down; the virus does not spread
 * beyond the carrier's tile and Nex skips her spawn/wake cinematic.
 */
class Nex @Inject constructor(deps: BossDeps, private val npcList: NpcList) :
    BossPluginScript(deps) {

    private val lineValidator = LineValidator(deps.collision)

    private val nexId by lazy { NEX.asRSCM(RSCMType.NPC) }
    private val nexIds by lazy { NEX_FORMS.map { it.asRSCM(RSCMType.NPC) }.toSet() }
    private val mageIds by lazy { MAGE_TYPES.map { it.asRSCM(RSCMType.NPC) }.toSet() }
    private val reaverType by lazy { ServerCacheManager.getNpc(REAVER.asRSCM(RSCMType.NPC)) }

    private val virusUntil = mutableMapOf<Player, Int>()
    private val sacrificed = mutableSetOf<Player>()

    private val mageSpec =
        boss(FUMUS, UMBRA, CRUOR, GLACIES) {
            stats(attackRate = MAGE_ATTACK_RATE)

            fun barrage(name: String, proj: String, impact: String?) =
                ability(name) {
                    anim(MAGE_ANIM)
                    projectile(
                        spotanim = proj,
                        impact = impact,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                (0..MAGE_MAX_HIT).roll(),
                                HitType.Magic,
                            ),
                    )
                }

            val fumus = barrage("fumus_barrage", SMOKE_PROJ, SMOKE_IMPACT)
            val umbra = barrage("umbra_barrage", SHADOW_PROJ, null)
            val cruor = barrage("cruor_barrage", BLOOD_PROJ, BLOOD_IMPACT)
            val glacies = barrage("glacies_barrage", ICE_PROJ, ICE_IMPACT)

            incoming { rule(Condition.VarnIn(VARN_CALLED, 0..0)) { scalePercent(0) } }

            phase("cast", lockMovement = true) {
                attackRate = MAGE_ATTACK_RATE
                weightedSelectorRandom {
                    +random(fumus, weight = 1, requires = isMage(FUMUS))
                    +random(umbra, weight = 1, requires = isMage(UMBRA))
                    +random(cruor, weight = 1, requires = isMage(CRUOR))
                    +random(glacies, weight = 1, requires = isMage(GLACIES))
                }
            }
        }

    override val spec =
        boss(NEX, NEX_SOULSPLIT, NEX_DEFLECT) {
            stats(attackRate = ATTACK_RATE)

            // Her standard melee always chains up to three swings while the
            // target stays in melee range.
            val melee =
                ability("melee") {
                    anim("seq.nex_attack")
                    include(
                        repeat(
                            MELEE_SWINGS,
                            gap = 1,
                            whenever(WithinMeleeRange, MELEE_HIT),
                        )
                    )
                }

            val meleeZaros =
                ability("melee_zaros") {
                    anim("seq.nex_attack")
                    include(
                        repeat(
                            MELEE_SWINGS,
                            gap = 1,
                            whenever(WithinMeleeRange, MELEE_HIT_ZAROS),
                        )
                    )
                }

            val meleeSoulsplit =
                ability("melee_soulsplit") {
                    anim("seq.nex_attack")
                    include(
                        repeat(
                            MELEE_SWINGS,
                            gap = 1,
                            whenever(WithinMeleeRange, MELEE_HIT_SOULSPLIT),
                        )
                    )
                }

            // Smoke
            val smokeRush =
                ability("smoke_rush") {
                    anim("seq.nex_alternate_cast_attack")
                    projectile(
                        spotanim = SMOKE_PROJ,
                        impact = SMOKE_IMPACT,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                (0..AUTO_MAX).roll(),
                                HitType.Magic,
                                penetration = MAGIC_PRAYER_THROUGH,
                                onHit = Effect.Poison(POISON_DAMAGE, chance = 1, outOf = 4),
                            ),
                    )
                }
            val choke =
                ability("choke") {
                    anim("seq.nex_cast_attack")
                    say("Let the virus flow through you!")
                    setVarn(VARN_CHOKED, 1)
                    include(external(EXT_VIRUS))
                }
            val drag =
                ability("drag") {
                    say("There is... NO ESCAPE!")
                    include(external(EXT_DRAG))
                }
            val dash =
                ability("dash") {
                    anim("seq.nex_dash_attack")
                    say("NO ESCAPE!")
                    include(external(EXT_DASH))
                }

            // Shadow
            val shadowShot =
                ability("shadow_shot") {
                    anim("seq.nex_cast_attack")
                    projectile(
                        spotanim = SHADOW_PROJ,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                DamageExpr.Custom { npc, target ->
                                    // Shadow shots hit harder the closer the
                                    // target stands: ~30 at 10+ tiles, up to
                                    // 60 point blank.
                                    val dist =
                                        npc.coords
                                            .chebyshevDistance(target.coords)
                                            .coerceIn(1, 10)
                                    val cap =
                                        SHADOW_BASE +
                                            (SHADOW_MAX - SHADOW_BASE) * (10 - dist) / 9
                                    deps.random.of(cap + 1)
                                },
                                HitType.Ranged,
                                penetration = RANGED_PRAYER_THROUGH,
                            ),
                    )
                }
            val shadowSmash =
                ability("shadow_smash") {
                    anim("seq.nex_smash_attack")
                    say("Fear the shadow!")
                    include(external(EXT_SHADOW_SMASH))
                }
            val embraceDarkness =
                ability("embrace_darkness") {
                    say("Embrace darkness!")
                    spotanim("spotanim.nex_turmoil")
                    setVarn(VARN_DARK, Now + DARK_TICKS)
                }

            // Blood
            val bloodBarrage =
                ability("blood_barrage") {
                    anim("seq.nex_cast_attack")
                    projectile(
                        spotanim = BLOOD_PROJ,
                        impact = BLOOD_IMPACT,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                (0..AUTO_MAX).roll(),
                                HitType.Magic,
                                penetration = BLOOD_PRAYER_THROUGH,
                                lifesteal = BLOOD_LEECH,
                            ),
                    )
                }
            val bloodSiphon =
                ability("blood_siphon") {
                    anim("seq.nex_blood_siphon")
                    say("A siphon will solve this!")
                    spotanim("spotanim.nex_blood_siphon")
                    setVarn(VARN_SIPHON, Now + SIPHON_TICKS)
                    include(external(EXT_SIPHON_REAVERS))
                    // Surviving reavers die at the window's end and heal Nex
                    // for their remaining hitpoints.
                    include(after(SIPHON_TICKS + 1, external(EXT_SIPHON_END)))
                    // She kneels for the whole window and stops attacking.
                    wait(SIPHON_TICKS)
                }
            val bloodSacrifice =
                ability("blood_sacrifice") {
                    say("I demand a blood sacrifice!")
                    include(external(EXT_SACRIFICE))
                }
            // Blood specials fire on a cadence and strictly alternate, like
            // OSRS: ~8-10 attacks, then siphon or sacrifice, never both in a row.
            val bloodSpecial =
                ability("blood_special") {
                    addVarn(VARN_BLOOD_SPEC, 1)
                    include(
                        Effect.Switch(
                            VARN_BLOOD_SPEC,
                            mapOf(
                                BLOOD_SPEC_SIPHON to Effect.Run(bloodSiphon.name),
                                BLOOD_SPEC_SACRIFICE to
                                    sequence(
                                        Effect.SetVarn(
                                            VARN_BLOOD_SPEC,
                                            VarExpr.Const(0),
                                        ),
                                        Effect.Run(bloodSacrifice.name),
                                    ),
                            ),
                        )
                    )
                }

            // Ice
            val iceBarrage =
                ability("ice_barrage") {
                    anim("seq.nex_cast_attack")
                    projectile(
                        spotanim = ICE_PROJ,
                        impact = ICE_IMPACT,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                (0..AUTO_MAX).roll(),
                                HitType.Magic,
                                penetration = MAGIC_PRAYER_THROUGH,
                                onHit = Effect.Freeze(ICE_FREEZE_TICKS, chance = 1, outOf = 3),
                            ),
                    )
                }
            val containment =
                ability("containment") {
                    anim("seq.nex_smash_attack")
                    say("Contain this!")
                    disablePrayers(overheadsOnly = true)
                    tileAoE(
                        tiles = { npc, _ -> square(npc.coords, CONTAINMENT_RADIUS) },
                        damage = (0..CONTAINMENT_MAX).roll(),
                        type = HitType.Ranged,
                    )
                }
            val icePrison =
                ability("ice_prison") {
                    anim("seq.nex_cast_attack")
                    say("Die now, in a prison of ice!")
                    projectile(spotanim = ICE_PRISON_PROJ)
                    freeze(PRISON_FREEZE_TICKS)
                    include(external(EXT_ICE_PRISON))
                }
            val iceSpecial =
                ability("ice_special") {
                    addVarn(VARN_ICE_SPEC, 1)
                    include(
                        Effect.Switch(
                            VARN_ICE_SPEC,
                            mapOf(
                                ICE_SPEC_CONTAIN to Effect.Run(containment.name),
                                ICE_SPEC_PRISON to
                                    sequence(
                                        Effect.SetVarn(VARN_ICE_SPEC, VarExpr.Const(0)),
                                        Effect.Run(icePrison.name),
                                    ),
                            ),
                        )
                    )
                }

            // Zaros
            val zarosMagic =
                ability("zaros_magic") {
                    anim("seq.nex_alternate_cast_attack")
                    projectile(
                        spotanim = FINALE_PROJ,
                        impact = FINALE_IMPACT,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                (0..AUTO_MAX).roll(),
                                HitType.Magic,
                                penetration = MAGIC_PRAYER_THROUGH,
                            ),
                    )
                }
            val zarosMagicSoulsplit =
                ability("zaros_magic_soulsplit") {
                    anim("seq.nex_alternate_cast_attack")
                    projectile(
                        spotanim = FINALE_PROJ,
                        impact = FINALE_IMPACT,
                        hit =
                            Effect.Hit(
                                TargetExpr.CurrentTarget,
                                (0..AUTO_MAX).roll(),
                                HitType.Magic,
                                penetration = MAGIC_PRAYER_THROUGH,
                                lifesteal = SOULSPLIT_LEECH,
                            ),
                    )
                }
            // OSRS cycles Soul Split -> Deflect Melee -> no curse on repeat.
            val curseCycle =
                ability("curse_cycle") {
                    addVarn(VARN_CURSE, 1)
                    include(
                        Effect.Switch(
                            VARN_CURSE,
                            mapOf(
                                CURSE_SOULSPLIT to
                                    Effect.Transmog(NEX_SOULSPLIT, Int.MAX_VALUE),
                                CURSE_DEFLECT to Effect.Transmog(NEX_DEFLECT, Int.MAX_VALUE),
                                CURSE_NONE to
                                    sequence(
                                        Effect.SetVarn(VARN_CURSE, VarExpr.Const(0)),
                                        Effect.Transmog(NEX, Int.MAX_VALUE),
                                    ),
                            ),
                        )
                    )
                }
            val zarosEntry =
                ability("zaros_entry") {
                    say("Now, the power of Zaros!")
                    spotanim("spotanim.nex_turmoil")
                    setVarn(VARN_CURSE, CURSE_SOULSPLIT)
                }
            val deflectReflect =
                ability("deflect_reflect") {
                    hit {
                        damage(0..DEFLECT_MAX).roll()
                        type(HitType.Typeless)
                        hazard()
                    }
                }

            // Phase transitions — fired once the called mage dies.
            val toShadow = ability("to_shadow") { transitionTo("shadow") }
            val toBlood = ability("to_blood") { transitionTo("blood") }
            val toIce = ability("to_ice") { transitionTo("ice") }
            val toZaros = ability("to_zaros") { transitionTo("zaros") }

            // Mage calls at each HP threshold; each fires once per encounter.
            val callFumus =
                ability("call_fumus") {
                    say("Fill my soul with smoke!")
                    include(external(EXT_CALL_MAGE, FUMUS))
                }
            val callUmbra =
                ability("call_umbra") {
                    say("Darken my shadow!")
                    include(external(EXT_CALL_MAGE, UMBRA))
                }
            val callCruor =
                ability("call_cruor") {
                    say("Flood my lungs with blood!")
                    include(external(EXT_CALL_MAGE, CRUOR))
                }
            val callGlacies =
                ability("call_glacies") {
                    say("Infuse me with the power of ice!")
                    include(external(EXT_CALL_MAGE, GLACIES))
                }

            incoming {
                // Invulnerable while her called mage lives.
                rule(calledMageAlive()) { scalePercent(0) }
                // Deflect Melee halves (and reflects) melee during the deflect window.
                rule(
                    Condition.InPhase(PHASE_ZAROS) and
                        Condition.VarnIn(VARN_CURSE, CURSE_DEFLECT..CURSE_DEFLECT) and
                        Condition.HitStyle(HitType.Melee)
                ) {
                    scalePercent(0)
                    run(deflectReflect)
                }
            }

            triggers {
                on(Condition.HpBelow(HP_FUMUS)) runs Effect.Run(callFumus.name)
                on(Condition.HpBelow(HP_UMBRA)) runs Effect.Run(callUmbra.name)
                on(Condition.HpBelow(HP_CRUOR)) runs Effect.Run(callCruor.name)
                on(Condition.HpBelow(HP_GLACIES)) runs Effect.Run(callGlacies.name)
            }

            // Recurring windows.
            every(
                DARK_DAMAGE_PERIOD,
                whenever(
                    timerActive(VARN_DARK),
                    Effect.OnEach(
                        TargetExpr.AllInRadius(DARK_RADIUS, TargetExpr.Self),
                        Effect.Hit(
                            TargetExpr.CurrentTarget,
                            (0..DARK_MAX).roll(),
                            HitType.Typeless,
                            hazard = true,
                        ),
                    ),
                ),
            )

            phase("smoke") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(smokeRush, weight = 10)
                    +random(melee, weight = 5, requires = WithinMeleeRange)
                    +random(choke, weight = 2)
                    +random(drag, weight = 1)
                    +random(dash, weight = 1)
                }
                forceWhen(
                    Condition.PhaseTicksAtLeast(CHOKE_FORCE_TICKS) and
                        Condition.VarnIn(VARN_CHOKED, 0..0),
                    choke,
                    once = true,
                )
                forceWhen(mageDead(FUMUS), toShadow, once = true)
            }

            phase("shadow") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(shadowShot, weight = 11)
                    +random(melee, weight = 5, requires = WithinMeleeRange)
                    +random(shadowSmash, weight = 2)
                    +random(embraceDarkness, weight = 1)
                }
                forceWhen(mageDead(UMBRA), toBlood, once = true)
            }

            phase("blood") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(bloodBarrage, weight = 11)
                    +random(melee, weight = 5, requires = WithinMeleeRange)
                }
                forceEveryAttacks(BLOOD_SPECIAL_MIN, BLOOD_SPECIAL_MAX, bloodSpecial)
                forceWhen(mageDead(CRUOR), toIce, once = true)
            }

            phase("ice") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(iceBarrage, weight = 11)
                    +random(melee, weight = 5, requires = WithinMeleeRange)
                }
                forceEveryAttacks(ICE_SPECIAL_MIN, ICE_SPECIAL_MAX, iceSpecial)
                forceWhen(mageDead(GLACIES), toZaros, once = true)
            }

            phase("zaros", transmog = NEX_SOULSPLIT) {
                entry = zarosEntry.name
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(zarosMagicSoulsplit, weight = 7, requires = cursing(CURSE_SOULSPLIT))
                    +random(zarosMagic, weight = 7, requires = Condition.Not(cursing(CURSE_SOULSPLIT)))
                    +random(
                        meleeSoulsplit,
                        weight = 5,
                        requires = WithinMeleeRange and cursing(CURSE_SOULSPLIT),
                    )
                    +random(
                        meleeZaros,
                        weight = 5,
                        requires = WithinMeleeRange and Condition.Not(cursing(CURSE_SOULSPLIT)),
                    )
                }
                forceEveryAttacks(CURSE_PERIOD, CURSE_PERIOD, curseCycle)
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onLethal = ::onNexLethal,
            onModifyHit = { onNexIncoming() },
        )
        BossCombat.register(this, mageSpec, deps)

        deps.extensionRegistry.register(EXT_CALL_MAGE) { ctx ->
            (ctx.params as? String)?.let { callMage(ctx.npc, ctx.target, it) }
        }
        deps.extensionRegistry.register(EXT_VIRUS) { ctx -> startVirus(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_DRAG) { ctx -> dragTarget(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_DASH) { ctx -> dash(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_SHADOW_SMASH) { ctx -> shadowSmash(ctx.npc) }
        deps.extensionRegistry.register(EXT_SACRIFICE) { ctx -> sacrifice(ctx.npc) }
        deps.extensionRegistry.register(EXT_ICE_PRISON) { ctx -> icePrison(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_SIPHON_REAVERS) { ctx ->
            summonReavers(ctx.npc, ctx.target)
        }
        deps.extensionRegistry.register(EXT_SIPHON_END) { ctx -> siphonEnd(ctx.npc) }

        // Reavers heal Nex for their damage dealt; attackers heal half of what
        // they deal to a reaver.
        onPlayerHit { reaverLeech(hit) }
        reaverType?.let { type ->
            onNpcHit(type) {
                if (!hit.isFromPlayer || hit.damage <= 0) return@onNpcHit
                val p = hit.resolvePlayerSource(deps.playerList) ?: return@onNpcHit
                val missing = p.statBase("stat.hitpoints") - p.stat("stat.hitpoints")
                val amount = minOf(hit.damage / 2, missing)
                if (amount > 0) p.statAdd("stat.hitpoints", constant = amount, percent = 0)
            }
        }

        // Mages stay passive and unattackable until Nex calls them: they take
        // no damage (mageSpec incoming rule) and cannot retaliate or hunt.
        for (mageId in mageIds) {
            onEvent<NpcStateEvents.Spawn>(mageId.toLong()) {
                npc.ignoreCombatInteractions = true
            }
        }

        // A respawned Nex restarts the whole encounter: her slain mages (which
        // never respawn on their own) come back, still uncalled.
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id == nexId) {
                reviveDeadMages(npc)
            } else if (npc.id in mageIds) {
                npc.vars[VARN_CALLED] = 0
                npc.ignoreCombatInteractions = true
            }
        }
    }

    // ——— Extensions ———

    private fun callMage(nex: Npc, target: Player, mageType: String) {
        val mage = findMage(nex, mageType) ?: return
        mage.vars[VARN_CALLED] = 1
        mage.ignoreCombatInteractions = false
        mage.apPlayer2(target, deps.playerInteractions)
    }

    private fun startVirus(nex: Npc, target: Player) {
        virusUntil[target] = deps.mapClock.cycle + VIRUS_TICKS
        tickVirus(nex, target)
    }

    private fun tickVirus(nex: Npc, player: Player) {
        val until = virusUntil[player] ?: return
        if (deps.mapClock.cycle >= until || !player.isValidTarget()) {
            virusUntil -= player
            return
        }
        player.statSub("stat.prayer", constant = VIRUS_PRAYER_DRAIN, percent = 0)
        val stat = VIRUS_STATS[deps.random.of(VIRUS_STATS.size)]
        player.statSub(stat, constant = VIRUS_STAT_DRAIN, percent = 0)
        deps.worldQueues.add(VIRUS_PERIOD) { tickVirus(nex, player) }
    }

    private fun dragTarget(nex: Npc, target: Player) {
        val dest = freeAdjacent(nex, target.coords) ?: return
        if (!deps.collision.isZoneValid(dest)) return
        PathingEntityCommon.telejump(target, deps.collision, dest)
        CombatEffects.freeze(target, DRAG_STUN_TICKS)
    }

    private fun dash(nex: Npc, target: Player) {
        val dx = (target.coords.x - nex.coords.x).compareTo(0)
        val dz = (target.coords.z - nex.coords.z).compareTo(0)
        if (dx == 0 && dz == 0) return
        val dest =
            (DASH_TILES downTo 1)
                .map { nex.coords.translate(dx * it, dz * it) }
                .firstOrNull { canStand(nex, it) } ?: return
        nex.telejump(deps.collision, dest)
        for (p in roomPlayers(nex)) {
            if (p.coords.chebyshevDistance(dest) <= DASH_HIT_RADIUS) {
                p.queueHit(nex, 1, org.rsmod.game.hit.HitType.Typeless,
                    deps.random.of(DASH_MAX + 1), deps.playerHitModifier)
            }
        }
    }

    private fun shadowSmash(nex: Npc) {
        val tiles = roomPlayers(nex).map { it.coords }.distinct()
        if (tiles.isEmpty()) return
        val locs =
            tiles.mapNotNull { tile ->
                deps.locRepo.add(
                    tile, SHADOW_SMASH_LOC, SMASH_LOC_TICKS, LocAngle[0], LocShape.CentrepieceStraight)
            }
        deps.worldQueues.add(SMASH_WINDUP) {
            for (p in roomPlayers(nex)) {
                if (p.coords in tiles) {
                    p.queueHit(nex, 1, org.rsmod.game.hit.HitType.Typeless,
                        deps.random.of(SMASH_MAX + 1), deps.playerHitModifier)
                }
            }
            locs.forEach { deps.locRepo.del(it, Int.MAX_VALUE) }
        }
    }

    private fun sacrifice(nex: Npc) {
        val players = roomPlayers(nex)
        val target = deps.random.pickOrNull(players) ?: return
        sacrificed += target
        deps.worldQueues.add(SACRIFICE_TICKS) {
            if (!sacrificed.remove(target) || !target.isValidTarget()) return@add
            if (target.coords.chebyshevDistance(nex.coords) > SACRIFICE_ESCAPE) return@add
            val dmg = deps.random.of(SACRIFICE_MAX + 1)
            target.queueHit(nex, 1, org.rsmod.game.hit.HitType.Magic, dmg, deps.playerHitModifier)
            target.statSub("stat.prayer", constant = 0, percent = SACRIFICE_PRAYER_PERCENT)
            nex.heal(SACRIFICE_HEAL)
            for (p in roomPlayers(nex)) {
                if (p !== target && p.coords.chebyshevDistance(target.coords) <= SACRIFICE_SPLASH) {
                    p.queueHit(nex, 1, org.rsmod.game.hit.HitType.Magic,
                        deps.random.of(SACRIFICE_SPLASH_MAX + 1), deps.playerHitModifier)
                    p.statSub("stat.prayer", constant = 0, percent = SACRIFICE_SPLASH_PRAYER_PERCENT)
                }
            }
        }
    }

    private fun icePrison(nex: Npc, target: Player) {
        val centre = target.coords
        val locs =
            PRISON_RING.map { (dx, dz) ->
                deps.locRepo.add(
                    centre.translate(dx, dz),
                    ICE_PRISON_LOC,
                    PRISON_LOC_TICKS,
                    LocAngle[0],
                    LocShape.CentrepieceStraight,
                )
            }
        deps.worldQueues.add(PRISON_BREAK_TICKS) {
            locs.forEach { deps.locRepo.del(it, Int.MAX_VALUE) }
            for (p in roomPlayers(nex)) {
                if (p.coords.chebyshevDistance(centre) <= PRISON_RADIUS) {
                    p.queueHit(nex, 1, org.rsmod.game.hit.HitType.Typeless,
                        deps.random.of(PRISON_MAX + 1), deps.playerHitModifier)
                }
            }
        }
    }

    private fun siphonEnd(nex: Npc) {
        val reavers =
            deps.npcRepo.findAll(ZoneKey.from(nex.coords), CHAMBER_ZONE_RADIUS)
                .filter { it.isVisType(REAVER) && it.hitpoints > 0 }
                .toList()
        val heal = reavers.sumOf { it.hitpoints }
        reavers.forEach { deps.npcRepo.del(it, Int.MAX_VALUE) }
        if (heal > 0) nex.heal(heal)
    }

    private fun summonReavers(nex: Npc, target: Player) {
        // A fresh siphon consumes any leftover reavers for their hitpoints.
        siphonEnd(nex)
        val party = roomPlayers(nex).size.coerceAtLeast(1)
        val count = (REAVER_BASE + (party - 1) * REAVER_PER_EXTRA).coerceAtMost(REAVER_MAX)
        val tiles = freeTilesAround(nex.coords, REAVER_SPAWN_RADIUS, reaverType?.size ?: 1)
        repeat(count) {
            val tile =
                if (tiles.isNotEmpty()) {
                    tiles.removeAt(deps.random.of(tiles.size))
                } else {
                    nex.coords
                }
            val spawned = deps.spawnOwnedNpc(nex, REAVER, tile) ?: return@repeat
            if (target.isValidTarget()) {
                spawned.assignSpawnOwner(target, deps.mapClock.cycle)
                spawned.apPlayer2(target, deps.playerInteractions)
            }
        }
    }

    private fun NpcHitEvents.Modify.onNexIncoming() {
        // During the siphon window, hits that would have damaged Nex heal her.
        val until = npc.vars[VARN_SIPHON]
        if (until <= 0 || deps.mapClock.cycle >= until || hit.damage <= 0) return
        npc.heal(hit.damage, showHitsplat = true)
        hit.damage = 0
    }

    private fun reaverLeech(hit: org.rsmod.game.hit.Hit) {
        if (!hit.isFromNpc || hit.damage <= 0) return
        val source = hit.resolveNpcSource(npcList) ?: return
        if (!source.isVisType(REAVER)) return
        findNex(source)?.heal(hit.damage, showHitsplat = true)
    }

    private fun findNex(near: Npc): Npc? =
        deps.npcRepo.findAll(ZoneKey.from(near.coords), CHAMBER_ZONE_RADIUS).firstOrNull {
            it.hitpoints > 0 && it.id in nexIds
        }

    private fun freeTilesAround(centre: CoordGrid, radius: Int, size: Int): MutableList<CoordGrid> =
        buildList {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val origin = centre.translate(dx, dz)
                    var free = deps.collision.isZoneValid(origin)
                    for (x in 0 until size) {
                        for (z in 0 until size) {
                            if (deps.collision.isWalkBlocked(origin.translate(x, z))) {
                                free = false
                            }
                        }
                    }
                    if (free) add(origin)
                }
            }
        }.toMutableList()

    private fun onNexLethal(nex: Npc) {
        nex.say("Taste my wrath!")
        val centre = nex.coords
        deps.worldQueues.add(WRATH_TICKS) {
            for (p in deps.playerList) {
                if (p.isValidTarget() && p.coords.chebyshevDistance(centre) <= WRATH_RADIUS) {
                    p.queueHit(nex, 1, org.rsmod.game.hit.HitType.Typeless,
                        deps.random.of(WRATH_MAX + 1), deps.playerHitModifier)
                }
            }
        }
    }

    // ——— Helpers ———

    private fun findMage(nex: Npc, mageType: String): Npc? =
        deps.npcRepo.findAll(ZoneKey.from(nex.coords), CHAMBER_ZONE_RADIUS).firstOrNull {
            it.isVisType(mageType) && it.hitpoints > 0
        }

    private fun roomPlayers(nex: Npc): List<Player> =
        deps.playerList.filter {
            it.isValidTarget() && it.coords.chebyshevDistance(nex.coords) <= CHAMBER_RADIUS
        }

    private fun freeAdjacent(nex: Npc, near: CoordGrid): CoordGrid? {
        val candidates = buildList {
            for (dx in -1..nex.size) for (dz in -1..nex.size) {
                if (dx in 0 until nex.size && dz in 0 until nex.size) continue
                add(nex.coords.translate(dx, dz))
            }
        }
        return candidates
            .filter { !deps.collision.isWalkBlocked(it) }
            .filter {
                lineValidator.hasLineOfWalk(near.level, near.x, near.z, it.x, it.z)
            }
            .minByOrNull { it.chebyshevDistance(near) }
    }

    private fun canStand(nex: Npc, origin: CoordGrid): Boolean {
        if (!deps.collision.isZoneValid(origin)) return false
        for (dx in 0 until nex.size) {
            for (dz in 0 until nex.size) {
                if (deps.collision.isWalkBlocked(origin.translate(dx, dz))) return false
            }
        }
        return true
    }

    private fun reviveDeadMages(nex: Npc) {
        for (mage in deps.npcRepo.findAll(ZoneKey.from(nex.coords), CHAMBER_ZONE_RADIUS)) {
            if (mage.id !in mageIds) continue
            if (mage.hitpoints == 0) {
                mage.lifecycleRespawnCycle = deps.mapClock.cycle + 1
            } else {
                mage.vars[VARN_CALLED] = 0
                mage.ignoreCombatInteractions = true
                mage.clearInteraction()
                mage.resetMode()
            }
        }
    }

    private fun isMage(type: String): Condition =
        Condition.Custom { npc, _ -> npc.isVisType(type) }

    private fun calledMageAlive(): Condition =
        Condition.Custom { npc, _ ->
            deps.npcRepo.findAll(ZoneKey.from(npc.coords), CHAMBER_ZONE_RADIUS).any {
                it.id in mageIds && it.hitpoints > 0 && it.vars[VARN_CALLED] != 0
            }
        }

    private fun mageDead(type: String): Condition =
        Condition.Custom { npc, _ ->
            deps.npcRepo.findAll(ZoneKey.from(npc.coords), CHAMBER_ZONE_RADIUS).none {
                it.isVisType(type) && it.hitpoints > 0
            }
        }

    private fun cursing(state: Int): Condition = Condition.VarnIn(VARN_CURSE, state..state)

    private fun timerActive(varn: String): Condition =
        Condition.VarnIn(varn, 1..Int.MAX_VALUE) and Condition.Not(Condition.VarnExpired(varn))

    private fun square(centre: CoordGrid, radius: Int): List<CoordGrid> = buildList {
        for (dx in -radius..radius) for (dz in -radius..radius) add(centre.translate(dx, dz))
    }

    private companion object {
        private const val NEX = "npc.nex"
        private const val NEX_SOULSPLIT = "npc.nex_soulsplit"
        private const val NEX_DEFLECT = "npc.nex_deflect"
        private const val FUMUS = "npc.nex_smokemage"
        private const val UMBRA = "npc.nex_shadowmage"
        private const val CRUOR = "npc.nex_bloodmage"
        private const val GLACIES = "npc.nex_icemage"
        private const val REAVER = "npc.nex_prison_blood_reaver_boss"
        private val NEX_FORMS = listOf(NEX, NEX_SOULSPLIT, NEX_DEFLECT)
        private val MAGE_TYPES = listOf(FUMUS, UMBRA, CRUOR, GLACIES)

        private const val VARN_CALLED = "varn.nex_mage_called"
        private const val VARN_CURSE = "varn.nex_curse"
        private const val VARN_SIPHON = "varn.nex_siphon"
        private const val VARN_DARK = "varn.nex_darkness"
        private const val VARN_CHOKED = "varn.nex_choked"
        private const val VARN_BLOOD_SPEC = "varn.nex_blood_special"
        private const val VARN_ICE_SPEC = "varn.nex_ice_special"

        private const val EXT_CALL_MAGE = "nex.call_mage"
        private const val EXT_VIRUS = "nex.virus"
        private const val EXT_DRAG = "nex.drag"
        private const val EXT_DASH = "nex.dash"
        private const val EXT_SHADOW_SMASH = "nex.shadow_smash"
        private const val EXT_SACRIFICE = "nex.sacrifice"
        private const val EXT_ICE_PRISON = "nex.ice_prison"
        private const val EXT_SIPHON_REAVERS = "nex.siphon_reavers"
        private const val EXT_SIPHON_END = "nex.siphon_end"

        private const val SMOKE_PROJ = "spotanim.nex_smoke_attack_proj"
        private const val SMOKE_IMPACT = "spotanim.nex_smoke_attack_impact"
        private const val SHADOW_PROJ = "spotanim.nex_shadow_attack_proj"
        private const val BLOOD_PROJ = "spotanim.nex_blood_attack_proj"
        private const val BLOOD_IMPACT = "spotanim.nex_blood_attack_impact"
        private const val ICE_PROJ = "spotanim.nex_ice_attack_proj"
        private const val ICE_IMPACT = "spotanim.nex_ice_attack_impact"
        private const val ICE_PRISON_PROJ = "spotanim.nex_ice_prison_proj"
        private const val FINALE_PROJ = "spotanim.nex_finale_attack_proj"
        private const val FINALE_IMPACT = "spotanim.nex_finale_attack_impact"
        private const val MAGE_ANIM = "seq.nex_cast_attack"
        private const val SHADOW_SMASH_LOC = "loc.nex_shadow_smash"
        private const val ICE_PRISON_LOC = "loc.nex_icicle_1"

        private const val PHASE_ZAROS = "zaros"

        private const val ATTACK_RATE = 4
        private const val MAGE_ATTACK_RATE = 5
        private const val CHAMBER_RADIUS = 30
        private const val CHAMBER_ZONE_RADIUS = 6

        private const val MELEE_MAX = 30
        private const val MELEE_SWINGS = 3
        private const val AUTO_MAX = 33
        private const val SHADOW_BASE = 30
        private const val SHADOW_MAX = 60
        private const val MAGE_MAX_HIT = 25
        private const val DEFLECT_MAX = 25
        private const val POISON_DAMAGE = 8

        // Fraction of the hit that lands through the matching protection
        // prayer: melee 20% (34% in Zaros), magic 50% (34% blood), ranged 50%.
        private const val MELEE_PRAYER_THROUGH = 20
        private const val MELEE_ZAROS_PRAYER_THROUGH = 34
        private const val MAGIC_PRAYER_THROUGH = 50
        private const val BLOOD_PRAYER_THROUGH = 34
        private const val RANGED_PRAYER_THROUGH = 50

        private val MELEE_HIT =
            Effect.Hit(
                TargetExpr.CurrentTarget,
                (0..MELEE_MAX).roll(),
                HitType.Melee,
                penetration = MELEE_PRAYER_THROUGH,
            )
        private val MELEE_HIT_ZAROS =
            Effect.Hit(
                TargetExpr.CurrentTarget,
                (0..MELEE_MAX).roll(),
                HitType.Melee,
                penetration = MELEE_ZAROS_PRAYER_THROUGH,
            )
        private val MELEE_HIT_SOULSPLIT =
            Effect.Hit(
                TargetExpr.CurrentTarget,
                (0..MELEE_MAX).roll(),
                HitType.Melee,
                penetration = MELEE_ZAROS_PRAYER_THROUGH,
                lifesteal = SOULSPLIT_LEECH,
            )

        private const val HP_FUMUS = 0.80
        private const val HP_UMBRA = 0.60
        private const val HP_CRUOR = 0.40
        private const val HP_GLACIES = 0.20

        private const val CHOKE_FORCE_TICKS = 3
        private const val VIRUS_TICKS = 14
        private const val VIRUS_PERIOD = 2
        private const val VIRUS_PRAYER_DRAIN = 1
        private const val VIRUS_STAT_DRAIN = 2
        private val VIRUS_STATS =
            listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")

        private const val DRAG_STUN_TICKS = 3
        private const val DASH_TILES = 8
        private const val DASH_HIT_RADIUS = 2
        private const val DASH_MAX = 50

        private const val SMASH_WINDUP = 3
        private const val SMASH_MAX = 50
        private const val SMASH_LOC_TICKS = 6
        private const val DARK_TICKS = 25
        private const val DARK_RADIUS = 2
        private const val DARK_MAX = 15
        private const val DARK_DAMAGE_PERIOD = 2

        private const val SIPHON_TICKS = 8
        // OSRS scales the wave with team size: four for a solo player, up to
        // eight in a full chamber.
        private const val REAVER_BASE = 4
        private const val REAVER_PER_EXTRA = 2
        private const val REAVER_MAX = 8
        private const val REAVER_SPAWN_RADIUS = 4
        private const val BLOOD_SPEC_SIPHON = 1
        private const val BLOOD_SPEC_SACRIFICE = 2
        private const val BLOOD_SPECIAL_MIN = 8
        private const val BLOOD_SPECIAL_MAX = 10
        private const val BLOOD_LEECH = 100
        private const val SACRIFICE_TICKS = 5
        private const val SACRIFICE_ESCAPE = 7
        private const val SACRIFICE_MAX = 80
        private const val SACRIFICE_HEAL = 500
        private const val SACRIFICE_PRAYER_PERCENT = 34
        private const val SACRIFICE_SPLASH = 2
        private const val SACRIFICE_SPLASH_MAX = 12
        private const val SACRIFICE_SPLASH_PRAYER_PERCENT = 12

        private const val ICE_SPEC_CONTAIN = 1
        private const val ICE_SPEC_PRISON = 2
        private const val ICE_SPECIAL_MIN = 8
        private const val ICE_SPECIAL_MAX = 10
        private const val CONTAINMENT_RADIUS = 2
        private const val CONTAINMENT_MAX = 60
        private const val ICE_FREEZE_TICKS = 2
        private const val PRISON_FREEZE_TICKS = 5
        private const val PRISON_BREAK_TICKS = 6
        private const val PRISON_LOC_TICKS = 10
        private const val PRISON_RADIUS = 1
        private const val PRISON_MAX = 75
        private val PRISON_RING =
            listOf(
                -1 to -1, 0 to -1, 1 to -1,
                -1 to 0, 1 to 0,
                -1 to 1, 0 to 1, 1 to 1,
            )

        private const val SOULSPLIT_LEECH = 20
        private const val CURSE_SOULSPLIT = 1
        private const val CURSE_DEFLECT = 2
        private const val CURSE_NONE = 3
        private const val CURSE_PERIOD = 4
        private const val WRATH_TICKS = 5
        private const val WRATH_RADIUS = 2
        private const val WRATH_MAX = 50
    }
}
