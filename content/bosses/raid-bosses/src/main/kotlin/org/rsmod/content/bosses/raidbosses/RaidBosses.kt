package org.rsmod.content.bosses.raidbosses

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// --- Chambers of Xeric ---
// Rates/anims/spotanims verified against the packed cache (param.attackrate +
// per-family seq/spotanim symbols). CoX hitpoints come from the server overlay;
// OSRS scales them per raid size.

class Tekton @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_tekton_fighting_standard", "npc.raids_tekton_walking_standard") {
            stats(attackRate = 6)
            val melee = ability("melee") {
                anim("seq.tekton_attack_stab")
                hit { damage(0..42).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

class TektonEnraged @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_tekton_fighting_enraged", "npc.raids_tekton_walking_enraged") {
            stats(attackRate = 3)
            val melee = ability("melee") {
                anim("seq.tekton_attack_stab_enraged")
                hit { damage(0..50).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

class VasaNistirio @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_vasanistirio_walking", "npc.raids_vasanistirio_healing") {
            stats(attackRate = 3)
            val melee = ability("melee") {
                anim("seq.vasa_attack")
                hit { damage(0..38).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

class Vespula @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.raids_vespula_flying", "npc.raids_vespula_enraged", "npc.raids_vespula_walking") {
            stats(attackRate = 3)
            val ranged = ability("ranged") {
                anim("seq.vespula_attack_ranged_flying")
                projectile(
                    spotanim = "spotanim.raids_vespula_poison",
                    hit = Effect.Hit(damage = Roll(0..20), type = Ranged),
                )
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
                projectile(spotanim = "spotanim.dharok_head_fly", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
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
        val acid = ability("acid") {
            anim("seq.olm_head_attack_acid_front")
            projectile(spotanim = "spotanim.olm_acid_spit", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
        }
        val ranged = ability("ranged") {
            projectile(spotanim = "spotanim.olm_generic_range_proj", hit = Effect.Hit(damage = Roll(0..30), type = Ranged))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(acid, weight = 2)
                +random(ranged, weight = 2)
            }
        }
    }
}

// --- Theatre of Blood ---

class MaidenOfSugadinti @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.tob_maiden_100", "npc.tob_maiden_70", "npc.tob_maiden_50", "npc.tob_maiden_30") {
            stats(attackRate = 10)
            val magic = ability("magic") {
                anim("seq.maiden_attack_blood")
                projectile(spotanim = "spotanim.maiden_blood_proj", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
            }
            val summonNylos = ability("summon") {
                summon(
                    npc = "npc.maiden_elemental",
                    count = 2,
                    radius = 6,
                    mode = NpcMode.ApPlayer2,
                    duration = 200,
                    owned = true,
                )
            }
            phase("combat") {
                forceEveryAttacks(10, 10, summonNylos)
                weightedSelectorRandom { +random(magic, weight = 1) }
            }
        }
}

class PestilentBloat @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.tob_bloat", "npc.tob_bloat_hard") {
        stats(attackRate = 1)
        val melee = ability("melee") {
            anim("seq.tob_bloat_swinging_chain")
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
                anim("seq.nylocas_queen_melee")
                hit { damage(0..35).roll(); type(Melee) }
            }
            val ranged = ability("ranged") {
                anim("seq.nylocas_queen_ranged")
                projectile(
                    spotanim = "spotanim.tob_nylocas_rangedprojectile_size2",
                    hit = Effect.Hit(damage = Roll(0..30), type = Ranged),
                )
            }
            val magic = ability("magic") {
                anim("seq.nylocas_queen_magic")
                hit { damage(0..30).roll(); type(Magic) }
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 2, requires = WithinMeleeRange)
                    +random(ranged, weight = 2)
                    +random(magic, weight = 2)
                }
            }
        }
}

class Sotetseg @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.tob_sotetseg_combat", "npc.tob_sotetseg_combat_hard") {
        stats(attackRate = 5)
        val magic = ability("magic") {
            anim("seq.tob_sotetseg_attack_ranged")
            projectile(
                spotanim = "spotanim.tob_sotetseg_sharedattack",
                hit = Effect.Hit(damage = Roll(0..48), type = Magic),
            )
        }
        val melee = ability("melee") {
            anim("seq.tob_sotetseg_attack_melee")
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
        stats(attackRate = 4)
        val ranged = ability("ranged") {
            anim("seq.tob_xarpus_attack_ranged")
            projectile(spotanim = "spotanim.tob_xarpus_acidspit", hit = Effect.Hit(damage = Roll(0..35), type = Ranged))
        }
        phase("combat") { weightedSelectorRandom { +random(ranged, weight = 1) } }
    }
}

class VerzikPhase1 @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.verzik_initial", "npc.verzik_phase1") {
        stats(attackRate = 5)
        val magic = ability("magic") {
            anim("seq.verzik_phase1_attack_magic")
            projectile(spotanim = "spotanim.verzik_phase1_projectile", hit = Effect.Hit(damage = Roll(0..35), type = Magic))
        }
        phase("combat") { weightedSelectorRandom { +random(magic, weight = 1) } }
    }
}

