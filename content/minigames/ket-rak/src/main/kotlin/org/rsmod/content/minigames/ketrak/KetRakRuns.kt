package org.rsmod.content.minigames.ketrak

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.flag.CollisionFlag

class KetRakRun(
    var jadCount: Int = 0,
    var endless: Boolean = false,
    var completed: Boolean = false,
    var kills: Int = 0,
    val npcs: MutableList<Npc> = mutableListOf(),
)

private class WaveState {
    var crossed = false
    var everSpawned = false
    var reachedFull = false
    var nextHealTick = 0
}

@Singleton
class KetRakRuns
@Inject
constructor(
    private val manager: InstanceManager,
    private val clock: MapClock,
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val steps: StepFactory,
    private val worldQueues: WorldQueueList,
    private val worldRepo: WorldRepository,
    private val random: GameRandom,
) {
    private val active = HashMap<InstanceId, KetRakRun>()
    private val healerWaves = IdentityHashMap<Npc, WaveState>()
    private val healers = IdentityHashMap<Npc, MutableList<Npc>>()
    private val healerJads = IdentityHashMap<Npc, Npc>()
    private var healSpot = SpotanimType(0)

    fun runFor(player: Player): KetRakRun? =
        manager.sessionForPlayer(player)?.let { active[it.id] }

    fun sessionFor(player: Player) = manager.sessionForPlayer(player)

    fun isRunNpc(player: Player, npc: Npc): Boolean =
        runFor(player)?.npcs?.any { it === npc } == true

    fun jadFor(healer: Npc): Npc? = healerJads[healer]

    suspend fun ProtectedAccess.enter(jadCount: Int, endless: Boolean = false) {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        when (val result = manager.create(player, KEY, SPEC, InstanceAccess.Private, clock.cycle)) {
            is InstanceManager.Result.Failed -> mes(result.reason)
            is InstanceManager.Result.Created -> {
                val session = result.session
                // Callers resuming from menu() lose protected access on the transition delay
                // and never return from it, so challenge startup runs on the world queue.
                worldQueues.add(3) { startChallenge(player, session.id, jadCount, endless) }
                withInstanceEnterTransition(InstanceEnterTransition()) {
                    telejump(result.enter, TeleportType.Exempt)
                    manager.finalizeEntry(player, session, clock.cycle)
                }
            }
            is InstanceManager.Result.Joined -> Unit
        }
    }

    fun startChallenge(player: Player, sessionId: InstanceId, jadCount: Int, endless: Boolean) {
        if (manager.sessionForPlayer(player)?.id != sessionId) return
        active.keys.removeIf { manager.sessionForId(it) == null }
        val run = KetRakRun(jadCount = jadCount, endless = endless)
        active[sessionId] = run
        healSpot = SpotanimType(HEAL_SPOT.asRSCM(RSCMType.SPOTANIM))
        if (endless) {
            player.mes("Final Challenge: the JalTok-Jads will keep coming.")
        } else {
            player.mes("Challenge $jadCount: $jadCount JalTok-Jad${if (jadCount > 1) "s" else ""}.")
        }
        var spawned = 0
        repeat(jadCount) { index ->
            spawn(sessionId, JAD, player.coords, index)?.let { jad ->
                engage(jad, player)
                run.npcs.add(jad)
                spawned++
            }
        }
        if (spawned == 0) player.mes("There is no room to fight here - keep moving.")
        player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
    }

    suspend fun ProtectedAccess.leave() {
        val session = manager.sessionForPlayer(player) ?: return
        remove(session.id)
        withInstanceLeaveTransition(InstanceEnterTransition()) {
            val exit = manager.leave(player, session, clock.cycle)
            telejump(exit, TeleportType.Exempt)
        }
    }

    fun reset(player: Player) {
        manager.sessionForPlayer(player) ?: return
        player.clearSoftTimer(WAVE_TIMER)
    }

    /**
     * OSRS healer mechanics, shared with TzTok-Jad: each Yt-HurKot spawns beside its
     * Jad and channels heals while close enough — even while fighting the player —
     * until killed or pulled out of range. If the wave heals its Jad to full, dead
     * healers may return when the Jad next crosses half health.
     */
    fun tickHealers(player: Player, sessionId: InstanceId, jad: Npc) {
        val run = active[sessionId] ?: return
        val wave = healerWaves.getOrPut(jad) { WaveState() }
        val living = healers[jad]?.filter { it.isSlotAssigned && it.hitpoints > 0 }.orEmpty()

        if (jad.hitpoints >= jad.baseHitpointsLvl && living.isNotEmpty()) {
            wave.reachedFull = true
        }
        if (jad.hitpoints > jad.baseHitpointsLvl / 2) {
            wave.crossed = false
        } else if (!wave.crossed) {
            wave.crossed = true
            if (living.isEmpty() && (!wave.everSpawned || wave.reachedFull)) {
                wave.everSpawned = true
                wave.reachedFull = false
                spawnHealers(player, sessionId, jad, run)
            }
        }

        val now = clock.cycle
        if (now < wave.nextHealTick) return
        wave.nextHealTick = now + HEAL_INTERVAL
        val centre = jad.coords.translate(jad.size / 2, jad.size / 2)
        for (healer in living) {
            if (healer.coords.chebyshevDistance(centre) > HEAL_RANGE) continue
            jad.heal(HEAL_MIN + random.of(HEAL_ROLL), showHitsplat = true)
            healer.anim(HEAL_SEQ, 0, 0)
            worldRepo.spotanimMap(healSpot, centre)
        }
    }

    private fun spawnHealers(player: Player, sessionId: InstanceId, jad: Npc, run: KetRakRun) {
        val centre = jad.coords.translate(jad.size / 2, jad.size / 2)
        var spawned = false
        for ((dx, dz) in HEALER_OFFSETS) {
            val tile = centre.translate(dx, dz)
            if (!open(tile, 1)) continue
            val healer = Npc(HEALER, tile)
            npcRepo.add(healer, NPC_LIFETIME)
            manager.attachNpc(sessionId, healer)
            healer.mode = NpcMode.None
            healers.getOrPut(jad) { mutableListOf() } += healer
            healerJads[healer] = jad
            run.npcs.add(healer)
            spawned = true
        }
        if (spawned) player.mes("The JalTok-Jad calls for help!")
    }

    fun topUp(player: Player, sessionId: InstanceId) {
        val run = active[sessionId] ?: return
        spawn(sessionId, JAD, player.coords, run.kills)?.let { jad ->
            engage(jad, player)
            run.npcs.add(jad)
        }
    }

    fun complete(player: Player, sessionId: InstanceId, objRepo: ObjRepository) {
        val run = active[sessionId] ?: return
        run.completed = true
        val reward = TOKKUL.getOrElse(run.jadCount - 1) { 0 }
        player.mes("Challenge complete! TzHaar-Ket-Rak will be impressed.")
        if (reward > 0) {
            player.invAddOrDrop(objRepo, "obj.tzhaar_token", count = reward)
            player.mes("TzHaar-Ket-Rak rewards you with $reward TokKul.")
        }
    }

    fun spawn(sessionId: InstanceId, sym: String, anchor: CoordGrid, index: Int): Npc? {
        manager.sessionForId(sessionId) ?: return null
        val npc = Npc(sym, anchor)
        val coords = spawnTile(npc, anchor, index) ?: return null
        npc.coords = coords
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
        npc.setHuntMode(HUNT_RANGED)
        npc.setHunt(AGGRO_RANGE)
        npc.apPlayer2(player, aiPlayerInteractions)
        npc.apRangeOverride = JAD_ATTACK_RANGE
    }

    fun remove(sessionId: InstanceId): KetRakRun? {
        val run = active.remove(sessionId)
        run?.npcs?.forEach { npc ->
            healerWaves.remove(npc)
            healers.remove(npc)
            healerJads.remove(npc)
        }
        return run
    }

    companion object {
        const val KEY = "ket_rak"
        const val WAVE_TIMER = "timer.ketrak_wave"
        const val EXIT_TIMER = "timer.ketrak_exit"
        const val WAVE_CHECK_TICKS = 3
        const val AGGRO_RANGE = 64
        const val JAD_ATTACK_RANGE = 15
        const val NPC_LIFETIME = 100_000
        const val SPAWN_RADIUS_MIN = 6
        const val SPAWN_RADIUS_MAX = 20
        const val RING_SLOTS = 16
        const val ANGLE_STEP = 5
        const val JAD = "npc.jad_challenge_jad"
        const val HEALER = "npc.jad_challenge_healer"
        const val BLOCKED_MASK =
            CollisionFlag.LOC or
                CollisionFlag.BLOCK_WALK or
                CollisionFlag.GROUND_DECOR or
                CollisionFlag.BLOCK_NPCS or
                CollisionFlag.BLOCK_PLAYERS
        val CITY_EXIT = CoordGrid(2538, 5110, 0)
        val TOKKUL = intArrayOf(80, 190, 480, 1190, 2970, 7430)
        private val HUNT_RANGED = checkNotNull(ServerCacheManager.getHunt(2))

        private const val HEAL_SPOT = "spotanim.tzhaar_heal"
        private const val HEAL_SEQ = "seq.lizard_cleric_heal"
        private const val HEAL_INTERVAL = 4
        private const val HEAL_MIN = 1
        private const val HEAL_ROLL = 2
        private const val HEAL_RANGE = 4
        private val HEALER_OFFSETS = listOf(0 to 3, 3 to 0, 0 to -3, -3 to 0)

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
