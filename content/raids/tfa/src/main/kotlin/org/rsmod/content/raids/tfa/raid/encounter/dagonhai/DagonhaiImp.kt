package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.plugin.scripts.ScriptContext

class DagonhaiImp @Inject constructor(deps: BossDeps, private val fights: DagonhaiEncounter) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { fights.of(npc)?.modifyImpHit(this) },
            onHit = { fights.of(npc)?.impactImp(this) },
        )
    }

    override val spec: BossSpec =
        boss(IMP) {
            stats(attackRate = ATTACK_RATE)

            val strike =
                ability(
                    "strike",
                    sequence(
                        anim("seq.imp_attack"),
                        onEach(
                            CurrentTarget,
                            hit {
                                damage(0..DagonhaiArena.IMP_MAX).roll()
                                type(Melee)
                            },
                        ),
                    ),
                )

            phase("fight") {
                weightedSelectorRandom { +random(strike, weight = 1, requires = WithinMeleeRange) }
            }
        }

    private companion object {
        const val IMP = "npc.tfa_dagonhai_demon"
        const val ATTACK_RATE = 4
    }
}
