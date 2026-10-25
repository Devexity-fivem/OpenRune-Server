package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class HunterTrapManager
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
) {
    private val traps = mutableListOf<BirdSnare>()

    fun activeTraps(player: Player): Int = traps.count { it.owner == player }

    fun maxTraps(level: Int): Int =
        when {
            level >= 80 -> 5
            level >= 60 -> 4
            level >= 40 -> 3
            else -> 2
        }

    fun trapAt(coords: CoordGrid): BirdSnare? = traps.firstOrNull { it.coords == coords }

    fun layTrap(player: Player, coords: CoordGrid): BirdSnare? {
        if (activeTraps(player) >= maxTraps(player.hunterLvl)) {
            return null
        }
        val loc = locRepo.add(coords, SET_TRAP, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        val trap = BirdSnare(player, loc, coords)
        traps += trap
        return trap
    }

    /**
     * Removes the trap loc and stops tracking. Returns true when the snare item should be
     * returned to the player; a caught trap returns its snare through [check] instead.
     */
    fun dismantle(trap: BirdSnare): Boolean {
        removeTrap(trap)
        return trap.state != State.CAUGHT
    }

    fun reset(trap: BirdSnare) {
        if (trap.state != State.FAILED && trap.state != State.CAUGHT) return
        transition(trap, SET_TRAP)
        trap.state = State.ARMED
        trap.scanCycle = 0
        trap.bird = null
        trap.birdNpc = null
    }

    fun birdFor(trap: BirdSnare): HunterBird? = trap.bird

    fun clear(player: Player) {
        traps.filter { it.owner == player }.forEach(::removeTrap)
    }

    fun tick() {
        for (trap in traps.toList()) {
            when (trap.state) {
                State.ARMED -> tickArmed(trap)
                State.APPROACHING -> tickApproach(trap)
                State.TRAPPING -> tickTrapping(trap)
                State.FAILING -> tickFailing(trap)
                State.CAUGHT, State.FAILED -> Unit
            }
        }
    }

    private fun tickArmed(trap: BirdSnare) {
        if (trap.owner.coords == trap.coords) return
        trap.scanCycle++
        if (trap.scanCycle < SCAN_TICKS) return
        trap.scanCycle = 0
        val npc = nearestBird(trap.coords, trap.owner.hunterLvl, targetedNpcs())
        if (npc == null) return
        trap.birdNpc = npc
        trap.bird = birdsByNpcId[npc.type.id]
        trap.state = State.APPROACHING
    }

    private fun tickApproach(trap: BirdSnare) {
        val npc = trap.birdNpc
        if (npc == null || !npc.isVisible) {
            trap.state = State.ARMED
            trap.birdNpc = null
            return
        }
        if (npc.coords == trap.coords) {
            resolveLanding(trap, npc)
            return
        }
        val dx = (trap.coords.x - npc.coords.x).coerceIn(-1, 1)
        val dz = (trap.coords.z - npc.coords.z).coerceIn(-1, 1)
        npc.teleport(collision, npc.coords.translate(dx, dz))
    }

    private fun resolveLanding(trap: BirdSnare, npc: Npc) {
        val bird = trap.bird ?: run {
            trap.state = State.ARMED
            trap.birdNpc = null
            return
        }
        npcRepo.del(npc, BIRD_RESPAWN_TICKS)
        trap.birdNpc = null
        if (catchRoll(trap.owner.hunterLvl, bird.level)) {
            transition(trap, bird.trappingLoc)
            trap.state = State.TRAPPING
            trap.stateCycle = 0
        } else {
            transition(trap, FAILING_TRAP)
            trap.state = State.FAILING
            trap.stateCycle = 0
        }
    }

    private fun tickTrapping(trap: BirdSnare) {
        if (++trap.stateCycle < TRAPPING_TICKS) return
        val bird = trap.bird
        if (bird == null) {
            removeTrap(trap)
            return
        }
        transition(trap, bird.fullLoc)
        trap.state = State.CAUGHT
    }

    private fun tickFailing(trap: BirdSnare) {
        if (++trap.stateCycle < FAILING_TICKS) return
        transition(trap, BROKEN_TRAP)
        trap.state = State.FAILED
    }

    /**
     * Replaces the trap loc with its next state. The returned [LocInfo] must be tracked: loc
     * deletion matches the exact entity, so keeping the stale info would orphan the loc.
     */
    private fun transition(trap: BirdSnare, internal: String) {
        trap.loc = locRepo.add(trap.coords, internal, Int.MAX_VALUE, trap.loc.angle, trap.loc.shape)
    }

    fun check(trap: BirdSnare): HunterBird? {
        val bird = trap.bird ?: return null
        if (trap.state != State.CAUGHT) return null
        removeTrap(trap)
        return bird
    }

    private fun removeTrap(trap: BirdSnare) {
        locRepo.del(trap.loc, Int.MAX_VALUE)
        traps.remove(trap)
    }

    private fun targetedNpcs(): Set<Npc> =
        traps.mapNotNull { it.birdNpc }.toSet()

    private fun nearestBird(
        coords: CoordGrid,
        hunterLvl: Int,
        targeted: Set<Npc>,
    ): Npc? {
        val zone = ZoneKey.from(coords)
        var best: Npc? = null
        var bestDist = Int.MAX_VALUE
        for (npc in npcRepo.findAll(zone, BIRD_SCAN_ZONE_RADIUS)) {
            if (!npc.isVisible || npc.coords.level != coords.level) continue
            val bird = birdsByNpcId[npc.type.id] ?: continue
            if (npc in targeted || bird.level > hunterLvl) continue
            val dist = chebyshev(npc.coords, coords)
            if (dist < bestDist) {
                best = npc
                bestDist = dist
            }
        }
        return best
    }

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int =
        maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    var catchChance: (level: Int, required: Int) -> Double = { level, required ->
        (0.55 + (level - required) * 0.02).coerceIn(0.10, 0.95)
    }

    private fun catchRoll(level: Int, required: Int): Boolean =
        Random.nextDouble() < catchChance(level, required)

    fun registerBirds(birds: List<HunterBird>) {
        birdsByNpcId.clear()
        for (bird in birds) {
            birdsByNpcId[bird.npcId] = bird
        }
    }

    private val birdsByNpcId = mutableMapOf<Int, HunterBird>()

    enum class State {
        ARMED,
        APPROACHING,
        TRAPPING,
        CAUGHT,
        FAILING,
        FAILED,
    }

    class BirdSnare(
        val owner: Player,
        var loc: LocInfo,
        val coords: CoordGrid,
        var state: State = State.ARMED,
        var bird: HunterBird? = null,
        var birdNpc: Npc? = null,
        var scanCycle: Int = 0,
        var stateCycle: Int = 0,
    )

    class HunterBird(
        val name: String,
        val npcSymbol: String,
        val npcId: Int,
        val level: Int,
        val xpTenths: Int,
        val fullLoc: String,
        val trappingLoc: String,
        val feather: String,
        val featherMin: Int,
        val featherMax: Int,
    )

    companion object {
        const val SET_TRAP = "loc.hunting_ojibway_trap"
        const val BROKEN_TRAP = "loc.hunting_ojibway_trap_broken"
        const val FAILING_TRAP = "loc.hunting_ojibway_trap_failing"

        const val SCAN_TICKS = 4
        const val BIRD_SCAN_ZONE_RADIUS = 1
        const val TRAPPING_TICKS = 4
        const val FAILING_TICKS = 3
        const val BIRD_RESPAWN_TICKS = 100
    }
}
