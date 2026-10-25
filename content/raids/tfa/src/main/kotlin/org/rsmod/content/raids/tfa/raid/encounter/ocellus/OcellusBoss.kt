package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.VarExpr
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.spotanimSpecific
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.ARRIVED_AT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.CENTRE_SIDE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.CORNER
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.METER
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.THRESHOLD
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.VOLLEY_STYLE
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class OcellusBoss
@Inject
constructor(
    deps: BossDeps,
    private val encounter: OcellusEncounter,
    private val hpBar: BossHpBarScript,
    npcList: NpcList,
) : BossPluginScript(deps) {
    private val spin = OcellusSpin(deps)
    private val curses = OcellusCurses(deps, npcList)

    override fun ScriptContext.startup() {
        spin.register()
        curses.register(this)
        OcellusTendrils(deps, encounter.meter).register()
        OcellusBarrage(deps, encounter.meter).register()
        OcellusSlash.register(deps)
        deps.extensionRegistry.register(UNTETHER_PERSONAL) {
            it.target.spotanimSpecific(UNTETHER_PERSONAL_SPOT, slot = UNTETHER_PERSONAL_SLOT)
        }
        deps.extensionRegistry.register(UNTETHER_IMPACT) {
            it.target.spotanim(SPREAD_IMPACT, delay = UNTETHER_IMPACT_DELAY)
        }
        encounter.phaseTwo.register()
        val finalForm = ServerCacheManager.getNpc("npc.tfa_ocellus_p3".asRSCM(RSCMType.NPC))!!
        onNpcQueue(finalForm, "queue.death") { death() }
        BossCombat.register(
            this,
            spec,
            deps,
            onCombatTick = { encounter.debug.noteCombatTick() },
            onHit = {
                curses.reflect(this)
                for (player in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(player, npc)
                if (npc.hitpoints <= 0) deathFx(npc)
            },
        )
    }

    private var dying: Npc? = null

    private suspend fun StandardNpcAccess.death() {
        deps.encounterRegistry.of(npc).interrupt(deps.mapClock.cycle)
        encounter.complete(npc)
        noneMode()
        hideAllOps()
        anim(DEATH_SEQ)
        delay(DEATH_REMOVE_DELAY)
        encounter.phaseTwo.clear()
        deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun deathFx(npc: Npc) {
        if (dying === npc) return
        dying = npc
        npc.infoProtocol.setSpotanim(-1, 0, 0, BOSS_SPOT_SLOT)
        npc.spotanim(DEATH_SKIRT_SPOT, delay = DEATH_FX_DELAY)
        npc.spotanim(DEATH_SPOT, delay = DEATH_FX_DELAY, slot = DEATH_SPOT_SLOT)
        npc.spotanim(DEATH_CRACK_SPOT, delay = DEATH_FX_DELAY, slot = DEATH_CRACK_SLOT)
    }

    override val spec: BossSpec =
        boss("npc.tfa_ocellus_intro", "npc.tfa_ocellus_p1", "npc.tfa_ocellus_p3") {
            phase(WALKING, scriptedMovement = true) { every(VOLLEY_GAP, run(VOLLEY_SLOT)) }
            phase(CORNER_CYCLE, scriptedMovement = true) {}
            phase(CENTRE, scriptedMovement = true, lockMovement = true) {}
            phase(BROKEN, scriptedMovement = true, lockMovement = true) {}
            phase(PHASE_TWO, scriptedMovement = true, lockMovement = true) {}
            phase(PHASE_THREE) {
                weightedSelectorRandom { +random(ATTACK_CYCLE, weight = 1) }
                forceWhen(varnAtLeast(OcellusVarns.SPECIAL_CYCLES, CYCLES_PER_SPECIAL), SPECIAL)
            }

            ability(START, start())
            ability(WALK, walk())
            ability(VOLLEY_SLOT, volleySlot())
            ability(VOLLEY_START, volley(VOLLEY_START_SEQ))
            ability(VOLLEY_END, volley(VOLLEY_END_SEQ))
            ability(CORNER_CYCLE, cornerCycle())
            ability(CENTRE, centre())
            ability(NEXT, next())
            ability(BREAK, shieldBreak())
            ability(PHASE_TWO, phaseTwo())
            ability(PHASE_THREE, phaseThree())
            ability(
                ATTACK_CYCLE,
                whenever(
                    varnIs(THRESHOLD, QUADRANT_STAGE + QUADRANT_THRESHOLDS.size),
                    run(CURSES),
                    otherwise = attackCycle(),
                ),
                attackDelay = ATTACK_CYCLE_CAP,
            )
            ability(CURSES, curses(), attackDelay = ATTACK_CYCLE_CAP)
            ability(SPECIAL, special(), attackDelay = ATTACK_CYCLE_CAP)
            ability(DESTROY, destroyQuadrant())
            ability(SKIP_TO_THREE, skipToPhaseThree())

            triggers {
                val skipping = varnIs(THRESHOLD, OcellusVarns.SKIP_TO_PHASE_THREE)
                val shielded =
                    Condition.Not(Condition.InPhase(PHASE_TWO)) and
                        Condition.Not(Condition.InPhase(PHASE_THREE))
                on(skipping and shielded) runs
                    sequence(
                        interrupt(),
                        dropProtection(),
                        external(OcellusPhaseTwo.ENTER),
                        run(SKIP_TO_THREE),
                    )
                on(skipping and Condition.InPhase(PHASE_TWO)) runs
                    sequence(interrupt(), run(SKIP_TO_THREE))
                on(Condition.InPhase(WALKING) and Condition.Not(skipping)) runs run(START)
                on(Condition.HpExact(1) and shielded) runs run(BREAK)
                QUADRANT_THRESHOLDS.forEachIndexed { stage, fraction ->
                    val crossed =
                        varnIs(THRESHOLD, QUADRANT_STAGE + stage) and
                            Condition.HpBelow(fraction, inclusive = true) and
                            Condition.InPhase(PHASE_THREE)
                    on(crossed) runs
                        sequence(
                            interrupt(),
                            setVarn(THRESHOLD, QUADRANT_STAGE + stage + 1),
                            run(DESTROY),
                        )
                }
                THRESHOLDS.forEachIndexed { stage, fraction ->
                    val crossedWhileWalking =
                        varnIs(THRESHOLD, stage) and
                            Condition.HpBelow(fraction, inclusive = true) and
                            Condition.InPhase(WALKING)
                    on(crossedWhileWalking) runs sequence(interrupt(), enterCentre(stage + 1))
                }
            }
        }

    private fun start(): Effect =
        sequence(
            oneOf(OcellusArena.CORNERS.indices.map { setVarn(CORNER, it) }),
            oneOf(VolleyStyle.entries.map { setVarn(VOLLEY_STYLE, it.ordinal) }),
            run(WALK),
        )

    private fun walk(): Effect {
        val arrive = sequence(setVarn(ARRIVED_AT, VarExpr.Now + 1), faceTile(OcellusArena.MIDDLE))
        val routes =
            OcellusArena.CORNERS.map { (dx, dz) -> walkTo(spawnTile(dx, dz), onArrival = arrive) }
        return sequence(
            setVarn(ARRIVED_AT, NOT_ARRIVED),
            transitionTo(WALKING),
            protectMelee(),
            run(VOLLEY_START),
            switch(CORNER, routes),
        )
    }

    /**
     * Each 5-tick slot of a walk: a volley while walking (closing the cast if it arrived this
     * tick), and on the first slot it is already standing at the corner, close the cast if the
     * arrival slot didn't and start charging the tick after.
     */
    private fun volleySlot(): Effect {
        val settle =
            sequence(
                whenever(varnAtLeast(ARRIVED_AT, CAST_OPEN), anim(VOLLEY_END_SEQ)),
                transitionTo(CORNER_CYCLE),
                dropProtection(),
                wait(1),
                run(CORNER_CYCLE),
            )
        return whenever(
            varnIs(ARRIVED_AT, NOT_ARRIVED),
            run(VOLLEY_START),
            otherwise =
                whenever(
                    Condition.Not(varnExpired(ARRIVED_AT)),
                    sequence(run(VOLLEY_END), setVarn(ARRIVED_AT, CAST_CLOSED)),
                    otherwise = settle,
                ),
        )
    }

    private fun volley(seq: String): Effect {
        val swaps = VolleyStyle.entries.map { setVarn(VOLLEY_STYLE, it.other.ordinal) }
        val swapStyle = switch(VOLLEY_STYLE, swaps)
        return sequence(
            chance(STYLE_SWITCH_ONE_IN, swapStyle),
            anim(seq),
            switch(VOLLEY_STYLE, VolleyStyle.entries.map(::volleyOf)),
        )
    }

    private fun volleyOf(style: VolleyStyle): Effect =
        sequence(
            spotanim(style.cast),
            onEach(
                playersIn(OcellusArena.AREA),
                sequence(
                    soundTo(style.castSound),
                    soundTo(style.travelSound, delay = VOLLEY_TRAVEL_SOUND_DELAY),
                    whenever(
                        targetWithin(VOLLEY_CLOSE_RANGE),
                        volleyProjectile(style, VOLLEY_CLOSE_TRAVEL),
                        otherwise = volleyProjectile(style, VOLLEY_FAR_TRAVEL),
                    ),
                ),
            ),
        )

    private fun volleyProjectile(style: VolleyStyle, travel: Int): Effect =
        projectile(
            spotanim = style.projanim,
            config =
                ProjectileConfig.fixed(
                    startHeight = VOLLEY_START_HEIGHT,
                    endHeight = VOLLEY_END_HEIGHT,
                    delay = VOLLEY_DELAY,
                    travel = travel,
                    angle = VOLLEY_ANGLE,
                ),
            hit =
                hit {
                    damage(0..VOLLEY_MAX_HIT).roll()
                    type(style.hitType)
                    spotanim(style.impact)
                },
            resolveOnImpact = true,
            onImpact = soundTo(style.impactSound),
        )

    private fun cornerCycle(): Effect =
        sequence(
            repeat(
                CORNER_CHARGES,
                gap = CHARGE_GAP,
                effect = sequence(anim(CHARGE_SEQ), external(OcellusTendrils.CORNER)),
            ),
            wait(CORNER_EXPLODE - (CORNER_CHARGES - 1) * CHARGE_GAP),
            explode(),
            wait(EXPLODE_TO_MOVE),
            switch(CORNER, OcellusArena.CORNERS.indices.map { setVarn(CORNER, nextCorner(it)) }),
            run(NEXT),
        )

    private fun centre(): Effect {
        val round =
            sequence(
                faceSide(),
                anim(CHARGE_SEQ),
                external(OcellusTendrils.CENTRE),
                wait(CENTRE_CHARGE_TO_EXPLODE),
                explode(),
                addVarn(CENTRE_SIDE, 1),
            )
        return sequence(
            transitionTo(CENTRE),
            dropProtection(),
            teleportToCentre(),
            oneOf(OcellusTendril.SIDES.indices.map { setVarn(CENTRE_SIDE, it) }),
            faceSide(instant = true),
            wait(CENTRE_FIRST_CHARGE),
            repeat(CENTRE_ROUNDS, gap = CENTRE_ROUND_GAP - CENTRE_CHARGE_TO_EXPLODE, round),
            wait(EXPLODE_TO_RELEASE),
            whenever(
                varnAtLeast(METER, OcellusMeter.SINGLE_WAVE_MAX + 1),
                say("Embrace... annihilation..."),
                otherwise = say("Death... awaits..."),
            ),
            external(OcellusBarrage.RELEASE),
            wait(RELEASE_TO_EXPEND),
            anim(EXPEND_SEQ),
            external(OcellusBarrage.FIRE),
            wait(EXPEND_TO_MOVE),
            run(NEXT),
        )
    }

    private fun next(): Effect {
        val (first, second) = THRESHOLDS
        return whenever(
            varnIs(THRESHOLD, 0) and Condition.HpBelow(first, inclusive = true),
            enterCentre(1),
            otherwise =
                whenever(
                    varnIs(THRESHOLD, 1) and Condition.HpBelow(second, inclusive = true),
                    enterCentre(2),
                    otherwise = run(WALK),
                ),
        )
    }

    private fun enterCentre(stage: Int): Effect = sequence(setVarn(THRESHOLD, stage), run(CENTRE))

    private fun shieldBreak(): Effect {
        val atCentre = Condition.Custom { npc, _ -> npc.coords == OcellusArena.centre(npc) }
        return sequence(
            interrupt(),
            transitionTo(BROKEN),
            dropProtection(),
            whenever(atCentre, Effect.NoOp, otherwise = teleportToCentre()),
            anim("seq.npc_ocellus_phase1_transformation"),
            wait(TRANSFORM_TO_P2),
            spotanim("spotanim.vfx_ocellus_p1_to_p2_transformation", slot = TELEPORT_SPOT_SLOT),
            anim("seq.npc_ocellus_phase2_transformation"),
            transmog("npc.tfa_ocellus_p2", Int.MAX_VALUE),
            external(OcellusPhaseTwo.ENTER),
            transitionTo(PHASE_TWO),
            run(PHASE_TWO),
        )
    }

    /** Everything is timed from the transform tick; the walls run in three windows around the pools. */
    private fun phaseTwo(): Effect =
        parallel(
            sequence(
                wait(WALLS_SPAWN_AT),
                external(OcellusPhaseTwo.WALLS_SPAWN),
                toPlayers(soundTo("synth.ocellus_p2_walls_spawn")),
            ),
            sequence(wait(1), external(OcellusPhaseTwo.SET_LEVEL)),
            sequence(
                wait(OPEN_AT),
                oneOf(PHASE_TWO_LINES.map(::say)),
                external(OcellusPhaseTwo.OPEN),
                external(OcellusPhaseTwo.SWORD_RAISE),
            ),
            sequence(wait(SWORD_IDLE_AT), external(OcellusPhaseTwo.SWORD_IDLE)),
            *WALL_WINDOWS.map(::wallWindow).toTypedArray(),
            *ROTATIONS.map(::rotation).toTypedArray(),
            discharges(),
            ending(),
        )

    /** The sword drops, the walls give way and the arena collapses around the transform to phase 3. */
    private fun ending(): Effect =
        sequence(
            wait(ENDING_AT),
            spotanim("spotanim.vfx_ocellus_p2_to_p3_arm_glow01", delay = ARM_GLOW_DELAY),
            spotanim("spotanim.vfx_ocellus_sword_root_fire", slot = ROOT_FIRE_SLOT),
            spotanim("spotanim.vfx_ocellus_p2_to_p3_arm01", delay = ARM_GLOW_DELAY, slot = TELEPORT_SPOT_SLOT),
            anim("seq.npc_ocellus_p2_to_p3_part1"),
            say("This... is enough..."),
            external(OcellusPhaseTwo.FINISH),
            external(OcellusPhaseTwo.SWORD_DROP),
            mapSpotanim("spotanim.vfx_ocellus_sword_drop", OcellusArena.MIDDLE),
            wait(1),
            mapSpotanim("spotanim.tfa_arena_sword_drop_explosion", OcellusArena.MIDDLE),
            wait(1),
            external(OcellusPhaseTwo.WALLS_DESPAWN),
            wait(CRACK_AT - ENDING_AT - 2),
            external(OcellusPhaseTwo.ARENA_CRACK),
            wait(PHASE_THREE_AT - CRACK_AT),
            spotanim("spotanim.vfx_ocellus_null", height = NULL_SPOT_HEIGHT, delay = NULL_SPOT_DELAY),
            spotanim(
                "spotanim.vfx_ocellus_null",
                height = NULL_SPOT_HEIGHT,
                delay = NULL_SPOT_DELAY,
                slot = TELEPORT_SPOT_SLOT,
            ),
            anim("seq.npc_ocellus_p2_to_p3_part2"),
            transmog("npc.tfa_ocellus_p3", Int.MAX_VALUE),
            external(OcellusPhaseTwo.PHASE_THREE),
            wait(1),
            external(OcellusPhaseTwo.ARENA_COLLAPSE),
            wait(GAP_WALLS_AT - PHASE_THREE_AT - 1),
            external(OcellusPhaseTwo.GAP_WALLS),
            transitionTo(PHASE_THREE),
            run(PHASE_THREE),
        )

    /** Testing shortcut: everything phase 2's ending leaves behind, without waiting for it. */
    private fun skipToPhaseThree(): Effect =
        sequence(
            external(OcellusPhaseTwo.WALLS_CLEAR),
            external(OcellusPhaseTwo.CLOSE),
            external(OcellusPhaseTwo.FINISH),
            external(OcellusPhaseTwo.ARENA_CRACK),
            transmog("npc.tfa_ocellus_p3", Int.MAX_VALUE),
            external(OcellusPhaseTwo.PHASE_THREE),
            wait(1),
            external(OcellusPhaseTwo.ARENA_COLLAPSE),
            wait(1),
            external(OcellusPhaseTwo.GAP_WALLS),
            transitionTo(PHASE_THREE),
            run(PHASE_THREE),
        )

    /** The boss fades out as the arena settles, then reappears in the quadrant the party is in. */
    private fun phaseThree(): Effect =
        sequence(
            lockMovement(),
            nextAttackIn(ATTACK_HOLD),
            setVarn(THRESHOLD, QUADRANT_STAGE),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = OPAQUE, to = INVISIBLE),
            wait(QUADRANT_ENTER_AT - 1),
            external(OcellusPhaseTwo.QUADRANT_FREEZE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_ENTER),
            voidBombs(),
            nextAttackIn(ENTER_TO_FIRST_ATTACK),
            resetSpecials(),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = INVISIBLE, to = OPAQUE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_THAW),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_SPAWN),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER),
            unlockMovement(),
        )

    /**
     * Two slots, each a melee swing when the target is in reach and the magic spread when not,
     * then the shadow slash. A swing leaves 3 ticks before the next slot and a spread 6; the
     * slash is aimed a tick before it starts, so the last slot's wait is a tick short.
     */
    private fun attackCycle(): Effect {
        val swing =
            sequence(
                anim(MELEE_BASIC_SEQ),
                hit {
                    damage(Accuracy(npcMaxHit()))
                    type(Melee)
                },
            )
        fun slot(aimLead: Int) =
            whenever(
                WithinMeleeRange,
                sequence(swing, wait(MELEE_SWING_GAP - aimLead)),
                otherwise = sequence(magicSpread(), wait(SPREAD_GAP - aimLead)),
            )
        return sequence(
            slot(0),
            slot(1),
            shadowSlash(),
            nextAttackIn(SLASH_RECOVERY),
            addVarn(OcellusVarns.SPECIAL_CYCLES, 1),
        )
    }

    /**
     * Every third attack is a special. Which one is the quadrant's script, which runs in order and
     * loops for as long as the quadrant lasts. The script position is
     * `quadrant * QUADRANT_STEPS + step`.
     */
    private fun special(): Effect {
        val untether = sequence(untether(), wait(UNTETHER_RIFT_DELAY))
        val scripts =
            listOf(
                listOf(debris(0), untether),
                listOf(debris(1), insolence(), debris(1), untether),
                listOf(debris(2), insolence(), debris(2)),
            )
        val cases =
            scripts.flatMapIndexed { quadrant, steps ->
                steps.mapIndexed { step, effect ->
                    val next = quadrant * QUADRANT_STEPS + (step + 1) % steps.size
                    quadrant * QUADRANT_STEPS + step to
                        sequence(
                            external(OcellusPhaseTwo.RIFTS_BUSY, params = true),
                            effect,
                            external(OcellusPhaseTwo.RIFTS_BUSY, params = false),
                            setVarn(OcellusVarns.SPECIAL_STEP, next),
                        )
                }
            }
        return sequence(
            setVarn(OcellusVarns.SPECIAL_CYCLES, 0),
            switch(OcellusVarns.SPECIAL_STEP, *cases.toTypedArray(), otherwise = nextAttackIn(0)),
        )
    }

    /**
     * Forked off the boss's arrival in a quadrant, so it runs beside its first attacks: the marks
     * go up a tick in, hold for two, and a tick later the bombs show where they will fall and
     * land two ticks after that.
     */
    private fun voidBombs(): Effect {
        val tiles = customTiles(OcellusArena.FLOOR) { npc, _, _ -> encounter.phaseTwo.bombTiles(npc) }
        val idle =
            sequence(
                mapSpotanim(BOMB_IDLE_SPOT, CurrentTile, height = BOMB_HEIGHT),
                mapSpotanim(BOMB_IDLE_SPOT, CurrentTile, height = BOMB_HEIGHT, delay = BOMB_IDLE_LOOP_DELAY),
            )
        val land =
            sequence(
                mapSpotanim(BOMB_DROP_SPOT, CurrentTile, height = BOMB_HEIGHT),
                mapSpotanim(BOMB_IMPACT_SPOT, CurrentTile),
                external(OcellusVoid.BOMB, at = CurrentTile),
            )
        return after(
            BOMB_MARK_AT,
            sequence(
                external(OcellusPhaseTwo.BOMBS_MARK),
                wait(1),
                external(OcellusPhaseTwo.BOMBS_IDLE),
                wait(1),
                external(OcellusPhaseTwo.BOMBS_IDLE),
                wait(1),
                withTiles(
                    BOMB_TILES,
                    tiles,
                    sequence(
                        onTiles(bound(BOMB_TILES), idle),
                        wait(BOMB_IDLE_TO_DROP),
                        onTiles(bound(BOMB_TILES), land),
                    ),
                ),
            ),
        )
    }

    /** A new quadrant starts its script over, and its first special two attacks in. */
    private fun resetSpecials(): Effect {
        val starts =
            (0..QUADRANT_THRESHOLDS.size).map { quadrant ->
                QUADRANT_STAGE + quadrant to
                    sequence(
                        setVarn(OcellusVarns.SPECIAL_STEP, quadrant * QUADRANT_STEPS),
                        external(OcellusPhaseTwo.RIFTS_RESET, params = RIFT_KINDS[quadrant]),
                    )
            }
        return sequence(
            setVarn(OcellusVarns.SPECIAL_CYCLES, 0),
            switch(OcellusVarns.THRESHOLD, *starts.toTypedArray()),
        )
    }

    /**
     * The last quadrant's 60-tick loop, with no other attacks. The boss deflects missiles, pulls
     * half the party's hitpoints into a soul fragment, then swings a hybrid attack under soul split
     * and deflects melee, and does it all again before the rifts open and the loop restarts.
     */
    private fun curses(): Effect {
        fun hybrid(at: Int): Effect {
            fun swing(spot: String, hitType: HitType): Effect {
                val strike =
                    hit {
                        damage(CURSES_HYBRID_DAMAGE).roll()
                        type(hitType)
                    }
                return sequence(
                    anim(HYBRID_SEQ),
                    spotanim(spot, slot = BOSS_SPOT_SLOT),
                    wait(HYBRID_HIT_DELAY),
                    onEach(playersIn(OcellusArena.FLOOR), strike),
                )
            }
            return sequence(
                wait(at),
                oneOf(swing(HYBRID_MAGIC_SPOT, HitType.Magic), swing(HYBRID_RANGED_SPOT, HitType.Ranged)),
            )
        }
        fun fragment(levitating: Boolean) =
            sequence(
                anim(SOUL_START_SEQ),
                spotanim(if (levitating) SOUL_LEVITATING_SPOT else SOUL_IDLE_SPOT, slot = BOSS_SPOT_SLOT),
                external(OcellusPhaseTwo.SOULS_TAKE),
                external(OcellusPhaseTwo.VOID_CHAIN),
            )
        fun icon(index: Int, curse: Int) =
            sequence(headIcon(HEADICON_SLOT, HEADICON_GRAPHIC, index), setVarn(OcellusCurses.VARN, curse))
        val clearIcon =
            sequence(clearHeadIcon(HEADICON_SLOT), setVarn(OcellusCurses.VARN, OcellusCurses.NONE))
        val events =
            listOf(
                0 to
                    sequence(
                        anim(DEFLECT_IN_SEQ),
                        idleAnim(DEFLECT_LOOP_SEQ),
                        icon(HEADICON_DEFLECT_MISSILES, OcellusCurses.DEFLECT_MISSILES),
                        external(OcellusPhaseTwo.DEFLECT_START, params = OcellusDeflect.Style.Ranged),
                    ),
                CURSES_FRAGMENT_AT to fragment(levitating = true),
                CURSES_DEFLECT_OUT_AT to
                    sequence(
                        anim(DEFLECT_OUT_SEQ),
                        clearIdleAnim(),
                        external(OcellusPhaseTwo.SOULS_RELEASE),
                        external(OcellusPhaseTwo.DEFLECT_END),
                    ),
                CURSES_SOUL_SPLIT_AT to icon(HEADICON_SOUL_SPLIT, OcellusCurses.SOUL_SPLIT),
                CURSES_MELEE_ARENA_AT to
                    sequence(
                        clearIcon,
                        external(OcellusPhaseTwo.DEFLECT_START, params = OcellusDeflect.Style.Melee),
                    ),
                CURSES_DEFLECT_MELEE_AT to icon(HEADICON_DEFLECT_MELEE, OcellusCurses.DEFLECT_MELEE),
                CURSES_SECOND_FRAGMENT_AT to fragment(levitating = false),
                CURSES_FRAGMENT_END_AT to
                    sequence(
                        anim(SOUL_END_SEQ),
                        external(OcellusPhaseTwo.SOULS_RELEASE),
                        external(OcellusPhaseTwo.DEFLECT_END),
                    ),
                CURSES_SECOND_SOUL_SPLIT_AT to icon(HEADICON_SOUL_SPLIT, OcellusCurses.SOUL_SPLIT),
                CURSES_LENGTH - 1 to sequence(clearIcon, nextAttackIn(1)),
            )
        return parallel(
            timeline(events, from = 0),
            hybrid(CURSES_HYBRID_AT),
            hybrid(CURSES_SECOND_HYBRID_AT),
        )
    }

    /**
     * The spin attack. The boss opens a purple portal (no overhead prayer; it takes everything), then spins once
     * plus once more for each quadrant already taken ([loops]). Each spin is a fade (protection
     * prayers must be down), the spin animation (Protect from Melee must be back up) and, a tick
     * later, a hit to everyone; a player who missed either step takes it unprotected
     * ([OcellusSpin]). Blades also mark every player's tile and 5 ticks later void takes the
     * marked tiles, whatever the spin count.
     */
    private fun debris(loops: Int): Effect {
        val marks = if (loops == 0) DEBRIS_MARKS_SHORT else DEBRIS_MARKS_LOOPED
        val impact = marks + DEBRIS_FALL
        val out = DEBRIS_OUT + DEBRIS_LOOP * loops
        val prayed =
            hit {
                damage(DEBRIS_DAMAGE).roll()
                type(Melee)
            }
        val unprayed =
            hit {
                damage(DEBRIS_DAMAGE).roll()
                type(HitType.Typeless)
            }
        val blades =
            onEach(
                playersIn(OcellusArena.FLOOR),
                whenever(
                    Condition.Custom { _, player -> player != null && spin.isProtected(player) },
                    prayed,
                    otherwise = unprayed,
                ),
            )
        val events = mutableListOf<Pair<Int, Effect>>()
        events += 0 to anim(PRAYER_IN_SEQ)
        events += 0 to spotanim(PRAYER_IN_SPOT, slot = PRAYER_IN_SLOT)
        for (spinIndex in 0..loops) {
            val last = spinIndex == loops
            val fade = DEBRIS_LOOP_SPOT + DEBRIS_LOOP * spinIndex
            val seq = DEBRIS_OUT + DEBRIS_LOOP * spinIndex
            events += fade to spotanim(if (last) PRAYER_OUT_SPOT else PRAYER_LOOP_SPOT, slot = PRAYER_SLOT)
            events += fade to external(OcellusSpin.CHECK_OFF)
            events += seq to anim(if (last) PRAYER_OUT_SEQ else PRAYER_LOOP_SEQ)
            events += seq to external(OcellusSpin.CHECK_ON)
            events += seq + SPIN_HIT_DELAY to blades
        }
        events += marks to onTiles(bound(DEBRIS_TILES), mapSpotanim(DEBRIS_SPOT, CurrentTile))
        events += impact to onTiles(bound(DEBRIS_TILES), external(OcellusVoid.DEBRIS, at = CurrentTile))
        events += impact to after(DEBRIS_VINE_DELAY, external(OcellusVine.SPAWN))
        events +=
            out + DEBRIS_RECOVERY to
                sequence(
                    say("Your panic... is futile..."),
                    nextAttackIn(0),
                    unlockMovement(),
                )
        val before = events.filter { it.first < marks }
        val tiles =
            customTiles(OcellusArena.FLOOR) { npc, _, _ ->
                OcellusArena.quadrantPlayers(npc, deps.playerList).map { it.coords }
            }
        return sequence(
            lockMovement(),
            timeline(before, from = 0),
            wait(marks - before.maxOf { it.first }),
            withTiles(DEBRIS_TILES, tiles, timeline(events.filter { it.first >= marks }, from = marks)),
        )
    }

    /**
     * The boss glides home and hangs in the air while the void builds on the quadrant's floor in
     * four waves, each going off as the next opens like phase 2's pools. Then the whole quadrant
     * erupts except the boss's footprint, and a tick later the boss slams down on the footprint.
     * The footprint is only safe from the eruption: stand under it for the eruption, be out for the
     * slam. Hits land a tick after they are queued, so these are queued a tick ahead of the splats.
     */
    private fun untether(): Effect {
        val dots =
            customTiles(OcellusArena.FLOOR) { npc, _, random ->
                val underfoot =
                    OcellusArena.quadrantPlayers(npc, deps.playerList).map { it.coords }.distinct()
                val free = quadrantFloor(npc).apply { removeAll(underfoot) }
                val extra = minOf(random.of(UNTETHER_DOTS) - underfoot.size, free.size).coerceAtLeast(0)
                underfoot + List(extra) { free.removeAt(random.of(free.size)) }
            }
        val pool =
            hit {
                damage(UNTETHER_POOL_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        val bolt =
            hit {
                damage(UNTETHER_MAGIC_DAMAGE).roll()
                type(HitType.Magic)
            }
        val eruption =
            hit {
                damage(UNTETHER_ERUPTION_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        val slam =
            hit {
                damage(UNTETHER_SLAM_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        fun underBoss(npc: Npc, player: Player) =
            player.coords.x - npc.coords.x in 0 until BOSS_SIZE &&
                player.coords.z - npc.coords.z in 0 until BOSS_SIZE
        val erupting =
            Condition.Custom { npc, player ->
                player != null &&
                    OcellusArena.sameQuadrantFloor(npc, player.coords) &&
                    !underBoss(npc, player)
            }
        val slamming = Condition.Custom { npc, player -> player != null && underBoss(npc, player) }
        val inQuadrant =
            Condition.Custom { npc, player ->
                player != null && OcellusArena.sameQuadrantFloor(npc, player.coords)
            }
        fun pools(index: Int): Effect {
            val name = "untether_$index"
            return sequence(
                wait(UNTETHER_FIRST_WAVE + UNTETHER_WAVE_GAP * index),
                withTiles(
                    name,
                    dots,
                    sequence(
                        onTiles(bound(name), mapSpotanim(UNTETHER_DOT_SPOT, CurrentTile)),
                        wait(UNTETHER_WAVE_GAP),
                        onTiles(bound(name), onEach(playersOn(CurrentTile), pool)),
                    ),
                ),
            )
        }
        fun spell(index: Int): Effect {
            val targets = playersIn(OcellusArena.FLOOR)
            return sequence(
                wait(UNTETHER_FIRST_WAVE + UNTETHER_WAVE_GAP * index - UNTETHER_PERSONAL_LEAD),
                onEach(targets, whenever(inQuadrant, external(UNTETHER_PERSONAL))),
                wait(UNTETHER_PERSONAL_LEAD),
                onEach(targets, whenever(inQuadrant, external(UNTETHER_IMPACT))),
                wait(UNTETHER_WAVE_GAP - 1),
                onEach(targets, whenever(inQuadrant, bolt)),
            )
        }
        val events =
            listOf(
                0 to
                    sequence(
                        lockMovement(),
                        external(OcellusPhaseTwo.UNTETHER_GLIDE),
                        say("Untether..."),
                        anim(UNTETHER_IN_SEQ),
                        idleAnim(UNTETHER_LOOP_SEQ),
                    ),
                UNTETHER_LOOP_AT to anim(UNTETHER_LOOP_SEQ),
                UNTETHER_GLOW_AT to spotanim(UNTETHER_GLOW_SPOT, delay = UNTETHER_GLOW_DELAY),
                UNTETHER_BORDER_AT to slamBurst(),
                UNTETHER_ERUPT_AT to onEach(playersIn(OcellusArena.FLOOR), whenever(erupting, eruption)),
                UNTETHER_SLAM_AT to onEach(playersIn(OcellusArena.FLOOR), whenever(slamming, slam)),
                UNTETHER_OUT_AT to
                    sequence(
                        clearIdleAnim(),
                        anim(UNTETHER_OUT_SEQ),
                        nextAttackIn(UNTETHER_RECOVERY),
                        unlockMovement(),
                    ),
            )
        val waves = (0 until UNTETHER_WAVES).flatMap { listOf(pools(it), spell(it)) }
        return parallel(timeline(events, from = 0), *waves.toTypedArray())
    }

    /** The slam's floor burst, turned to match the corner of the room the boss is in. */
    private fun slamBurst(): Effect {
        fun inCorner(east: Boolean, north: Boolean) =
            Condition.Custom { npc, _ ->
                val middle = OcellusArena.middle(npc)
                (npc.coords.x > middle.x) == east && (npc.coords.z > middle.z) == north
            }
        val (southWest, southEast, northEast, northWest) = UNTETHER_ARENA_SPOTS.map { mapSpotanim(it, Centre) }
        return whenever(
            inCorner(east = false, north = false),
            southWest,
            otherwise =
                whenever(
                    inCorner(east = true, north = false),
                    southEast,
                    otherwise = whenever(inCorner(east = true, north = true), northEast, otherwise = northWest),
                ),
        )
    }

    private fun quadrantFloor(npc: Npc): MutableList<CoordGrid> {
        val centre = npc.coords.translate(BOSS_SIZE / 2, BOSS_SIZE / 2)
        val tiles = ArrayList<CoordGrid>()
        for (dx in -UNTETHER_REACH..UNTETHER_REACH) {
            for (dz in -UNTETHER_REACH..UNTETHER_REACH) {
                val tile = centre.translate(dx, dz)
                if (OcellusArena.sameQuadrantFloor(npc, tile)) tiles.add(tile)
            }
        }
        return tiles
    }

    /** [events] in tick order, waiting out the gaps between them. */
    private fun timeline(events: List<Pair<Int, Effect>>, from: Int): Effect {
        val steps = mutableListOf<Effect>()
        var now = from
        for ((tick, effect) in events.sortedBy { it.first }) {
            if (tick > now) {
                steps += wait(tick - now)
                now = tick
            }
            steps += effect
        }
        return sequence(*steps.toTypedArray())
    }

    private fun magicSpread(): Effect =
        sequence(
            anim(SPREAD_SEQ),
            spotanim(SPREAD_CAST_SPOT, delay = SPREAD_CAST_DELAY, slot = SPREAD_CAST_SLOT),
            sound("synth.ocellus_magic_spread_cast", delay = SPREAD_SOUND_DELAY),
            onEach(
                playersIn(OcellusArena.FLOOR),
                projectile(
                    spotanim = SPREAD_PROJECTILE,
                    config =
                        ProjectileConfig.fixed(
                            startHeight = SPREAD_START_HEIGHT,
                            endHeight = SPREAD_END_HEIGHT,
                            delay = SPREAD_DELAY,
                            travel = SPREAD_TRAVEL,
                            angle = SPREAD_ANGLE,
                        ),
                    hit =
                        hit {
                            damage(SPREAD_DAMAGE).roll()
                            type(HitType.Magic)
                            spotanim(SPREAD_IMPACT)
                        },
                    resolveOnImpact = true,
                ),
            ),
        )

    private fun shadowSlash(): Effect {
        val strike =
            hit {
                damage(Fixed(SLASH_DAMAGE))
                type(HitType.Typeless)
                hazard()
            }
        val steps = 1..OcellusSlash.STEPS
        val sweeps =
            steps.map { step ->
                val sweep =
                    mapSpotanim(SLASH_SPOT, CurrentTile, delay = step * OcellusSlash.STEP_DELAY)
                onTiles(bound("slash_$step"), sequence(sweep, onEach(playersOn(CurrentTile), strike)))
            }
        val slash =
            sequence(
                lockMovement(),
                external(OcellusSlash.AIM),
                wait(1),
                anim(SLASH_SEQ),
                wait(1),
                *sweeps.toTypedArray(),
                wait(1),
                external(OcellusSlash.RELEASE),
                unlockMovement(),
            )
        return withBands("slash", OcellusSlash::band, slash)
    }

    /**
     * Three superior slashes, each at a random player in the quadrant: aimed a tick before it
     * swings, landing three ticks after, and leaving void on the floor behind. The boss stays put
     * and pinned on the aim throughout.
     */
    private fun insolence(): Effect {
        val strike =
            hit {
                damage(Fixed(INSOLENCE_DAMAGE))
                type(HitType.Typeless)
                hazard()
            }
        val sweeps =
            (1..OcellusSlash.STEPS).map { step ->
                val sweep =
                    mapSpotanim(
                        INSOLENCE_SPOT,
                        CurrentTile,
                        delay = INSOLENCE_SWEEP_DELAY + step * OcellusSlash.STEP_DELAY,
                    )
                onTiles(
                    bound("insolence_$step"),
                    sequence(
                        sweep,
                        external(OcellusVoid.INSOLENCE, params = step, at = CurrentTile),
                        onEach(playersOn(CurrentTile), strike),
                    ),
                )
            }
        val swing =
            sequence(
                external(OcellusSlash.AIM_AT),
                wait(1),
                anim(INSOLENCE_SEQ, delay = INSOLENCE_ANIM_DELAY),
                wait(INSOLENCE_CAST_TO_SWEEP),
                *sweeps.toTypedArray(),
                wait(1),
                external(OcellusSlash.RELEASE),
            )
        val aim = customTile { npc, _ -> OcellusSlash.pickAim(npc, deps) }
        val cast = onEach(playersOn(aim), withBands("insolence", OcellusSlash::superiorBand, swing))
        return sequence(
            lockMovement(),
            say("Insolence..."),
            wait(1),
            cast,
            cast,
            cast,
            nextAttackIn(INSOLENCE_RECOVERY),
            unlockMovement(),
        )
    }

    /** Binds each step's band of tiles up front, so what later hits is what was aimed at. */
    private fun withBands(
        prefix: String,
        band: (Npc, Player, Int) -> List<CoordGrid>,
        inner: Effect,
    ): Effect =
        (1..OcellusSlash.STEPS).reversed().fold(inner) { acc, step ->
            val tiles = customTiles(OcellusArena.FLOOR) { npc, target, _ -> band(npc, target, step) }
            withTiles("${prefix}_$step", tiles, acc)
        }

    /** Timed from the tick health crosses a threshold; the quadrant goes and the boss moves on. */
    private fun destroyQuadrant(): Effect =
        sequence(
            lockMovement(),
            external(OcellusPhaseTwo.RIFTS_STOP),
            external(OcellusPhaseTwo.VINES_CLEAR),
            nextAttackIn(ATTACK_HOLD),
            external(OcellusSlash.RELEASE),
            anim("seq.npc_ocellus_quadrant_destroy_in"),
            idleAnim("seq.npc_ocellus_quadrant_destroy_loop"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_in", slot = TELEPORT_SPOT_SLOT),
            headbar(DESTROY_BAR, fromPercent = 100, toPercent = 0, cycles = DESTROY_BAR_CYCLES),
            oneOf(say("Run... It matters not..."), say("Obliteration...")),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_DESPAWN),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_REMOVE),
            wait(1),
            anim("seq.npc_ocellus_quadrant_destroy_loop"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_loop", slot = TELEPORT_SPOT_SLOT),
            wait(DESTROY_OUT_AT - 2),
            clearHeadbar(DESTROY_BAR),
            clearIdleAnim(),
            anim("seq.npc_ocellus_phase3_unleash_out_90f"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_out_01", slot = TELEPORT_SPOT_SLOT),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_out_02", slot = COLLAPSE_OUT_SLOT),
            oneOf(say("Everything... ends..."), say("You can't... escape... the end...")),
            wait(1),
            transparency(FADE_IN_START, DESTROY_FADE_END, from = OPAQUE, to = INVISIBLE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_COLLAPSE),
            wait(DESTROY_MOVE_AT - DESTROY_COLLAPSE_AT - 1),
            external(OcellusPhaseTwo.QUADRANT_FREEZE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_ENTER),
            voidBombs(),
            nextAttackIn(ENTER_TO_FIRST_ATTACK),
            resetSpecials(),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = INVISIBLE, to = OPAQUE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_THAW),
            external(OcellusPhaseTwo.QUADRANT_STONES_FALL),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_SPAWN),
            whenever(varnIs(THRESHOLD, QUADRANT_STAGE + QUADRANT_THRESHOLDS.size), say("End...")),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_STONES_BLOCK),
            whenever(
                varnIs(THRESHOLD, QUADRANT_STAGE + QUADRANT_THRESHOLDS.size),
                lockMovement(),
                otherwise = unlockMovement(),
            ),
        )

    /** Pools, then a closed volley with no gap, while a portal in each occupied quadrant rotates players on. */
    private fun rotation(start: Int): Effect =
        parallel(
            sequence(
                wait(start),
                external(OcellusPhaseTwo.POOLS),
                toPlayers(
                    sequence(
                        soundTo("synth.ocellus_p2_pool_hit"),
                        soundTo("synth.ocellus_p2_pool_echo", delay = POOL_ECHO_DELAY),
                    )
                ),
            ),
            sequence(
                wait(start + CLOSED_TELEGRAPH_AT),
                external(OcellusPhaseTwo.WALLS_CLOSED_TELEGRAPH),
                telegraphSound(CLOSED_ECHO_DELAY),
                after(CLOSED_FIRE_DELAY, external(OcellusPhaseTwo.WALLS_FIRE)),
            ),
            sequence(wait(start + POOLS_DETONATE_AT), external(OcellusPhaseTwo.POOLS_DETONATE)),
            sequence(
                wait(start + PORTALS_AT),
                external(OcellusPhaseTwo.PORTALS_SPAWN),
                toPlayers(soundTo("synth.ocellus_p2_portal_spawn")),
            ),
            sequence(
                wait(start + PORTALS_AT + 1),
                repeat(PORTAL_TICKS, gap = 1, effect = external(OcellusPhaseTwo.PORTALS_TICK)),
            ),
            sequence(
                wait(start + PORTALS_AT + PORTAL_TICKS + 1),
                external(OcellusPhaseTwo.PORTALS_END),
            ),
        )

    private fun wallWindow(start: Int): Effect =
        sequence(
            wait(start),
            repeat(
                WALL_CYCLES,
                gap = WALL_CYCLE_GAP,
                effect =
                    sequence(
                        external(OcellusPhaseTwo.WALLS_TELEGRAPH),
                        telegraphSound(WALL_ECHO_DELAY),
                        after(WALL_FIRE_DELAY, external(OcellusPhaseTwo.WALLS_FIRE)),
                    ),
            ),
        )

    private fun telegraphSound(echoDelay: Int): Effect =
        toPlayers(
            sequence(
                soundTo("synth.ocellus_p2_telegraph"),
                soundTo("synth.ocellus_p2_telegraph_echo", delay = echoDelay),
            )
        )

    private fun toPlayers(effect: Effect): Effect = onEach(playersIn(OcellusArena.AREA), effect)

    private fun discharges(): Effect {
        val vfx =
            sequence(
                spotanim("spotanim.vfx_ocellus_p2_sword_discharge", delay = DISCHARGE_VFX_DELAY),
                sound("synth.ocellus_sword_discharge_vfx", radius = DISCHARGE_VFX_RADIUS, at = OcellusArena.MIDDLE),
            )
        val discharge =
            sequence(
                sound("synth.ocellus_sword_discharge_hit", radius = DISCHARGE_HIT_RADIUS, at = OcellusArena.MIDDLE),
                external(OcellusPhaseTwo.DISCHARGE),
            )
        return sequence(
            wait(FIRST_DISCHARGE_VFX),
            vfx,
            wait(1),
            discharge,
            wait(DISCHARGE_GAP - 1),
            vfx,
            wait(1),
            discharge,
            wait(LAST_DISCHARGE_GAP),
            external(OcellusPhaseTwo.CLOSE),
            sound("synth.ocellus_sword_discharge_vfx", radius = DISCHARGE_VFX_RADIUS, at = OcellusArena.MIDDLE),
            wait(1),
            discharge,
        )
    }

    private fun protectMelee(): Effect =
        sequence(
            headIcon(HEADICON_SLOT, HEADICON_GRAPHIC, HEADICON_MELEE),
            setVarn(IMMUNE_MELEE, 1),
        )

    private fun dropProtection(): Effect =
        sequence(clearHeadIcon(HEADICON_SLOT), setVarn(IMMUNE_MELEE, 0))

    private fun teleportToCentre(): Effect =
        sequence(
            anim("seq.npc_ocellus_phase1_teleport_out"),
            spotanim("spotanim.vfx_ocellus_p1_teleport_out", slot = TELEPORT_SPOT_SLOT),
            transparency(FADE_OUT_START, FADE_OUT_END, from = OPAQUE, to = INVISIBLE),
            wait(TELEPORT_TICKS),
            teleport(OcellusArena.CENTRE),
            anim("seq.npc_ocellus_phase1_teleport_in"),
            spotanim("spotanim.vfx_ocellus_p1_teleport_in", slot = TELEPORT_SPOT_SLOT),
            transparency(FADE_IN_START, FADE_IN_END, from = INVISIBLE, to = OPAQUE),
        )

    private fun faceSide(instant: Boolean = false): Effect {
        val sides = OcellusTendril.SIDES
        val faces =
            List(sides.size * 2) {
                val (nx, nz) = sides[it % sides.size]
                val tile = offset(OcellusArena.MIDDLE, nx * FACE_DISTANCE, nz * FACE_DISTANCE)
                faceTile(tile, instant)
            }
        return switch(CENTRE_SIDE, faces)
    }

    private fun explode(): Effect =
        sequence(
            anim("seq.npc_ocellus_phase1_explode"),
            spotanim("spotanim.vfx_ocellus_p1_explode_npc"),
            after(
                SLAM_LAND,
                whenever(
                    Condition.Not(Condition.InPhase(BROKEN)),
                    sequence(slamRing(1, delay = 0), slamRing(2, SLAM_OUTER_DELAY)),
                ),
            ),
        )

    private fun slamRing(distance: Int, delay: Int): Effect {
        val tiles = customTiles(OcellusArena.AREA) { npc, _, _ -> ring(npc.coords, distance) }
        val floor = SLAM_FLOOR.map { mapSpotanim(it, CurrentTile, delay = delay) }
        val hit =
            hit {
                damage(SLAM_DAMAGE).roll()
                type(HitType.Typeless)
                hazard()
            }
        return onTiles(tiles, sequence(oneOf(floor), onEach(playersOn(CurrentTile), hit)))
    }

    private fun ring(sw: CoordGrid, distance: Int): List<CoordGrid> {
        val min = -distance
        val max = BOSS_SIZE - 1 + distance
        return buildList {
            for (dx in min..max) {
                for (dz in min..max) {
                    if (dx == min || dx == max || dz == min || dz == max) add(sw.translate(dx, dz))
                }
            }
        }
    }

    private fun nextCorner(corner: Int): Int = (corner + 1) % OcellusArena.CORNERS.size

    private enum class VolleyStyle(
        val hitType: HitType,
        val cast: String,
        val projanim: String,
        val impact: String,
        val castSound: String,
        val travelSound: String,
        val impactSound: String,
    ) {
        Magic(
            HitType.Magic,
            "spotanim.vfx_ocellus_water_attack01_spotanim",
            "spotanim.vfx_ocellus_water_attack01_projanim",
            "spotanim.vfx_ocellus_water_attack01_impactanim",
            "synth.ocellus_water_attack_cast",
            "synth.ocellus_water_attack_travel",
            "synth.mark_of_darkness_hit",
        ),
        Ranged(
            HitType.Ranged,
            "spotanim.vfx_ocellus_fire_attack01_spotanim",
            "spotanim.vfx_ocellus_fire_attack01_projanim",
            "spotanim.vfx_ocellus_fire_attack01_impactanim",
            "synth.ocellus_fire_attack_cast",
            "synth.ocellus_fire_attack_travel",
            "synth.leather_hit",
        );

        val other: VolleyStyle
            get() = if (this == Magic) Ranged else Magic
    }

    private companion object {
        const val WALKING = "walking"
        const val CORNER_CYCLE = "corner_cycle"
        const val CENTRE = "centre"
        const val BROKEN = "broken"

        const val START = "start"
        const val WALK = "walk"
        const val VOLLEY_SLOT = "volley_slot"
        const val VOLLEY_START = "volley_start"
        const val VOLLEY_END = "volley_end"
        const val NEXT = "next"
        const val BREAK = "shield_break"

        const val PHASE_TWO = "phase_two"

        val PHASE_TWO_LINES =
            List(3) { "Your time... comes..." } + "Pointless..."
        const val WALLS_SPAWN_AT = 2
        const val OPEN_AT = 7
        val WALL_WINDOWS = listOf(11, 53, 95)
        val ROTATIONS = listOf(39, 81)
        const val PHASE_THREE = "phase_three"
        const val ATTACK_CYCLE = "attack_cycle"
        const val ATTACK_CYCLE_CAP = 20
        const val SPECIAL = "special"
        const val CYCLES_PER_SPECIAL = 2
        const val QUADRANT_STEPS = 10
        const val DEBRIS_TILES = "debris"
        const val BOMB_TILES = "bombs"
        const val BOMB_MARK_AT = 1
        const val BOMB_IDLE_TO_DROP = 2
        const val BOMB_IDLE_LOOP_DELAY = 30
        const val BOMB_HEIGHT = 255
        const val BOMB_IDLE_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_idle_01"
        const val BOMB_DROP_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_drop_01"
        const val BOMB_IMPACT_SPOT = "spotanim.vfx_ocellus_quadrant_bomb_impact_01"
        // PLACEHOLDER: an estimate; no source for the real figure.
        val DEBRIS_DAMAGE = 35..55
        const val DEBRIS_MARKS_SHORT = 3
        const val DEBRIS_MARKS_LOOPED = 6
        const val DEBRIS_FALL = 5
        const val DEBRIS_OUT = 5
        const val DEBRIS_LOOP = 3
        const val DEBRIS_LOOP_SPOT = 3
        const val DEBRIS_RECOVERY = 4
        const val SPIN_HIT_DELAY = 1
        const val DEBRIS_VINE_DELAY = 4
        const val PRAYER_IN_SLOT = 4
        const val PRAYER_SLOT = 2
        const val PRAYER_IN_SEQ = "seq.npc_ocellus_phase3_prayer_in"
        const val PRAYER_LOOP_SEQ = "seq.npc_ocellus_phase3_prayer_loop"
        const val PRAYER_OUT_SEQ = "seq.npc_ocellus_phase3_prayer_out"
        const val PRAYER_IN_SPOT = "spotanim.vfx_ocellus_phase3_melee_prayer_in"
        const val PRAYER_LOOP_SPOT = "spotanim.vfx_ocellus_phase3_melee_prayer"
        const val PRAYER_OUT_SPOT = "spotanim.vfx_ocellus_phase3_melee_prayer_out"
        const val DEBRIS_SPOT = "spotanim.vfx_ocellus_falling_debris"
        const val UNTETHER_IN_SEQ = "seq.npc_ocellus_phase3_unleash_in"
        const val UNTETHER_LOOP_SEQ = "seq.npc_ocellus_phase3_unleash_loop"
        const val UNTETHER_OUT_SEQ = "seq.npc_ocellus_phase3_unleash_out"
        const val UNTETHER_DOT_SPOT = "spotanim.vfx_ocellus_p2_aoe_dodge"
        const val UNTETHER_GLOW_SPOT = "spotanim.vfx_ocellus_p3_unleash_spotanim"
        const val UNTETHER_GLOW_DELAY = 360
        val UNTETHER_ARENA_SPOTS =
            listOf(
                "spotanim.vfx_ocellus_unleash_arena",
                "spotanim.vfx_ocellus_unleash_arena_270",
                "spotanim.vfx_ocellus_unleash_arena_180",
                "spotanim.vfx_ocellus_unleash_arena_90",
            )
        const val UNTETHER_LOOP_AT = 2
        const val UNTETHER_FIRST_WAVE = 6
        const val UNTETHER_WAVE_GAP = 4
        const val UNTETHER_WAVES = 4
        const val UNTETHER_GLOW_AT = 18
        const val UNTETHER_BORDER_AT = 25
        const val UNTETHER_ERUPT_AT = 28
        const val UNTETHER_OUT_AT = 29
        const val UNTETHER_SLAM_AT = 29
        const val UNTETHER_PERSONAL = "ocellus.untether_personal"
        const val UNTETHER_PERSONAL_SPOT = "spotanim.vfx_ocellus_magic_projanim02"
        const val UNTETHER_PERSONAL_SLOT = 2
        const val UNTETHER_PERSONAL_LEAD = 1
        const val UNTETHER_IMPACT = "ocellus.untether_impact"
        const val UNTETHER_IMPACT_DELAY = 120
        const val UNTETHER_RECOVERY = 8
        const val UNTETHER_RIFT_DELAY = 3
        val RIFT_KINDS = listOf(-1, 0, 1, 2)
        const val UNTETHER_REACH = 12
        val UNTETHER_DOTS = 12..15
        // PLACEHOLDER: every capture shows the magic hit fully prayed against (0); this is the spread's figure.
        val UNTETHER_MAGIC_DAMAGE = 20..22
        val UNTETHER_POOL_DAMAGE = 5..25
        val UNTETHER_ERUPTION_DAMAGE = 35..45
        val UNTETHER_SLAM_DAMAGE = 18..45
        const val INSOLENCE_SEQ = "seq.npc_ocellus_phase3_melee_superior_slash"
        const val INSOLENCE_SPOT = "spotanim.vfx_ocellus_p3_shadow_slash_superior_spotanim01"
        const val INSOLENCE_ANIM_DELAY = 30
        const val INSOLENCE_CAST_TO_SWEEP = 3
        const val INSOLENCE_SWEEP_DELAY = 2
        const val INSOLENCE_RECOVERY = 4
        const val INSOLENCE_DAMAGE = 43
        const val MELEE_BASIC_SEQ = "seq.npc_ocellus_phase3_melee_basic"
        const val MELEE_SWING_GAP = 3
        const val SLASH_RECOVERY = 2
        const val SPREAD_GAP = 6
        const val SPREAD_SEQ = "seq.npc_ocellus_phase3_basic_projectile_all"
        const val SPREAD_CAST_SPOT = "spotanim.vfx_ocellus_magic_spotanim01"
        const val SPREAD_CAST_SLOT = 4
        const val SPREAD_CAST_DELAY = 30
        const val SPREAD_SOUND_DELAY = 25
        const val SPREAD_PROJECTILE = "spotanim.vfx_ocellus_magic_projanim01"
        const val SPREAD_IMPACT = "spotanim.vfx_ocellus_magic_projanim_impact01"
        const val SPREAD_START_HEIGHT = 435
        const val SPREAD_END_HEIGHT = 124
        const val SPREAD_DELAY = 45
        const val SPREAD_TRAVEL = 15
        const val SPREAD_ANGLE = 100
        val SPREAD_DAMAGE = 20..22
        const val SLASH_SEQ = "seq.npc_ocellus_phase3_melee_slash"
        const val SLASH_SPOT = "spotanim.vfx_ocellus_p3_shadow_slash_spotanim01"
        const val SLASH_DAMAGE = 27
        const val DESTROY = "destroy_quadrant"
        const val SKIP_TO_THREE = "skip_to_three"
        const val QUADRANT_STAGE = 10
        val QUADRANT_THRESHOLDS = listOf(0.74, 0.51, 0.26)
        const val QUADRANT_ENTER_AT = 2
        const val ATTACK_HOLD = 30
        const val ENTER_TO_FIRST_ATTACK = 4
        const val QUADRANT_FADE_END = 28
        const val DESTROY_BAR = "headbar.ocellus_quadrant_destroy"
        const val DESTROY_BAR_CYCLES = 330
        const val DESTROY_OUT_AT = 11
        const val DESTROY_COLLAPSE_AT = 13
        const val DESTROY_MOVE_AT = 17
        const val DESTROY_FADE_END = 30
        const val COLLAPSE_OUT_SLOT = 4
        const val SWORD_IDLE_AT = 39
        const val ENDING_AT = 123
        const val CRACK_AT = 127
        const val PHASE_THREE_AT = 131
        const val GAP_WALLS_AT = 135
        const val ARM_GLOW_DELAY = 150
        const val ROOT_FIRE_SLOT = 5
        const val NULL_SPOT_HEIGHT = 154
        const val NULL_SPOT_DELAY = 300
        const val WALL_ECHO_DELAY = 120
        const val CLOSED_ECHO_DELAY = 270
        const val POOL_ECHO_DELAY = 90
        const val DISCHARGE_VFX_RADIUS = 15
        const val DISCHARGE_HIT_RADIUS = 10
        const val CLOSED_TELEGRAPH_AT = 3
        const val CLOSED_FIRE_DELAY = 9
        const val POOLS_DETONATE_AT = 4
        const val PORTALS_AT = 5
        const val PORTAL_TICKS = 6
        const val WALL_CYCLES = 6
        const val WALL_CYCLE_GAP = 5
        const val WALL_FIRE_DELAY = 4
        const val FIRST_DISCHARGE_VFX = 45
        const val DISCHARGE_VFX_DELAY = 30
        const val DISCHARGE_GAP = 42
        const val LAST_DISCHARGE_GAP = 35

        const val CHARGE_SEQ = "seq.npc_ocellus_phase1_charge"
        const val EXPEND_SEQ = "seq.npc_ocellus_phase1_expend"
        const val VOLLEY_START_SEQ = "seq.npc_ocellus_phase1_basic_projectile_start"
        const val VOLLEY_END_SEQ = "seq.npc_ocellus_phase1_basic_projectile_end"

        const val IMMUNE_MELEE = "varn.immune_melee"
        const val HEADICON_SLOT = 0
        const val HEADICON_GRAPHIC = 440
        const val HEADICON_MELEE = 0
        const val HEADICON_SOUL_SPLIT = 11
        const val HEADICON_DEFLECT_MISSILES = 15
        const val HEADICON_DEFLECT_MELEE = 16

        const val CURSES = "curses"
        const val CURSES_LENGTH = 60
        const val CURSES_FRAGMENT_AT = 3
        const val CURSES_DEFLECT_OUT_AT = 21
        const val CURSES_HYBRID_AT = 24
        const val CURSES_SOUL_SPLIT_AT = 25
        const val CURSES_MELEE_ARENA_AT = 30
        const val CURSES_DEFLECT_MELEE_AT = 31
        const val CURSES_SECOND_FRAGMENT_AT = 34
        const val CURSES_FRAGMENT_END_AT = 50
        const val CURSES_SECOND_HYBRID_AT = 52
        const val CURSES_SECOND_SOUL_SPLIT_AT = 53
        const val HYBRID_HIT_DELAY = 4
        const val BOSS_SPOT_SLOT = 2
        const val DEATH_SEQ = "seq.npc_ocellus_phase3_death"
        const val DEATH_REMOVE_DELAY = 16
        const val DEATH_FX_DELAY = 1
        const val DEATH_SPOT_SLOT = 5
        const val DEATH_CRACK_SLOT = 3
        const val DEATH_SKIRT_SPOT = "spotanim.vfx_ocellus_death_skirt_spotanim01"
        const val DEATH_SPOT = "spotanim.vfx_ocellus_death"
        const val DEATH_CRACK_SPOT = "spotanim.vfx_ocellus_death_crack_spotanim01"
        // PLACEHOLDER: both captured hybrid hits landed for 7 and 8 on a party whose prayers can't be seen.
        val CURSES_HYBRID_DAMAGE = 7..8
        const val DEFLECT_IN_SEQ = "seq.npc_ocellus_phase3_deflect_in"
        const val DEFLECT_LOOP_SEQ = "seq.npc_ocellus_phase3_deflect_loop"
        const val DEFLECT_OUT_SEQ = "seq.npc_ocellus_phase3_deflect_out"
        const val SOUL_START_SEQ = "seq.npc_ocellus_phase3_soul_fragment_start"
        const val SOUL_END_SEQ = "seq.npc_ocellus_phase3_soul_fragment_end"
        const val SOUL_IDLE_SPOT = "spotanim.vfx_ocellus_soul_fragment_ocellus_idle"
        const val SOUL_LEVITATING_SPOT = "spotanim.vfx_ocellus_soul_fragment_ocellus_levitating_idle"
        const val HYBRID_SEQ = "seq.npc_ocellus_phase3_melee_hybrid"
        const val HYBRID_MAGIC_SPOT = "spotanim.vfx_ocellus_phase3_melee_hybrid_magic"
        const val HYBRID_RANGED_SPOT = "spotanim.vfx_ocellus_phase3_melee_hybrid_ranged"

        const val NOT_ARRIVED = 0
        const val CAST_CLOSED = 1
        const val CAST_OPEN = 2

        val THRESHOLDS = listOf(0.66, 0.25)

        const val TELEPORT_SPOT_SLOT = 2
        const val TELEPORT_TICKS = 4
        const val TRANSFORM_TO_P2 = 7
        const val OPAQUE = 0
        const val INVISIBLE = 255
        const val FADE_OUT_START = 30
        const val FADE_OUT_END = 45
        const val FADE_IN_START = 0
        const val FADE_IN_END = 20
        const val FACE_DISTANCE = 10

        const val VOLLEY_GAP = 5
        const val VOLLEY_CLOSE_RANGE = 4
        const val VOLLEY_MAX_HIT = 30
        const val STYLE_SWITCH_ONE_IN = 4
        const val VOLLEY_TRAVEL_SOUND_DELAY = 30
        const val VOLLEY_START_HEIGHT = 578
        const val VOLLEY_END_HEIGHT = 124
        const val VOLLEY_DELAY = 30
        const val VOLLEY_ANGLE = 40
        const val VOLLEY_CLOSE_TRAVEL = 65
        const val VOLLEY_FAR_TRAVEL = 95

        const val CORNER_CHARGES = 7
        const val CHARGE_GAP = 2
        const val CORNER_EXPLODE = 18
        const val EXPLODE_TO_MOVE = 3

        const val CENTRE_FIRST_CHARGE = 4
        const val CENTRE_ROUNDS = 4
        const val CENTRE_ROUND_GAP = 8
        const val CENTRE_CHARGE_TO_EXPLODE = 6
        const val EXPLODE_TO_RELEASE = 2
        const val RELEASE_TO_EXPEND = 2
        const val EXPEND_TO_MOVE = 7

        const val BOSS_SIZE = 3
        const val SLAM_LAND = 1
        const val SLAM_OUTER_DELAY = 2
        val SLAM_DAMAGE = 31..35
        val SLAM_FLOOR =
            listOf(
                "spotanim.vfx_ocellus_p1_explode_floor",
                "spotanim.vfx_ocellus_p1_explode_floor_90",
                "spotanim.vfx_ocellus_p1_explode_floor_180",
                "spotanim.vfx_ocellus_p1_explode_floor_270",
            )
    }
}
