package org.rsmod.content.bosses.crazyarchaeologist

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr

// OSRS wiki: ranged book attack (max 14), melee crush when adjacent,
// "Rain of knowledge!" throws 3 exploding books in 3x3, one splitting into
// two more. Aggressive, attack rate 3t, respawn 15t, 225 hp.
class CrazyArchaeologist @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.crazy_archaeologist") {
        stats(attackRate = 3)

        val melee =
            ability("melee") {
                anim("seq.book_chuck")
                hit {
                    damage(0..14).roll()
                    type(Melee)
                }
            }

        val ranged =
            ability("ranged") {
                anim("seq.book_chuck")
                projectile(
                    spotanim = "spotanim.crazy_archaeologist_book",
                    hit = Effect.Hit(damage = DamageExpr.Roll(0..14), type = Ranged),
                )
            }

        val rainOfKnowledge =
            ability("rain_of_knowledge") {
                say("Rain of knowledge!")
                anim("seq.book_chuck_merge")
                debris(
                    telegraph = "spotanim.crazy_archaeologist_book",
                    damage = DamageExpr.Roll(0..24),
                    type = Typeless,
                    windup = 3,
                    targetRadius = 12,
                    scatterRadius = 4,
                    count = 3..3,
                    center = TargetExpr.CurrentTarget,
                )
                wait(4)
                debris(
                    telegraph = "spotanim.crazy_archaeologist_book_special",
                    damage = DamageExpr.Roll(0..24),
                    type = Typeless,
                    windup = 3,
                    targetRadius = 12,
                    scatterRadius = 4,
                    count = 2..2,
                    center = TargetExpr.CurrentTarget,
                )
            }

        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 1, requires = WithinMeleeRange)
                +random(ranged, weight = 5)
                +random(rainOfKnowledge, weight = 1)
            }
        }
    }
}
