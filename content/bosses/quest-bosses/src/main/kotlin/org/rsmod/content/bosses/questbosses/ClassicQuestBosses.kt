package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Dragon Slayer: melee up to ~12 plus dragonfire breath up to ~25.
class Elvarg @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.elvarg", "npc.elvarg_alive") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.dragon_attack")
            hit { damage(0..12).roll(); type(Melee) }
        }
        val fire = ability("dragonfire") {
            anim("seq.dragon_attack")
            projectile(spotanim = "spotanim.firebreath_travel", hit = Effect.Hit(damage = Roll(0..25), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(fire, weight = 2)
            }
        }
    }
}

// Demon Slayer: melee up to ~7.
class Delrith @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.delrith", "npc.delrith_weakened") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..7).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Vampire Slayer: melee up to ~7.
class CountDraynor @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.count_draynor") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("vamp_claw")
            hit { damage(0..7).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Shadow of the Storm: melee up to ~16 with a magic projectile fallback.
class AgrithNaar @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.agrith_naar") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..16).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            anim("seq.demon_attack")
            projectile(spotanim = "spotanim.firebolt_travel", hit = Effect.Hit(damage = Roll(0..8), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Dragon Slayer side boss: melee up to ~7 with occasional magic.
class MelzarTheMad @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.melzar_the_mad") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_unarmedpunch")
            hit { damage(0..7).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.earthbolt_travel", hit = Effect.Hit(damage = Roll(0..5), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Family Crest: melee up to ~10.
class Chronozon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.chronozon") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..10).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Merlin's Crystal: melee only; respawns on death until quest completes.
class SirMordred @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.sir_mordred") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_sword_slash")
            hit { damage(0..12).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Fight Arena hellhound: melee up to ~11.
class ArenaBouncer @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.arena_bouncer") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("dog_attack")
            hit { damage(0..11).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Holy Grail: melee up to ~12.
class BlackKnightTitan @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.black_knight_titan") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_sword_slash")
            hit { damage(0..12).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Lost City / Fairy Tale tree spirits: melee up to ~14.
class TreeSpiritBoss @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.tree_spirit",
            "npc.fairy2_dryhadguardian_1", "npc.fairy2_dryhadguardian_2",
            "npc.fairy2_dryhadguardian_3", "npc.fairy2_dryhadguardian_4",
            "npc.fairy2_dryhadguardian_5", "npc.fairy2_dryhadguardian_6",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("human_unarmedpunch")
                hit { damage(0..14).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Rag and Bone Man wish list: melee up to ~8.
class MadAngel @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.mad_angel", "npc.mad_angel_quest") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_unarmedpunch")
            hit { damage(0..8).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Recruitment Drive: melee knight that must be beaten without combat gear.
class SirLeye @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.rd_combat_npc_room_3") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("human_sword_slash")
            hit { damage(0..6).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}
