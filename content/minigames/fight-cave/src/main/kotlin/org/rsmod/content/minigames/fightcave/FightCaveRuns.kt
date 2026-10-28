package org.rsmod.content.minigames.fightcave

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
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

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
    private val regions: RegionRegistry,
    private val npcRepo: NpcRepository,
) {
    private val active = HashMap<InstanceId, FightCaveRun>()

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
            is InstanceManager.Result.Created ->
                withInstanceEnterTransition(InstanceEnterTransition()) {
                    active.keys.removeIf { manager.sessionForId(it) == null }
                    active[result.session.id] = FightCaveRun()
                    telejump(result.enter, TeleportType.Exempt)
                    manager.finalizeEntry(player, result.session, clock.cycle)
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
        val region = regions[player.coords] ?: return
        val southWest = region.southWest
        val npcSyms =
            if (wave == FightCaveWaves.LAST_WAVE) {
                listOf(FightCaveWaves.TZTOK_JAD)
            } else {
                FightCaveWaves.WAVES[wave - 1]
            }
        npcSyms.forEachIndexed { index, sym ->
            val offset =
                if (wave == FightCaveWaves.LAST_WAVE) {
                    FightCaveWaves.JAD_OFFSET
                } else {
                    FightCaveWaves.SPAWN_OFFSETS[index % FightCaveWaves.SPAWN_OFFSETS.size]
                }
            val npc = spawn(sessionId, sym, southWest, offset)
            run.npcs.add(npc)
        }
        player.mes("Wave: $wave")
    }

    fun spawnHealers(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        if (run.healersSpawned) return
        run.healersSpawned = true
        val region = regions[player.coords] ?: return
        for (offset in FightCaveWaves.HEALER_OFFSETS) {
            run.npcs.add(
                spawn(sessionId, FightCaveWaves.YT_HURKOT, region.southWest, offset)
            )
        }
        player.mes("TzTok-Jad calls for help!")
    }

    fun spawn(
        sessionId: InstanceId,
        sym: String,
        southWest: CoordGrid,
        offset: Pair<Int, Int>,
    ): Npc {
        val npc = Npc(sym, southWest.translate(offset.first, offset.second))
        npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(sessionId, npc)
        return npc
    }

    fun remove(sessionId: InstanceId) {
        active.remove(sessionId)
    }

    companion object {
        const val KEY = "fight_cave"
        const val WAVE_TIMER = "timer.fightcave_wave"
        const val WAVE_CHECK_TICKS = 3
        val CITY_EXIT = CoordGrid(2438, 5171, 0)

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
                        enterCoord = RegionLocal(0, 301, 638, 5, 6),
                        exitCoord = CITY_EXIT,
                    ),
                settingsRowId = 0,
            )
    }
}
