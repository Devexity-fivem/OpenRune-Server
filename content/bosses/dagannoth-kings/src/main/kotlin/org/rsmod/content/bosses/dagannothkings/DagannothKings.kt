package org.rsmod.content.bosses.dagannothkings

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.dsl.onEach
import org.rsmod.api.bosses.dsl.projectile
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.StatDrainEntry
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.util.PathingEntityCommon

// OSRS wiki + Kronos-184 reference (io.ruin Waterbirth kings + Spinolyp):
// all three kings share the sub-level lair and attack every 4 ticks.
// Prime casts a single magic wave (max 50) that splashes onto every player
// within 1 tile of the target tile on impact; Supreme fires a spine at every
// player within ~6 tiles (max 30); Rex only attacks in melee (max 26).
// Spinolyps harass with ranged spines that can poison or magic bolts that
// drain 1 prayer point, and periodically submerge and resurface elsewhere.
class DagannothPrime @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = dagannothPrimeSpec()
}

class DagannothSupreme @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = dagannothSupremeSpec()
}

class DagannothRex @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = dagannothRexSpec()
}

class Spinolyp @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = spinolypSpec()

    init {
        deps.extensionRegistry.register("dks.spinolyp_dive") { _, npc, _, _ ->
            dive(npc)
        }
    }

    private fun dive(npc: Npc) {
        if (deps.random.of(2) != 0) {
            return
        }
        val dx = deps.random.of(5) - 2
        val dz = deps.random.of(5) - 2
        if (dx == 0 && dz == 0) {
            return
        }
        npc.anim("seq.dagannoth_water_creature_go_down")
        PathingEntityCommon.telejump(npc, deps.collision, npc.coords.translate(dx, dz))
        npc.anim("seq.dagannoth_water_creature_spring_up")
    }
}

internal fun dagannothPrimeSpec(): BossSpec = boss("npc.dagcave_magic_boss") {
    stats(attackRate = 4)

    val waterWave =
        ability("water_wave") {
            anim("seq.dagannoth_meganoth_attack_mage")
            projectile(
                spotanim = "spotanim.waterwave_travel",
                resolveOnImpact = true,
                hit =
                    Effect.Hit(
                        target = TargetExpr.AllInRadius(radius = 1, of = TargetExpr.CurrentTarget),
                        damage = DamageExpr.Roll(0..50),
                        type = HitType.Magic,
                        spotanim = "spotanim.dagannoth_whirlpool",
                        spotanimHeight = 100,
                    ),
            )
        }

    phase("combat") {
        weightedSelectorRandom {
            +random(waterWave, weight = 1)
        }
    }
}

internal fun dagannothSupremeSpec(): BossSpec = boss("npc.dagcave_ranged_boss") {
    stats(attackRate = 4)

    val spineVolley =
        ability("spine_volley") {
            anim("seq.dagannoth_meganoth_attack_range")
            include(
                onEach(
                    targets = TargetExpr.AllInRadius(radius = 6, of = TargetExpr.Self),
                    effect =
                        Effect.Projectile(
                            spotanim = "spotanim.dagannoth_spine_spotanim_travel",
                            hit =
                                Effect.Hit(
                                    damage = DamageExpr.Roll(0..30),
                                    type = HitType.Ranged,
                                    spotanim = "spotanim.dagannoth_arrow_spotanim_hit",
                                    spotanimHeight = 100,
                                ),
                        ),
                )
            )
        }

    phase("combat") {
        weightedSelectorRandom {
            +random(spineVolley, weight = 1)
        }
    }
}

internal fun dagannothRexSpec(): BossSpec = boss("npc.dagcave_melee_boss") {
    stats(attackRate = 4)

    val slash =
        ability("slash") {
            anim("seq.dagannoth_meganoth_attack_melee")
            hit {
                damage(DamageExpr.Roll(0..26))
                type(HitType.Melee)
            }
        }

    phase("combat") {
        weightedSelectorRandom {
            +random(slash, weight = 1)
        }
    }
}

internal fun spinolypSpec(): BossSpec = boss("npc.dagannoth_water_creature_1x1") {
    stats(attackRate = 4)

    val spineSpit =
        ability("spine_spit") {
            anim("seq.dagannoth_water_creature_attack")
            projectile(
                spotanim = "spotanim.dagannoth_spine_spotanim_travel2",
                hit =
                    Effect.Hit(
                        damage = DamageExpr.Roll(0..8),
                        type = HitType.Ranged,
                        onHit = Effect.Poison(damage = 6, chance = 1, outOf = 3),
                    ),
            )
        }

    val waterBolt =
        ability("water_bolt") {
            anim("seq.dagannoth_water_creature_attack")
            projectile(
                spotanim = "spotanim.earthbolt_travel",
                hit =
                    Effect.Hit(
                        damage = DamageExpr.Roll(0..8),
                        type = HitType.Magic,
                        onHit = Effect.StatDrain(listOf(StatDrainEntry("prayer", 1))),
                    ),
            )
        }

    phase("combat") {
        every(10..16, external("dks.spinolyp_dive"), skipWhileBusy = true)
        weightedSelectorRandom {
            +random(spineSpit, weight = 3)
            +random(waterBolt, weight = 2)
        }
    }
}
