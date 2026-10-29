package org.rsmod.content.bosses.zalcano

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

@Singleton
class ZalcanoManager
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val playerRepo: PlayerRepository,
    private val worldRepo: WorldRepository,
    private val hitProcessor: DamageOnlyPlayerHitProcessor,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val eventBus: EventBus,
) {
    enum class Phase {
        SHIELDED,
        DOWNED,
    }

    var phase = Phase.SHIELDED
        private set

    var shield = MAX_SHIELD
        private set

    private var initialized = false
    private var phaseEndCycle = 0
    private var lastAttackCycle = 0
    private var lastAnimCycle = 0
    private var lastGolemCycle = 0
    private var boss: Npc? = null
    private val rockfalls = ArrayDeque<Impact>()
    private val pentagrams = ArrayDeque<Impact>()
    private val golems = mutableListOf<Npc>()

    private class Impact(val coords: CoordGrid, val impactCycle: Int, val duration: Int = 0)

    fun tick() {
        val cycle = mapClock.cycle
        if (!initialized) {
            initialized = true
            locateBoss()
            spawnRocks()
        }
        if (cycle % 40 == 0 && boss == null) {
            locateBoss()
        }
        when (phase) {
            Phase.SHIELDED -> tickShielded(cycle)
            Phase.DOWNED -> tickDowned(cycle)
        }
        processImpacts(cycle)
        processPentagrams(cycle)
        tickGolems(cycle)
    }

    private fun arenaZones(): List<ZoneKey> = buildList {
        for (x in (ARENA_MIN_X / 8)..(ARENA_MAX_X / 8)) {
            for (z in (ARENA_MIN_Z / 8)..(ARENA_MAX_Z / 8)) {
                add(ZoneKey(x, z, 0))
            }
        }
    }

    private fun locateBoss() {
        for (zone in arenaZones()) {
            for (npc in npcRepo.findAll(zone)) {
                if (npc.type.id == ZALCANO_ID) {
                    boss = npc
                    if (npc.visType.id == ZALCANO_WEAK_ID && phase == Phase.SHIELDED) {
                        phase = Phase.DOWNED
                        phaseEndCycle = mapClock.cycle + DOWNED_TICKS
                    }
                    return
                }
            }
        }
        boss = null
        despawnGolems()
    }

    private fun spawnRocks() {
        for (zone in arenaZones()) {
            for (loc in locRepo.findAll(zone)) {
                if (loc.id == ROCK_ACTIVE_ID) return
            }
        }
        for (coords in ROCK_TILES) {
            locRepo.add(
                coords,
                "loc.zalcano_rock_active",
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        }
    }

    fun arenaPlayers(): List<Player> {
        val players = mutableListOf<Player>()
        for (zone in arenaZones()) {
            for (player in playerRepo.findAll(zone)) {
                if (isInArena(player)) players += player
            }
        }
        return players
    }

    fun isInArena(player: Player): Boolean =
        player.coords.level == 0 &&
            player.coords.x in ARENA_MIN_X..ARENA_MAX_X &&
            player.coords.z in ARENA_MIN_Z..ARENA_MAX_Z

    private fun tickShielded(cycle: Int) {
        val npc = boss ?: return
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
            boss = null
            despawnGolems()
            return
        }
        if (npc.visType.id != ZALCANO_ID) {
            phase = Phase.DOWNED
            phaseEndCycle = cycle + DOWNED_TICKS
            return
        }
        if (cycle - lastAnimCycle >= BOSS_ANIM_INTERVAL) {
            lastAnimCycle = cycle
            npc.anim("seq.zalcano_idle")
        }
        if (cycle - lastAttackCycle >= ATTACK_INTERVAL && arenaPlayers().isNotEmpty()) {
            lastAttackCycle = cycle
            runAttack(cycle, npc)
        }
    }

    private fun tickDowned(cycle: Int) {
        val npc = boss
        if (npc == null || !npc.isSlotAssigned || npc.hitpoints <= 0) {
            boss = null
            phase = Phase.SHIELDED
            shield = MAX_SHIELD
            despawnGolems()
            return
        }
        if (npc.visType.id == ZALCANO_ID) {
            phase = Phase.SHIELDED
            shield = MAX_SHIELD
            return
        }
        if (cycle - lastAnimCycle >= BOSS_ANIM_INTERVAL) {
            lastAnimCycle = cycle
            npc.anim("seq.zalcano_fallen_idle")
        }
        if (cycle >= phaseEndCycle) {
            standUp(npc)
        }
    }

    private fun standUp(npc: Npc) {
        val upType = ServerCacheManager.getNpc(ZALCANO_ID) ?: return
        npc.transmog(upType, Int.MAX_VALUE)
        npc.anim("seq.zalcano_stand_up")
        npc.defaultMode()
        phase = Phase.SHIELDED
        shield = MAX_SHIELD
        for (player in arenaPlayers()) {
            player.mes("Zalcano gets back up, her barrier restored.")
        }
    }

    private fun runAttack(cycle: Int, npc: Npc) {
        val players = arenaPlayers()
        if (players.isEmpty()) return
        when (random.of(100)) {
            in 0..49 -> rockfall(cycle, npc, players)
            in 50..74 -> pentagram(cycle, npc, players)
            else -> summonGolem(npc)
        }
    }

    private fun rockfall(cycle: Int, npc: Npc, players: List<Player>) {
        npc.anim("seq.zalcano_attack_rock_fall")
        val shuffled = players.toMutableList()
        val targets = buildList {
            repeat(ROCKFALL_TARGETS) {
                if (shuffled.isEmpty()) return@buildList
                add(shuffled.removeAt(random.of(shuffled.size)))
            }
        }
        for (player in targets) {
            worldRepo.spotanimMap(
                SpotanimType(SPOT_ROCKFALL),
                player.coords,
                height = ROCKFALL_HEIGHT,
                delay = 0,
            )
            rockfalls += Impact(player.coords, cycle + ROCKFALL_DELAY)
        }
    }

    private fun pentagram(cycle: Int, npc: Npc, players: List<Player>) {
        npc.anim("seq.zalcano_pentagram")
        val player = players[random.of(players.size)]
        worldRepo.spotanimMap(
            SpotanimType(SPOT_PENTAGRAM_BURNING),
            player.coords,
            height = 0,
            delay = 0,
        )
        pentagrams += Impact(player.coords, cycle, PENTAGRAM_DURATION)
    }

    private fun summonGolem(npc: Npc) {
        golems.removeIf { !it.isSlotAssigned || it.hitpoints <= 0 }
        if (golems.size >= MAX_GOLEMS) return
        npc.anim("seq.zalcano_attack_golem")
        val spawn =
            CoordGrid(
                npc.coords.x + random.of(GOLEM_SPAWN_RADIUS * 2 + 1) - GOLEM_SPAWN_RADIUS,
                npc.coords.z + random.of(GOLEM_SPAWN_RADIUS * 2 + 1) - GOLEM_SPAWN_RADIUS,
                0,
            )
        val golem = Npc("npc.zalcano_golem", spawn)
        if (golem.baseHitpointsLvl <= 0) {
            golem.baseHitpointsLvl = GOLEM_HITPOINTS
        }
        golem.hitpoints = GOLEM_HITPOINTS
        npcRepo.add(golem, Int.MAX_VALUE)
        worldRepo.spotanimMap(SpotanimType(SPOT_GOLEM_SPAWN), spawn, height = 0, delay = 0)
        golems += golem
    }

    private fun processImpacts(cycle: Int) {
        val iterator = rockfalls.iterator()
        while (iterator.hasNext()) {
            val impact = iterator.next()
            if (cycle < impact.impactCycle) continue
            iterator.remove()
            for (player in arenaPlayers()) {
                if (player.coords.chebyshevDistance(impact.coords) <= ROCKFALL_RADIUS) {
                    player.takeInstantHit(
                        HitType.Typeless,
                        ROCKFALL_DAMAGE_MIN + random.of(ROCKFALL_DAMAGE_VARIANCE),
                        hitProcessor,
                    )
                }
            }
        }
    }

    private fun processPentagrams(cycle: Int) {
        val iterator = pentagrams.iterator()
        while (iterator.hasNext()) {
            val mark = iterator.next()
            if (cycle - mark.impactCycle >= mark.duration) {
                iterator.remove()
                continue
            }
            if (cycle % PENTAGRAM_HIT_INTERVAL != 0) continue
            for (player in arenaPlayers()) {
                if (player.coords.chebyshevDistance(mark.coords) <= PENTAGRAM_RADIUS) {
                    player.takeInstantHit(HitType.Typeless, PENTAGRAM_DAMAGE, hitProcessor)
                }
            }
        }
    }

    private fun despawnGolems() {
        val found = buildList {
            for (zone in arenaZones()) {
                for (npc in npcRepo.findAll(zone)) {
                    if (npc.type.id == GOLEM_ID && npc.isSlotAssigned) add(npc)
                }
            }
        }
        for (npc in found) npcRepo.del(npc, Int.MAX_VALUE)
        golems.clear()
    }

    private fun tickGolems(cycle: Int) {
        if (golems.isEmpty()) return
        if (cycle - lastGolemCycle < GOLEM_HIT_INTERVAL) return
        lastGolemCycle = cycle
        val iterator = golems.iterator()
        while (iterator.hasNext()) {
            val golem = iterator.next()
            if (!golem.isSlotAssigned || golem.hitpoints <= 0) {
                iterator.remove()
                continue
            }
            val target =
                arenaPlayers()
                    .filter { it.coords.chebyshevDistance(golem.coords) <= GOLEM_ATTACK_RADIUS }
                    .minByOrNull { it.coords.chebyshevDistance(golem.coords) }
            if (target != null) {
                golem.facePlayer(target)
                golem.spotanim("spotanim.zalcano_golem_attack")
                target.takeInstantHit(
                    HitType.Typeless,
                    GOLEM_DAMAGE_MIN + random.of(GOLEM_DAMAGE_VARIANCE),
                    hitProcessor,
                )
            }
        }
    }

    fun tephraImpact(): Boolean {
        val npc = boss
        if (npc == null || phase != Phase.SHIELDED || npc.visType.id != ZALCANO_ID) {
            return false
        }
        npc.anim("seq.zalcano_defend")
        worldRepo.spotanimMap(SpotanimType(SPOT_ORE_HIT), npc.coords, height = 0, delay = 0)
        shield = (shield - TEPHRA_DAMAGE_MIN - random.of(TEPHRA_DAMAGE_VARIANCE)).coerceAtLeast(0)
        if (shield <= 0) {
            knockDown(npc)
        }
        return true
    }

    private fun knockDown(npc: Npc) {
        val weakType = ServerCacheManager.getNpc(ZALCANO_WEAK_ID) ?: return
        npc.transmog(weakType, Int.MAX_VALUE)
        npc.anim("seq.zalcano_fall_down")
        npc.noneMode()
        phase = Phase.DOWNED
        phaseEndCycle = mapClock.cycle + DOWNED_TICKS
        for (player in arenaPlayers()) {
            player.clearPendingAction(eventBus)
            player.clearQueue("queue.com_retaliate_npc")
            player.mes("Zalcano's barrier is down - mine her now!")
        }
    }

    fun isDowned(): Boolean = phase == Phase.DOWNED

    companion object {
        const val ARENA_MIN_X = 3016
        const val ARENA_MAX_X = 3048
        const val ARENA_MIN_Z = 6032
        const val ARENA_MAX_Z = 6064

        const val MAX_SHIELD = 600
        const val TEPHRA_DAMAGE_MIN = 35
        const val TEPHRA_DAMAGE_VARIANCE = 25

        const val DOWNED_TICKS = 25
        const val ATTACK_INTERVAL = 9
        const val BOSS_ANIM_INTERVAL = 6

        const val ROCKFALL_TARGETS = 3
        const val ROCKFALL_DELAY = 3
        const val ROCKFALL_RADIUS = 1
        const val ROCKFALL_HEIGHT = 100
        const val ROCKFALL_DAMAGE_MIN = 15
        const val ROCKFALL_DAMAGE_VARIANCE = 20

        const val PENTAGRAM_DURATION = 16
        const val PENTAGRAM_RADIUS = 1
        const val PENTAGRAM_HIT_INTERVAL = 2
        const val PENTAGRAM_DAMAGE = 8

        const val MAX_GOLEMS = 2
        const val GOLEM_SPAWN_RADIUS = 4
        const val GOLEM_ATTACK_RADIUS = 8
        const val GOLEM_HIT_INTERVAL = 5
        const val GOLEM_DAMAGE_MIN = 4
        const val GOLEM_DAMAGE_VARIANCE = 10
        const val GOLEM_HITPOINTS = 30

        val ZALCANO_ID = "npc.zalcano".asRSCM(RSCMType.NPC)
        val ZALCANO_WEAK_ID = "npc.zalcano_weak".asRSCM(RSCMType.NPC)
        val GOLEM_ID = "npc.zalcano_golem".asRSCM(RSCMType.NPC)

        val SPOT_ROCKFALL = "spotanim.zalcano_rock_fall".asRSCM(RSCMType.SPOTANIM)
        val SPOT_PENTAGRAM_BURNING = "spotanim.zalcano_pentagram_burning".asRSCM(RSCMType.SPOTANIM)
        val SPOT_GOLEM_SPAWN = "spotanim.zalcano_golem_spawn".asRSCM(RSCMType.SPOTANIM)
        val SPOT_ORE_HIT = "spotanim.zalcano_ore_hit".asRSCM(RSCMType.SPOTANIM)

        val ROCK_ACTIVE_ID = "loc.zalcano_rock_active".asRSCM(RSCMType.LOC)
        val ROCK_TILES =
            listOf(
                CoordGrid(3022, 6041, 0),
                CoordGrid(3022, 6052, 0),
                CoordGrid(3031, 6056, 0),
                CoordGrid(3040, 6052, 0),
                CoordGrid(3040, 6041, 0),
                CoordGrid(3031, 6038, 0),
            )
    }
}
