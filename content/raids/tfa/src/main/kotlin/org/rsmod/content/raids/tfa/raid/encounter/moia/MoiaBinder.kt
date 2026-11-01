package org.rsmod.content.raids.tfa.raid.encounter.moia

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec

class MoiaBinder @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec =
        boss(MINION) {
            stats(attackRate = ATTACK_RATE)

            val strike =
                ability(
                    "strike",
                    sequence(
                        anim("seq.moia_minion_attack"),
                        onEach(
                            CurrentTarget,
                            hit {
                                damage(0..MoiaArena.BINDER_MAX).roll()
                                type(Melee)
                            },
                        ),
                    ),
                )

            phase("fight", scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random(strike, weight = 1, requires = WithinMeleeRange) }
            }
        }

    private companion object {
        const val MINION = "npc.tfa_moia_minion"
        const val ATTACK_RATE = 4
    }
}
