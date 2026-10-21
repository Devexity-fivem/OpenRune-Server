package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * Phase 1 choreography: walk counter-clockwise between the four corners charging tendrils at each,
 * and jump to the centre for a charge cycle every time the shield crosses a threshold; a broken
 * shield interrupts whatever is running. Timings come from the 5-man kill capture
 * (`docs/ocellus-plan.md`). Tendrils, AoE and volleys are not implemented yet, only the boss
 * animations.
 */
class OcellusPhaseOne(
    private val deps: BossDeps,
    private val boss: Npc,
    private val onShieldBroken: () -> Unit,
) {
    private var state = State.WALKING
    private var stateTick = 0
    private var corner = deps.random.of(CORNERS.size)
    private var thresholdIndex = 0
    private var shieldBroken = false
    private var arrived = false

    fun start() {
        walkToCorner()
    }

    fun tick() {
        stateTick++
        if (state != State.TELEPORTING && state != State.BROKEN && boss.hitpoints <= 1) {
            breakOrTeleport()
            return
        }
        when (state) {
            State.WALKING -> tickWalking()
            State.CORNER -> tickCorner()
            State.TELEPORTING -> tickTeleport()
            State.CENTRE -> tickCentre()
            State.BROKEN -> Unit
        }
    }

    private fun tickWalking() {
        if (teleportOnThreshold()) return
        if (arrived && stateTick >= ARRIVAL_TO_CHARGE) {
            enter(State.CORNER)
            charge()
        }
    }

    private fun tickCorner() {
        when {
            stateTick < CORNER_CHARGES * CHARGE_GAP && stateTick % CHARGE_GAP == 0 -> charge()
            stateTick == CORNER_EXPLODE -> explode()
            stateTick == CORNER_EXPLODE + EXPLODE_TO_MOVE ->
                if (!teleportOnThreshold()) {
                    corner = (corner + 1) % CORNERS.size
                    walkToCorner()
                }
        }
    }

    private fun tickTeleport() {
        if (stateTick != TELEPORT_TICKS) return
        boss.telejump(deps.collision, CENTRE)
        boss.anim("seq.npc_ocellus_phase1_teleport_in")
        boss.spotanim("spotanim.vfx_ocellus_p1_teleport_in", slot = TELEPORT_SPOT_SLOT)
        if (shieldBroken) {
            breakShield()
        } else {
            enter(State.CENTRE)
        }
    }

    private fun tickCentre() {
        val sinceFirstCharge = stateTick - CENTRE_FIRST_CHARGE
        if (sinceFirstCharge >= 0 && sinceFirstCharge / CENTRE_ROUND_GAP < CENTRE_ROUNDS) {
            when (sinceFirstCharge % CENTRE_ROUND_GAP) {
                0 -> charge()
                CENTRE_CHARGE_TO_EXPLODE -> explode()
            }
            return
        }
        when (stateTick) {
            CENTRE_EXPEND -> boss.anim("seq.npc_ocellus_phase1_expend")
            CENTRE_EXPEND + EXPEND_TO_MOVE -> if (!teleportOnThreshold()) walkToCorner()
        }
    }

    private fun teleportOnThreshold(): Boolean {
        if (thresholdIndex > THRESHOLDS.lastIndex) return false
        if (boss.hitpoints > boss.baseHitpointsLvl * THRESHOLDS[thresholdIndex]) return false
        thresholdIndex++
        teleportOut()
        return true
    }

    private fun breakOrTeleport() {
        shieldBroken = true
        if (boss.coords == CENTRE) breakShield() else teleportOut()
    }

    private fun teleportOut() {
        boss.resetMovement()
        boss.anim("seq.npc_ocellus_phase1_teleport_out")
        boss.spotanim("spotanim.vfx_ocellus_p1_teleport_out", slot = TELEPORT_SPOT_SLOT)
        enter(State.TELEPORTING)
    }

    private fun walkToCorner() {
        arrived = false
        enter(State.WALKING)
        boss.walk(CORNERS[corner]) {
            arrived = true
            stateTick = 0
        }
    }

    private fun charge() {
        boss.anim("seq.npc_ocellus_phase1_charge")
        deps.worldQueues.add(1) {
            if (boss.isSlotAssigned) boss.spotanim("spotanim.vfx_ocellus_p1_charge")
        }
    }

    private fun explode() {
        boss.anim("seq.npc_ocellus_phase1_explode")
        boss.spotanim("spotanim.vfx_ocellus_p1_explode_npc")
    }

    private fun breakShield() {
        enter(State.BROKEN)
        boss.anim("seq.npc_ocellus_phase1_transformation")
        onShieldBroken()
    }

    private fun enter(next: State) {
        state = next
        stateTick = 0
    }

    private enum class State {
        WALKING,
        CORNER,
        TELEPORTING,
        CENTRE,
        BROKEN,
    }

    private companion object {
        val CORNERS =
            listOf(
                CoordGrid(4132, 5678, 1),
                CoordGrid(4120, 5678, 1),
                CoordGrid(4120, 5666, 1),
                CoordGrid(4132, 5666, 1),
            )
        val CENTRE = CoordGrid(4126, 5672, 1)
        val THRESHOLDS = doubleArrayOf(0.66, 0.25)

        const val TELEPORT_SPOT_SLOT = 2

        const val ARRIVAL_TO_CHARGE = 2
        const val CORNER_CHARGES = 7
        const val CHARGE_GAP = 2
        const val CORNER_EXPLODE = 18
        const val EXPLODE_TO_MOVE = 3

        const val TELEPORT_TICKS = 4
        const val CENTRE_FIRST_CHARGE = 4
        const val CENTRE_ROUNDS = 4
        const val CENTRE_ROUND_GAP = 8
        const val CENTRE_CHARGE_TO_EXPLODE = 6
        const val EXPLODE_TO_EXPEND = 4
        const val CENTRE_EXPEND =
            CENTRE_FIRST_CHARGE +
                (CENTRE_ROUNDS - 1) * CENTRE_ROUND_GAP +
                CENTRE_CHARGE_TO_EXPLODE +
                EXPLODE_TO_EXPEND
        const val EXPEND_TO_MOVE = 7
    }
}
