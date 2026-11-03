package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Condition.Not
import org.rsmod.api.bosses.spec.Condition.WithinMeleeRange
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub

// Desert Treasure — Damis first form: melee up to 22.
class Damis @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.fd_damis_normal") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_unarmedpunch")
            hit { damage(0..22).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Desert Treasure — Damis second form: melee up to 28 at a faster rate, and
// drains 5% + 1 of current prayer every tick spent within melee distance.
class DamisTougher @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    init {
        deps.extensionRegistry.register("damis.prayer_drain") { ctx ->
            val prayer = ctx.target.stat("stat.prayer")
            if (prayer > 0) {
                ctx.target.statSub("stat.prayer", prayer * PRAYER_DRAIN_PERCENT / 100 + 1, 0)
            }
        }
    }

    override val spec: BossSpec = boss("npc.fd_damis_tougher") {
        stats(attackRate = 3)
        every(1, Effect.Whenever(WithinMeleeRange, external("damis.prayer_drain")))
        val melee = ability("melee") {
            anim("seq.human_unarmedpunch")
            hit { damage(0..28).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }

    private companion object {
        const val PRAYER_DRAIN_PERCENT = 5
    }
}

// Desert Treasure — Fareed: melee up to ~18; fire-weak.
class Fareed @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.firediamond_firewarrior") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..37).roll(); type(Melee) }
        }
        val tooHot = ability("too_hot") {
            message("You need Ice Gloves to harm Fareed!")
        }
        incoming {
            rule(
                Condition.HitStyle(Melee) and
                    Not(
                        Condition.Custom { _, target ->
                            target != null && target.worn.count("obj.ice_gloves") > 0
                        }
                    )
            ) {
                cap(0)
                run(tooHot)
            }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Desert Treasure — Kamil: melee up to 23; his ice spell always lands for
// double 5s and encases the target, and only fire spells can hurt him.
class Kamil @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.icediamond_icewarrior") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..23).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            projectile(spotanim = "spotanim.ice_barrage_travel", hit = Effect.Hit(damage = Roll(5..5), type = Magic))
            freeze(8)
            hit { damage(5..5).roll(); type(Magic) }
        }
        incoming {
            rule(Condition.HitStyle(Magic) and Not(hitSecondaryObj(*FIRE_SPELLS))) { cap(0) }
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }

    private companion object {
        val FIRE_SPELLS =
            arrayOf(
                "obj.13_fire_strike", "obj.35_fire_bolt", "obj.59_fire_blast",
                "obj.75_fire_wave", "obj.95_fire_surge",
            )
    }
}

// Desert Treasure — Dessous: rapid melee up to 19, and a combination attack
// that always lands for a ranged 5 and a magic 5 together.
class Dessous @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss("npc.blooddiamond_vampirewarrior", "npc.blooddiamond_vampirewarrior_unkillable") {
            stats(attackRate = 3)
            val melee = ability("melee") {
                anim("seq.vamp_claw")
                hit { damage(0..19).roll(); type(Melee) }
            }
            val combo = ability("combo") {
                projectile(spotanim = "spotanim.blood_blitz_travel", hit = Effect.Hit(damage = Roll(5..5), type = Magic))
                projectile(spotanim = "spotanim.rune_arrow_travel", hit = Effect.Hit(damage = Roll(5..5), type = Ranged))
            }
            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 3, requires = WithinMeleeRange)
                    +random(combo, weight = 2)
                }
            }
        }
}
