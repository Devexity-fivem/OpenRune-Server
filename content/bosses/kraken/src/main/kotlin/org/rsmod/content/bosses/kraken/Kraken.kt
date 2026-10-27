package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.npc.apPlayer2
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kraken (npc.slayer_kraken_boss) + Enormous Tentacles in the Kraken Cove pool.
 *
 * OSRS: the pool starts as dormant whirlpools; attacking one disturbs it and the
 * creature surfaces. The Kraken itself is immune to everything but magic and lobs
 * a water blast; the four Enormous Tentacles slap in melee range.
 * Deviation: the slayer-task gate is not enforced — the boss is always attackable.
 */
class Kraken @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        BossCombat.register(this, tentacleSpec, deps)
        BossCombat.register(this, whirlpoolSpec, deps)

        deps.extensionRegistry.register("kraken.wake") { _, npc, target, _ ->
            val whirlpool = ServerCacheManager.getNpc(WHIRLPOOL.asRSCM(RSCMType.NPC))
            val into =
                if (npc.visType.id == whirlpool?.id) {
                    ServerCacheManager.getNpc(BOSS.asRSCM(RSCMType.NPC))
                } else {
                    ServerCacheManager.getNpc(TENTACLE.asRSCM(RSCMType.NPC))
                } ?: return@register
            npc.transmog(into, Int.MAX_VALUE)
            npc.anim(ARISE_ANIM, 0, 1)
            if (target != null) {
                npc.mode = NpcMode.ApPlayer2
                npc.apPlayer2(target, deps.playerInteractions)
            }
        }
    }

    override val spec =
        boss(BOSS) {
            stats(attackRate = ATTACK_RATE)

            val immune =
                ability("immune") { message("The Kraken resists your attack.") }

            val blast =
                ability("blast") {
                    anim("seq.human_caststrike")
                    projectile(
                        spotanim = "spotanim.waterwave_travel",
                        impact = "spotanim.waterwave_impact",
                        hit = Effect.Hit(damage = (0..MAGIC_MAX).roll(), type = Magic),
                    )
                }

            incoming {
                rule(Condition.HitStyle(Melee)) {
                    cap(0)
                    run(immune)
                }
                rule(Condition.HitStyle(Ranged)) {
                    cap(0)
                    run(immune)
                }
            }

            phase("combat") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom { +random(blast, weight = 1) }
            }
        }

    private val tentacleSpec =
        boss(TENTACLE) {
            stats(attackRate = TENTACLE_ATTACK_RATE)

            val slap =
                ability("slap") {
                    anim("seq.tentacle_monster_attack")
                    hit {
                        damage((0..TENTACLE_MAX).roll())
                        type(Melee)
                    }
                }

            phase("combat") {
                weightedSelectorRandom { +random(slap, weight = 1, requires = WithinMeleeRange) }
            }
        }

    private val whirlpoolSpec =
        boss(WHIRLPOOL, TENTACLE_WHIRLPOOL) {
            val wake = ability("wake") { external("kraken.wake") }

            incoming {
                rule(Always) {
                    cap(0)
                    run(wake)
                }
            }

            phase("dormant") {}
        }

    private companion object {
        private const val BOSS = "npc.slayer_kraken_boss"
        private const val WHIRLPOOL = "npc.slayer_kraken_boss_whirlpool"
        private const val TENTACLE = "npc.slayer_kraken_boss_tentacle"
        private const val TENTACLE_WHIRLPOOL = "npc.slayer_kraken_boss_tentacle_whirlpool"
        private const val ARISE_ANIM = "seq.slayer_kraken_arise"

        private const val ATTACK_RATE = 4
        private const val MAGIC_MAX = 28
        private const val TENTACLE_ATTACK_RATE = 4
        private const val TENTACLE_MAX = 13
    }
}
