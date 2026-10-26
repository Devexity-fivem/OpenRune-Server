package org.rsmod.content.bosses.bryophyta

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.game.entity.Npc

// OSRS wiki: crush melee up to 16 and a magic bolt up to 10 at a 6-tick attack
// rate; the magic attack can poison for 8. When first attacked she calls three
// growthlings (re-castable ~20 ticks later once they are dead); while any
// growthling lives she takes no damage. Growthlings only take damage from an
// axe or secateurs.
class Bryophyta @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val growthlings = HashSet<Npc>()

    init {
        deps.extensionRegistry.register(GROWTHLING_TRACK) { _, growthling, _, _ ->
            growthlings += growthling
        }
    }

    override val spec: BossSpec = boss("npc.gb_mossgiant") {

        stats(attackRate = 6)

        val melee =
            ability("melee") {
                anim("seq.giant_attack")
                hit {
                    damage(0..16).roll()
                    type(Melee)
                }
            }

        val magic =
            ability("magic") {
                anim("seq.giant_attack")
                projectile(
                    spotanim = "spotanim.earthbolt_travel",
                    hit = Effect.Hit(damage = Roll(0..10), type = Magic),
                )
                poison(8, chance = 1, outOf = 4)
            }

        val summonGrowthlings =
            ability("summon_growthlings") {
                summon(
                    "npc.gb_growthling",
                    count = 3,
                    radius = 3,
                    centeredOn = TargetExpr.CurrentTarget,
                    mode = NpcMode.ApPlayer1,
                    owned = true,
                    duration = Int.MAX_VALUE,
                    onSummon = GROWTHLING_TRACK,
                )
                setVarn(SUMMON_COOLDOWN_VARN, Now + SUMMON_COOLDOWN_TICKS)
            }

        incoming { rule(Condition.Custom { _, _ -> liveGrowthlings() > 0 }) { cap(0) } }

        phase("combat") {
            // First combat tick: she calls the growthlings once when engaged.
            forceWhen(Condition.Always, summonGrowthlings, once = true)
            weightedSelectorRandom {
                +random(melee, weight = 4, requires = WithinMeleeRange)
                +random(magic, weight = 3)
                +random(
                    summonGrowthlings,
                    weight = 1,
                    requires =
                        Condition.And(
                            Condition.VarnExpired(SUMMON_COOLDOWN_VARN),
                            Condition.Custom { _, _ -> liveGrowthlings() == 0 },
                        ),
                )
            }
        }
    }

    private fun liveGrowthlings(): Int {
        growthlings.removeAll { !it.isSlotAssigned }
        return growthlings.count { it.hitpoints > 0 }
    }

    private companion object {
        const val GROWTHLING_TRACK = "bryophyta.growthling_track"
        const val SUMMON_COOLDOWN_VARN = "varn.bryophyta_summon_cd"
        const val SUMMON_COOLDOWN_TICKS = 20
    }
}
