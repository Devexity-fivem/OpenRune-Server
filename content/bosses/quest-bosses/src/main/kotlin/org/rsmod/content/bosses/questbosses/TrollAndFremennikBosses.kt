package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Troll Stronghold — Dad: heavy melee up to ~28.
class Dad @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.fd_troll_dad") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..28).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Troll Romance — Arrg: melee up to ~15.
class Arrg @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.trollromance_arrg_attackable") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..15).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// My Arm's Big Adventure — Giant Roc: melee up to ~16.
class GiantRoc @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.myarm_giant_roc") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.bird_attack")
            hit { damage(0..16).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// One Small Favour — Slagilith: melee up to ~11.
class Slagilith @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.slagilith") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.golem_attack")
            hit { damage(0..11).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Grim Tales — Glod: melee up to ~20.
class Glod @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.grim_glod") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..20).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// The Fremennik Isles — Ice Troll King: melee up to ~15.
class IceTrollKing @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.fris_troll_king_true") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..15).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Fight Arena — General Khazard: melee up to ~9.
class GeneralKhazard @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.general_khazard_arena") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_sword_slash")
            hit { damage(0..9).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Monkey Madness — Jungle Demon: melee up to ~32 plus magic when out of reach.
class JungleDemon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.mm_demon") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..32).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            anim("seq.demon_attack")
            projectile(spotanim = "firebolt_travel", hit = Effect.Hit(damage = Roll(0..26), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Fremennik Trials — Koschei the deathless: melee (revive cycle is quest-driven).
class Koschei @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.vikingexile_koschei_battle") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_unarmedpunch")
            hit { damage(0..14).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Fremennik Trials — The Draugen: melee up to ~8.
class Draugen @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.viking_draugen") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.ghost_attack")
            hit { damage(0..8).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Olaf's Quest — Ulfric: melee up to ~11.
class Ulfric @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.olaf2_ulfric") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.skeleton_attack")
            hit { damage(0..11).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}
