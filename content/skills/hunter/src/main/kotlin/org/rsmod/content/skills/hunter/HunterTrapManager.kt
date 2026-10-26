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

    fun activeTraps(player: Player, kind: TrapKind): Int =
        traps.count { it.owner == player && it.kind == kind }

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

    fun canSet(player: Player, kind: TrapKind): Boolean =
        activeTraps(player) < trapLimit(player, kind) &&
            activeTraps(player, kind) < (kind.kindLimit ?: Int.MAX_VALUE)

    fun trapAt(coords: CoordGrid): HunterTrap? = traps.firstOrNull { it.coords == coords }

    fun layTrap(player: Player, coords: CoordGrid, kind: TrapKind): HunterTrap? {
        if (!canSet(player, kind)) {
            return null
        }
        val armed = kind.armedLoc ?: return null
        val loc =
            locRepo.add(coords, armed, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        val trap = HunterTrap(player, loc, coords, kind)
        traps += trap
        return trap
    }

    /**
     * Sets a deadfall on a static boulder: the armed loc is spawned over it, so deleting
     * the trap later reveals the original boulder automatically.
     */
    fun setDeadfall(
        player: Player,
        coords: CoordGrid,
        angle: LocAngle,
        shape: LocShape,
    ): HunterTrap? {
        if (!canSet(player, TrapKind.DEADFALL) || trapAt(coords) != null) {
            return null
        }
        val loc =
            locRepo.add(
                coords,
                checkNotNull(TrapKind.DEADFALL.armedLoc),
                Int.MAX_VALUE,
                angle,
                shape,
            )
        val trap = HunterTrap(player, loc, coords, TrapKind.DEADFALL)
        traps += trap
        return trap
    }

    /**
     * Sets a net trap on a static young tree: like a deadfall the trap overlays the
     * loc, so deleting it later reveals the original tree. The tree type fixes the
     * prey, so [HunterTrap.prey] is assigned at set time rather than when a creature
     * triggers it.
     */
    fun setNetTrap(
        player: Player,
        coords: CoordGrid,
        angle: LocAngle,
        shape: LocShape,
        prey: TrapPrey,
    ): HunterTrap? {
        if (!canSet(player, TrapKind.NET) || trapAt(coords) != null) {
            return null
        }
        val armed = checkNotNull(prey.armedLoc)
        val loc = locRepo.add(coords, armed, Int.MAX_VALUE, angle, shape)
        val trap = HunterTrap(player, loc, coords, TrapKind.NET, prey = prey)
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
        transition(trap, armedLoc(trap))
        trap.state = State.ARMED
        trap.scanCycle = 0
        trap.prey = if (trap.kind == TrapKind.NET) trap.prey else null
        trap.preyNpc = null
        trap.baited = false
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
        if (trap.kind.suppressesOwner && trap.owner.coords == trap.coords) return
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
        if (inFootprint(trap, npc.coords)) {
            resolveLanding(trap, npc)
            return
        }
        trap.fromDir = entryDir(trap, npc.coords)
        val (w, d) = footprintSize(trap)
        val tx = npc.coords.x.coerceIn(trap.coords.x, trap.coords.x + w - 1)
        val tz = npc.coords.z.coerceIn(trap.coords.z, trap.coords.z + d - 1)
        val dx = (tx - npc.coords.x).coerceIn(-1, 1)
        val dz = (tz - npc.coords.z).coerceIn(-1, 1)
        npc.teleport(collision, npc.coords.translate(dx, dz))
    }

    private fun footprintSize(trap: HunterTrap): Pair<Int, Int> =
        if (!trap.kind.wide) {
            1 to 1
        } else if (trap.loc.angle == LocAngle.West || trap.loc.angle == LocAngle.East) {
            2 to 1
        } else {
            1 to 2
        }

    private fun inFootprint(trap: HunterTrap, coords: CoordGrid): Boolean {
        if (coords.level != trap.coords.level) return false
        val (w, d) = footprintSize(trap)
        return coords.x in trap.coords.x until trap.coords.x + w &&
            coords.z in trap.coords.z until trap.coords.z + d
    }

    private fun entryDir(trap: HunterTrap, coords: CoordGrid): Dir {
        val (w, _) = footprintSize(trap)
        return when {
            coords.x < trap.coords.x -> Dir.WEST
            coords.x >= trap.coords.x + w -> Dir.EAST
            coords.z < trap.coords.z -> Dir.SOUTH
            else -> Dir.NORTH
        }
    }

    private fun resolveLanding(trap: HunterTrap, npc: Npc) {
        val prey = trap.prey ?: run {
            trap.state = State.ARMED
            trap.preyNpc = null
            return
        }
        npcRepo.del(npc, PREY_RESPAWN_TICKS)
        trap.preyNpc = null
        if (catchRoll(trap.owner.hunterLvl, prey.level, trap.baited)) {
            transition(trap, prey.trappingLoc(trap.fromDir, trap.mirroredEntry()))
            trap.state = State.TRAPPING
            trap.stateCycle = 0
        } else {
            transition(trap, failingLoc(trap))
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
        val broken = trap.prey?.failedLoc ?: trap.kind.brokenLoc
        if (broken != null) {
            transition(trap, broken)
            trap.state = State.FAILED
        } else {
            // Deadfalls have no broken state: the sprung trap is removed and the
            // static boulder underneath is revealed again.
            removeTrap(trap)
        }
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
            // A net trap's prey is fixed by the tree it was strung on.
            if (trap.prey != null && npc.type.id != trap.prey?.npcId) continue
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

    private fun catchRoll(level: Int, required: Int, baited: Boolean): Boolean {
        val chance = catchChance(level, required) + if (baited) BAIT_BONUS else 0.0
        return Random.nextDouble() < chance
    }

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

    private fun armedLoc(trap: HunterTrap): String =
        trap.prey?.armedLoc ?: checkNotNull(trap.kind.armedLoc)

    private fun failingLoc(trap: HunterTrap): String =
        trap.prey?.failingLoc ?: checkNotNull(trap.kind.failingLoc)

    enum class TrapKind(
        val item: String?,
        val armedLoc: String?,
        val failingLoc: String?,
        val brokenLoc: String?,
        val display: String,
        val suppressesOwner: Boolean,
        val kindLimit: Int?,
        val wide: Boolean,
        val materials: List<String> = emptyList(),
    ) {
        SNARE(
            item = "obj.hunting_ojibway_bird_snare",
            armedLoc = "loc.hunting_ojibway_trap",
            failingLoc = "loc.hunting_ojibway_trap_failing",
            brokenLoc = "loc.hunting_ojibway_trap_broken",
            display = "bird snare",
            suppressesOwner = true,
            kindLimit = null,
            wide = false,
        ),
        BOX(
            item = "obj.hunting_box_trap",
            armedLoc = "loc.hunting_boxtrap_empty",
            failingLoc = "loc.hunting_boxtrap_failing",
            brokenLoc = "loc.hunting_boxtrap_failed",
            display = "box trap",
            suppressesOwner = true,
            kindLimit = null,
            wide = false,
        ),
        DEADFALL(
            item = null,
            armedLoc = "loc.hunting_deadfall_trap",
            failingLoc = "loc.hunting_deadfall_failing",
            brokenLoc = null,
            display = "deadfall trap",
            suppressesOwner = false,
            kindLimit = 2,
            wide = true,
        ),
        NET(
            item = null,
            armedLoc = null,
            failingLoc = null,
            brokenLoc = null,
            display = "net trap",
            suppressesOwner = false,
            kindLimit = null,
            wide = false,
            materials = listOf("obj.rope", "obj.net"),
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
        var baited: Boolean = false,
    ) {
        // A prey entering a deadfall from the boulder's back side uses the mirrored loc.
        fun mirroredEntry(): Boolean = fromDir != null && fromDir == backDir(loc.angle)

        private fun backDir(angle: LocAngle): Dir =
            when (angle) {
                LocAngle.West -> Dir.NORTH
                LocAngle.North -> Dir.EAST
                LocAngle.East -> Dir.SOUTH
                LocAngle.South -> Dir.WEST
            }
    }

    class TrapPrey(
        val name: String,
        val npcSymbol: String,
        val npcId: Int,
        val level: Int,
        val xpTenths: Int,
        val fullLoc: String,
        val trappingLocs: Map<Dir, String>,
        val mirroredTrappingLocs: Map<Dir, String>? = null,
        val feather: String? = null,
        val featherMin: Int = 0,
        val featherMax: Int = 0,
        val caughtItem: String? = null,
        val fixedDrops: List<String> = emptyList(),
        // Net-trap traps keep all state locs per prey because they differ by biome.
        val treeLoc: String? = null,
        val armedLoc: String? = null,
        val failingLoc: String? = null,
        val failedLoc: String? = null,
        val bait: String? = null,
    ) {
        fun trappingLoc(fromDir: Dir?, mirrored: Boolean): String {
            val locs = if (mirrored) mirroredTrappingLocs ?: trappingLocs else trappingLocs
            return locs[fromDir] ?: locs[Dir.SOUTH] ?: locs.values.first()
        }
    }

    companion object {
        const val SCAN_TICKS = 4
        const val PREY_SCAN_ZONE_RADIUS = 1
        const val TRAPPING_TICKS = 4
        const val FAILING_TICKS = 3
        const val PREY_RESPAWN_TICKS = 100
        const val BAIT_BONUS = 0.10
    }
}
