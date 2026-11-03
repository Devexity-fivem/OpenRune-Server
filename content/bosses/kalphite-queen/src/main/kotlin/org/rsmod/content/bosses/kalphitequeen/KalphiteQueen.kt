package org.rsmod.content.bosses.kalphitequeen

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.spawnOwnedNpc
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.StatDrainEntry
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// OSRS wiki: two forms, each with a fresh 255-hp bar. Grounded form protects from
// ranged and magic; flying form protects from melee (modelled as a heavy damage
// cut - see docs/quirks.md). She alternates claw melee, chained lightning magic,
// and a spine volley, and periodically rouses nearby kalphite workers.
// KalphiteQueenFloor keeps the grounded form at 1 hp instead of dying; the
// trigger below then runs her emergence into the flying form at full hp.
class KalphiteQueen
@Inject
constructor(private val deps: BossDeps, private val interactions: AiPlayerInteractions) :
    PluginScript() {

    private val spec: BossSpec = boss("npc.kalphite_queen", "npc.kalphite_flyingqueen") {

        stats(attackRate = ATTACK_RATE)

        val claws =
            ability("claws") {
                anim("seq.kalphite_queen_attack_claws")
                hit {
                    damage(0..31).roll()
                    type(Melee)
                }
            }

        val lightning =
            ability("lightning") {
                anim("seq.kalphite_queen_lightning")
                include(
                    onEach(
                        AllInRadius(LIGHTNING_CHAIN_RADIUS, of = CurrentTarget),
                        Effect.Projectile(
                            spotanim = "spotanim.kalphite_queen_glow",
                            hit = Effect.Hit(damage = Roll(0..31), type = Magic),
                        ),
                    )
                )
            }

        val spines =
            ability("spines") {
                anim("seq.kalphite_queen_ranged_attack")
                include(
                    onEach(
                        AllInRadius(SPINE_SPLASH_RADIUS, of = CurrentTarget),
                        Effect.Projectile(
                            spotanim = "spotanim.kalphite_queen_spine",
                            hit =
                                Effect.Hit(
                                    damage = Roll(0..31),
                                    type = Ranged,
                                    onHit =
                                        Effect.StatDrain(
                                            listOf(StatDrainEntry("stat.prayer", amount = 1))
                                        ),
                                ),
                        ),
                    )
                )
            }

        val emerge =
            ability("emerge") {
                anim("seq.kalphite_queen_glow")
                spotanim("spotanim.kalphite_queen_glow")
                delay(EMERGE_DELAY_TICKS)
                healSelf(EMERGE_HEAL)
                transitionTo(PHASE_FLYING)
            }

        incoming {
            rule(Condition.And(Condition.InPhase(PHASE_GROUNDED), Condition.HitStyle(Ranged))) {
                scalePercent(PROTECTED_SCALE)
            }
            rule(Condition.And(Condition.InPhase(PHASE_GROUNDED), Condition.HitStyle(Magic))) {
                scalePercent(PROTECTED_SCALE)
            }
            rule(Condition.And(Condition.InPhase(PHASE_FLYING), Condition.HitStyle(Melee))) {
                scalePercent(PROTECTED_SCALE)
            }
        }

        triggers {
            on(Condition.And(Condition.InPhase(PHASE_GROUNDED), Condition.HpExact(1))) runs
                Effect.Run(emerge.name)
        }

        phase(PHASE_GROUNDED, transmog = "npc.kalphite_queen") {
            every(WORKER_AGGRO_PERIOD, chance(WORKER_AGGRO_ONE_IN, external("kq.aggro_workers")))
            weightedSelectorRandom {
                +random(claws, weight = 2, requires = WithinMeleeRange)
                +random(lightning, weight = 1)
                +random(spines, weight = 1)
            }
        }

        phase(PHASE_FLYING, transmog = "npc.kalphite_flyingqueen") {
            every(WORKER_AGGRO_PERIOD, chance(WORKER_AGGRO_ONE_IN, external("kq.aggro_workers")))
            weightedSelectorRandom {
                +random(claws, weight = 2, requires = WithinMeleeRange)
                +random(lightning, weight = 1)
                +random(spines, weight = 1)
            }
        }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        deps.extensionRegistry.register("kq.aggro_workers") { _, npc, target, _ ->
            aggroWorkers(npc, target)
        }

        for (symbol in QUEEN_SYMBOLS) {
            val type =
                ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC))
                    ?: error("Missing npc type for $symbol")
            onNpcHit(type) { maybeHatchWorker(npc, this) }
        }
    }

    // Bulging cocoons can hatch an aggressive worker each time the queen is hit near one.
    private fun maybeHatchWorker(queen: Npc, event: NpcHitEvents.Impact) {
        if (!event.hit.isFromPlayer || !queen.isSlotAssigned) return
        if (deps.random.of(COCOON_HATCH_ONE_IN) != 0) return
        val attacker = event.hit.resolvePlayerSource(deps.playerList) ?: return
        val zone = ZoneKey.from(queen.coords)
        val egg =
            deps.locRepo.findAll(zone).firstOrNull {
                it.id in cocoonLocIds &&
                    it.coords.chebyshevDistance(queen.coords) <= COCOON_PROXIMITY
            } ?: return
        val worker = deps.spawnOwnedNpc(queen, WORKER, egg.coords, WORKER_TICKS) ?: return
        worker.apPlayer2(attacker, interactions)
    }

    private fun aggroWorkers(npc: Npc, target: Player) {
        val zone = ZoneKey.from(npc.coords)
        for (worker in deps.npcRepo.findAll(zone, WORKER_ZONE_RADIUS)) {
            if (!worker.isType(WORKER) || worker.mode != NpcMode.Wander) continue
            worker.apPlayer2(target, interactions)
        }
    }

    private val cocoonLocIds by lazy {
        COCOON_LOCS.map { it.asRSCM(RSCMType.LOC) }.toSet()
    }

    private companion object {
        const val PHASE_GROUNDED = "grounded"
        const val PHASE_FLYING = "flying"
        const val ATTACK_RATE = 4
        const val EMERGE_HEAL = 255
        const val EMERGE_DELAY_TICKS = 20
        const val PROTECTED_SCALE = 10
        const val SPINE_SPLASH_RADIUS = 1
        const val LIGHTNING_CHAIN_RADIUS = 4
        const val WORKER_AGGRO_PERIOD = 6
        const val WORKER_AGGRO_ONE_IN = 3
        const val WORKER_ZONE_RADIUS = 1
        const val WORKER = "npc.kalphite_worker"
        const val WORKER_TICKS = 300
        const val COCOON_PROXIMITY = 4
        const val COCOON_HATCH_ONE_IN = 20
        val COCOON_LOCS = listOf("loc.kalphite_egg", "loc.kalphite_chamber_egg_update")
        val QUEEN_SYMBOLS = listOf("npc.kalphite_queen", "npc.kalphite_flyingqueen")
    }
}

/** Grounded form cannot die - depleting her first health bar triggers the flying form. */
@Singleton
class KalphiteQueenFloor @Inject constructor() : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int =
        if (npc.isVisType("npc.kalphite_queen")) 1 else 0
}

class KalphiteQueenModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcHitpointsFloorHook>(KalphiteQueenFloor::class.java)
    }
}
