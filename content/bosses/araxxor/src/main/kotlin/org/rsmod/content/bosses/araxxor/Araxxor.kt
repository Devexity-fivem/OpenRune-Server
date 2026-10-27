package org.rsmod.content.bosses.araxxor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.bosses.runtime.spawnOwnedNpc
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Araxxor (npc.araxxor).
 *
 * OSRS: melee in range, magic and ranged attacks otherwise; every six standard attacks one
 * of three acid specials in rotation — venom drip under the player, an acid spray of pools
 * thrown outward, and an acid cannon blast on the player's tile. Eggs hatch into minions
 * on the same cadence (explosive, mirrorback, acidic araxytes). Acid pools inflict venom
 * and burn while stood on. Below 25% hp Araxxor enrages: faster attack rate, tougher
 * defence, and its melee becomes a cleave that leaves acid pools.
 */
class Araxxor @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val pools = IdentityHashMap<Npc, MutableList<Pool>>()
    private val poolTicking = IdentityHashMap<Npc, Boolean>()
    private val mirrorbacks = IdentityHashMap<Npc, MutableSet<Npc>>()
    private val minionCount = IdentityHashMap<Npc, Int>()

    private class Pool(val tile: CoordGrid, val expiry: Int)

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)

        poolsProjectile = POOLS_PROJ.asRSCM(RSCMType.SPOTANIM)
        explodeSpot = SpotanimType(EXPLODE_SPOT.asRSCM(RSCMType.SPOTANIM))

        deps.extensionRegistry.register("araxxor.venom") { _, _, target, _ ->
            if (deps.random.of(VENOM_ROLL) == 0) PlayerVenom.tryVenom(target)
        }

        deps.extensionRegistry.register("araxxor.hatch") { _, npc, target, _ ->
            npc?.let { hatchMinion(it, target) }
        }

        deps.extensionRegistry.register("araxxor.acid_drip") { _, npc, target, _ ->
            npc?.let { acidDrip(it, target) }
        }

        deps.extensionRegistry.register("araxxor.acid_spray") { _, npc, target, _ ->
            npc?.let { acidSpray(it, target) }
        }

        deps.extensionRegistry.register("araxxor.acid_bullet") { _, npc, target, _ ->
            npc?.let { acidBullet(it, target) }
        }

        deps.extensionRegistry.register("araxxor.cleave_pools") { _, npc, target, _ ->
            npc?.let { cleavePools(it, target) }
        }
    }

    private fun hatchMinion(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val count = minionCount.merge(boss, 1, Int::plus) ?: return
        if (count > MAX_MINIONS) return
        val kind = MINION_ORDER[(count - 1) % MINION_ORDER.size]
        val tile = freeTileNear(boss.coords.translate(boss.size / 2, boss.size / 2), 3) ?: return
        val egg =
            deps.spawnOwnedNpc(boss, kind.egg, tile, EGG_HATCH_TICKS + 2) ?: return
        egg.anim(EGG_SPAWN_ANIM, 0, 0)
        deps.worldQueues.add(EGG_HATCH_TICKS) {
            if (!boss.isSlotAssigned || boss.hitpoints <= 0) {
                deps.npcRepo.del(egg, Int.MAX_VALUE)
                return@add
            }
            deps.npcRepo.del(egg, Int.MAX_VALUE)
            val minion = deps.spawnOwnedNpc(boss, kind.npc, tile) ?: return@add
            minion.anim(HATCH_ANIM, 0, 0)
            minion.mode = NpcMode.ApPlayer2
            if (target.isValidTarget()) {
                minion.assignSpawnOwner(target, deps.mapClock.cycle)
                minion.apPlayer2(target, deps.playerInteractions)
            }
            when (kind) {
                MinionKind.Explode -> explodeWatch(minion, target)
                MinionKind.Mirrorback ->
                    mirrorbacks.getOrPut(boss) { mutableSetOf() } += minion
                MinionKind.Venom -> Unit
            }
        }
    }

    private fun explodeWatch(minion: Npc, target: Player) {
        deps.repeatTick(
            ticks = MINION_MAX_TICKS,
            onTick = { remaining ->
                if (minion.hitpoints <= 0 || !minion.isSlotAssigned) return@repeatTick false
                if (!target.isValidTarget()) {
                    deps.npcRepo.del(minion, Int.MAX_VALUE)
                    return@repeatTick false
                }
                if (minion.isWithinDistance(target, 1)) {
                    explodeMinion(minion, target)
                    return@repeatTick false
                }
                remaining > 1
            },
        )
    }

    private fun explodeMinion(minion: Npc, target: Player) {
        deps.worldRepo.spotanimMap(explodeSpot, minion.coords)
        minion.anim(EXPLODE_ANIM, 0, 0)
        if (target.isValidTarget() && minion.isWithinDistance(target, 1)) {
            target.queueHit(
                minion,
                1,
                HitType.Typeless,
                deps.random.of(EXPLODE_MAX + 1),
                deps.playerHitModifier,
            )
        }
        deps.npcRepo.del(minion, Int.MAX_VALUE)
    }

    private fun acidDrip(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        deps.repeatTick(
            ticks = DRIP_TICKS,
            onTick = { remaining ->
                if (boss.hitpoints <= 0 || !target.isValidTarget()) return@repeatTick false
                spitPool(boss, centre, target.coords)
                remaining > 1
            },
        )
    }

    private fun acidSpray(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        repeat(SPRAY_COUNT) {
            val tile =
                target.coords.translate(
                    deps.random.of(SPRAY_RADIUS * 2 + 1) - SPRAY_RADIUS,
                    deps.random.of(SPRAY_RADIUS * 2 + 1) - SPRAY_RADIUS,
                )
            spitPool(boss, centre, tile)
        }
    }

    private fun acidBullet(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val tiles =
            listOf(
                target.coords,
                target.coords.translate(deps.random.of(3) - 1, deps.random.of(3) - 1),
                target.coords.translate(deps.random.of(3) - 1, deps.random.of(3) - 1),
            )
        for (tile in tiles) {
            spitPool(boss, centre, tile)
        }
    }

    private fun cleavePools(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val dx = target.coords.x - centre.x
        val dz = target.coords.z - centre.z
        val steps = max(abs(dx), abs(dz))
        if (steps <= 0) return
        val tiles =
            (1..CLEAVE_LEN).map { centre.translate(dx * it / steps, dz * it / steps) }
        addPools(boss, tiles, POOL_LINGER_TICKS)
    }

    private fun spitPool(boss: Npc, centre: CoordGrid, tile: CoordGrid) {
        val distance = max(abs(tile.x - centre.x), abs(tile.z - centre.z))
        val travel = POOL_TRAVEL_BASE + POOL_TRAVEL_PER_TILE * distance
        deps.bossProjectile(
            poolsProjectile,
            centre,
            tile,
            POOL_START_HEIGHT,
            0,
            delay = 0,
            travel = travel,
            curve = 0,
        )
        deps.worldQueues.add(1 + travel / CLIENT_CYCLES_PER_TICK) {
            if (boss.isSlotAssigned) {
                addPools(boss, listOf(tile), POOL_LINGER_TICKS)
            }
        }
    }

    private fun addPools(boss: Npc, tiles: List<CoordGrid>, linger: Int) {
        val list = pools.getOrPut(boss) { mutableListOf() }
        val expiry = deps.mapClock.cycle + linger
        for (tile in tiles) {
            if (!deps.collision.isWalkBlocked(tile) && list.none { it.tile == tile }) {
                list += Pool(tile, expiry)
                if (!deps.locRepo.findLoc(tile, ACID_POOL_LOC)) {
                    deps.locRepo.add(
                        tile,
                        ACID_POOL_LOC,
                        linger,
                        LocAngle.West,
                        LocShape.CentrepieceStraight,
                    )
                }
            }
        }
        if (list.isNotEmpty() && poolTicking[boss] != true) {
            poolTicking[boss] = true
            tickPools(boss)
        }
    }

    private fun tickPools(boss: Npc) {
        deps.repeatTick(
            ticks = MAX_POOL_TICKS,
            onTick = {
                val list = pools[boss]
                if (boss.hitpoints <= 0 || !boss.isSlotAssigned || list.isNullOrEmpty()) {
                    pools.remove(boss)
                    poolTicking.remove(boss)
                    return@repeatTick false
                }
                val now = deps.mapClock.cycle
                list.removeIf { now >= it.expiry }
                for (player in deps.playerList) {
                    if (
                        player.isValidTarget() &&
                            player.coords.level == boss.coords.level &&
                            list.any { it.tile == player.coords }
                    ) {
                        PlayerVenom.tryVenom(player)
                        player.queueHit(
                            boss,
                            1,
                            HitType.Typeless,
                            POOL_DAMAGE_MIN + deps.random.of(POOL_DAMAGE_ROLL),
                            deps.playerHitModifier,
                        )
                    }
                }
                list.isNotEmpty()
            },
            onStop = {
                pools.remove(boss)
                poolTicking.remove(boss)
            },
        )
    }

    private fun freeTileNear(centre: CoordGrid, radius: Int): CoordGrid? {
        repeat(12) {
            val tile =
                centre.translate(
                    deps.random.of(radius * 2 + 1) - radius,
                    deps.random.of(radius * 2 + 1) - radius,
                )
            if (!deps.collision.isWalkBlocked(tile)) return tile
        }
        return null
    }

    private fun mirrorbackAlive(boss: Npc): Boolean =
        mirrorbacks[boss]?.any { it.isSlotAssigned && it.hitpoints > 0 } == true

    private var poolsProjectile: Int = -1
    private var explodeSpot: SpotanimType = SpotanimType(0)

    private val acidDripEffect: Effect =
        sequence(
            anim("seq.npc_araxxor_01_attack_acid_leak_01"),
            external("araxxor.acid_drip"),
        )

    private val acidSprayEffect: Effect =
        sequence(
            anim("seq.npc_araxxor_01_attack_acid_spray_01"),
            external("araxxor.acid_spray"),
        )

    private val acidBulletEffect: Effect =
        sequence(
            anim("seq.npc_araxxor_01_acid_cannon_01"),
            external("araxxor.acid_bullet"),
        )

    private val specialEffect: Effect =
        sequence(
            external("araxxor.hatch"),
            choose(
                selector =
                    rotation {
                        +then("acid_drip")
                        +then("acid_spray")
                        +then("acid_bullet")
                    },
                branches =
                    mapOf(
                        "acid_drip" to acidDripEffect,
                        "acid_spray" to acidSprayEffect,
                        "acid_bullet" to acidBulletEffect,
                    ),
            ),
        )

    override val spec =
        boss(ARAXXOR) {
            stats(attackRate = ATTACK_RATE)

            incoming {
                rule(Condition.InPhase("enrage")) { scalePercent(ENRAGE_DEFENCE_PERCENT) }
                rule(Condition.Custom { boss, _ -> mirrorbackAlive(boss) }) {
                    scalePercent(MIRRORBACK_ABSORB_PERCENT)
                }
            }

            val melee =
                ability("melee") {
                    anim("seq.npc_araxxor_01_attack_melee_01")
                    hit { damage((0..MELEE_MAX).roll()); type(Melee) }
                    external("araxxor.venom")
                }

            val magic =
                ability("magic") {
                    anim("seq.npc_araxxor_01_attack_magic_01")
                    projectile(
                        spotanim = "spotanim.araxxor_magic_projectile",
                        impact = "spotanim.araxxor_magic_impact",
                        hit = Effect.Hit(damage = (0..MAGIC_MAX).roll(), type = Magic),
                    )
                    external("araxxor.venom")
                }

            val ranged =
                ability("ranged") {
                    anim("seq.npc_araxxor_01_attack_ranged_01")
                    projectile(
                        spotanim = "spotanim.araxxor_ranged_projectile",
                        hit = Effect.Hit(damage = (0..RANGED_MAX).roll(), type = Ranged),
                    )
                    external("araxxor.venom")
                }

            val cleave =
                ability("cleave") {
                    anim("seq.npc_araxxor_01_attack_melee_enraged_01")
                    hit { damage((0..CLEAVE_MAX).roll()); type(Melee) }
                    external("araxxor.cleave_pools")
                }

            ability("enrage_transition") {
                anim("seq.npc_araxxor_01_enrage_transition_01")
            }

            val special =
                ability("special") {
                    include(specialEffect)
                }

            phase("combat") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(melee, weight = 4, requires = WithinMeleeRange)
                    +random(magic, weight = 3)
                    +random(ranged, weight = 3)
                }
                forceEveryAttacks(SPECIAL_INTERVAL, SPECIAL_INTERVAL, special)
            }

            phase("enrage", entryHp = ENRAGE_HP_FRACTION) {
                entry = "enrage_transition"
                attackRate = ENRAGE_ATTACK_RATE
                weightedSelectorRandom {
                    +random(cleave, weight = 4, requires = WithinMeleeRange)
                    +random(magic, weight = 3)
                    +random(ranged, weight = 3)
                }
                forceEveryAttacks(SPECIAL_INTERVAL, SPECIAL_INTERVAL, special)
            }
        }

    private enum class MinionKind(val egg: String, val npc: String) {
        Mirrorback("npc.araxxor_minion_egg_mirrorback", "npc.araxxor_minion_mirrorback"),
        Explode("npc.araxxor_minion_egg_explode", "npc.araxxor_minion_explode"),
        Venom("npc.araxxor_minion_egg_venom", "npc.araxxor_minion_venom"),
    }

    private companion object {
        private const val ARAXXOR = "npc.araxxor"
        private const val ACID_POOL_LOC = "loc.araxxor_acidpool"
        private const val POOLS_PROJ = "spotanim.araxxor_pools_proj"
        private const val EGG_SPAWN_ANIM = "seq.egg_araxyte_spawn_01"
        private const val HATCH_ANIM = "seq.npc_araxyte02_hatch"
        private const val EXPLODE_ANIM = "seq.npc_araxyte_explode"
        private const val EXPLODE_SPOT = "spotanim.araxyte_explosive_spider_explosion"

        private val MINION_ORDER =
            listOf(MinionKind.Mirrorback, MinionKind.Explode, MinionKind.Venom)

        private const val ATTACK_RATE = 6
        private const val ENRAGE_ATTACK_RATE = 4
        private const val ENRAGE_HP_FRACTION = 0.25
        private const val SPECIAL_INTERVAL = 6

        private const val MELEE_MAX = 38
        private const val MAGIC_MAX = 31
        private const val RANGED_MAX = 31
        private const val CLEAVE_MAX = 38
        private const val VENOM_ROLL = 3

        private const val ENRAGE_DEFENCE_PERCENT = 70
        private const val MIRRORBACK_ABSORB_PERCENT = 50

        private const val MAX_MINIONS = 9
        private const val EGG_HATCH_TICKS = 8
        private const val MINION_MAX_TICKS = 200
        private const val EXPLODE_MAX = 28

        private const val DRIP_TICKS = 6
        private const val SPRAY_COUNT = 8
        private const val SPRAY_RADIUS = 3
        private const val CLEAVE_LEN = 3

        private const val POOL_LINGER_TICKS = 15
        private const val MAX_POOL_TICKS = 60
        private const val POOL_DAMAGE_MIN = 4
        private const val POOL_DAMAGE_ROLL = 4
        private const val POOL_START_HEIGHT = 43
        private const val POOL_TRAVEL_BASE = 15
        private const val POOL_TRAVEL_PER_TILE = 3
        private const val CLIENT_CYCLES_PER_TICK = 30
    }
}
