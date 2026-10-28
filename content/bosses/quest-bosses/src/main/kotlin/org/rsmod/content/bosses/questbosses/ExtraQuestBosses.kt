package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Zogre Flesh Eaters — Slash Bash: melee up to ~30.
class SlashBash @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.zogre_slash_bash") {
        stats(attackRate = 5)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Royal Titans — Branda the Fire Queen: melee + fire magic up to ~30.
class RoyalTitanFire @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.rt_fire_queen") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.fireblast_travel", hit = Effect.Hit(damage = Roll(0..28), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Royal Titans — Eldric the Ice King: melee + ice magic up to ~30.
class RoyalTitanIce @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.rt_ice_king") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.spell_ice_barrage_impact", hit = Effect.Hit(damage = Roll(0..28), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// The Nightmare (standard mode) — melee + magic, up to ~30.
class NightmareStandard @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.nightmare_phase_01", "npc.nightmare_phase_02", "npc.nightmare_phase_03",
            "npc.nightmare_weak_phase_01", "npc.nightmare_weak_phase_02", "npc.nightmare_weak_phase_03",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..24), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// Chambers of Xeric — Ice demon: melee + ice magic up to ~35.
class RaidsIceDemon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.raids_icedemon_combat") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..35).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.spell_ice_barrage_impact", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Inferno — TzKal-Zuk placeholder encounter: melee + ranged up to ~40.
class TzKalZuk @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.inferno_tzkalzuk_placeholder") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..40).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            projectile(spotanim = "spotanim.dharok_head_fly", hit = Effect.Hit(damage = Roll(0..35), type = Ranged))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(ranged, weight = 2)
            }
        }
    }
}

// Shadows of the Storm / DT2 — Bouncer: melee up to ~30.
class ShadowBouncer @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.shadow_maj_bouncer") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.hell_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Secrets of the North / DT2 — General Khazard: melee + magic up to ~35.
class ShadowKhazard @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.shadow_maj_khazard", "npc.general_khazard", "npc.sotn_khazard_ghorrock") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..35).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.fireblast_travel", hit = Effect.Hit(damage = Roll(0..30), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Horror from the Deep — Dagannoth Mother all-colour cycle: melee up to ~28.
class DagannothMotherAll @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.horror_dagganoth_air", "npc.horror_dagganoth_water", "npc.horror_dagganoth_fire",
            "npc.horror_dagganoth_earth", "npc.horror_dagganoth_ranged", "npc.horror_dagganoth_melee",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.tentacle_monster_attack")
                hit { damage(0..28).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// The Grand Tree — Black demon: melee + magic up to ~30.
class GrandTreeBlackDemon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.grandtree_blackdemon", "npc.nzone_grandtree_blackdemon_normal", "npc.nzone_grandtree_blackdemon_hard") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.firebolt_travel", hit = Effect.Hit(damage = Roll(0..25), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// Troll Stronghold — Dad (troll champion): melee up to ~27.
class DadChampion @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.troll_champion") {
        stats(attackRate = 5)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..27).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}
