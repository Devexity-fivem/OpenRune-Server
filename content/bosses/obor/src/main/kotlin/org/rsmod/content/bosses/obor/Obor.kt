package org.rsmod.content.bosses.obor

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr

// OSRS wiki: melee crush up to 22 and a thrown-rock ranged attack up to 26 at
// 6-tick attack rate; occasionally slams the ground, knocking the player back.
// The lair instance and giant-key gate live outside this encounter spec.
class Obor @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec = boss("npc.hillgiant_boss") {

        stats(attackRate = 6)

        val melee =
            ability("melee") {
                hit {
                    damage(0..22).roll()
                    type(Melee)
                }
            }

        val slam =
            ability("slam") {
                hit {
                    damage(0..16).roll()
                    type(Melee)
                }
                knockback(
                    "seq.agilityarena_player_spikedback",
                    Area(
                        TargetExpr.Offset(TargetExpr.CurrentTargetTile, -3, -3),
                        TargetExpr.Offset(TargetExpr.CurrentTargetTile, 3, 3),
                    ),
                )
            }

        val rockThrow =
            ability("rock_throw") {
                projectile(
                    spotanim = "spotanim.myarm_rock_roc_travel",
                    hit = Effect.Hit(damage = Roll(0..26), type = Ranged),
                )
            }

        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 5, requires = WithinMeleeRange)
                +random(slam, weight = 1, requires = WithinMeleeRange)
                +random(rockThrow, weight = 3)
            }
        }
    }
}
