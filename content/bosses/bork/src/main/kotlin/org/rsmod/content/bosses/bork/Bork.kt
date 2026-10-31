package org.rsmod.content.bosses.bork

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bork — Fractured Archive raid boss.
 *
 * The fight itself lives in [BorkEncounter]; this script registers Bork's beat attacks
 * and target-switch calls through the boss DSL, wires the archive entrance/exit locs,
 * applies his flat-damage reduction, and forwards the partner-death enrages.
 */
class Bork @Inject constructor(deps: BossDeps, private val encounter: BorkEncounter) :
    BossPluginScript(deps) {

    override val spec =
        boss("npc.tfa_bork") {
            stats(attackRate = 7)

            val beat =
                ability("beat") {
                    anim("seq.npc_bork_gong_melee01")
                    spotanim("spotanim.npc_bork_gong_melee_spotanim01")
                    tileAoE(
                        tiles = { npc, _ -> BorkArena.beatTiles(npc) },
                        damage =
                            DamageExpr.Accuracy(
                                on = (0..28).roll(),
                                meleeAttackType = MeleeAttackType.Crush,
                            ),
                        type = Melee,
                    )
                }

            val bigBeat =
                ability("big_beat") {
                    anim("seq.npc_bork_gong_melee01")
                    spotanim("spotanim.npc_bork_gong_melee_spotanim01")
                    tileAoE(
                        tiles = { npc, _ -> BorkArena.bigBeatTiles(npc) },
                        damage =
                            DamageExpr.Accuracy(
                                on = (0..42).roll(),
                                meleeAttackType = MeleeAttackType.Crush,
                            ),
                        type = Melee,
                    )
                }

            val retarget = ability("retarget", external("bork.retarget"))

            phase("combat") {
                forceEveryAttacks(8, 10, retarget)
                weightedSelectorRandom {
                    +random(beat, weight = 3, requires = WithinMeleeRange)
                    +random(bigBeat, weight = 2)
                }
            }
        }

    override fun ScriptContext.startup() {
        encounter.placeEntrance()
        deps.extensionRegistry.register("bork.retarget") { _, npc, _, _ ->
            encounter.retarget(npc)
        }

        BossCombat.register(
            this,
            spec,
            deps,
            onLethal = { npc -> encounter.onBorkDead(npc) },
            onModifyHit = {
                val attacker = hit.sourceUid?.let { PlayerUid(it).resolve(deps.playerList) }
                if (attacker != null) {
                    if (encounter.isStunned(npc) && hit.type == HitType.Melee) {
                        hit.damage = hit.damage.coerceAtLeast(1)
                    } else {
                        hit.damage =
                            (hit.damage - encounter.flatArmourReduction(npc, hit.type))
                                .coerceAtLeast(0)
                    }
                }
            },
        )

        onOpLoc1(BorkEncounter.ENTRANCE_LOC_SYMBOL) { encounter.create(player, this) }
        onOpLoc2(BorkEncounter.ENTRANCE_LOC_SYMBOL) { encounter.create(player, this) }
        onOpLoc1(BorkEncounter.EXIT_LOC_SYMBOL) { encounter.exit(this) }
        onOpLoc2(BorkEncounter.EXIT_LOC_SYMBOL) { encounter.exit(this) }

        ServerCacheManager.getNpc("npc.tfa_smork".asRSCM(RSCMType.NPC))?.let { type ->
            onNpcQueue(type, "queue.death") { encounter.onSmorksDead(npc) }
        }
    }
}
