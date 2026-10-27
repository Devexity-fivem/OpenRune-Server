package org.rsmod.content.bosses.vorkath

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
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
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
 * Vorkath — post-quest (npc.vorkath) and quest (npc.vorkath_quest) forms.
 *
 * Attack pattern per OSRS: six regular attacks (melee in range, magic, ranged and dragonfire
 * variants, plus the dodgeable bomb fireball) then one of two alternating specials — a
 * freeze + Zombified Spawn, or the acid-pool barrage with rapid fireballs and 50% damage
 * reduction. Vorkath is immune to damage while a summoned spawn lives.
 */
class Vorkath @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private var acidProjectileId: Int = -1

    private val bossOfSpawn = IdentityHashMap<Npc, Npc>()
    private val liveSpawns = IdentityHashMap<Npc, MutableSet<Npc>>()
    private val frozenBy = IdentityHashMap<Npc, Player>()
    private val acidUntil = IdentityHashMap<Npc, Int>()
    private val freezeNext = IdentityHashMap<Npc, Boolean>()

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)

        acidProjectileId = ACID_PROJECTILE.asRSCM(RSCMType.SPOTANIM)

        deps.extensionRegistry.register("vorkath.track_spawn") { access, spawn, target, _ ->
            val boss = access?.npc ?: return@register
            bossOfSpawn[spawn] = boss
            liveSpawns.getOrPut(boss) { mutableSetOf() } += spawn
            frozenBy[spawn] = target
            trackSpawn(spawn, target)
        }

        deps.extensionRegistry.register("vorkath.apply_venom") { _, _, target, _ ->
            PlayerVenom.tryVenom(target)
        }

        deps.extensionRegistry.register("vorkath.acid_phase") { _, npc, target, _ ->
            runAcidPhase(npc, target)
        }

        deps.extensionRegistry.register("vorkath.spent_freeze") { _, npc, _, _ ->
            npc?.let { freezeNext[it] = false }
        }

        deps.extensionRegistry.register("vorkath.spent_acid") { _, npc, _, _ ->
            npc?.let { freezeNext[it] = true }
        }
    }

    private fun trackSpawn(spawn: Npc, target: Player) {
        deps.repeatTick(
            ticks = SPAWN_MAX_TICKS,
            onTick = { remaining ->
                if (spawn.hitpoints <= 0 || !spawn.isSlotAssigned) {
                    clearSpawn(spawn)
                    return@repeatTick false
                }
                if (!target.isValidTarget()) {
                    clearSpawn(spawn)
                    deps.npcRepo.del(spawn, Int.MAX_VALUE)
                    return@repeatTick false
                }
                if (spawn.isWithinDistance(target, 1)) {
                    explodeSpawn(spawn, target)
                    return@repeatTick false
                }
                remaining > 1
            },
        )
    }

    private fun explodeSpawn(spawn: Npc, target: Player) {
        val boss = bossOfSpawn[spawn]
        val damage = SPAWN_EXPLODE_MAX * spawn.hitpoints / SPAWN_MAX_HP
        deps.worldRepo.spotanimMap(
            SpotanimType(SPAWN_EXPLODE_SPOT.asRSCM(RSCMType.SPOTANIM)),
            spawn.coords,
        )
        if (boss != null && target.isValidTarget() && spawn.isWithinDistance(target, 1)) {
            target.finishNpcHit(
                boss,
                1,
                HitType.Typeless,
                damage,
                deps.playerHitModifier,
            )
        }
        clearSpawn(spawn)
        deps.npcRepo.del(spawn, Int.MAX_VALUE)
    }

    private fun clearSpawn(spawn: Npc) {
        val boss = bossOfSpawn.remove(spawn)
        if (boss != null) {
            liveSpawns[boss]?.remove(spawn)
            if (liveSpawns[boss]?.isEmpty() == true) liveSpawns.remove(boss)
        }
        frozenBy.remove(spawn)?.let { if (it.isFrozen) CombatEffects.unfreeze(it) }
    }

    private fun runAcidPhase(boss: Npc, target: Player) {
        acidUntil[boss] = deps.mapClock.cycle + ACID_PHASE_TICKS
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        deps.repeatTick(
            ticks = ACID_PHASE_TICKS,
            onTick = { remaining ->
                if (boss.hitpoints <= 0) return@repeatTick false
                repeat(ACID_SPITS_PER_TICK) {
                    val tile =
                        target.coords.translate(
                            deps.random.of(ACID_SPREAD * 2 + 1) - ACID_SPREAD,
                            deps.random.of(ACID_SPREAD * 2 + 1) - ACID_SPREAD,
                        )
                    if (!deps.collision.isWalkBlocked(tile)) {
                        spitAcid(centre, tile)
                    }
                }
                for (player in deps.playerList) {
                    if (
                        player.isValidTarget() &&
                            player.coords.level == boss.coords.level &&
                            deps.locRepo.findLoc(player.coords, ACID_LOC)
                    ) {
                        player.queueHit(
                            boss,
                            1,
                            HitType.Typeless,
                            ACID_POOL_DAMAGE_MIN + deps.random.of(ACID_POOL_DAMAGE_ROLL),
                            deps.playerHitModifier,
                        )
                    }
                }
                remaining > 1
            },
            onStop = { acidUntil.remove(boss) },
        )
    }

    private fun spitAcid(centre: CoordGrid, tile: CoordGrid) {
        val distance = max(abs(tile.x - centre.x), abs(tile.z - centre.z))
        val travel = ACID_TRAVEL_BASE + ACID_TRAVEL_PER_TILE * distance
        deps.bossProjectile(
            acidProjectileId,
            centre,
            tile,
            ACID_START_HEIGHT,
            0,
            delay = 0,
            travel = travel,
            curve = 0,
        )
        deps.worldQueues.add(1 + travel / CLIENT_CYCLES_PER_TICK) {
            if (!deps.collision.isWalkBlocked(tile) && !deps.locRepo.findLoc(tile, ACID_LOC)) {
                deps.locRepo.add(
                    tile,
                    ACID_LOC,
                    ACID_POOL_LINGER_TICKS,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
            }
        }
    }

    private fun spawnAlive(npc: Npc): Boolean =
        liveSpawns[npc]?.any { it.isSlotAssigned && it.hitpoints > 0 } == true

    private fun acidActive(npc: Npc): Boolean = (acidUntil[npc] ?: 0) > deps.mapClock.cycle

    private fun nextIsFreeze(npc: Npc): Boolean = freezeNext.getOrPut(npc) { deps.random.of(2) == 0 }

    private fun playerUnderneath(npc: Npc): Boolean {
        val bounds = npc.bounds()
        return deps.playerList.any {
            it.isValidTarget() &&
                it.coords.level == npc.coords.level &&
                it.coords.x in bounds.coords.x until bounds.coords.x + bounds.width &&
                it.coords.z in bounds.coords.z until bounds.coords.z + bounds.length
        }
    }

    private val zombifiedSpawnEffect: Effect =
        sequence(
            anim("seq.ds2_vorkath_spawn"),
            mapSpotanim("spotanim.ice_barrage_impact", at = CurrentTargetTile),
            freeze(FREEZE_TICKS),
            projectile(
                spotanim = "spotanim.vorkath_spawn_travel",
                target = randomWalkableTile(radius = SPAWN_LOB_RADIUS, of = CurrentTarget),
                config =
                    ProjectileConfig.fixed(
                        startHeight = 160,
                        endHeight = 0,
                        delay = 0,
                        travel = 50,
                        angle = 128,
                    ),
                onImpact =
                    summon(
                        npc = ZOMBIFIED_SPAWN,
                        radius = 0,
                        centeredOn = ImpactTile,
                        mode = NpcMode.ApPlayer1,
                        duration = Int.MAX_VALUE,
                        owned = true,
                        onSummon = "vorkath.track_spawn",
                    ),
            ),
            external("vorkath.spent_freeze"),
        )

    private val acidPhaseEffect: Effect =
        sequence(
            anim("seq.ds2_vorkath_acid"),
            external("vorkath.acid_phase"),
            external("vorkath.spent_acid"),
            transitionTo("acid"),
            repeat(
                times = ACID_FIREBALL_COUNT,
                gap = 1,
                effect =
                    projectile(
                        spotanim = "spotanim.vorkath_area_small_travel",
                        hit =
                            Effect.Hit(
                                damage = (0..RAPID_FIRE_MAX).roll(),
                                type = Dragonfire,
                            ),
                        resolveOnImpact = true,
                    ),
            ),
        )

    override val spec =
        boss(VORKATH, VORKATH_QUEST) {
            stats(attackRate = ATTACK_RATE)

            incoming {
                rule(Condition.Custom { npc, _ -> spawnAlive(npc) }) { cap(0) }
                rule(Condition.Custom { npc, _ -> acidActive(npc) }) { scalePercent(50) }
            }

            val melee =
                ability("melee") {
                    anim("seq.ds2_vorkath_attack_melee")
                    hit { damage((0..MELEE_MAX).roll()); type(Melee) }
                }

            val magic =
                ability("magic") {
                    anim("seq.ds2_vorkath_ranged")
                    projectile(
                        spotanim = "spotanim.vorkath_magic_travel",
                        impact = "spotanim.vorkath_magic_impact",
                        hit = Effect.Hit(damage = (0..MAGIC_MAX).roll(), type = Magic),
                        resolveOnImpact = true,
                    )
                }

            val ranged =
                ability("ranged") {
                    anim("seq.ds2_vorkath_ranged")
                    projectile(
                        spotanim = "spotanim.vorkath_ranged_travel",
                        impact = "spotanim.vorkath_ranged_impact",
                        hit = Effect.Hit(damage = (0..RANGED_MAX).roll(), type = Ranged),
                        resolveOnImpact = true,
                    )
                }

            val dragonfire =
                ability("dragonfire") {
                    anim("seq.ds2_vorkath_fireball")
                    projectile(
                        spotanim = "spotanim.dragon_ranged_fire_attack",
                        travel = "projanim.dragonfire",
                        hit = Effect.Hit(damage = (0..DRAGONFIRE_MAX).roll(), type = Dragonfire),
                    )
                }

            val venomBreath =
                ability("venom_breath") {
                    anim("seq.ds2_vorkath_fireball")
                    projectile(
                        spotanim = "spotanim.dragon_ranged_toxic_attack",
                        travel = "projanim.dragonfire",
                        hit =
                            Effect.Hit(
                                damage = (0..DRAGONFIRE_MAX).roll(),
                                type = Dragonfire,
                                onHit = Effect.External("vorkath.apply_venom"),
                                onHitEvenOnMiss = true,
                            ),
                    )
                }

            val prayerBreath =
                ability("prayer_breath") {
                    anim("seq.ds2_vorkath_fireball")
                    projectile(
                        spotanim = "spotanim.dragon_ranged_ice_attack",
                        travel = "projanim.dragonfire",
                        hit = Effect.Hit(damage = (0..DRAGONFIRE_MAX).roll(), type = Dragonfire),
                    )
                    disablePrayers()
                }

            val fireball =
                ability("fireball") {
                    anim("seq.ds2_vorkath_fireball")
                    projectile(
                        spotanim = "spotanim.vorkath_area_travel",
                        target = CurrentTargetTile,
                        config =
                            ProjectileConfig.fixed(
                                startHeight = 400,
                                endHeight = 0,
                                delay = 10,
                                travel = 90,
                                angle = 140,
                            ),
                        onImpact =
                            Effect.Hit(
                                damage = (0..BOMB_MAX).roll(),
                                type = Typeless,
                                target =
                                    playersIn(
                                        area(
                                            offset(ImpactTile, -BOMB_RADIUS, -BOMB_RADIUS),
                                            offset(ImpactTile, BOMB_RADIUS, BOMB_RADIUS),
                                        ),
                                    ),
                            ),
                    )
                }

            val special =
                ability("special") {
                    include(
                        choose(
                            selector =
                                weightedRandom {
                                    +random(
                                        "freeze_spawn",
                                        weight = 1,
                                        requires = Condition.Custom { npc, _ -> nextIsFreeze(npc) },
                                    )
                                    +random(
                                        "acid_phase",
                                        weight = 1,
                                        requires = Condition.Custom { npc, _ -> !nextIsFreeze(npc) },
                                    )
                                },
                            branches =
                                mapOf(
                                    "freeze_spawn" to zombifiedSpawnEffect,
                                    "acid_phase" to acidPhaseEffect,
                                ),
                        ),
                    )
                }

            phase("combat", lockMovement = true) {
                weightedSelectorRandom {
                    +random(melee, weight = 4, requires = WithinMeleeRange)
                    +random(magic, weight = 3)
                    +random(ranged, weight = 3)
                    +random(dragonfire, weight = 1)
                    +random(venomBreath, weight = 1)
                    +random(prayerBreath, weight = 1)
                    +random(fireball, weight = 1)
                }
                forceEveryAttacks(SPECIAL_ATTACK_INTERVAL, SPECIAL_ATTACK_INTERVAL, special)
                every(
                    STOMP_PERIOD,
                    whenever(
                        Condition.Custom { npc, _ -> playerUnderneath(npc) },
                        parallel(
                            anim("seq.ds2_vorkath_stomp"),
                            onEach(
                                playersIn(underArea),
                                hit(damage = (0..STOMP_MAX).roll(), type = Typeless),
                            ),
                        ),
                    ),
                )
            }

            phase("acid", lockMovement = true, exitAfter = ACID_PHASE_TICKS, nextPhase = "combat") {}
        }

    private val underArea: Area
        get() =
            area(
                TargetExpr.Custom { npc, _ -> npc.bounds().coords },
                TargetExpr.Custom { npc, _ ->
                    npc.bounds().coords.translate(npc.bounds().width - 1, npc.bounds().length - 1)
                },
            )

    private companion object {
        private const val VORKATH = "npc.vorkath"
        private const val VORKATH_QUEST = "npc.vorkath_quest"
        private const val ZOMBIFIED_SPAWN = "npc.vorkath_spawn"
        private const val ACID_LOC = "loc.vorkath_acid"
        private const val ACID_PROJECTILE = "spotanim.vorkath_acid_travel"
        private const val SPAWN_EXPLODE_SPOT = "spotanim.ice_minion_explode_spotanim"

        private const val ATTACK_RATE = 5
        private const val SPECIAL_ATTACK_INTERVAL = 6

        private const val MELEE_MAX = 32
        private const val MAGIC_MAX = 30
        private const val RANGED_MAX = 32
        private const val DRAGONFIRE_MAX = 80
        private const val BOMB_MAX = 121
        private const val BOMB_RADIUS = 1
        private const val RAPID_FIRE_MAX = 41

        private const val FREEZE_TICKS = 25
        private const val SPAWN_LOB_RADIUS = 2
        private const val SPAWN_MAX_TICKS = 200
        private const val SPAWN_MAX_HP = 38
        private const val SPAWN_EXPLODE_MAX = 60

        private const val ACID_PHASE_TICKS = 25
        private const val ACID_FIREBALL_COUNT = 20
        private const val ACID_SPITS_PER_TICK = 2
        private const val ACID_SPREAD = 5
        private const val ACID_POOL_LINGER_TICKS = 30
        private const val ACID_POOL_DAMAGE_MIN = 4
        private const val ACID_POOL_DAMAGE_ROLL = 8
        private const val ACID_START_HEIGHT = 120
        private const val ACID_TRAVEL_BASE = 15
        private const val ACID_TRAVEL_PER_TILE = 3
        private const val CLIENT_CYCLES_PER_TICK = 30

        private const val STOMP_PERIOD = 2
        private const val STOMP_MAX = 10
    }
}
