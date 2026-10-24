package org.rsmod.content.bosses.scorpia

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition

// OSRS wiki: melee stab (max 16), poisons for 20. The first player hit below
// 50% hp summons two guardians that rapidly heal her when nearby.
// Gap: guardian healing behaviour is not yet implemented.
class Scorpia @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = boss("npc.scorpia") {
        stats(attackRate = 4)

        val stab =
            ability("stab") {
                anim("seq.scorpion_attack")
                hit {
                    damage(0..16).roll()
                    type(Melee)
                }
                poison(20)
            }

        val summonGuardians =
            ability("summon_guardians") {
                summon("npc.scorpia_guardian", count = 2, radius = 5, owned = true)
            }

        phase("combat") {
            weightedSelectorRandom { +random(stab, weight = 1) }
        }

        onIncomingHit(
            summonGuardians,
            requires = Condition.HpBelow(0.5) and !Condition.AbilityUsed("summon_guardians"),
        )
    }
}
