package org.rsmod.content.bosses.sarachnis

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr

// OSRS wiki: crush melee up to 31 and a ranged spit at a 4-tick attack rate.
// She periodically fires a sticky web that binds the target. At ~66% and ~33%
// hitpoints she summons one melee and one magic Spawn of Sarachnis; spawn hits
// on the player should heal her (not modelled - see docs/quirks.md).
class Sarachnis @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = boss("npc.sarachnis") {

        stats(attackRate = 4)

        val melee =
            ability("melee") {
                anim("seq.spider_attack")
                hit {
                    damage(0..31).roll()
                    type(Melee)
                }
            }

        val ranged =
            ability("ranged") {
                anim("seq.spider_attack")
                projectile(
                    spotanim = "spotanim.ice_burst_impact",
                    hit = Effect.Hit(damage = Roll(0..18), type = Ranged),
                )
            }

        val stickyWeb =
            ability("sticky_web") {
                anim("seq.spider_attack")
                projectile(spotanim = "spotanim.ice_burst_impact")
                freeze(WEB_BIND_TICKS)
                message("Sarachnis spits a sticky web at you!")
            }

        val summonFirst = summonAbility("summon_first")
        val summonSecond = summonAbility("summon_second")

        triggers {
            on(Condition.HpBelow(SUMMON_ONE_HP)) runs Effect.Run(summonFirst.name)
            on(Condition.HpBelow(SUMMON_TWO_HP)) runs Effect.Run(summonSecond.name)
        }

        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 5, requires = WithinMeleeRange)
                +random(ranged, weight = 3)
                +random(stickyWeb, weight = 1)
            }
        }
    }

    private fun BossSpecBuilder.summonAbility(name: String) =
        ability(name) {
            message("Sarachnis summons her brood!")
            summon(
                "npc.sarachnis_melee_spawn",
                count = 1,
                radius = 2,
                centeredOn = TargetExpr.Self,
                mode = NpcMode.ApPlayer1,
                owned = true,
                duration = Int.MAX_VALUE,
            )
            summon(
                "npc.sarachnis_mage_spawn",
                count = 1,
                radius = 2,
                centeredOn = TargetExpr.Self,
                mode = NpcMode.ApPlayer1,
                owned = true,
                duration = Int.MAX_VALUE,
            )
        }

    private companion object {
        const val WEB_BIND_TICKS = 10
        const val SUMMON_ONE_HP = 0.66
        const val SUMMON_TWO_HP = 0.33
    }
}