class VerzikPhase2 @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.verzik_phase1_to2_transition", "npc.verzik_phase2", "npc.verzik_phase2_to3_transition") {
            stats(attackRate = 5)
            val ranged = ability("ranged") {
                anim("seq.verzik_phase2_attack_magic")
                projectile(spotanim = "spotanim.verzik_phase2_ranged", hit = Effect.Hit(damage = Roll(0..40), type = Ranged))
            }
            val melee = ability("melee") {
                anim("seq.verzik_phase2_attack_melee")
                hit { damage(0..45).roll(); type(Melee) }
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 2, requires = WithinMeleeRange)
                    +random(ranged, weight = 2)
                }
            }
        }
}

class VerzikPhase3 @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.verzik_phase3") {
        stats(attackRate = 7)
        val melee = ability("melee") {
            anim("seq.verzik_phase3_attack_melee")
            hit { damage(0..50).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            anim("seq.verzik_phase3_attack_magic")
            projectile(spotanim = "spotanim.verzik_phase3_mageproj", hit = Effect.Hit(damage = Roll(0..50), type = Magic))
        }
        val ranged = ability("ranged") {
            anim("seq.verzik_phase3_attack_ranged")
            projectile(spotanim = "spotanim.verzik_phase3_rangeproj", hit = Effect.Hit(damage = Roll(0..45), type = Ranged))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 2, requires = WithinMeleeRange)
                +random(magic, weight = 2)
                +random(ranged, weight = 2)
            }
        }
    }
}

// --- Tombs of Amascut ---

class Akkha @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.akkha_melee", "npc.akkha_range", "npc.akkha_mage",
            "npc.akkha_enrage_initial", "npc.akkha_enrage",
        ) {
            stats(attackRate = 6)
            val melee = ability("melee") {
                anim("seq.npc_akkha_attack_melee_spear")
                hit { damage(0..30).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                anim("seq.npc_akkha_attack_magic_spear")
                hit { damage(0..28).roll(); type(Magic) }
            }
            val ranged = ability("ranged") {
                anim("seq.npc_akkha_attack_ranged_spear")
                hit { damage(0..28).roll(); type(Ranged) }
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
    override val spec: BossSpec = boss("npc.toa_baba", "npc.toa_baba_coffin", "npc.toa_baba_digging") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.npc_baba_attack_melee")
            hit { damage(0..38).roll(); type(Melee) }
        }
        val boulders = ability("boulders") {
            debris(
                telegraph = "spotanim.toa_baba_rock_fall",
                damage = Roll(0..25),
                windup = 3,
                count = 2..3,
            )
        }
        phase("combat") {
            forceEveryAttacks(8, 8, boulders)
            weightedSelectorRandom { +random(melee, weight = 1) }
        }
    }
}

class Kephri @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.toa_kephri_boss_shielded", "npc.toa_kephri_boss_weak", "npc.toa_kephri_boss_enrage") {
            stats(attackRate = 6)
            val ranged = ability("ranged") {
                anim("seq.npc_kephri_attack_ranged")
                projectile(
                    spotanim = "spotanim.toa_scarab_ranged_projectile01",
                    hit = Effect.Hit(damage = Roll(0..30), type = Ranged),
                )
            }
            val summonScarabs = ability("summon") {
                summon(
                    npc = "npc.toa_kephri_guardian_melee",
                    count = 2,
                    radius = 5,
                    mode = NpcMode.ApPlayer2,
                    duration = 100,
                    owned = true,
                )
            }
            phase("combat") {
                forceEveryAttacks(9, 9, summonScarabs)
                weightedSelectorRandom { +random(ranged, weight = 1) }
            }
        }
}

class Zebak @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.toa_zebak", "npc.toa_zebak_enraged") {
        stats(attackRate = 7)
        val melee = ability("melee") {
            anim("seq.npc_zebak01_attack_melee")
            hit { damage(0..35).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            anim("seq.npc_zebak01_attack_ranged")
            projectile(
                spotanim = "spotanim.zebak_range_projanim_initial",
                hit = Effect.Hit(damage = Roll(0..30), type = Ranged),
            )
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
            "npc.toa_warden_tumeken_phase2_mage",
            "npc.toa_warden_tumeken_phase2_range",
            "npc.toa_warden_tumeken_phase3",
        ) {
            stats(attackRate = 7)
            val magic = ability("magic") {
                anim("seq.npc_wardens_attackcenter01")
                hit { damage(0..30).roll(); type(Magic) }
            }
            val ranged = ability("ranged") {
                anim("seq.npc_wardens_attackleft01")
                hit { damage(0..30).roll(); type(Ranged) }
            }
            val stomp = ability("stomp") {
                anim("seq.npc_wardens_stomp01")
                hit { damage(0..30).roll(); type(Melee) }
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(stomp, weight = 2, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                    +random(ranged, weight = 2)
                }
            }
        }
}

class ElidinisWarden @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.toa_warden_elidinis_phase2_mage",
            "npc.toa_warden_elidinis_phase2_range",
            "npc.toa_warden_elidinis_phase3",
        ) {
            stats(attackRate = 7)
            val magic = ability("magic") {
                anim("seq.npc_wardens_attackcenter01")
                hit { damage(0..30).roll(); type(Magic) }
            }
            val ranged = ability("ranged") {
                anim("seq.npc_wardens_attackleft01")
                hit { damage(0..30).roll(); type(Ranged) }
            }
            val stomp = ability("stomp") {
                anim("seq.npc_wardens_stomp01")
                hit { damage(0..30).roll(); type(Melee) }
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(stomp, weight = 2, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                    +random(ranged, weight = 2)
                }
            }
        }
}
