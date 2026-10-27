package org.rsmod.content.bosses.cerberus

import dev.openrune.rscm.RSCM
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
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cerberus — the three-headed hellhound (npc.cerberus_attacking / _resetting / _sitting).
 *
 * OSRS: melee bite in range, magic and ranged attacks; once under 400hp she randomly adds
 * specials — a howl that summons the three souls (one per style, ~10s lifespan), lava
 * pools placed under the player that burn while stood on, and the flame wall breath.
 */
class Cerberus @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val pools = IdentityHashMap<Npc, MutableList<Pool>>()
    private val poolTicking = IdentityHashMap<Npc, Boolean>()

    private class Pool(val tile: CoordGrid, val expiry: Int)

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)

        flameProjectile = FLAME_TRAVEL_SPOT.asRSCM(RSCMType.SPOTANIM)
        poolSpot = SpotanimType(POOL_SPOT.asRSCM(RSCMType.SPOTANIM))

        deps.extensionRegistry.register("cerberus.lava_pools") { _, npc, target, _ ->
            spawnLavaPools(npc, target)
        }

        deps.extensionRegistry.register("cerberus.flame_wall") { _, npc, target, _ ->
            spawnFlameWall(npc, target)
        }

        deps.extensionRegistry.register("cerberus.soul_attack") { _, npc, target, p ->
            val attack = p as? SoulAttack ?: return@register
            deps.worldQueues.add(attack.delayTicks) {
                if (!npc.isSlotAssigned || !target.isValidTarget()) return@add
                npc.visType.paramOrNull(params.attack_anim)
                    ?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
                    ?.let { npc.anim(it, 0, 0) }
                target.queueHit(
                    npc,
                    1,
                    attack.type,
                    deps.random.of(SOUL_MAX_HIT + 1),
                    deps.playerHitModifier,
                )
            }
        }
    }

    private class SoulAttack(val delayTicks: Int, val type: HitType)

    private fun spawnLavaPools(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val origin = target.coords
        val tiles =
            listOf(
                origin,
                origin.translate(deps.random.of(3) - 1, deps.random.of(3) - 1),
                origin.translate(deps.random.of(3) - 1, deps.random.of(3) - 1),
            )
        addPools(boss, tiles, POOL_LINGER_TICKS)
    }

    private fun spawnFlameWall(boss: Npc, target: Player) {
        if (!boss.isSlotAssigned || !target.isValidTarget()) return
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        deps.bossProjectile(
            flameProjectile,
            centre,
            target.coords,
            FLAME_START_HEIGHT,
            0,
            delay = 0,
            travel = flameTravel(centre, target.coords),
            curve = 0,
        )
        val dx = target.coords.x - centre.x
        val dz = target.coords.z - centre.z
        val steps = max(abs(dx), abs(dz))
        deps.worldQueues.add(1 + flameTravel(centre, target.coords) / CLIENT_CYCLES_PER_TICK) {
            if (steps > 0 && boss.isSlotAssigned) {
                val tiles = (1..FLAME_WALL_LEN).map { centre.translate(dx * it / steps, dz * it / steps) }
                addPools(boss, tiles, FLAME_WALL_LINGER_TICKS)
            }
        }
    }

    private fun addPools(boss: Npc, tiles: List<CoordGrid>, linger: Int) {
        val list = pools.getOrPut(boss) { mutableListOf() }
        val expiry = deps.mapClock.cycle + linger
        for (tile in tiles) {
            if (!deps.collision.isWalkBlocked(tile) && list.none { it.tile == tile }) {
                list += Pool(tile, expiry)
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
                for (pool in list) {
                    deps.worldRepo.spotanimMap(poolSpot, pool.tile)
                }
                for (player in deps.playerList) {
                    if (
                        player.isValidTarget() &&
                            player.coords.level == boss.coords.level &&
                            chebyshev(player.coords, boss.coords) <= ARENA_RADIUS &&
                            list.any { it.tile == player.coords }
                    ) {
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

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int =
        max(abs(a.x - b.x), abs(a.z - b.z))

    private fun flameTravel(from: CoordGrid, to: CoordGrid): Int =
        FLAME_TRAVEL_BASE + FLAME_TRAVEL_PER_TILE * chebyshev(from, to)

    private var flameProjectile: Int = -1
    private var poolSpot: SpotanimType = SpotanimType(0)

    private val summonSoulsEffect: Effect =
        sequence(
            anim("seq.cerberus_howl"),
            summon(
                npc = SPECTRE_MAGIC,
                radius = 2,
                mode = NpcMode.None,
                duration = SOUL_TICKS,
                owned = true,
                onSummon = "cerberus.soul_attack",
                onSummonParams = SoulAttack(SOUL_MAGIC_DELAY, HitType.Magic),
            ),
            summon(
                npc = SPECTRE_MELEE,
                radius = 2,
                mode = NpcMode.None,
                duration = SOUL_TICKS,
                owned = true,
                onSummon = "cerberus.soul_attack",
                onSummonParams = SoulAttack(SOUL_MELEE_DELAY, HitType.Melee),
            ),
            summon(
                npc = SPECTRE_RANGED,
                radius = 2,
                mode = NpcMode.None,
                duration = SOUL_TICKS,
                owned = true,
                onSummon = "cerberus.soul_attack",
                onSummonParams = SoulAttack(SOUL_RANGED_DELAY, HitType.Ranged),
            ),
        )

    override val spec =
        boss(CERBERUS_SITTING, CERBERUS_ATTACKING, CERBERUS_RESETTING) {
            stats(attackRate = ATTACK_RATE)

            val bite =
                ability("bite") {
                    anim("seq.cerberus_bite")
                    hit { damage((0..MELEE_MAX).roll()); type(Melee) }
                }

            val magic =
                ability("magic") {
                    anim("seq.cerberus_attack_range")
                    projectile(
                        spotanim = "spotanim.dragon_ranged_ice_attack",
                        travel = "projanim.dragonfire",
                        hit = Effect.Hit(damage = (0..MAGIC_MAX).roll(), type = Magic),
                    )
                }

            val ranged =
                ability("ranged") {
                    anim("seq.cerberus_attack_range")
                    projectile(
                        spotanim = "spotanim.dragon_ranged_fire_attack",
                        travel = "projanim.dragonfire",
                        hit = Effect.Hit(damage = (0..RANGED_MAX).roll(), type = Ranged),
                    )
                }

            val souls =
                ability("souls") {
                    include(summonSoulsEffect)
                }

            val lavaPools =
                ability("lava_pools") {
                    anim("seq.cerberus_special_attack_spray")
                    external("cerberus.lava_pools")
                }

            val flameWall =
                ability("flame_wall") {
                    anim("seq.cerberus_special_attack_flame")
                    external("cerberus.flame_wall")
                }

            phase("combat", transmog = CERBERUS_ATTACKING) {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(bite, weight = 4, requires = WithinMeleeRange)
                    +random(magic, weight = 3)
                    +random(ranged, weight = 3)
                    +random(
                        souls,
                        weight = 1,
                        requires = Condition.HpBelow(SPECIAL_HP_FRACTION),
                        cooldown = SOULS_COOLDOWN,
                    )
                    +random(
                        lavaPools,
                        weight = 1,
                        requires = Condition.HpBelow(SPECIAL_HP_FRACTION),
                        cooldown = POOLS_COOLDOWN,
                    )
                    +random(
                        flameWall,
                        weight = 1,
                        requires = Condition.HpBelow(SPECIAL_HP_FRACTION),
                        cooldown = FLAME_COOLDOWN,
                    )
                }
            }
        }

    private companion object {
        private const val CERBERUS_SITTING = "npc.cerberus_sitting"
        private const val CERBERUS_ATTACKING = "npc.cerberus_attacking"
        private const val CERBERUS_RESETTING = "npc.cerberus_resetting"
        private const val SPECTRE_MAGIC = "npc.cerberus_spectre_magic"
        private const val SPECTRE_MELEE = "npc.cerberus_spectre_melee"
        private const val SPECTRE_RANGED = "npc.cerberus_spectre_ranged"
        private const val POOL_SPOT = "spotanim.vfx_flames_of_cerberus_1x1"
        private const val FLAME_TRAVEL_SPOT = "spotanim.dragon_ranged_fire_attack"

        private const val ATTACK_RATE = 5
        private const val SPECIAL_HP_FRACTION = 0.67

        private const val MELEE_MAX = 23
        private const val MAGIC_MAX = 23
        private const val RANGED_MAX = 23

        private const val SOUL_TICKS = 16
        private const val SOUL_MAX_HIT = 30
        private const val SOUL_MAGIC_DELAY = 2
        private const val SOUL_RANGED_DELAY = 4
        private const val SOUL_MELEE_DELAY = 6
        private const val SOULS_COOLDOWN = 45
        private const val POOLS_COOLDOWN = 40
        private const val FLAME_COOLDOWN = 40

        private const val POOL_LINGER_TICKS = 13
        private const val MAX_POOL_TICKS = 40
        private const val POOL_DAMAGE_MIN = 4
        private const val POOL_DAMAGE_ROLL = 10
        private const val ARENA_RADIUS = 24

        private const val FLAME_WALL_LEN = 5
        private const val FLAME_WALL_LINGER_TICKS = 13
        private const val FLAME_START_HEIGHT = 43
        private const val FLAME_TRAVEL_BASE = 15
        private const val FLAME_TRAVEL_PER_TILE = 3
        private const val CLIENT_CYCLES_PER_TICK = 30
    }
}
