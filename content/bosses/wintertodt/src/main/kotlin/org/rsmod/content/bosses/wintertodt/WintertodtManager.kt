package org.rsmod.content.bosses.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.getInvObj
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

var Player.warmth by intVarBit("varbit.wint_warmth")
var Player.wintertodtPoints by intVarp("varp.wintertodt_points")

@Singleton
class WintertodtManager
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val playerRepo: PlayerRepository,
    private val hitProcessor: DamageOnlyPlayerHitProcessor,
    private val mapClock: MapClock,
    private val random: GameRandom,
) {
    var energy = MAX_ENERGY
        private set

    var resting = false
        private set

    private var initialized = false
    private var restEndCycle = 0
    private var boss: Npc? = null

    private val braziers = arrayOfNulls<Brazier>(BRAZIER_COUNT)
    private val pyros = arrayOfNulls<Pyromancer>(PYRO_COUNT)
    private val snowBursts = ArrayDeque<SnowBurst>()
    private var lastAttackCycle = 0
    private var lastPulseCycle = 0
    private var lastHudCycle = 0

    class Brazier(val coords: CoordGrid) {
        var state = State.UNLIT

        enum class State {
            UNLIT,
            LIT,
            BROKEN,
        }
    }

    class Pyromancer(val npc: Npc) {
        var down = false
    }

    private class SnowBurst(val coords: CoordGrid, val impactCycle: Int)

    fun tick() {
        if (!initialized) {
            initialize()
            return
        }
        val cycle = mapClock.cycle
        processSnowBursts(cycle)
        if (resting) {
            if (cycle >= restEndCycle) {
                startRound()
            }
            pushHud(cycle, force = true)
            return
        }
        drainEnergy()
        if (energy <= 0) {
            endRound()
            return
        }
        if (cycle - lastAttackCycle >= ATTACK_INTERVAL) {
            lastAttackCycle = cycle
            runAttack()
        }
        if (cycle - lastPulseCycle >= COLD_PULSE_INTERVAL) {
            lastPulseCycle = cycle
            runColdPulse()
        }
        pushHud(cycle, force = false)
    }

    private fun initialize() {
        initialized = true
        locateBraziers()
        locatePyromancers()
        spawnBoss()
    }

    private fun arenaZones(): List<ZoneKey> = buildList {
        for (x in (ARENA_MIN_X / 8)..(ARENA_MAX_X / 8)) {
            for (z in (ARENA_MIN_Z / 8)..(ARENA_MAX_Z / 8)) {
                add(ZoneKey(x, z, 0))
            }
        }
    }

    private fun locateBraziers() {
        val found = mutableListOf<CoordGrid>()
        for (zone in arenaZones()) {
            for (loc in locRepo.findAll(zone)) {
                if (loc.id in BRAZIER_LOC_IDS) {
                    found += loc.coords
                }
            }
        }
        val ordered = found.sortedWith(compareBy({ it.x }, { it.z }))
        for ((index, coords) in ordered.withIndex()) {
            if (index < BRAZIER_COUNT) {
                braziers[index] = Brazier(coords)
            }
        }
    }

    private fun locatePyromancers() {
        val found = mutableListOf<Npc>()
        for (zone in arenaZones()) {
            for (npc in npcRepo.findAll(zone)) {
                if (npc.type.id == PYRO_UP_ID || npc.type.id == PYRO_DOWN_ID) {
                    found += npc
                }
            }
        }
        val ordered = found.sortedWith(compareBy({ it.coords.x }, { it.coords.z }))
        for ((index, npc) in ordered.withIndex()) {
            if (index < PYRO_COUNT) {
                pyros[index] = Pyromancer(npc).apply { down = npc.type.id == PYRO_DOWN_ID }
            }
        }
    }

    private fun spawnBoss() {
        if (boss != null) return
        val npc = Npc("npc.wint_fire", BOSS_SPAWN)
        npc.anim("seq.wintertodt_idle_b")
        npcRepo.add(npc, Int.MAX_VALUE)
        boss = npc
    }

    fun arenaPlayers(): List<Player> {
        val players = mutableListOf<Player>()
        for (zone in arenaZones()) {
            for (player in playerRepo.findAll(zone)) {
                if (isInArena(player)) {
                    players += player
                }
            }
        }
        return players
    }

    fun isInArena(player: Player): Boolean =
        player.coords.level == 0 &&
            player.coords.x in ARENA_MIN_X..ARENA_MAX_X &&
            player.coords.z in ARENA_MIN_Z..ARENA_MAX_Z

    private fun drainEnergy() {
        energy = (energy - litCount()).coerceAtLeast(0)
    }

    fun litCount(): Int = braziers.count { it?.state == Brazier.State.LIT }

    fun brazierAt(coords: CoordGrid): Brazier? = braziers.firstOrNull { it?.coords == coords }

    fun brazierIndex(brazier: Brazier): Int = braziers.indexOf(brazier)

    private fun setBrazier(brazier: Brazier, state: Brazier.State) {
        brazier.state = state
        val loc = locRepo.findAll(brazier.coords).firstOrNull { it.id in BRAZIER_LOC_IDS }
        if (loc != null) {
            val internal =
                when (state) {
                    Brazier.State.UNLIT -> "loc.wint_brazier"
                    Brazier.State.LIT -> "loc.wint_brazier_lit"
                    Brazier.State.BROKEN -> "loc.wint_brazier_broken"
                }
            val into = ServerCacheManager.getObject(internal.asRSCM(RSCMType.LOC)) ?: return
            locRepo.change(loc, into, Int.MAX_VALUE)
        }
    }

    fun lightBrazier(brazier: Brazier) {
        setBrazier(brazier, Brazier.State.LIT)
    }

    fun repairBrazier(brazier: Brazier) {
        setBrazier(brazier, Brazier.State.UNLIT)
    }

    fun feedBrazier(kindling: Boolean) {
        energy = (energy - if (kindling) KINDLING_DRAIN else ROOT_DRAIN).coerceAtLeast(0)
    }

    fun injurePyromancer(index: Int) {
        val pyro = pyros.getOrNull(index) ?: return
        if (pyro.down) return
        pyro.down = true
        val downType = ServerCacheManager.getNpc(PYRO_DOWN_ID) ?: return
        pyro.npc.transmog(downType, Int.MAX_VALUE)
        braziers.getOrNull(index)?.let { setBrazier(it, Brazier.State.UNLIT) }
    }

    fun healPyromancer(index: Int) {
        val pyro = pyros.getOrNull(index) ?: return
        if (!pyro.down) return
        pyro.down = false
        val upType = ServerCacheManager.getNpc(PYRO_UP_ID) ?: return
        pyro.npc.transmog(upType, Int.MAX_VALUE)
    }

    fun pyroDown(index: Int): Boolean = pyros.getOrNull(index)?.down == true

    fun pyroIndexOf(npc: Npc): Int = pyros.indexOfFirst { it?.npc === npc }

    fun brazierState(index: Int): Int =
        when (braziers.getOrNull(index)?.state) {
            Brazier.State.UNLIT -> 0
            Brazier.State.LIT -> 1
            Brazier.State.BROKEN -> 2
            null -> 0
        }

    private fun runAttack() {
        when (random.of(3)) {
            0 -> attackPyromancer()
            1 -> attackBrazier()
            else -> snowBarrage()
        }
    }

    private fun attackPyromancer() {
        val up = pyros.withIndex().filter { it.value != null && !it.value!!.down }
        if (up.isEmpty()) return
        val pick = up[random.of(up.size)].index
        injurePyromancer(pick)
        for (player in arenaPlayers()) {
            player.mes("The Wintertodt strikes at the pyromancer!")
        }
    }

    private fun attackBrazier() {
        val lit = braziers.filterNotNull().filter { it.state == Brazier.State.LIT }
        if (lit.isEmpty()) return
        val pick = lit[random.of(lit.size)]
        setBrazier(pick, Brazier.State.BROKEN)
        for (player in arenaPlayers()) {
            player.mes("The brazier has been damaged by the Wintertodt's magic!")
        }
    }

    private fun snowBarrage() {
        val players = arenaPlayers()
        if (players.isEmpty()) return
        val shuffled = players.toMutableList()
        repeat(SNOW_TARGETS) {
            if (shuffled.isEmpty()) return
            val player = shuffled.removeAt(random.of(shuffled.size))
            val tile = player.coords
            locRepo.add(
                tile,
                "loc.wint_area_attack_snow",
                SNOW_IMPACT_DELAY + 2,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
            snowBursts += SnowBurst(tile, mapClock.cycle + SNOW_IMPACT_DELAY)
        }
    }

    private fun processSnowBursts(cycle: Int) {
        val iterator = snowBursts.iterator()
        while (iterator.hasNext()) {
            val burst = iterator.next()
            if (cycle < burst.impactCycle) continue
            iterator.remove()
            for (player in arenaPlayers()) {
                if (player.coords.chebyshevDistance(burst.coords) <= SNOW_RADIUS) {
                    player.takeInstantHit(HitType.Typeless, SNOW_DAMAGE, hitProcessor, COLD_HITMARK)
                }
            }
        }
    }

    private fun runColdPulse() {
        for (player in arenaPlayers()) {
            if (player.warmth <= 0) {
                player.takeInstantHit(
                    HitType.Typeless,
                    coldDamage(player),
                    hitProcessor,
                    COLD_HITMARK,
                )
                continue
            }
            val drain = coldDrain(player)
            val regen = warmthRegen(player)
            player.warmth = (player.warmth - drain + regen).coerceIn(0, MAX_WARMTH)
        }
    }

    fun coldDamage(player: Player): Int {
        val base = 16.0 - player.firemakingLvl * 0.11
        val lit = 1.0 - litCount() * 0.15
        val clothing = 1.0 - warmPieces(player) * 0.1
        return (base * lit * clothing).toInt().coerceAtLeast(1)
    }

    private fun coldDrain(player: Player): Int {
        val base = 110.0 - player.firemakingLvl * 0.55
        val lit = litCount() * 12.0
        val clothing = warmPieces(player) * 8.0
        return (base - lit - clothing).toInt().coerceIn(MIN_WARMTH_DRAIN, MAX_WARMTH)
    }

    private fun warmthRegen(player: Player): Int = 10 + warmPieces(player) * 2

    fun warmPieces(player: Player): Int =
        player.worn.objs.count { it != null && getInvObj(it).internalName in WARM_ITEMS }
            .coerceAtMost(MAX_WARM_PIECES)

    fun pushHud(cycle: Int, force: Boolean) {
        if (!force && cycle - lastHudCycle < HUD_INTERVAL) return
        lastHudCycle = cycle
        for (player in arenaPlayers()) {
            sendStatus(player)
        }
    }

    fun sendStatus(player: Player) {
        player.runClientScript(
            WINT_UPDATE_CS2,
            player.wintertodtPoints,
            if (resting) -1 else energy,
            if (pyroDown(0)) 1 else 0,
            if (pyroDown(1)) 1 else 0,
            if (pyroDown(2)) 1 else 0,
            if (pyroDown(3)) 1 else 0,
            brazierState(0),
            brazierState(1),
            brazierState(2),
            brazierState(3),
        )
    }

    private fun endRound() {
        resting = true
        restEndCycle = mapClock.cycle + REST_DURATION
        boss?.anim("seq.wintertodt_resting")
        for (player in arenaPlayers()) {
            player.mes("The Wintertodt's energy has been subdued!")
            awardRound(player)
            player.warmth = MAX_WARMTH
        }
        for ((index, brazier) in braziers.withIndex()) {
            if (brazier != null && !pyroDown(index)) {
                setBrazier(brazier, Brazier.State.UNLIT)
            }
        }
    }

    private fun startRound() {
        resting = false
        energy = MAX_ENERGY
        boss?.anim("seq.wintertodt_idle_b")
        for (player in arenaPlayers()) {
            player.mes("The Wintertodt's energy begins to rise...")
        }
    }

    private fun awardRound(player: Player) {
        val points = player.wintertodtPoints
        if (points <= 0) return
        val crates = points / CRATE_COST
        if (crates > 0) {
            player.invAddOrDrop(objRepo, "obj.wint_reward_box", crates)
            player.mes("You have gained enough points to receive a supply crate.")
            val kc = player.vars["varp.total_wintertodt_kills"] + 1
            VarPlayerIntMapSetter.set(player, "varp.total_wintertodt_kills", kc)
            player.statAdvance("stat.firemaking", player.firemakingLvl * 100.0 * crates)
            player.wintertodtPoints = points - crates * CRATE_COST
        } else {
            player.mes(
                "You did not gain enough points for a reward. " +
                    "Your points will carry over to the next round."
            )
        }
    }

    companion object {
        const val MAX_ENERGY = 3500
        const val MAX_WARMTH = 1000
        const val MIN_WARMTH_DRAIN = 15
        const val CRATE_COST = 500
        const val MAX_WARM_PIECES = 4

        const val ARENA_MIN_X = 1600
        const val ARENA_MAX_X = 1663
        const val ARENA_MIN_Z = 3968
        const val ARENA_MAX_Z = 4031

        const val BRAZIER_COUNT = 4
        const val PYRO_COUNT = 4

        const val ATTACK_INTERVAL = 12
        const val COLD_PULSE_INTERVAL = 8
        const val HUD_INTERVAL = 4
        const val REST_DURATION = 50
        const val SNOW_IMPACT_DELAY = 3
        const val SNOW_RADIUS = 1
        const val SNOW_DAMAGE = 4
        const val SNOW_TARGETS = 3

        const val ROOT_DRAIN = 8
        const val KINDLING_DRAIN = 20

        const val POINTS_LIGHT = 25
        const val POINTS_REPAIR = 25
        const val POINTS_ROOT = 10
        const val POINTS_KINDLING = 25
        const val POINTS_HEAL_PYRO = 75

        val BOSS_SPAWN = CoordGrid(1630, 4007, 0)

        val BRAZIER_LOC_IDS =
            setOf(
                "loc.wint_brazier".asRSCM(RSCMType.LOC),
                "loc.wint_brazier_lit".asRSCM(RSCMType.LOC),
                "loc.wint_brazier_broken".asRSCM(RSCMType.LOC),
            )

        val PYRO_UP_ID = "npc.wint_wizard".asRSCM(RSCMType.NPC)
        val PYRO_DOWN_ID = "npc.wint_wizard_down".asRSCM(RSCMType.NPC)

        val WINT_UPDATE_CS2 =
            "clientscript.[clientscript,wint_update]".asRSCM(RSCMType.CLIENTSCRIPT)

        val COLD_HITMARK =
            HitmarkTypeGroup(
                lit = "hitmark.wintertodt_drain_lit",
                tint = "hitmark.wintertodt_drain_tint",
            )

        val WARM_ITEMS =
            setOf(
                "obj.firemaking_cape",
                "obj.firemaking_cape_t",
                "obj.firemaking_hood",
                "obj.pyromancer_hood",
                "obj.pyromancer_top",
                "obj.pyromancer_bottom",
                "obj.pyromancer_boots",
                "obj.clue_hunter_garb",
                "obj.clue_hunter_trousers",
                "obj.clue_hunter_gloves",
                "obj.clue_hunter_boots",
                "obj.clue_hunter_cloak",
                "obj.clue_hunter_helm",
                "obj.warm_gloves",
                "obj.santa_hat",
                "obj.santa_jacket",
                "obj.santa_pantaloons",
                "obj.santa_gloves",
                "obj.santa_boots",
                "obj.bunny_paws",
                "obj.staff_of_fire",
                "obj.inferno_axe",
                "obj.infernal_axe",
            )
    }
}
