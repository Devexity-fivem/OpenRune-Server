package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect

// Zogre Flesh Eaters — Slash Bash: melee up to ~30.
class SlashBash @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.zogre_slash_bash") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..13).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            anim("seq.troll_attack")
            projectile(spotanim = "spotanim.myarm_rock_roc_travel", hit = Effect.Hit(damage = Roll(0..13), type = Ranged))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(ranged, weight = 1)
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
            stats(attackRate = 6)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..50).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..33), type = Magic))
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
        stats(attackRate = 3)
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
        stats(attackRate = 10)
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
    override val spec: BossSpec = boss("npc.shadow_maj_khazard", "npc.sotn_khazard_ghorrock") {
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

// Horror from the Deep — Dagannoth Mother: cycles colours throughout the
// fight; each colour only takes damage from its matching combat style or
// elemental spell. Melee up to ~9, ranged ~12.
class DagannothMotherAll @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    init {
        deps.extensionRegistry.register("dagannoth_mother.next_colour") { ctx ->
            val encounter = deps.encounter(ctx.npc)
            val next = COLOURS.filter { it != encounter.currentPhaseName }
            encounter.transitionTo(next[deps.random.of(next.size)], deps.mapClock.cycle)
        }
    }

    override val spec: BossSpec =
        boss(
            "npc.horror_dagganoth_air", "npc.horror_dagganoth_water", "npc.horror_dagganoth_fire",
            "npc.horror_dagganoth_earth", "npc.horror_dagganoth_ranged", "npc.horror_dagganoth_melee",
        ) {
            stats(attackRate = 4)

            every(COLOUR_CYCLE_TICKS, external("dagannoth_mother.next_colour"))

            val melee = ability("melee") {
                anim("seq.horror_dagannoth_attack")
                hit { damage(0..9).roll(); type(Melee) }
            }
            val ranged = ability("ranged") {
                projectile(
                    spotanim = "spotanim.troll_rock_travel",
                    hit = Effect.Hit(damage = Roll(0..12), type = Ranged),
                )
                hit { damage(0..12).roll(); type(Ranged) }
            }

            incoming {
                rule(InPhase("air") and Condition.Not(hitSecondaryObj(*AIR_SPELLS))) { cap(0) }
                rule(InPhase("water") and Condition.Not(hitSecondaryObj(*WATER_SPELLS))) { cap(0) }
                rule(InPhase("earth") and Condition.Not(hitSecondaryObj(*EARTH_SPELLS))) { cap(0) }
                rule(InPhase("fire") and Condition.Not(hitSecondaryObj(*FIRE_SPELLS))) { cap(0) }
                rule(InPhase("ranged") and Condition.Not(hitStyle(Ranged))) { cap(0) }
                rule(InPhase("melee") and Condition.Not(hitStyle(Melee))) { cap(0) }
            }

            for (colour in COLOURS) {
                phase(colour, transmog = "npc.horror_dagganoth_$colour") {
                    weightedSelectorRandom {
                        +random(melee, weight = 2, requires = WithinMeleeRange)
                        +random(ranged, weight = 1)
                    }
                }
            }
        }

    private companion object {
        const val COLOUR_CYCLE_TICKS = 16
        val COLOURS = arrayOf("air", "water", "earth", "fire", "ranged", "melee")
        val AIR_SPELLS =
            arrayOf(
                "obj.01_wind_strike", "obj.17_wind_bolt", "obj.41_wind_blast",
                "obj.62_wind_wave", "obj.81_wind_surge", "obj.50_slayer_dart",
            )
        val WATER_SPELLS =
            arrayOf(
                "obj.05_water_strike", "obj.23_water_bolt", "obj.47_water_blast",
                "obj.65_water_wave", "obj.85_water_surge",
            )
        val EARTH_SPELLS =
            arrayOf(
                "obj.09_earth_strike", "obj.29_earth_bolt", "obj.53_earth_blast",
                "obj.70_earth_wave", "obj.90_earth_surge",
            )
        val FIRE_SPELLS =
            arrayOf(
                "obj.13_fire_strike", "obj.35_fire_bolt", "obj.59_fire_blast",
                "obj.75_fire_wave", "obj.95_fire_surge",
            )
    }
}

// The Grand Tree — Black demon: melee + magic up to ~30.
class GrandTreeBlackDemon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.grandtree_blackdemon", "npc.nzone_grandtree_blackdemon_normal", "npc.nzone_grandtree_blackdemon_hard") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..16).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// The Fractured Archive — guardian tentacles: melee up to ~20.
class ArchiveGuardianTentacle @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.raidquest_library_archive_guardian") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.horror_dagannoth_attack")
            hit { damage(0..20).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Troll Stronghold — Dad (troll champion): melee up to ~27.
class DadChampion @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.troll_champion") {
        stats(attackRate = 8)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..27).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}
