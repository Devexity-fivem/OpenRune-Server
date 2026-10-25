package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.boolVarBit
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
    private val traps = mutableListOf<HunterTrap>()
    private val preyByNpcId = mutableMapOf<TrapKind, Map<Int, TrapPrey>>()

    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    fun activeTraps(player: Player): Int = traps.count { it.owner == player }

    fun trapLimit(player: Player, kind: TrapKind): Int {
        val base =
            when {
                player.hunterLvl >= 80 -> 5
                player.hunterLvl >= 60 -> 4
                player.hunterLvl >= 40 -> 3
                else -> 2
            }
        // An extra box trap may be set while hunting in the Wilderness.
        return if (kind == TrapKind.BOX && player.insideWilderness) base + 1 else base
    }

    fun trapAt(coords: CoordGrid): HunterTrap? = traps.firstOrNull { it.coords == coords }

    fun layTrap(player: Player, coords: CoordGrid, kind: TrapKind): HunterTrap? {
        if (activeTraps(player) >= trapLimit(player, kind)) {
            return null
        }
        val loc =
            locRepo.add(coords, kind.armedLoc, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        val trap = HunterTrap(player, loc, coords, kind)
        traps += trap
        return trap
    }

    /**
     * Removes the trap loc and stops tracking. Returns true when the trap item should be
     * returned to the player; a caught trap returns it through [check] instead.
     */
    fun dismantle(trap: HunterTrap): Boolean {
        removeTrap(trap)
        return trap.state != State.CAUGHT
    }

    fun reset(trap: HunterTrap) {
        if (trap.state != State.FAILED && trap.state != State.CAUGHT) return
        transition(trap, trap.kind.armedLoc)
        trap.state = State.ARMED
        trap.scanCycle = 0
        trap.prey = null
        trap.preyNpc = null
    }

    fun preyFor(trap: HunterTrap): TrapPrey? = trap.prey

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

    private fun tickArmed(trap: HunterTrap) {
        if (trap.owner.coords == trap.coords) return
        trap.scanCycle++
        if (trap.scanCycle < SCAN_TICKS) return
        trap.scanCycle = 0
        val npc = nearestPrey(trap, targetedNpcs()) ?: return
        trap.preyNpc = npc
        trap.prey = preyByNpcId[trap.kind]?.get(npc.type.id)
        trap.state = State.APPROACHING
        trap.fromDir = null
    }

    private fun tickApproach(trap: HunterTrap) {
        val npc = trap.preyNpc
        if (npc == null || !npc.isVisible) {
            trap.state = State.ARMED
            trap.preyNpc = null
            return
        }
        if (npc.coords == trap.coords) {
            resolveLanding(trap, npc)
            return
        }
        val dx = (trap.coords.x - npc.coords.x).coerceIn(-1, 1)
        val dz = (trap.coords.z - npc.coords.z).coerceIn(-1, 1)
        if (dx != 0) {
            trap.fromDir = if (dx > 0) Dir.WEST else Dir.EAST
        } else if (dz != 0) {
            trap.fromDir = if (dz > 0) Dir.SOUTH else Dir.NORTH
        }
        npc.teleport(collision, npc.coords.translate(dx, dz))
    }

    private fun resolveLanding(trap: HunterTrap, npc: Npc) {
        val prey = trap.prey ?: run {
            trap.state = State.ARMED
            trap.preyNpc = null
            return
        }
        npcRepo.del(npc, PREY_RESPAWN_TICKS)
        trap.preyNpc = null
        if (catchRoll(trap.owner.hunterLvl, prey.level)) {
            transition(trap, prey.trappingLoc(trap.fromDir))
            trap.state = State.TRAPPING
            trap.stateCycle = 0
        } else {
            transition(trap, trap.kind.failingLoc)
            trap.state = State.FAILING
            trap.stateCycle = 0
        }
    }

    private fun tickTrapping(trap: HunterTrap) {
        if (++trap.stateCycle < TRAPPING_TICKS) return
        val prey = trap.prey
        if (prey == null) {
            removeTrap(trap)
            return
        }
        transition(trap, prey.fullLoc)
        trap.state = State.CAUGHT
    }

    private fun tickFailing(trap: HunterTrap) {
        if (++trap.stateCycle < FAILING_TICKS) return
        transition(trap, trap.kind.brokenLoc)
        trap.state = State.FAILED
    }

    /**
     * Replaces the trap loc with its next state. The returned [LocInfo] must be tracked: loc
     * deletion matches the exact entity, so keeping the stale info would orphan the loc.
     */
    private fun transition(trap: HunterTrap, internal: String) {
        trap.loc = locRepo.add(trap.coords, internal, Int.MAX_VALUE, trap.loc.angle, trap.loc.shape)
    }

    fun check(trap: HunterTrap): TrapPrey? {
        val prey = trap.prey ?: return null
        if (trap.state != State.CAUGHT) return null
        removeTrap(trap)
        return prey
    }

    private fun removeTrap(trap: HunterTrap) {
        locRepo.del(trap.loc, Int.MAX_VALUE)
        traps.remove(trap)
    }

    private fun targetedNpcs(): Set<Npc> = traps.mapNotNull { it.preyNpc }.toSet()

    private fun nearestPrey(trap: HunterTrap, targeted: Set<Npc>): Npc? {
        val preyMap = preyByNpcId[trap.kind] ?: return null
        val zone = ZoneKey.from(trap.coords)
        val hunterLvl = trap.owner.hunterLvl
        var best: Npc? = null
        var bestDist = Int.MAX_VALUE
        for (npc in npcRepo.findAll(zone, PREY_SCAN_ZONE_RADIUS)) {
            if (!npc.isVisible || npc.coords.level != trap.coords.level) continue
            val prey = preyMap[npc.type.id] ?: continue
            if (npc in targeted || prey.level > hunterLvl) continue
            val dist = chebyshev(npc.coords, trap.coords)
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

    fun registerPrey(kind: TrapKind, prey: List<TrapPrey>) {
        preyByNpcId[kind] = prey.associateBy { it.npcId }
    }

    enum class State {
        ARMED,
        APPROACHING,
        TRAPPING,
        CAUGHT,
        FAILING,
        FAILED,
    }

    /** The side the prey entered the trap from; picks the directional trapping loc. */
    enum class Dir(val locSuffix: String) {
        NORTH("n"),
        SOUTH("s"),
        EAST("e"),
        WEST("w"),
    }

    enum class TrapKind(
        val item: String,
        val armedLoc: String,
        val failingLoc: String,
        val brokenLoc: String,
        val display: String,
    ) {
        SNARE(
            item = "obj.hunting_ojibway_bird_snare",
            armedLoc = "loc.hunting_ojibway_trap",
            failingLoc = "loc.hunting_ojibway_trap_failing",
            brokenLoc = "loc.hunting_ojibway_trap_broken",
            display = "bird snare",
        ),
        BOX(
            item = "obj.hunting_box_trap",
            armedLoc = "loc.hunting_boxtrap_empty",
            failingLoc = "loc.hunting_boxtrap_failing",
            brokenLoc = "loc.hunting_boxtrap_failed",
            display = "box trap",
        ),
    }

    class HunterTrap(
        val owner: Player,
        var loc: LocInfo,
        val coords: CoordGrid,
        val kind: TrapKind,
        var state: State = State.ARMED,
        var prey: TrapPrey? = null,
        var preyNpc: Npc? = null,
        var fromDir: Dir? = null,
        var scanCycle: Int = 0,
        var stateCycle: Int = 0,
    )

    class TrapPrey(
        val name: String,
        val npcSymbol: String,
        val npcId: Int,
        val level: Int,
        val xpTenths: Int,
        val fullLoc: String,
        val trappingLocs: Map<Dir, String>,
        val feather: String? = null,
        val featherMin: Int = 0,
        val featherMax: Int = 0,
        val caughtItem: String? = null,
    ) {
        fun trappingLoc(fromDir: Dir?): String =
            trappingLocs[fromDir] ?: trappingLocs[Dir.SOUTH] ?: trappingLocs.values.first()
    }

    companion object {
        const val SCAN_TICKS = 4
        const val PREY_SCAN_ZONE_RADIUS = 1
        const val TRAPPING_TICKS = 4
        const val FAILING_TICKS = 3
        const val PREY_RESPAWN_TICKS = 100
    }
}
