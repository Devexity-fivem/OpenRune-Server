package org.rsmod.content.minigames.inferno

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

class InfernoRun(
    var wave: Int = 0,
    var jadHealersSpawned: Boolean = false,
    var zukHealersSpawned: Boolean = false,
    var completed: Boolean = false,
    val npcs: MutableList<Npc> = mutableListOf(),
)

@Singleton
class InfernoRuns
@Inject
constructor(
    private val manager: InstanceManager,
    private val clock: MapClock,
    private val regions: RegionRegistry,
    private val npcRepo: NpcRepository,
) {
    private val active = HashMap<InstanceId, InfernoRun>()

    fun runFor(player: Player): InfernoRun? =
        manager.sessionForPlayer(player)?.let { active[it.id] }

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
                    active[result.session.id] = InfernoRun()
                    telejump(result.enter, TeleportType.Exempt)
                    manager.finalizeEntry(player, result.session, clock.cycle)
                    mes("You enter the Inferno.")
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
        when (wave) {
            InfernoWaves.LAST_WAVE -> {
                run.npcs.add(
                    spawn(sessionId, InfernoWaves.TZKAL_ZUK, southWest, InfernoWaves.ZUK_OFFSET)
                )
            }
            InfernoWaves.TRIPLE_JAD_WAVE -> {
                InfernoWaves.TRIPLE_JAD.forEachIndexed { index, sym ->
                    run.npcs.add(
                        spawn(
                            sessionId,
                            sym,
                            southWest,
                            InfernoWaves.JAD_OFFSETS[index % InfernoWaves.JAD_OFFSETS.size],
                        )
                    )
                }
            }
            else ->
                InfernoWaves.WAVES[wave - 1].forEachIndexed { index, sym ->
                    run.npcs.add(
                        spawn(
                            sessionId,
                            sym,
                            southWest,
                            InfernoWaves.SPAWN_OFFSETS[
                                index % InfernoWaves.SPAWN_OFFSETS.size],
                        )
                    )
                }
        }
        player.mes("Wave: $wave")
    }

    fun spawnJadHealers(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        if (run.jadHealersSpawned) return
        run.jadHealersSpawned = true
        val region = regions[player.coords] ?: return
        for (offset in InfernoWaves.HEALER_OFFSETS) {
            run.npcs.add(
                spawn(sessionId, InfernoWaves.JALTOK_JAD_HEALER, region.southWest, offset)
            )
        }
        player.mes("The JalTok-Jads call for help!")
    }

    fun spawnZukHealers(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        if (run.zukHealersSpawned) return
        run.zukHealersSpawned = true
        val region = regions[player.coords] ?: return
        for (offset in InfernoWaves.ZUK_HEALER_OFFSETS) {
            run.npcs.add(spawn(sessionId, InfernoWaves.ZUK_HEALER, region.southWest, offset))
        }
        player.mes("TzKal-Zuk calls for help!")
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
        const val KEY = "inferno"
        const val WAVE_TIMER = "timer.inferno_wave"
        const val WAVE_CHECK_TICKS = 3
        val CITY_EXIT = CoordGrid(2494, 5115, 0)

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
                        enterCoord = RegionLocal(0, 308, 636, 0, 0),
                        exitCoord = CITY_EXIT,
                    ),
                settingsRowId = 0,
            )
    }
}
