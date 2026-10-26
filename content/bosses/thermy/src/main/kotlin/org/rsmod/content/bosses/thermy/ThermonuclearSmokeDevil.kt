package org.rsmod.content.bosses.thermy

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType

// OSRS wiki + Kronos-184 (io.ruin SmokeDevil/ThermonuclearSmokeDevil): attacks
// at range 8 with a smoke projectile (max 8). When the target wears no face
// mask or slayer helm, every attack is replaced by unavoidable smoke: a flat
// 18 damage and the "smoke blinds" warnings.
class ThermonuclearSmokeDevil @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = thermonuclearSmokeDevilSpec()
}

internal fun thermonuclearSmokeDevilSpec(): BossSpec = boss("npc.smoke_devil_boss") {
    stats(attackRate = 4)

    val spit =
        ability("spit") {
            anim("seq.lore_dust_devil_attack")
            projectile(
                spotanim = "spotanim.smoke_devil_smoke_proj",
                hit =
                    Effect.Hit(
                        damage = DamageExpr.Roll(0..8),
                        type = HitType.Magic,
                        spotanim = "spotanim.smoke_devil_smoke_player_spotanim",
                    ),
            )
        }

    val smoke =
        ability("smoke") {
            anim("seq.lore_dust_devil_attack")
            include(
                Effect.Hit(
                    damage = DamageExpr.Fixed(18),
                    type = HitType.Magic,
                    hazard = true,
                )
            )
            include(Effect.Message("The devil's smoke blinds and damages you!"))
            include(Effect.Message("A facemask can protect you from this attack."))
        }

    phase("combat") {
        forceWhen(Condition.Custom { _, target -> target != null && !hasFaceProtection(target) }, smoke)
        weightedSelectorRandom {
            +random(spit, weight = 1)
        }
    }
}
