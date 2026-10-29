package org.rsmod.content.bosses.maggotking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import kotlin.math.max
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.spawnOwnedNpc
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.baseAttackLvl
import org.rsmod.api.player.stat.baseDefenceLvl
import org.rsmod.api.player.stat.baseStrengthLvl
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The boss-side mechanics the DSL can't express: bile pools (spawn, step-on and cleanup), the
 * bone-wall approach + 3x4 slam, the screech defence swing and style alternation, carrion throws
 * that clear bile, and the ur-maggot larvae flight/lane AI.
 *
 * All state is keyed by the boss [Npc] so every instanced copy gets its own arena.
 */
@Singleton
class MaggotKingEncounter
@Inject
constructor(
    private val deps: BossDeps,
    private val aiPlayerInteractions: AiPlayerInteractions,
) {
    private enum class BileKind(val loc: String) {
        Poisonous("loc.maggot_king_poisonous_bile"),
        Sticky("loc.maggot_king_sticky_bile"),
    }

    private class BilePool(val kind: BileKind, val loc: LocInfo) {
        val triggered: MutableSet<PlayerUid> = mutableSetOf()
    }

    private class Larva(
        val boss: Npc,
        val player: Player,
        var direction: Int,
        var chips: Int = 0,
        var latched: Boolean = false,
    )

    private val bile: MutableMap<Npc, MutableMap<CoordGrid, BilePool>> = IdentityHashMap()
    private val carrion: MutableMap<Npc, MutableMap<CoordGrid, LocInfo>> = IdentityHashMap()
    private val larvae: MutableMap<Npc, Larva> = IdentityHashMap()

    fun register(script: ScriptContext) {
        deps.extensionRegistry.register(EXT_BILE_POISONOUS) { ctx -> strikeBile(ctx.npc, ctx.target, BileKind.Poisonous) }
        deps.extensionRegistry.register(EXT_BILE_STICKY) { ctx -> strikeBile(ctx.npc, ctx.target, BileKind.Sticky) }
        deps.extensionRegistry.register(EXT_APPROACH) { ctx -> approach(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_SLAM) { ctx -> slam(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_SCREECH_BEGIN) { ctx -> screechBegin(ctx.npc) }
        deps.extensionRegistry.register(EXT_SCREECH_END) { ctx -> screechEnd(ctx.npc) }
        deps.extensionRegistry.register(EXT_CARRION) { ctx -> throwCarrion(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_LARVA) { ctx -> launchLarva(ctx.npc, ctx.target) }
        deps.extensionRegistry.register(EXT_LARVA_SWARM) { ctx -> swarm(ctx.npc, ctx.target) }

        val larvaType = ServerCacheManager.getNpc(LARVA.asRSCM(RSCMType.NPC))
        if (larvaType != null) {
            script.onModifyNpcHit(larvaType) { modifyLarvaHit(npc, hit) }
            script.onNpcQueue(larvaType, "queue.death") { larvaDeath() }
        }
        script.onPlayerCoordsChanged { bileStep(player) }
        script.onEvent<NpcStateEvents.Create> { if (npc.type.id == BOSS_TYPE_ID) wallFight(npc) }
        script.onEvent<NpcStateEvents.Delete> { cleanup(npc) }
    }

    fun larvaCount(boss: Npc): Int =
        larvae.count { it.value.boss === boss && it.key.isSlotAssigned && it.key.hitpoints > 0 }

    // --- Bile -----------------------------------------------------------------------------

    // Both basic attacks leave pools under where the player stood when the attack lands.
    private fun strikeBile(boss: Npc, target: Player, kind: BileKind) {
        val impact = target.coords
        val neighbours =
            CARDINAL
                .map { (dx, dz) -> impact.translate(dx, dz) }
                .filter { inStrip(boss, it) && bile[boss]?.contains(it) != true }
                .toMutableList()
        addBile(boss, impact, kind)
        repeat(BILE_SPLASH.coerceAtMost(neighbours.size)) {
            addBile(boss, neighbours.removeAt(deps.random.of(neighbours.size)), kind)
        }
    }

    private fun addBile(boss: Npc, tile: CoordGrid, kind: BileKind) {
        if (!inStrip(boss, tile) || bile[boss]?.contains(tile) == true) return
        removeCarrion(boss, tile)
        val loc =
            deps.locRepo.add(
                tile,
                kind.loc,
                Int.MAX_VALUE,
                LocAngle.South,
                LocShape.CentrepieceStraight,
            )
        bile.getOrPut(boss) { mutableMapOf() }[tile] = BilePool(kind, loc)
        deps.worldQueues.add(BILE_LIFETIME) { clearBile(boss, tile) }
    }

    private fun clearBile(boss: Npc, tile: CoordGrid) {
        bile[boss]?.remove(tile)?.let { deps.locRepo.del(it.loc, Int.MAX_VALUE) }
    }

    private fun bileStep(player: Player) {
        val tile = player.coords
        for ((boss, pools) in bile) {
            val pool = pools[tile] ?: continue
            if (!boss.isSlotAssigned || boss.hitpoints <= 0) continue
            if (!pool.triggered.add(player.uid)) continue
            when (pool.kind) {
                BileKind.Poisonous -> poisonBile(boss, player)
                BileKind.Sticky -> stickyBile(boss, player)
            }
            return
        }
    }

    private fun poisonBile(boss: Npc, player: Player) {
        if (ToxinImmunity.hasPoisonImmunity(player)) {
            ToxinImmunity.drainImmunity(player, BILE_IMMUNITY_DRAIN)
            player.mes(BILE_IMMUNITY_MESSAGE)
            return
        }
        player.queueHit(boss, 1, HitType.Typeless, deps.random.of(BILE_DAMAGE), deps.playerHitModifier)
        player.mes(BILE_DAMAGE_MESSAGE)
    }

    private fun stickyBile(boss: Npc, player: Player) {
        val damage = deps.random.of(BILE_DAMAGE)
        player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
        // UNVERIFIED: which stat drains on a given step. The wiki says Attack, Strength or
        // Defence based on the damage dealt; a random pick per step is a reasonable stand-in.
        val stat =
            when (deps.random.of(3)) {
                0 -> "attack" to player.baseAttackLvl
                1 -> "strength" to player.baseStrengthLvl
                else -> "defence" to player.baseDefenceLvl
            }
        if (stat.second > 0) player.statSub(stat.first, constant = STAT_DRAIN, percent = 0)
        player.actionDelay = maxOf(player.actionDelay, player.currentMapClock) + BILE_ATTACK_DELAY
        player.mes(BILE_STICKY_MESSAGE)
    }

    // --- Bone wall ------------------------------------------------------------------------

    // After a melee-phase screech the boss walks south until its face row hugs the wall, centred
    // over the player's tile (clamped inside the nest so it never clips the wall ends).
    private fun approach(boss: Npc, target: Player) {
        val centre = (target.coords.x - arenaXOffset(boss)).coerceIn(MIN_CENTRE_X, MAX_CENTRE_X)
        val dest = arena(boss, CoordGrid(centre - BOSS_HALF, WALL_FRONT_Z))
        if (boss.coords == dest) {
            boss.vars[AT_WALL] = 1
            return
        }
        boss.vars[AT_WALL] = 0
        val travel = travelTicks(boss.coords, dest)
        deps.suppressAttacks(boss, travel + APPROACH_SETTLE)
        PathingEntityCommon.exactMove(
            boss,
            boss.coords,
            dest,
            delay1 = 0,
            delay2 = travel * CLIENT_CYCLES_PER_TICK,
            dir = SOUTH,
            collision = deps.collision,
        )
        deps.worldQueues.add(travel) {
            if (boss.isSlotAssigned && boss.hitpoints > 0) boss.vars[AT_WALL] = 1
        }
    }

    private fun travelTicks(from: CoordGrid, to: CoordGrid): Int =
        maxOf(1, from.chebyshevDistance(to))

    // --- Slam -----------------------------------------------------------------------------

    private fun slam(boss: Npc, target: Player) {
        boss.vars[SLAM_TICK] = deps.mapClock.cycle
        val zone = slamZone(boss)
        if (target.coords in zone) {
            target.queueHit(
                source = boss,
                delay = SLAM_LAND_TICKS,
                type = HitType.Melee,
                damage = deps.random.of(0, SLAM_MAX_HIT),
                modifier = deps.playerHitModifier,
                penetration = SLAM_PRAYER_PENETRATION,
            )
        }
        val free =
            zone
                .filter { inStrip(boss, it) && bile[boss]?.contains(it) != true }
                .toMutableList()
        repeat(SLAM_BILE.coerceAtMost(free.size)) {
            val tile = free.removeAt(deps.random.of(free.size))
            addBile(boss, tile, if (deps.random.of(2) == 0) BileKind.Poisonous else BileKind.Sticky)
        }
    }

    /** The 3-wide by 4-deep patch in front of the wall under the boss's centre column. */
    fun slamZone(boss: Npc): Set<CoordGrid> {
        val centre = boss.coords.x + BOSS_HALF - arenaXOffset(boss)
        val tiles = mutableSetOf<CoordGrid>()
        for (x in centre - 1..centre + 1) {
            for (z in STRIP_Z_RANGE) {
                tiles += arena(boss, CoordGrid(x, z))
            }
        }
        return tiles
    }

    fun inPunishWindow(boss: Npc): Boolean =
        deps.mapClock.cycle - boss.vars[SLAM_TICK] in 0..PUNISH_WINDOW

    // --- Screech --------------------------------------------------------------------------

    private fun screechBegin(boss: Npc) {
        boss.vars[SCREECHES] = boss.vars[SCREECHES] + 1
        boss.defenceLvl = maxOf(1, boss.baseDefenceLvl / 2)
        if (boss.vars[AT_WALL] == 1) {
            boss.vars[AT_WALL] = 0
            boss.vars[MODE] = MODE_STYLE
            val home = arena(boss, BOSS_SPAWN)
            val travel = travelTicks(boss.coords, home)
            PathingEntityCommon.exactMove(
                boss,
                boss.coords,
                home,
                delay1 = 0,
                delay2 = travel * CLIENT_CYCLES_PER_TICK,
                dir = NORTH,
                collision = deps.collision,
            )
        }
    }

    private fun screechEnd(boss: Npc) {
        boss.defenceLvl = boss.baseDefenceLvl
        // The first screech quickens every later attack by one tick.
        deps.encounter(boss).attackRateOverride = ATTACK_RATE_FAST
        if (boss.vars[MODE] == MODE_STYLE) {
            boss.vars[STYLE] = 1 - boss.vars[STYLE]
        }
        // Past the melee threshold each screech flips between style and slam segments.
        if (boss.hitpoints * 100 <= boss.baseHitpointsLvl * MELEE_PHASE_PERCENT) {
            boss.vars[MODE] = 1 - boss.vars[MODE]
        }
    }

    // --- Carrion --------------------------------------------------------------------------

    // Screech flings carrion piles at random strip tiles; each wipes the bile it lands on and
    // splatters the four neighbours. Direct hits hurt, splash hits hurt less.
    private fun throwCarrion(boss: Npc, target: Player) {
        val aimed = mutableSetOf<CoordGrid>()
        repeat(deps.random.of(CARRION_COUNT)) {
            pickFreeStripTile(boss)?.let { tile ->
                if (aimed.add(tile)) telegraph(boss, tile)
            }
        }
        if (aimed.isEmpty()) return
        val targetUid = target.uid
        deps.worldQueues.add(CARRION_FLIGHT) {
            val player = targetUid.resolve(deps.playerList) ?: return@add
            for (tile in aimed) landCarrion(boss, player, tile)
        }
    }

    private fun telegraph(boss: Npc, tile: CoordGrid) {
        deps.worldRepo.spotanimMap(SpotanimType(CARRION_TELEGRAPH_GFX.asRSCM(RSCMType.SPOTANIM)), tile)
        deps.bossProjectile(
            spotanim = CARRION_PROJ.asRSCM(RSCMType.SPOTANIM),
            src = boss.coords.translate(BOSS_HALF, BOSS_HALF),
            target = tile,
            startHeight = PROJ_START_HEIGHT,
            endHeight = PROJ_END_HEIGHT,
            delay = 0,
            travel = CARRION_FLIGHT * CLIENT_CYCLES_PER_TICK,
            curve = PROJ_CURVE,
        )
    }

    private fun landCarrion(boss: Npc, target: Player, tile: CoordGrid) {
        clearBile(boss, tile)
        if (target.coords == tile) {
            target.queueHit(boss, 1, HitType.Typeless, deps.random.of(CARRION_HIT), deps.playerHitModifier)
        }
        deps.worldRepo.spotanimMap(SpotanimType(CARRION_LAND_GFX.asRSCM(RSCMType.SPOTANIM)), tile)
        if (carrion[boss]?.contains(tile) != true) {
            val loc =
                deps.locRepo.add(
                    tile,
                    CARRION,
                    Int.MAX_VALUE,
                    LocAngle.South,
                    LocShape.CentrepieceStraight,
                )
            carrion.getOrPut(boss) { mutableMapOf() }[tile] = loc
        }
        for ((dx, dz) in CARDINAL) {
            val splash = tile.translate(dx, dz)
            if (!inStrip(boss, splash)) continue
            clearBile(boss, splash)
            if (target.coords == splash) {
                target.queueHit(boss, 1, HitType.Typeless, deps.random.of(CARRION_SPLASH_HIT), deps.playerHitModifier)
            }
        }
    }

    private fun removeCarrion(boss: Npc, tile: CoordGrid) {
        carrion[boss]?.remove(tile)?.let { deps.locRepo.del(it, Int.MAX_VALUE) }
    }

    private fun pickFreeStripTile(boss: Npc): CoordGrid? {
        val options = mutableListOf<CoordGrid>()
        for (x in STRIP_X_RANGE) {
            for (z in STRIP_Z_RANGE) {
                val tile = arena(boss, CoordGrid(x, z))
                if (bile[boss]?.contains(tile) != true) options += tile
            }
        }
        return deps.random.pickOrNull(options)
    }

    // --- Larvae ---------------------------------------------------------------------------

    // At ~20% the boss lobs a whole wave at once (up to one per strip row) if none are left.
    private fun swarm(boss: Npc, target: Player) {
        val want = LARVA_SWARM_MAX - larvaCount(boss)
        repeat(want.coerceAtLeast(0)) { launchLarva(boss, target) }
    }

    private fun launchLarva(boss: Npc, target: Player) {
        val west = boss.vars[LARVA_SIDE] == 0
        boss.vars[LARVA_SIDE] = 1 - boss.vars[LARVA_SIDE]
        val lane = deps.random.pickOrNull(LARVA_ROWS) ?: return
        val edge = if (west) STRIP_X_RANGE.first else STRIP_X_RANGE.last
        val landing = arena(boss, CoordGrid(edge, lane))
        val launch = boss.coords.translate(BOSS_HALF, 0)
        val larva = deps.spawnOwnedNpc(boss, LARVA, launch) ?: return
        larva.mode = NpcMode.None
        larva.ignoreCombatInteractions = true
        larva.moveRestrict = MoveRestrict.PassThru
        larva.defaultMoveSpeed = MoveSpeed.Walk
        larva.vars[LARVA_FLY] = LARVA_FLIGHT
        larvae[larva] = Larva(boss, target, direction = if (west) 1 else -1)
        deps.bossProjectile(
            spotanim = LARVA_PROJ.asRSCM(RSCMType.SPOTANIM),
            src = launch,
            target = landing,
            startHeight = PROJ_START_HEIGHT,
            endHeight = PROJ_END_HEIGHT,
            delay = 0,
            travel = LARVA_FLIGHT * CLIENT_CYCLES_PER_TICK,
            curve = PROJ_CURVE,
        )
        PathingEntityCommon.exactMove(
            larva,
            larva.coords,
            landing,
            delay1 = 0,
            delay2 = LARVA_FLIGHT * CLIENT_CYCLES_PER_TICK,
            dir = if (west) WEST else EAST,
            collision = deps.collision,
        )
        deps.worldQueues.add(LARVA_FLIGHT) {
            if (!larva.isSlotAssigned || larva.hitpoints <= 0) return@add
            larva.vars[LARVA_FLY] = 0
            patrolTick(larva)
        }
    }

    // Landed larvae pace their row, eat carrion (healing the boss), and latch onto the player to
    // chip escalating damage that also feeds the boss.
    private fun patrolTick(larva: Npc) {
        val state = larvae[larva] ?: return
        if (!larva.isSlotAssigned || larva.hitpoints <= 0 || larva.vars[LARVA_FLY] > 0) return
        val boss = state.boss
        val player = state.player
        if (!boss.isSlotAssigned || boss.hitpoints <= 0 || !player.isValidTarget()) {
            larvae.remove(larva)
            if (larva.isSlotAssigned) deps.npcRepo.del(larva, Int.MAX_VALUE)
            return
        }
        if (larva.coords.chebyshevDistance(player.coords) <= LATCH_RANGE) {
            state.latched = true
            state.chips++
            val damage = (CHIP_START + state.chips).coerceAtMost(CHIP_MAX)
            player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
            boss.heal(damage, showHitsplat = false)
        } else {
            state.latched = false
            state.chips = 0
            patrolStep(larva, state)
        }
        deps.worldQueues.add(LARVA_STEP_TICKS) { patrolTick(larva) }
    }

    private fun patrolStep(larva: Npc, state: Larva) {
        var next = larva.coords.translate(state.direction, 0)
        if (next.x !in arenaXRange(larva)) {
            state.direction = -state.direction
            next = larva.coords.translate(state.direction, 0)
            if (next.x !in arenaXRange(larva)) return
        }
        larva.walk(listOf(next))
        carrion[state.boss]?.remove(next)?.let {
            deps.locRepo.del(it, Int.MAX_VALUE)
            state.boss.heal(CARRION_HEAL, showHitsplat = true)
        }
    }

    // A larva still in flight dies to any standard ranged hit outright.
    private fun modifyLarvaHit(larva: Npc, hit: HitBuilder) {
        if (larva.vars[LARVA_FLY] > 0 && hit.type == HitType.Ranged) {
            hit.damage = larva.hitpoints.coerceAtLeast(1)
        }
    }

    private suspend fun StandardNpcAccess.larvaDeath() {
        larvae.remove(npc)
        noneMode()
        hideAllOps()
    }

    // --- Arena geometry -------------------------------------------------------------------

    // Arena constants are expressed on the template lair (region 11645) and re-anchored to each
    // boss's spawn tile so instanced copies land correctly.
    private fun arena(boss: Npc, template: CoordGrid): CoordGrid =
        template.translate(boss.spawnCoords.x - BOSS_SPAWN.x, boss.spawnCoords.z - BOSS_SPAWN.z, 0)

    private fun arenaXOffset(boss: Npc): Int = boss.spawnCoords.x - BOSS_SPAWN.x

    private fun inStrip(boss: Npc, tile: CoordGrid): Boolean {
        val strip = arena(boss, CoordGrid(STRIP_X_RANGE.first, STRIP_Z_RANGE.first))
        val dx = tile.x - strip.x
        val dz = tile.z - strip.z
        return tile.level == strip.level &&
            dx in 0 until STRIP_X_RANGE.count() &&
            dz in 0 until STRIP_Z_RANGE.count()
    }

    private fun arenaXRange(npc: Npc): IntRange {
        val first = npc.spawnCoords.x - (BOSS_SPAWN.x - STRIP_X_RANGE.first)
        return first..first + STRIP_X_RANGE.count() - 1
    }

    private fun cleanup(npc: Npc) {
        bile.remove(npc)?.values?.forEach { deps.locRepo.del(it.loc, Int.MAX_VALUE) }
        carrion.remove(npc)?.values?.forEach { deps.locRepo.del(it, Int.MAX_VALUE) }
        larvae.remove(npc)
    }

    companion object {
        // The king fights across the bone wall: every attack reaches arena-wide without needing
        // line of sight to the player.
        fun wallFight(npc: Npc) {
            npc.apRequiresLineOfSight = false
            npc.apRangeOverride = ARENA_AP_RANGE
        }

        const val BOSS = "npc.maggot_king"
        val BOSS_TYPE_ID = BOSS.asRSCM(RSCMType.NPC)
        const val CORPSE = "npc.maggot_king_corpse"
        const val LARVA = "npc.ur_maggot_larvae"
        const val BOSS_SIZE = 5
        const val BOSS_HALF = BOSS_SIZE / 2

        const val STYLE = "varn.maggot_king_style"
        const val MODE = "varn.maggot_king_mode"
        const val AT_WALL = "varn.maggot_king_at_wall"
        const val SLAM_TICK = "varn.maggot_king_slam_tick"
        const val SCREECHES = "varn.maggot_king_screeches"
        const val LARVA_SIDE = "varn.maggot_king_larva_side"
        const val LARVA_FLY = "varn.maggot_king_larva_fly"

        const val STYLE_RANGED = 0
        const val STYLE_MAGIC = 1
        const val MODE_STYLE = 0
        const val MODE_MELEE = 1

        const val EXT_BILE_POISONOUS = "maggot_king.bile_poisonous"
        const val EXT_BILE_STICKY = "maggot_king.bile_sticky"
        const val EXT_APPROACH = "maggot_king.approach"
        const val EXT_SLAM = "maggot_king.slam"
        const val EXT_SCREECH_BEGIN = "maggot_king.screech_begin"
        const val EXT_SCREECH_END = "maggot_king.screech_end"
        const val EXT_CARRION = "maggot_king.carrion"
        const val EXT_LARVA = "maggot_king.larva"
        const val EXT_LARVA_SWARM = "maggot_king.larva_swarm"

        // Template lair (region 11645): a 15x4 player strip south of the bone wall, the boss's
        // nest behind it.
        val BOSS_SPAWN = CoordGrid(2909, 8039)
        val STRIP_X_RANGE = 2904..2918
        val STRIP_Z_RANGE = 8033..8036
        // The boss's south-west corner z when hugging the wall: its front row sits on the wall
        // tiles themselves so the head hangs over to the player side (and stays melee-reachable
        // for the punish window).
        const val WALL_FRONT_Z = 8037
        const val ARENA_AP_RANGE = 20
        const val MIN_CENTRE_X = 2906
        const val MAX_CENTRE_X = 2916
        val LARVA_ROWS = listOf(8033, 8034, 8035, 8036)

        const val MELEE_PHASE_PERCENT = 65
        const val LARVA_PHASE_PERCENT = 83
        const val FINAL_PHASE_PERCENT = 20

        private const val SOUTH = 0
        private const val WEST = 512
        private const val NORTH = 1024
        private const val EAST = 1536
        private const val CLIENT_CYCLES_PER_TICK = 30
        private const val APPROACH_SETTLE = 1
        private val CARDINAL = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        const val ATTACK_RATE = 4
        const val ATTACK_RATE_FAST = 3

        private const val BILE_SPLASH = 2
        private val BILE_DAMAGE = 4..8
        private const val BILE_LIFETIME = 100
        private const val BILE_ATTACK_DELAY = 1
        private const val BILE_IMMUNITY_DRAIN = 75
        private const val STAT_DRAIN = 3
        private const val BILE_DAMAGE_MESSAGE = "The poisonous bile burns you."
        private const val BILE_STICKY_MESSAGE = "The sticky bile clings to you."
        private const val BILE_IMMUNITY_MESSAGE = "The bile weakens your poison immunity."

        private const val SLAM_MAX_HIT = 55
        private const val SLAM_LAND_TICKS = 1
        private const val SLAM_PRAYER_PENETRATION = 40
        private const val SLAM_BILE = 3
        const val PUNISH_WINDOW = 1

        private val CARRION_COUNT = 4..6
        private const val CARRION_FLIGHT = 3
        private const val CARRION = "loc.maggot_king_blood"
        private const val CARRION_PROJ = "spotanim.maggot_king_blood_projectile_big"
        private const val CARRION_LAND_GFX = "spotanim.maggot_king_blood_ground_hit"
        private const val CARRION_TELEGRAPH_GFX = "spotanim.maggot_king_blood_ground_idle"
        private const val CARRION_HEAL = 1
        private val CARRION_HIT = 0..25
        private val CARRION_SPLASH_HIT = 0..10

        private const val LARVA_PROJ = "spotanim.maggot_king_blood_projectile_small"
        private const val LARVA_FLIGHT = 3
        private const val LARVA_STEP_TICKS = 2
        private const val LARVA_SWARM_MAX = 4
        private const val LATCH_RANGE = 1
        private const val CHIP_START = 2
        private const val CHIP_MAX = 25

        private const val PROJ_START_HEIGHT = 100
        private const val PROJ_END_HEIGHT = 16
        private const val PROJ_CURVE = 15
    }
}

private fun HitBuilder.playerAttacker(players: org.rsmod.game.entity.PlayerList): Player? =
    if (isFromPlayer) sourceUid?.let { PlayerUid(it).resolve(players) } else null

