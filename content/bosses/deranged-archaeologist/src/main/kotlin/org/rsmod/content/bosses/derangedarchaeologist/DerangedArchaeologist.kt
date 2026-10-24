package org.rsmod.content.bosses.derangedarchaeologist

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr

// OSRS wiki: ranged book attack and melee crush (max 25 each), melee only
// when adjacent. "Learn to read!" throws 3 books that explode 4 ticks later
// in 3x3 areas, one always on the target's tile; that book splits into two
// more books that explode after another delay. Direct hit max 56, indirect
// 18+. Attack rate 3t, respawn 50t, aggressive.
class DerangedArchaeologist @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = boss("npc.fossil_crazy_archaeologist") {
        stats(attackRate = 3)

        val melee =
            ability("melee") {
                anim("seq.book_chuck")
                hit {
                    damage(0..25).roll()
                    type(Melee)
                }
            }

        val ranged =
            ability("ranged") {
                anim("seq.book_chuck")
                projectile(
                    spotanim = "spotanim.crazy_archaeologist_book",
                    hit = Effect.Hit(damage = DamageExpr.Roll(0..25), type = Ranged),
                )
            }

        val learnToRead =
            ability("learn_to_read") {
                say("Learn to read!")
                anim("seq.book_chuck_merge")
                debris(
                    telegraph = "spotanim.crazy_archaeologist_book",
                    damage = DamageExpr.Roll(0..56),
                    type = Typeless,
                    windup = 4,
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
                +random(learnToRead, weight = 1)
            }
        }
    }
}
