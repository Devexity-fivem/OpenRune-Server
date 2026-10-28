package org.rsmod.content.minigames.nightmarezone

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.flag.CollisionFlag

class NightmareZoneRun(
    var kills: Int = 0,
    var points: Int = 0,
    val npcs: MutableList<Npc> = mutableListOf(),
)

@Singleton
class NightmareZoneRuns
@Inject
constructor(
    private val manager: InstanceManager,
    private val clock: MapClock,
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val steps: StepFactory,
) {
    private val active = HashMap<InstanceId, NightmareZoneRun>()

    fun runFor(player: Player): NightmareZoneRun? =
        manager.sessionForPlayer(player)?.let { active[it.id] }

    fun sessionFor(player: Player) = manager.sessionForPlayer(player)

    suspend fun ProtectedAccess.enter() {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        when (val result = manager.create(player, KEY, SPEC, InstanceAccess.Private, clock.cycle)) {
            is InstanceManager.Result.Failed -> mes(result.reason)
            is InstanceManager.Result.Created -> {
                withInstanceEnterTransition(InstanceEnterTransition()) {
                    telejump(result.enter, TeleportType.Exempt)
                    manager.finalizeEntry(player, result.session, clock.cycle)
                }
                active.keys.removeIf { manager.sessionForId(it) == null }
                active[result.session.id] = NightmareZoneRun()
                mes("You wake into a dream. Bosses will keep coming until you leave or fall.")
                topUp(player, result.session.id)
                player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
            }
            is InstanceManager.Result.Joined -> Unit
        }
    }

    suspend fun ProtectedAccess.leave() {
        val session = manager.sessionForPlayer(player) ?: return
        val run = active.remove(session.id)
        if (run != null) {
            player.mes("You wake up. Nightmare Zone points: ${run.points}.")
        }
        withInstanceLeaveTransition(InstanceEnterTransition()) {
            val exit = manager.leave(player, session, clock.cycle)
            telejump(exit, TeleportType.Exempt)
        }
    }

    fun reset(player: Player) {
        val session = manager.sessionForPlayer(player) ?: return
        val run = active.remove(session.id)
        if (run != null) {
            player.mes("The dream ends. Nightmare Zone points: ${run.points}.")
        }
        player.clearSoftTimer(WAVE_TIMER)
    }

    // Rumble: keep the arena topped up to a kill-count-scaled concurrency.
    fun topUp(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        val target = 1 + minOf(run.kills / 4, MAX_ACTIVE - 1)
        var index = 0
        while (run.npcs.size < target) {
            val sym = NightmareZoneRoster.NORMAL[(run.kills + index) % NightmareZoneRoster.NORMAL.size]
            val npc = spawn(sessionId, sym, player.coords, index, player) ?: break
            run.npcs.add(npc)
            index++
        }
    }

    fun awardKill(player: Player, sessionId: InstanceId, npc: Npc) {
        val run = active[sessionId] ?: return
        run.kills++
        val gained = maxOf(5, npc.type.hitpoints / 4)
        run.points += gained
        player.mes("Nightmare Zone: +$gained points (total ${run.points}).")
    }

    fun spawn(
        sessionId: InstanceId,
        sym: String,
        anchor: CoordGrid,
        index: Int,
        player: Player,
    ): Npc? {
        manager.sessionForId(sessionId) ?: return null
        val npc = Npc(sym, anchor)
        val coords = spawnTile(npc, anchor, index) ?: return null
        npc.coords = coords
        engage(npc, player)
        npcRepo.add(npc, NPC_LIFETIME)
        manager.attachNpc(sessionId, npc)
        return npc
    }

    private fun spawnTile(npc: Npc, anchor: CoordGrid, index: Int): CoordGrid? {
        for (radius in SPAWN_RADIUS_MIN..SPAWN_RADIUS_MAX) {
            for (slot in 0 until RING_SLOTS) {
                val angle = ((slot + index * ANGLE_STEP) % RING_SLOTS) * (Math.PI * 2 / RING_SLOTS)
                val x = anchor.x + (kotlin.math.cos(angle) * radius).toInt()
                val z = anchor.z + (kotlin.math.sin(angle) * radius).toInt()
                val tile = CoordGrid(x, z, anchor.level)
                if (open(tile, npc.size) && reaches(npc, anchor, tile)) return tile
            }
        }
        return null
    }

    // See InfernoRuns.reaches: npc movement is straight-line, StepFactory-validated.
    private fun reaches(npc: Npc, anchor: CoordGrid, from: CoordGrid): Boolean {
        val dest =
            RouteFinding.naiveDestination(
                sourceX = from.x,
                sourceZ = from.z,
                sourceWidth = npc.size,
                sourceLength = npc.size,
                targetX = anchor.x,
                targetZ = anchor.z,
                targetWidth = 1,
                targetLength = 1,
            )
        val target = CoordGrid(dest.x, dest.z, anchor.level)
        if (from == target) return true
        val extraFlag = if (npc.moveRestrict == MoveRestrict.PassThru) 0 else CollisionFlag.BLOCK_NPCS
        val path = steps.createPath(from, target, npc.size, extraFlag, CollisionStrategy.Normal)
        return path.lastOrNull() == target
    }

    private fun open(tile: CoordGrid, size: Int): Boolean {
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                val x = tile.x + dx
                val z = tile.z + dz
                if (!collision.isZoneAllocated(x, z, tile.level)) return false
                if (collision[x, z, tile.level] and BLOCKED_MASK != 0) return false
            }
        }
        return true
    }

    fun engage(npc: Npc, player: Player) {
        val ranged = npc.type.internalName in NightmareZoneRoster.RANGED
        npc.setHuntMode(if (ranged) HUNT_RANGED else HUNT_MELEE)
        npc.setHunt(AGGRO_RANGE)
        if (ranged) {
            npc.apPlayer2(player, aiPlayerInteractions)
        } else {
            npc.opPlayer2(player, aiPlayerInteractions)
        }
    }

    fun remove(sessionId: InstanceId): NightmareZoneRun? = active.remove(sessionId)

    companion object {
        const val KEY = "nightmare_zone"
        const val WAVE_TIMER = "timer.nzone_wave"
        const val WAVE_CHECK_TICKS = 3
        const val AGGRO_RANGE = 64
        const val NPC_LIFETIME = 100_000
        const val MAX_ACTIVE = 4
        const val SPAWN_RADIUS_MIN = 6
        const val SPAWN_RADIUS_MAX = 14
        const val RING_SLOTS = 16
        const val ANGLE_STEP = 5
        const val BLOCKED_MASK =
            CollisionFlag.LOC or
                CollisionFlag.BLOCK_WALK or
                CollisionFlag.GROUND_DECOR or
                CollisionFlag.BLOCK_NPCS or
                CollisionFlag.BLOCK_PLAYERS
        val CITY_EXIT = CoordGrid(2608, 3117, 0)
        private val HUNT_MELEE = checkNotNull(ServerCacheManager.getHunt(1))
        private val HUNT_RANGED = checkNotNull(ServerCacheManager.getHunt(2))

        private val SPEC =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = 0,
                graceTicks = 0,
                destroyWhenEmpty = true,
                area =
                    InstanceArea.copyRegions(
                        regionIds = listOf(9807),
                        enterCoord = RegionLocal(0, 38, 79, 32, 44),
                        exitCoord = CITY_EXIT,
                    ),
                settingsRowId = 0,
            )
    }
}
