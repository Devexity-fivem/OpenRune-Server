package org.rsmod.content.bosses.worldbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect

// Moons of Peril — Blood Moon: melee up to ~30.
class BloodMoon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.pmoon_boss_blood_moon", "npc.pmoon_boss_blood_moon_vis") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Moons of Peril — Blue Moon: melee up to ~28.
class BlueMoon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.pmoon_boss_blue_moon", "npc.pmoon_boss_blue_moon_vis") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..28).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Moons of Peril — Eclipse Moon: melee up to ~30.
class EclipseMoon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.pmoon_boss_eclipse_moon", "npc.pmoon_boss_eclipse_moon_vis") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Grotesque Guardians — Dawn: melee gargoyle up to ~27.
class Dawn @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.gargboss_dawn_spawn", "npc.gargboss_dawn_phase1", "npc.gargboss_dawn_phase3") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..27).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Grotesque Guardians — Dusk: melee up to ~30 across all combat phases.
class Dusk @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.gargboss_dusk_spawn", "npc.gargboss_dusk_phase1_defensive",
            "npc.gargboss_dusk_phase2_attacking", "npc.gargboss_dusk_phase3_defensive",
            "npc.gargboss_dusk_phase4_spawn", "npc.gargboss_dusk_phase4",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// The Nightmare / Phosani's Nightmare — all combat phases, melee + magic up to ~25.
class NightmareBoss @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(
            "npc.nightmare_challenge_initial", "npc.nightmare_challenge_phase_01",
            "npc.nightmare_challenge_phase_02", "npc.nightmare_challenge_phase_03",
            "npc.nightmare_challenge_weak_phase_01", "npc.nightmare_challenge_weak_phase_02",
            "npc.nightmare_challenge_weak_phase_03",
        ) {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..25).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.spell_blood_barrage_impact", hit = Effect.Hit(damage = Roll(0..20), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// Yama: melee + ranged/demonic magic up to ~30.
class YamaBoss @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.yama", "npc.yama_throne_occupied", "npc.voice_of_yama_3op") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..30).roll(); type(Melee) }
            }
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.fireblast_travel", hit = Effect.Hit(damage = Roll(0..26), type = Magic))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(magic, weight = 2)
                }
            }
        }
}

// Fortis Colosseum — Sol Heredit: melee up to ~35.
class SolHeredit @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.colosseum_sol_p1", "npc.colosseum_boss_seated") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..35).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// The Hueycoatl — head and enraged forms: melee up to ~25.
class Hueycoatl @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.huey_head", "npc.huey_head_enraged", "npc.huey_head_invulnerable") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.demon_attack")
                hit { damage(0..25).roll(); type(Melee) }
            }
            phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
        }
}

// Shellbane gryphon: melee up to ~22.
class ShellbaneGryphon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.gryphon_boss") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.bird_attack")
            hit { damage(0..22).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Barbarian Assault — Penance Queen: melee + magic up to ~24.
class PenanceQueen @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.barbassault_pen_queen_new") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.tentacle_monster_attack")
            hit { damage(0..24).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.firebolt_travel", hit = Effect.Hit(damage = Roll(0..18), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// The Mimic: melee up to ~20 plus chest-spawn adds scripted by the trail.
class TheMimic @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.trail_mimic_combat") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.tentacle_monster_attack")
            hit { damage(0..20).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Zalcano lives in content/bosses/zalcano — a dedicated spec here would double-register
// its npc types' hit and AI combat hooks.

// Tempoross — the sea spirit's enraged surface form hits up to ~20.
class Tempoross @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.tempoross_boss_ready", "npc.tempoross_boss_whirlpool", "npc.tempoross_boss_enraged") {
            stats(attackRate = 5)
            val magic = ability("magic") {
                projectile(spotanim = "spotanim.water_wave_travel", hit = Effect.Hit(damage = Roll(0..20), type = Magic))
            }
            phase("combat") { weightedSelectorRandom { +random(magic, weight = 1) } }
        }
}
