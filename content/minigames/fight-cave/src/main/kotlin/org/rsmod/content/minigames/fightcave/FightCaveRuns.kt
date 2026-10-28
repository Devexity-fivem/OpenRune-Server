package org.rsmod.content.minigames.fightcave

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
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
        run.wave = wave
        val npcSyms =
            if (wave == FightCaveWaves.LAST_WAVE) {
                listOf(FightCaveWaves.TZTOK_JAD)
            } else {
                FightCaveWaves.WAVES[wave - 1]
            }
        npcSyms.forEachIndexed { index, sym ->
            spawn(sessionId, sym, player.coords, index, player)?.let { run.npcs.add(it) }
        }
        player.mes("Wave: $wave")
    }

    fun spawnHealers(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        if (run.healersSpawned) return
        run.healersSpawned = true
        val anchor = run.npcs.firstOrNull { it.type.internalName == JAD_NAME }?.coords ?: player.coords
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
        val size = ServerCacheManager.getNpc(sym.asRSCM(RSCMType.NPC))?.size ?: 1
        val coords = spawnTile(anchor, size, index, radiusMin, radiusMax)
        if (coords == null) {
            logger.warn { "fightcave: no open spawn tile near $anchor for $sym" }
            return null
        }
        val npc = Npc(sym, coords)
        engage(npc, player)
        npcRepo.add(npc, NPC_LIFETIME)
        manager.attachNpc(sessionId, npc)
        return npc
    }

    private fun spawnTile(
        anchor: CoordGrid,
        size: Int,
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
                if (open(tile, size)) return tile
            }
        }
        return null
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
        const val SPAWN_RADIUS_MAX = 14
        const val RING_SLOTS = 16
        const val ANGLE_STEP = 5
        const val JAD_NAME = "tzhaar_fightcave_swarm_boss"
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
