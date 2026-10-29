package org.rsmod.content.bosses.worldbosses

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.Selector

// Moons of Peril — every moon's standard attack is a three-hit typeless slash
// (max 4+8+20): accuracy rolls against melee defence per hit and a miss ends
// the rest of the stack, so each stage chains through onHit. penetration(100)
// ignores protection prayers, matching the typeless style. The lifesteal
// percents are the Blood Moon's heal-per-hit (1x/2x/3x of damage dealt; the
// third is clamped 30..50 in OSRS — a clamped heal needs an extension handler,
// so the raw 3x is used for now).
private fun moonTripleHit(lifesteal1: Int, lifesteal2: Int, lifesteal3: Int): Effect.Hit =
    Effect.Hit(
        damage = DamageExpr.Accuracy(on = (0..4).roll()),
        type = Melee,
        penetration = 100,
        lifesteal = lifesteal1,
        onHit =
            Effect.Hit(
                damage = DamageExpr.Accuracy(on = (0..8).roll()),
                type = Melee,
                penetration = 100,
                lifesteal = lifesteal2,
                onHit =
                    Effect.Hit(
                        damage = DamageExpr.Accuracy(on = (0..20).roll()),
                        type = Melee,
                        penetration = 100,
                        lifesteal = lifesteal3,
                    ),
            ),
    )

private fun specialChooser(vararg branches: Pair<String, Effect>): Effect =
    choose(
        Selector.Rotation(branches.map { it.first }),
        branches.toMap(),
    )

// Moons of Peril — Blood Moon: after six standard attacks she alternates Blood
// Rain and Blood Jaguar. Both spawn loc.pmoon_boss_blood_pool hazards through
// MoonsExtensions; jaguar-phase pools also heal her 3x for the damage they deal
// (the jaguar adds' own damage-to-heal redirect is still an approximation). The
// _vis statue stays in the spec: the spawned entity keeps type 13011 after
// transmog, so encounter registration and respawn hooks resolve through it.
internal fun bloodMoonSpec(): BossSpec =
    boss("npc.pmoon_boss_blood_moon", "npc.pmoon_boss_blood_moon_vis") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.pmoon_macuahuitl_crush")
            include(moonTripleHit(100, 200, 300))
        }
        val special =
            ability(
                "special",
                specialChooser(
                    "rain" to
                        sequence(
                            anim("seq.pmoon_macuahuitl_crush"),
                            external("pmoon.blood_pools"),
                        ),
                    "jaguar" to
                        sequence(
                            summon(
                                npc = "npc.pmoon_boss_jaguar",
                                count = 4,
                                radius = 6,
                                mode = NpcMode.ApPlayer2,
                                duration = 40,
                                owned = true,
                            ),
                            external("pmoon.jaguar_pools"),
                        ),
                ),
            )
        phase("combat") {
            forceEveryAttacks(6, 6, special)
            weightedSelectorRandom { +random(melee, weight = 1) }
        }
    }

// Moons of Peril — Blue Moon: alternates Weapon Freeze and Frost Storm after
// six standard attacks. Weapon Freeze binds the player and disarms the weapon
// into loc.pmoon_boss_ice_block (MoonsExtensions); Frost Storm spawns the
// roaming storms and extinguishes the braziers, healing her while they stay
// unlit until the player relights them.
internal fun blueMoonSpec(): BossSpec =
    boss("npc.pmoon_boss_blue_moon", "npc.pmoon_boss_blue_moon_vis") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.pmoon_macuahuitl_crush")
            include(moonTripleHit(0, 0, 0))
        }
        val special =
            ability(
                "special",
                specialChooser(
                    "freeze" to
                        sequence(
                            Effect.Freeze(WEAPON_FREEZE_TICKS),
                            external("pmoon.weapon_freeze"),
                            summon(
                                npc = "npc.pmoon_boss_icicle_1",
                                count = 2,
                                mode = NpcMode.ApPlayer2,
                                duration = 40,
                                owned = true,
                            ),
                        ),
                    "storm" to
                        sequence(
                            summon(
                                npc = "npc.pmoon_boss_winter_storm",
                                count = 2,
                                mode = NpcMode.ApPlayer2,
                                duration = 30,
                                owned = true,
                            ),
                            external("pmoon.frost_storm"),
                        ),
                ),
            )
        phase("combat") {
            forceEveryAttacks(6, 6, special)
            weightedSelectorRandom { +random(melee, weight = 1) }
        }
    }

// Moons of Peril — Eclipse Moon: alternates Searing Rays and Clones after six
// standard attacks. TODO: Searing Rays only summons the moon shield — the real
// attack shoves the player behind it while light rays sweep the chamber, and
// Clones should bind the player at the centre and fire 15 attacks in five
// cycles of three that facing each clone parries.
internal fun eclipseMoonSpec(): BossSpec =
    boss("npc.pmoon_boss_eclipse_moon", "npc.pmoon_boss_eclipse_moon_vis") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.pmoon_macuahuitl_crush")
            include(moonTripleHit(0, 0, 0))
        }
        val special =
            ability(
                "special",
                specialChooser(
                    "rays" to
                        summon(
                            npc = "npc.pmoon_boss_eclipse_moon_shield",
                            count = 1,
                            duration = 30,
                            owned = true,
                        ),
                    "clones" to
                        sequence(
                            Effect.Freeze(CLONE_BIND_TICKS),
                            summon(
                                npc = "npc.pmoon_boss_eclipse_clone",
                                count = 3,
                                mode = NpcMode.ApPlayer2,
                                duration = 40,
                                owned = true,
                            ),
                        ),
                ),
            )
        phase("combat") {
            forceEveryAttacks(6, 6, special)
            weightedSelectorRandom { +random(melee, weight = 1) }
        }
    }

private const val WEAPON_FREEZE_TICKS = 8
private const val CLONE_BIND_TICKS = 3

class BloodMoon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = bloodMoonSpec()
}

class BlueMoon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = blueMoonSpec()
}

class EclipseMoon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = eclipseMoonSpec()
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
