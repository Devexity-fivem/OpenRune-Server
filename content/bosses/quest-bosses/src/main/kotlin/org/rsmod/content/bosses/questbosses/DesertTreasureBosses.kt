package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Desert Treasure — Damis: two encounter forms, melee up to ~18 with
// prayer-draining shadow attacks on the tougher form.
class Damis @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.fd_damis_normal", "npc.fd_damis_tougher") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_unarmedpunch")
            hit { damage(0..18).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Desert Treasure — Fareed: melee up to ~18; fire-weak.
class Fareed @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.firediamond_firewarrior") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..37).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Desert Treasure — Kamil: melee up to 23 plus 5x2 ice magic and periodic freezing.
class Kamil @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.icediamond_icewarrior") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..23).roll(); type(Melee) }
            freeze(8, chance = 1, outOf = 6)
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.ice_barrage_travel", hit = Effect.Hit(damage = Roll(0..5), type = Magic))
            hit { damage(0..5).roll(); type(Magic) }
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Desert Treasure — Dessous: melee up to 19, magic and ranged up to 5.
class Dessous @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.blooddiamond_vampirewarrior", "npc.blooddiamond_vampirewarrior_unkillable") {
            stats(attackRate = 3)
            val melee = ability("melee") {
                anim("seq.vamp_claw")
                hit { damage(0..19).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.blood_blitz_travel", hit = Effect.Hit(damage = Roll(0..5), type = Magic))
            }
            val ranged = ability("ranged") {
                projectile(spotanim = "spotanim.rune_arrow_travel", hit = Effect.Hit(damage = Roll(0..5), type = Ranged))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                    +random(ranged, weight = 2)
                }
            }
        }
}
