package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Lunar Diplomacy — "Me": the player's mirror, melee up to ~10.
class MirrorOfPlayer @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.quest_lunar_mirror_of_player", "npc.quest_lunar_mirror_of_player_female") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("human_unarmedpunch")
                hit { damage(0..10).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Dream Mentor — The Inadequacy: melee up to ~20.
class Inadequacy @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.dream_inadequacy") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..20).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Dream Mentor — The Everlasting: melee up to ~15.
class Everlasting @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.dream_everlasting") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("tentacle_monster_attack")
            hit { damage(0..15).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Dream Mentor — The Untouchable: melee up to ~17.
class Untouchable @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.dream_untouchable") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("tentacle_monster_attack")
            hit { damage(0..17).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Dream Mentor — The Illusive: melee up to ~13.
class Illusive @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.dream_illusive") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("tentacle_monster_attack")
            hit { damage(0..13).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// A Soul's Bane — three-headed Tolna: melee up to ~18.
class Tolna @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.soulbane_final_tolna1", "npc.soulbane_final_tolna2", "npc.soulbane_final_tolna3") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("tentacle_monster_attack")
                hit { damage(0..18).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Recipe for Disaster — Agrith-Na-Na: melee up to ~16 plus banana magic.
class AgrithNaNa @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.hundred_minion1") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..16).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.firebolt_travel", hit = Effect.Hit(damage = Roll(0..12), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Recipe for Disaster — Flambeed: melee up to ~18 (weapons unequipped on contact
// is handled by the quest script layer).
class Flambeed @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.hundred_minion2") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..18).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Recipe for Disaster — Karamel: melee up to ~18 plus stat-draining ice magic.
class Karamel @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.hundred_minion3") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("tentacle_monster_attack")
            hit { damage(0..18).roll(); type(Melee) }
            freeze(6, chance = 1, outOf = 8)
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Recipe for Disaster — Dessourt: melee up to ~18.
class Dessourt @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.hundred_minion4") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("vamp_claw")
            hit { damage(0..18).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Recipe for Disaster — Gelatinnoth Mother: melee up to ~18; the form-specific
// weakness rotation is handled by its six npc variants bound here.
class GelatinnothMother @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.hundred_minion5_air", "npc.hundred_minion5_melee",
            "npc.hundred_minion5_water", "npc.hundred_minion5_fire",
            "npc.hundred_minion5_ranged", "npc.hundred_minion5_earth",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("tentacle_monster_attack")
                hit { damage(0..18).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Recipe for Disaster — Culinaromancer: melee + magic barrage, up to ~20.
class Culinaromancer @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.hundred_culinaromancer", "npc.hundred_culinaromancer_base") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..20).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.ice_barrage_travel", hit = Effect.Hit(damage = Roll(0..16), type = Magic))
                freeze(6, chance = 1, outOf = 8)
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}
