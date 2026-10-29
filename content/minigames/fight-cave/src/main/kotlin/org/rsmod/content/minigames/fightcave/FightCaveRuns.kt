package org.rsmod.content.minigames.fightcave

import com.github.michaelbull.logging.InlineLogger
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

class FightCaveRun(
    var wave: Int = 0,
    var healersSpawned: Boolean = false,
    var completed: Boolean = false,
    val npcs: MutableList<Npc> = mutableListOf(),
)

@Singleton
class FightCaveRuns
@Inject
constructor(
    private val manager: InstanceManager,
    private val clock: MapClock,
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val steps: StepFactory,
) {
    private val active = HashMap<InstanceId, FightCaveRun>()
    private val logger = InlineLogger()

    fun runFor(player: Player): FightCaveRun? =
        manager.sessionForPlayer(player)?.let { active[it.id] }

    fun runFor(npc: Npc): FightCaveRun? = manager.instanceForNpc(npc)?.let { active[it] }

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
                active[result.session.id] = FightCaveRun()
                mes("You enter the Fight Cave.")
                spawnWave(player, result.session.id, 1)
                player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
            }
            is InstanceManager.Result.Joined -> Unit
        }
    }

    suspend fun ProtectedAccess.leave() {
        val session = manager.sessionForPlayer(player) ?: return
        active.remove(session.id)
        withInstanceLeaveTransition(InstanceEnterTransition()) {
            val exit = manager.leave(player, session, clock.cycle)
            telejump(exit, TeleportType.Exempt)
        }
    }

    fun reset(player: Player) {
        val session = manager.sessionForPlayer(player) ?: return
        active.remove(session.id)
        player.clearSoftTimer(WAVE_TIMER)
    }

    fun spawnWave(player: Player, sessionId: InstanceId, wave: Int) {
        val run = active[sessionId] ?: return
        val npcSyms =
            if (wave == FightCaveWaves.LAST_WAVE) {
                listOf(FightCaveWaves.TZTOK_JAD)
            } else {
                FightCaveWaves.WAVES[wave - 1]
            }
        val spawned =
            npcSyms.mapIndexedNotNull { index, sym ->
                spawn(sessionId, sym, player.coords, index, player)
            }
        if (spawned.isEmpty()) {
            player.mes("Wave $wave has no room to spawn - keep moving.")
            return
        }
        run.wave = wave
        run.npcs.addAll(spawned)
        player.mes("Wave: $wave")
    }

    fun spawnHealers(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        if (run.healersSpawned) return
        run.healersSpawned = true
        val anchor =
            run.npcs.firstOrNull { it.type.internalName == FightCaveWaves.TZTOK_JAD }?.coords
                ?: player.coords
        repeat(4) { index ->
            spawn(sessionId, FightCaveWaves.YT_HURKOT, anchor, index, player)?.let {
                run.npcs.add(it)
            }
        }
        player.mes("TzTok-Jad calls for help!")
    }

    fun spawn(
        sessionId: InstanceId,
        sym: String,
        anchor: CoordGrid,
        index: Int,
        player: Player,
        radiusMin: Int = SPAWN_RADIUS_MIN,
        radiusMax: Int = SPAWN_RADIUS_MAX,
    ): Npc? {
        val session = manager.sessionForId(sessionId)
        if (session == null) {
            logger.warn { "fightcave: no session for $sessionId" }
            return null
        }
        val npc = Npc(sym, anchor)
        val coords = spawnTile(npc, anchor, index, radiusMin, radiusMax)
        if (coords == null) {
            logger.warn { "fightcave: no open spawn tile near $anchor for $sym" }
            return null
        }
        npc.coords = coords
        engage(npc, player)
        npcRepo.add(npc, NPC_LIFETIME)
        manager.attachNpc(sessionId, npc)
        return npc
    }

    private fun spawnTile(
        npc: Npc,
        anchor: CoordGrid,
        index: Int,
        radiusMin: Int,
        radiusMax: Int,
    ): CoordGrid? {
        for (radius in radiusMin..radiusMax) {
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

    // NPC movement steps are straight-line only (no A*), so a spawn tile is useless
    // unless the npc can actually step from it to the anchor's perimeter the same way
    // the movement processor does: StepFactory-validated diagonal/horizontal/vertical.
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
        val ranged = npc.type.internalName in FightCaveWaves.RANGED
        npc.setHuntMode(if (ranged) HUNT_RANGED else HUNT_MELEE)
        npc.setHunt(AGGRO_RANGE)
        if (ranged) {
            npc.apPlayer2(player, aiPlayerInteractions)
        } else {
            npc.opPlayer2(player, aiPlayerInteractions)
        }
    }

    fun remove(sessionId: InstanceId) {
        active.remove(sessionId)
    }

    companion object {
        const val KEY = "fight_cave"
        const val WAVE_TIMER = "timer.fightcave_wave"
        const val WAVE_CHECK_TICKS = 3
        const val REGION_X = 37
        const val REGION_Z = 79
        const val AGGRO_RANGE = 64
        const val NPC_LIFETIME = 100_000
        const val SPAWN_RADIUS_MIN = 6
        const val MAX_ROUTE_STEPS = 24
        const val SPAWN_RADIUS_MAX = 14
        const val RING_SLOTS = 16
        const val ANGLE_STEP = 5
        const val BLOCKED_MASK =
            CollisionFlag.LOC or
                CollisionFlag.BLOCK_WALK or
                CollisionFlag.GROUND_DECOR or
                CollisionFlag.BLOCK_NPCS or
                CollisionFlag.BLOCK_PLAYERS
        val CITY_EXIT = CoordGrid(2438, 5171, 0)
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
                        regionIds = listOf(9551),
                        enterCoord = RegionLocal(0, 37, 79, 45, 54),
                        exitCoord = CITY_EXIT,
                    ),
                settingsRowId = 0,
            )
    }
}
