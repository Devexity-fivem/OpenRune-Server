package org.rsmod.content.bosses.hespori

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.clearOwnedNpcs
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Hespori (npc.hespori), the carnivorous plant fought in the cave beneath the
 * Farming Guild. It does not move: it throws ranged spore hits (two splats that
 * can poison) and a green magic wave at players in the cave. Four flower buds
 * surround its patch — they open when the fight starts and again at 66% and 33%
 * health, and while any are open Hespori is invulnerable. Each bud dies in one
 * hit. Periodically it entangles the target in vines: the victim cannot move or
 * fight back, and is crushed for heavy damage if still bound when the hold
 * ends. In OSRS the vines are escaped by clicking repeatedly; that input
 * counting is not modelled, so the hold always runs its full duration. The
 * boss is spawned by this plugin (it is grown from a seed in OSRS — the farming
 * patch loop is not modelled) and regrows a short while after death.
 */
class Hespori @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val bossId by lazy { BOSS.asRSCM(RSCMType.NPC) }

    override val spec =
        boss(BOSS) {
            stats(attackRate = ATTACK_RATE)

            val spores =
                ability("spores") {
                    anim("seq.hespori_attack_ranged")
                    projectile(
                        spotanim = "spotanim.hespori_range_proj",
                        hit =
                            Effect.Hit(
                                damage = (0..SPORE_MAX).roll(),
                                type = Ranged,
                                onHit =
                                    Effect.Poison(
                                        POISON_DAMAGE,
                                        chance = 1,
                                        outOf = POISON_OUT_OF,
                                    ),
                            ),
                        onImpact =
                            Effect.Hit(
                                damage = (0..SPORE_MAX).roll(),
                                type = Ranged,
                                onHit =
                                    Effect.Poison(
                                        POISON_DAMAGE,
                                        chance = 1,
                                        outOf = POISON_OUT_OF,
                                    ),
                            ),
                    )
                }

            val wave =
                ability("wave") {
                    anim("seq.hespori_attack_ranged")
                    projectile(
                        spotanim = "spotanim.hespori_magic_proj",
                        impact = "spotanim.hespori_magic_impact",
                        hit = Effect.Hit(damage = (0..MAGIC_MAX).roll(), type = Magic),
                    )
                }

            val vines =
                ability("vines") {
                    anim("seq.hespori_attack_special")
                    projectile(
                        spotanim = "spotanim.hespori_vine_proj",
                        impact = "spotanim.hespori_vine_impact",
                        hit =
                            Effect.Hit(
                                damage = (0..0).roll(),
                                type = Typeless,
                                onHit =
                                    Effect.Parallel(
                                        listOf(
                                            Effect.Message(ENTANGLE_MESSAGE),
                                            Effect.Freeze(VINE_BIND_TICKS),
                                        )
                                    ),
                                onHitEvenOnMiss = true,
                            ),
                    )
                    // The vines also stop the target fighting back; freeze alone only
                    // blocks movement, so their interaction is broken every tick.
                    include(
                        Effect.Repeat(
                            VINE_BIND_TICKS..VINE_BIND_TICKS,
                            external(VINE_HOLD),
                            gap = 1,
                        )
                    )
                    include(
                        Effect.After(
                            VINE_BIND_TICKS,
                            Effect.Whenever(
                                stillEntangled(),
                                Effect.Hit(
                                    damage = (VINE_DAMAGE_MIN..VINE_DAMAGE_MAX).roll(),
                                    type = Typeless,
                                ),
                            ),
                        )
                    )
                }

            val openBuds = ability("open_buds") { include(external(OPEN_BUDS)) }

            incoming { rule(anyOpenBud()) { scalePercent(0) } }

            triggers {
                on(engaged()) runs Effect.Run(openBuds.name)
                on(Condition.HpBelow(BUD_HP_FIRST)) runs Effect.Run(openBuds.name)
                on(Condition.HpBelow(BUD_HP_SECOND)) runs Effect.Run(openBuds.name)
            }

            every(VINE_TRY_TICKS, whenever(engaged(), Effect.Run(vines.name)))

            phase("combat", lockMovement = true) {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(spores, weight = 3)
                    +random(wave, weight = 2)
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onLethal = ::onBossLethal)
        deps.extensionRegistry.register(OPEN_BUDS) { ctx -> openBuds(ctx.npc) }
        deps.extensionRegistry.register(VINE_HOLD) { ctx -> ctx.target.clearInteraction() }
        ServerCacheManager.getNpc(BUD_ACTIVE.asRSCM(RSCMType.NPC))?.let { budType ->
            onModifyNpcHit(budType) { if (hit.damage > 0) hit.damage = npc.hitpoints }
        }
        onEvent<NpcStateEvents.Spawn>(bossId) { closeBuds(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.id == bossId) closeBuds(npc) }
        deps.worldQueues.add(WORLD_LOAD_DELAY) { spawnBoss() }
    }

    private fun onBossLethal(boss: Npc) {
        deps.clearOwnedNpcs(boss)
        closeBuds(boss)
    }

    private fun spawnBoss() {
        val zone = ZoneKey.from(BOSS_TILE)
        val alive =
            deps.npcRepo.findAll(zone, CAVE_RADIUS).any {
                it.hitpoints > 0 && it.isVisType(BOSS)
            }
        if (alive) return
        val type = ServerCacheManager.getNpc(bossId) ?: return
        val boss = Npc(type, BOSS_TILE)
        boss.movementLocked = true
        deps.npcRepo.add(boss, Int.MAX_VALUE)
    }

    private fun budTiles(boss: Npc): List<CoordGrid> =
        BUD_OFFSETS.map { boss.spawnCoords.translate(it.dx, it.dz) }

    private fun openBuds(boss: Npc) {
        val active = ServerCacheManager.getNpc(BUD_ACTIVE.asRSCM(RSCMType.NPC)) ?: return
        for (tile in budTiles(boss)) {
            val bud = budAt(tile) ?: spawnBud(tile) ?: continue
            if (bud.isVisType(BUD_INACTIVE)) {
                bud.transmog(active, Int.MAX_VALUE)
            }
        }
    }

    private fun closeBuds(boss: Npc) {
        val inactive =
            ServerCacheManager.getNpc(BUD_INACTIVE.asRSCM(RSCMType.NPC)) ?: return
        deps.npcRepo.findAll(ZoneKey.from(boss.coords), CAVE_RADIUS).forEach { bud ->
            if (bud.hitpoints > 0 && bud.isVisType(BUD_ACTIVE)) {
                bud.transmog(inactive, Int.MAX_VALUE)
            }
        }
    }

    private fun budAt(tile: CoordGrid): Npc? =
        deps.npcRepo.findAll(ZoneKey.from(tile), 0).firstOrNull {
            it.hitpoints > 0 &&
                it.coords.chebyshevDistance(tile) <= BUD_SEARCH_RADIUS &&
                (it.isVisType(BUD_INACTIVE) || it.isVisType(BUD_ACTIVE))
        }

    private fun spawnBud(tile: CoordGrid): Npc? {
        val type = ServerCacheManager.getNpc(BUD_INACTIVE.asRSCM(RSCMType.NPC)) ?: return null
        val bud = Npc(type, tile)
        bud.mode = NpcMode.None
        deps.npcRepo.add(bud, Int.MAX_VALUE)
        // One-shot like Skotizo's altars: the Delete path keeps replacement
        // timing under this plugin's control instead of the type respawn rate.
        bud.respawns = false
        return bud
    }

    private fun engaged(): Condition = Condition.Custom { _, target -> target != null }

    private fun stillEntangled(): Condition =
        Condition.Custom { _, target -> target?.isFrozen == true }

    private fun anyOpenBud(): Condition =
        Condition.Custom { npc, _ ->
            deps.npcRepo.findAll(ZoneKey.from(npc.coords), CAVE_RADIUS).any {
                it.hitpoints > 0 && it.isVisType(BUD_ACTIVE)
            }
        }

    private data class BudOffset(val dx: Int, val dz: Int)

    private companion object {
        private const val BOSS = "npc.hespori"
        private const val BUD_INACTIVE = "npc.hespori_healer_inactive"
        private const val BUD_ACTIVE = "npc.hespori_healer_active"
        private const val OPEN_BUDS = "hespori.open_buds"
        private const val VINE_HOLD = "hespori.vine_hold"

        private const val ATTACK_RATE = 6
        private const val SPORE_MAX = 8
        private const val MAGIC_MAX = 14
        private const val POISON_DAMAGE = 4
        private const val POISON_OUT_OF = 4

        private const val VINE_BIND_TICKS = 10
        private const val VINE_DAMAGE_MIN = 40
        private const val VINE_DAMAGE_MAX = 50
        private const val ENTANGLE_MESSAGE = "Hespori entangles you in some vines!"

        private const val BUD_HP_FIRST = 0.66
        private const val BUD_HP_SECOND = 0.33
        private const val BUD_SEARCH_RADIUS = 2

        private const val CAVE_RADIUS = 20
        private const val WORLD_LOAD_DELAY = 3

        private val VINE_TRY_TICKS = 50..70
        private val BUD_OFFSETS =
            listOf(
                BudOffset(-3, -3),
                BudOffset(-3, 5),
                BudOffset(5, -3),
                BudOffset(5, 5),
            )
        private val BOSS_TILE = CoordGrid(1246, 10086, 0)
    }
}
