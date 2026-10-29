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
            hit { damage(0..18).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Desert Treasure — Kamil: melee up to ~16 plus periodic freezing.
class Kamil @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.icediamond_icewarrior") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..16).roll(); type(Melee) }
            freeze(8, chance = 1, outOf = 6)
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Desert Treasure — Dessous: melee up to ~16.
class Dessous @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.blooddiamond_vampirewarrior", "npc.blooddiamond_vampirewarrior_unkillable") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.vamp_claw")
                hit { damage(0..16).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}
