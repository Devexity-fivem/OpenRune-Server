package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Spirits of the Elid — possessed golems: melee up to ~9 each.
class ElidGolem @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.elid_golem_black", "npc.elid_golem_white", "npc.elid_golem_grey") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.golem_attack")
                hit { damage(0..9).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Spirits of the Elid — Evil spirit: melee up to ~6.
class EvilSpirit @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.deal_evil_spirit") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.ghost_attack")
            hit { damage(0..6).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Contact! — Giant Scarab: melee up to ~20 with a burst of scarab adds handled
// by the dungeon script.
class GiantScarab @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.contact_scarab_boss") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.scarab_attack")
            hit { damage(0..20).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Swan Song — Sea Troll Queen: melee up to ~18.
class SeaTrollQueen @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.swan_seatroll_queen") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..18).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Royal Trouble — Giant Sea Snake: melee up to ~12.
class GiantSeaSnake @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.royal_sea_snake_medium", "npc.royal_sea_snake_mother_smaller") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.snake_attack")
                hit { damage(0..12).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Yanille agility dungeon — Salarin the twisted: melee up to ~15.
class Salarin @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.salarin_the_twisted") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..15).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Horror from the Deep — Dagannoth mother: melee and ranged alternation
// up to ~19 (colour-changing weakness handled by quest variants).
class DagannothMother @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.horror_dagannoth_medium", "npc.horror_dagannoth_medium_darker",
            "npc.horror_dagannoth_medium_lighter",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.horror_dagannoth_attack")
                hit { damage(0..19).roll(); type(Melee) }
            }
            val ranged = ability("ranged") {
                anim("seq.horror_dagannoth_attack")
                projectile(spotanim = "spotanim.troll_rock_travel", hit = Effect.Hit(damage = Roll(0..14), type = Ranged))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(ranged, weight = 2)
                }
            }
        }
}

// The Grand Tree — Glough: melee up to ~13.
class Glough @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.grandtree_glough_battle") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_unarmedpunch")
            hit { damage(0..13).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Legends' Quest — Nezikchened: melee up to ~10 with prayer-draining aura.
class Nezikchened @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.nezikchened") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..10).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Death to the Dorgeshuun / Another Slice — Sigmund: all three combat styles,
// up to ~11.
class Sigmund @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.dttd_sigmund_melee", "npc.dttd_sigmund_ranged", "npc.dttd_sigmund_magic",
            "npc.dttd_sigmund_mill", "npc.slice_sigmund_melee", "npc.slice_sigmund_ranged",
            "npc.slice_sigmund_magic", "npc.slice_sigmund_showdown",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.human_sword_slash")
                hit { damage(0..11).roll(); type(Melee) }
            }
            val ranged = ability("ranged") {
                anim("seq.human_bow")
                projectile(spotanim = "spotanim.rune_arrow_travel", hit = Effect.Hit(damage = Roll(0..9), type = Ranged))
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.earthbolt_travel", hit = Effect.Hit(damage = Roll(0..9), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 2, requires = WithinMeleeRange)
                    +random(ranged, weight = 1)
                    +random(magic, weight = 1)
                }
            }
        }
}

// Watchtower — Moss guardian: melee up to ~11.
class MossGuardian @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.roving_mossgiant") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..11).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Between a Rock — Arzinian avatars: melee/ranged/magic respectively, up to ~12.
class ArzinianAvatars @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.dwarf_rock_avatar_warrior", "npc.dwarf_rock_avatar_archer",
            "npc.dwarf_rock_avatar_mage", "npc.dwarf_rock_actual_demon",
            "npc.dwarf_rock_avatar_warrior_green", "npc.dwarf_rock_avatar_archer_green",
            "npc.dwarf_rock_avatar_mage_green", "npc.dwarf_rock_avatar_warrior_yellow",
            "npc.dwarf_rock_avatar_archer_yellow", "npc.dwarf_rock_avatar_mage_yellow",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.golem_attack")
                hit { damage(0..12).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Dragon Slayer II — Galvek: melee + dragonfire across all element phases,
// up to ~30.
class Galvek @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.galvek_waves", "npc.galvek_fire", "npc.galvek_water",
            "npc.galvek_wind", "npc.galvek_earth", "npc.galvek_transmog",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.dragon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            val fire = ability("dragonfire") {
                anim("seq.dragon_attack")
                projectile(spotanim = "spotanim.firebreath_travel", hit = Effect.Hit(damage = Roll(0..28), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(fire, weight = 2)
                }
            }
        }
}

// Wilderness — Revenant maledictus (wild_cave_superior): revenant-style
// tri-style hits up to ~30.
class RevenantMaledictus @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.wild_cave_superior") {
        stats(attackRate = 5)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..30).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            projectile(spotanim = "spotanim.rune_arrow_travel", hit = Effect.Hit(damage = Roll(0..26), type = Ranged))
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.firebolt_travel", hit = Effect.Hit(damage = Roll(0..26), type = Magic))
            freeze(8, chance = 1, outOf = 6)
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(ranged, weight = 2)
                +random(magic, weight = 2)
            }
        }
    }
}
