package org.rsmod.content.bosses.vetion

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TelegraphSpec
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// OSRS wiki: two 255-hp forms. At half health each form summons a pair of
// hellhounds (level-194 skeletons first, level-231 greaters when enraged) that
// grant him complete damage immunity while they live. His main attack launches
// lightning that strikes the 3x3 around each nearby player; when someone is
// close he can slam his shield into the ground, hitting the area in front of
// (and underneath) him and briefly stunning victims. Depleting the first bar
// enrages him - a fresh 255 hp, faster and harder hits, and an energy blast
// identical to his lightning. If the second form survives five minutes he
// reverts to normal keeping his current hp.
// VetionFloor holds the normal form at 1 hp so the every-tick enrage check can
// run the transition instead of a death.
class Vetion @Inject constructor(private val deps: BossDeps) : PluginScript() {

    private val spec: BossSpec = boss("npc.vetion", "npc.vetion_2") {

        stats(attackRate = ATTACK_RATE)

        val slash =
            ability("slash") {
                anim("seq.skeleton_update_giant_attack")
                hit {
                    damage(0..44).roll()
                    type(Melee)
                }
            }

        val slashEnraged =
            ability("slash_enraged") {
                anim("seq.skeleton_update_giant_attack")
                hit {
                    damage(0..50).roll()
                    type(Melee)
                }
            }

        val lightning =
            ability("lightning") {
                anim("seq.skeleton_update_giant_attack")
                tileAoE(
                    tiles = { npc, _ -> strikesAroundPlayers(npc) },
                    telegraph = TelegraphSpec(LIGHTNING_SPOT, windup = LIGHTNING_WINDUP),
                    damage = Roll(0..30),
                    type = Magic,
                )
            }

        val lightningEnraged =
            ability("lightning_enraged") {
                anim("seq.skeleton_update_giant_attack")
                tileAoE(
                    tiles = { npc, _ -> strikesAroundPlayers(npc) },
                    telegraph = TelegraphSpec(LIGHTNING_SPOT, windup = LIGHTNING_WINDUP),
                    damage = Roll(0..36),
                    type = Magic,
                )
            }

        val bash =
            ability("bash") {
                anim("seq.skeleton_update_giant_vary3_attack")
                tileAoE(
                    tiles = { npc, _ -> bashTiles(npc) },
                    damage = Roll(0..44),
                    type = Typeless,
                )
                freeze(BASH_STUN_TICKS)
            }

        val summonHellhounds =
            ability("summon_hellhounds") {
                say("Kill, my pets!")
                summon(
                    npc = "npc.vetion_hellhound_jnr",
                    count = 2,
                    radius = 2,
                    mode = NpcMode.ApPlayer2,
                    duration = Int.MAX_VALUE,
                    owned = true,
                )
            }

        val summonGreaterHellhounds =
            ability("summon_greater_hellhounds") {
                say("Bahh! Go, dogs!!")
                summon(
                    npc = "npc.vetion_hellhound_snr",
                    count = 2,
                    radius = 2,
                    mode = NpcMode.ApPlayer2,
                    duration = Int.MAX_VALUE,
                    owned = true,
                )
            }

        val enrage =
            ability("enrage") {
                anim("seq.skeleton_update_giant_attack")
                say("Now do it again!!")
                healSelf(255)
                tileAoE(
                    tiles = { npc, _ -> strikesAroundPlayers(npc) },
                    telegraph = TelegraphSpec(LIGHTNING_SPOT, windup = LIGHTNING_WINDUP),
                    damage = Roll(0..30),
                    type = Magic,
                )
                transitionTo(PHASE_ENRAGED)
            }

        val revert = ability("revert") { transitionTo(PHASE_NORMAL) }

        incoming {
            rule(dogsAlive()) { scalePercent(0) }
        }

        triggers {
            on(Condition.InPhase(PHASE_NORMAL) and Condition.HpBelow(0.5)) runs
                Effect.Run(summonHellhounds.name)
            on(Condition.InPhase(PHASE_ENRAGED) and Condition.HpBelow(0.5)) runs
                Effect.Run(summonGreaterHellhounds.name)
        }

        every(
            1,
            whenever(Condition.InPhase(PHASE_NORMAL) and Condition.HpExact(1), Effect.Run(enrage.name)),
            skipWhileBusy = false,
        )

        every(
            50,
            whenever(
                Condition.InPhase(PHASE_ENRAGED) and Condition.PhaseTicksAtLeast(REVERT_TICKS),
                Effect.Run(revert.name),
            ),
            skipWhileBusy = false,
        )

        phase(PHASE_NORMAL, transmog = "npc.vetion") {
            weightedSelectorRandom {
                +random(slash, weight = 3, requires = WithinMeleeRange)
                +random(lightning, weight = 6, requires = Condition.TargetWithin(ATTACK_RANGE))
                +random(bash, weight = 2, requires = Condition.TargetWithin(BASH_TRIGGER_RANGE))
            }
        }

        phase(PHASE_ENRAGED, transmog = "npc.vetion_2", attackRate = ENRAGED_ATTACK_RATE) {
            weightedSelectorRandom {
                +random(slashEnraged, weight = 3, requires = WithinMeleeRange)
                +random(lightningEnraged, weight = 6, requires = Condition.TargetWithin(ATTACK_RANGE))
                +random(bash, weight = 2, requires = Condition.TargetWithin(BASH_TRIGGER_RANGE))
            }
        }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
    }

    private fun strikesAroundPlayers(npc: Npc): Collection<CoordGrid> =
        deps.playerList
            .filter {
                it.coords.level == npc.coords.level &&
                    it.coords.chebyshevDistance(npc.coords) <= LIGHTNING_RANGE &&
                    it.hitpoints > 0
            }
            .flatMap { player -> player.coords.areaRadius(LIGHTNING_RADIUS) }

    private fun bashTiles(npc: Npc): Collection<CoordGrid> =
        npc.coords.translate(1, 1).areaRadius(BASH_RADIUS)

    private fun CoordGrid.areaRadius(radius: Int): List<CoordGrid> =
        (-radius..radius).flatMap { dx -> (-radius..radius).map { dz -> translate(dx, dz) } }

    private fun dogsAlive(): Condition =
        Condition.Custom { npc, _ ->
            deps.npcRepo
                .findAll(ZoneKey.from(npc.coords), DOG_SCAN_RADIUS)
                .any { it.hitpoints > 0 && (it.isType(DOG_JNR) || it.isType(DOG_SNR)) }
        }

    private companion object {
        const val PHASE_NORMAL = "normal"
        const val PHASE_ENRAGED = "enraged"
        const val ATTACK_RATE = 6
        const val ENRAGED_ATTACK_RATE = 5
        const val ATTACK_RANGE = 10
        const val LIGHTNING_RANGE = 15
        const val LIGHTNING_RADIUS = 1
        const val LIGHTNING_WINDUP = 2
        const val LIGHTNING_SPOT = "spotanim.kalphite_glow_impact"
        const val BASH_RADIUS = 2
        const val BASH_TRIGGER_RANGE = 4
        const val BASH_STUN_TICKS = 4
        const val REVERT_TICKS = 500
        const val DOG_SCAN_RADIUS = 24
        const val DOG_JNR = "npc.vetion_hellhound_jnr"
        const val DOG_SNR = "npc.vetion_hellhound_snr"
    }
}

/** The normal form cannot die - depleting his first bar triggers the enrage. */
@Singleton
class VetionFloor @Inject constructor() : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int = if (npc.isVisType("npc.vetion")) 1 else 0
}

class VetionModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcHitpointsFloorHook>(VetionFloor::class.java)
    }
}
