package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// In Aid of the Myreque — Gadderanks: melee up to ~5 with vampyre guard aid scripted elsewhere.
class Gadderanks @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.burgh_gadderanks_attackable") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_unarmedpunch")
            hit { damage(0..5).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Sins of the Father — Ranis Drakan: melee up to ~15 plus a vampyre bolt.
class RanisDrakan @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.myq3_ranis_drakan", "npc.myq3_ranis_drakan_grounded_vampire_state") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("vamp_claw")
                hit { damage(0..15).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "blood_blitz_travel", hit = Effect.Hit(damage = Roll(0..12), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// A Taste of Hope — the Abomination: melee up to ~14.
class Abomination @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.myq4_abomination") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..14).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// A Kingdom Divided — Judge of Yama: melee up to ~12 with demonic magic.
class JudgeOfYama @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.akd_judge_of_yama_combat", "npc.yama_judge_of_yama") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..12).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "firebolt_travel", hit = Effect.Hit(damage = Roll(0..10), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// A Kingdom Divided — Xamphur: melee up to ~14 plus phantom-hand magic.
class Xamphur @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.akd_xamphur_combat", "npc.akd_xamphur_combat_nohands") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..14).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "firebolt_travel", hit = Effect.Hit(damage = Roll(0..12), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// Sea Slug — Slug Prince: melee up to ~9.
class SlugPrince @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.slug2_the_slug_prince") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_unarmedpunch")
            hit { damage(0..9).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Sea Slug finale — the Maledict: melee up to ~14.
class Maledict @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.slug2_maledict", "npc.slug2_maledict_stage1", "npc.slug2_maledict_stage2") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..14).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// The Great Brain Robbery — Barrelchest: melee anchor-smash up to ~15.
class Barrelchest @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.brain_barrel_chest") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..15).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Haunted Mine — Treus Dayth: melee ghost up to ~10.
class TreusDayth @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.hauntedmine_boss_ghost") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.ghost_attack")
            hit { damage(0..10).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Lair of Tarn Razorlor — Tarn: melee up to ~9 plus ghost form.
class Tarn @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.lotr_tran_razorlor", "npc.lotr_tran_razorlor_ghost", "npc.lotr_tran_razorlor_mutant") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("human_sword_slash")
                hit { damage(0..9).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}
