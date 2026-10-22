package org.rsmod.content.raids.tfa.raid.encounter.ocellus

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
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.ARRIVED_AT
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.CENTRE_SIDE
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.CORNER
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.METER
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.THRESHOLD
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusVarns.VOLLEY_STYLE
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class OcellusBoss
@Inject
constructor(
    deps: BossDeps,
    private val encounter: OcellusEncounter,
    private val hpBar: BossHpBarScript,
) : BossPluginScript(deps) {
    override fun ScriptContext.startup() {
        OcellusTendrils(deps, encounter.meter).register()
        OcellusBarrage(deps, encounter.meter).register()
        encounter.phaseTwo.register()
        BossCombat.register(
            this,
            spec,
            deps,
            onCombatTick = { encounter.debug.noteCombatTick() },
            onHit = {
                for (player in OcellusArena.players(npc, deps.playerList)) hpBar.onUpdate(player, npc)
            },
        )
    }

    override val spec: BossSpec =
        boss("npc.tfa_ocellus_intro", "npc.tfa_ocellus_p1", "npc.tfa_ocellus_p3") {
            phase(WALKING, scriptedMovement = true) { every(VOLLEY_GAP, run(VOLLEY_SLOT)) }
            phase(CORNER_CYCLE, scriptedMovement = true) {}
            phase(CENTRE, scriptedMovement = true, lockMovement = true) {}
            phase(BROKEN, scriptedMovement = true, lockMovement = true) {}
            phase(PHASE_TWO, scriptedMovement = true, lockMovement = true) {}
            phase(PHASE_THREE, scriptedMovement = true, lockMovement = true) {}

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
            ability(DESTROY, destroyQuadrant())
            ability(SKIP_TO_THREE, skipToPhaseThree())

            triggers {
                on(Condition.Always) runs run(START)
                val shielded =
                    Condition.Not(Condition.InPhase(PHASE_TWO)) and
                        Condition.Not(Condition.InPhase(PHASE_THREE))
                on(Condition.HpExact(1) and shielded) runs run(BREAK)
                on(varnIs(THRESHOLD, OcellusVarns.SKIP_PHASE_TWO) and Condition.InPhase(PHASE_TWO)) runs
                    sequence(interrupt(), run(SKIP_TO_THREE))
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
            setVarn(THRESHOLD, QUADRANT_STAGE),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = OPAQUE, to = INVISIBLE),
            wait(QUADRANT_ENTER_AT),
            external(OcellusPhaseTwo.QUADRANT_ENTER),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = INVISIBLE, to = OPAQUE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_SPAWN),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER),
        )

    /** Timed from the tick health crosses a threshold; the quadrant goes and the boss moves on. */
    private fun destroyQuadrant(): Effect =
        sequence(
            anim("seq.npc_ocellus_quadrant_destroy_in"),
            idleAnim("seq.npc_ocellus_quadrant_destroy_loop"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_in", slot = TELEPORT_SPOT_SLOT),
            oneOf(say("Run... It matters not..."), say("Obliteration...")),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_DESPAWN),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_REMOVE),
            wait(1),
            anim("seq.npc_ocellus_quadrant_destroy_loop"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_loop", slot = TELEPORT_SPOT_SLOT),
            wait(DESTROY_OUT_AT - 2),
            clearIdleAnim(),
            anim("seq.npc_ocellus_phase3_unleash_out_90f"),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_out_01", slot = TELEPORT_SPOT_SLOT),
            spotanim("spotanim.vfx_ocellus_phase3_arena_collapse_out_02", slot = COLLAPSE_OUT_SLOT),
            oneOf(say("Everything... ends..."), say("You can't... escape... the end...")),
            wait(1),
            transparency(FADE_IN_START, DESTROY_FADE_END, from = OPAQUE, to = INVISIBLE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_COLLAPSE),
            wait(DESTROY_MOVE_AT - DESTROY_COLLAPSE_AT),
            external(OcellusPhaseTwo.QUADRANT_ENTER),
            transparency(FADE_IN_START, QUADRANT_FADE_END, from = INVISIBLE, to = OPAQUE),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_STONES_FALL),
            external(OcellusPhaseTwo.QUADRANT_BARRIER_SPAWN),
            whenever(varnIs(THRESHOLD, QUADRANT_STAGE + QUADRANT_THRESHOLDS.size), say("End...")),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_BARRIER),
            wait(1),
            external(OcellusPhaseTwo.QUADRANT_STONES_BLOCK),
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
        const val DESTROY = "destroy_quadrant"
        const val SKIP_TO_THREE = "skip_to_three"
        const val QUADRANT_STAGE = 10
        val QUADRANT_THRESHOLDS = listOf(0.74, 0.51, 0.26)
        const val QUADRANT_ENTER_AT = 2
        const val QUADRANT_FADE_END = 28
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
