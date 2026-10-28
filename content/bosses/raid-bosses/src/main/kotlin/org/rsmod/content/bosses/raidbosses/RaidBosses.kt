package org.rsmod.content.bosses.raidbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// --- Chambers of Xeric ---

class Tekton @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_tekton_fighting_standard", "npc.raids_tekton_walking_standard") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.golem_attack")
                hit { damage(0..42).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

class VasaNistirio @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_vasanistirio_walking", "npc.raids_vasanistirio_healing") {
            stats(attackRate = 4)
            val magic = ability("magic") {
                projectile(spotanim = "spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..38), type = Magic))
            }
            val melee = ability("melee") {
                anim("seq.golem_attack")
                hit { damage(0..35).roll(); type(Melee) }
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 2, requires = WithinMeleeRange)
                    +random(magic, weight = 3)
                }
            }
        }
}

class Vespula @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_vespula_flying", "npc.raids_vespula_enraged", "npc.raids_vespula_walking") {
            stats(attackRate = 4)
            val ranged = ability("ranged") {
                projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..20), type = Ranged))
            }
            phase("combat") { weightedSelectorRandom { +random(ranged, weight = 1) } }
        }
}

class Muttadile @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_dogodile", "npc.raids_dogodile_junior", "npc.raids_dogodile_submerged") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.dog_attack")
                hit { damage(0..35).roll(); type(Melee) }
            }
            val ranged = ability("ranged") {
                projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(ranged, weight = 2)
                }
            }
        }
}

class GreatOlm @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.olm_head", "npc.olm_head_spawning") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.tentacle_monster_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "fireblast_travel", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 2, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// --- Theatre of Blood ---

class MaidenOfSugadinti @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.tob_maiden_100", "npc.tob_maiden_70", "npc.tob_maiden_50", "npc.tob_maiden_30") {
            stats(attackRate = 4)
            val magic = ability("magic") {
                projectile(spotanim = "spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
            }
            phase("combat") { weightedSelectorRandom { +random(magic, weight = 1) } }
        }
}

class PestilentBloat @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.tob_bloat", "npc.tob_bloat_hard") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.tentacle_monster_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

class NylocasVasilias @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.nylocas_boss_melee", "npc.nylocas_boss_magic", "npc.nylocas_boss_ranged") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.tentacle_monster_attack")
                hit { damage(0..35).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

class Sotetseg @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.tob_sotetseg_combat", "npc.tob_sotetseg_combat_hard") {
        stats(attackRate = 4)
        val magic = ability("magic") {
            projectile(spotanim = "spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..48), type = Magic))
        }
        val melee = ability("melee") {
            anim("seq.tentacle_monster_attack")
            hit { damage(0..40).roll(); type(Melee) }
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 2, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

class Xarpus @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.tob_xarpus_combat", "npc.tob_xarpus_combat_hard") {
        stats(attackRate = 5)
        val ranged = ability("ranged") {
            projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..35), type = Ranged))
        }
        phase("combat") { weightedSelectorRandom { +random(ranged, weight = 1) } }
    }
}

class VerzikVitur @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.verzik_phase1", "npc.verzik_phase2", "npc.verzik_phase3") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.tentacle_monster_attack")
                hit { damage(0..35).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..35), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 2, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// --- Tombs of Amascut ---

class Akkha @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.akkha_melee", "npc.akkha_range", "npc.akkha_mage", "npc.akkha_spawn") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.human_sword_slash")
                hit { damage(0..30).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "firebolt_travel", hit = Effect.Hit(damage = Roll(0..28), type = Magic))
            }
            val ranged = ability("ranged") {
                projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..28), type = Ranged))
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

class BaBa @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.toa_baba") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.monkey_attack")
            hit { damage(0..38).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

class Kephri @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.toa_kephri_boss_shielded", "npc.toa_kephri_boss_weak", "npc.toa_kephri_boss_enrage") {
            stats(attackRate = 4)
            val ranged = ability("ranged") {
                projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
            }
            phase("combat") { weightedSelectorRandom { +random(ranged, weight = 1) } }
        }
}

class Zebak @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.toa_zebak", "npc.toa_zebak_enraged") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.tentacle_monster_attack")
            hit { damage(0..35).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(ranged, weight = 2)
            }
        }
    }
}

class TumekensWarden @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.toa_warden_tumeken_phase1", "npc.toa_warden_tumeken_phase2_mage",
            "npc.toa_warden_tumeken_phase2_range", "npc.toa_warden_tumeken_phase3",
        ) {
            stats(attackRate = 4)
            val magic = ability("magic") {
                projectile(spotanim = "fireblast_travel", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
            }
            val ranged = ability("ranged") {
                projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(magic, weight = 2)
                    +random(ranged, weight = 2)
                }
            }
        }
}

class ElidinisWarden @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.toa_warden_elidinis_phase1", "npc.toa_warden_elidinis_phase2_mage",
            "npc.toa_warden_elidinis_phase2_range", "npc.toa_warden_elidinis_phase3",
        ) {
            stats(attackRate = 4)
            val magic = ability("magic") {
                projectile(spotanim = "fireblast_travel", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
            }
            val ranged = ability("ranged") {
                projectile(spotanim = "dharok_head_fly", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(magic, weight = 2)
                    +random(ranged, weight = 2)
                }
            }
        }
}

