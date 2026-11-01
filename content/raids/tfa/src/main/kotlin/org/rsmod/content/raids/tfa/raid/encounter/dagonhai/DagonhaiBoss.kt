package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiAbilities.COMBAT
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiAbilities.EMPOWERED
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiAbilities.RITUAL
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.DEAD
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.PHASE
import org.rsmod.plugin.scripts.ScriptContext

class DagonhaiBoss @Inject constructor(deps: BossDeps, private val fights: DagonhaiEncounter) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { fights.of(npc)?.modifyTrioHit(this) },
            onHit = { fights.of(npc)?.let { it.impactTrio(this); it.updateBar() } },
        )
    }

    override val spec: BossSpec =
        boss(ELDER, ELDER_UNSHIELDED) {
            stats(attackRate = 1)

            val arena = DagonhaiArena.AREA

            // The attack lands on the target and anyone standing next to it.
            val splash =
                area(
                    customTile { _, target -> target.coords.translate(-2, -2) },
                    customTile { _, target -> target.coords.translate(2, 2) },
                )

            val ranged =
                sequence(
                    anim("seq.npc_dagonhai_attack_ranged_01"),
                    spotanim("spotanim.vfx_dagonhai_attack_ranged_01"),
                    onEach(
                        playersIn(splash),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.vfx_projanim_dagonhai_attack_ranged_01",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        RANGED_DELAY,
                                        RANGED_FLIGHT,
                                        PROJ_ANGLE,
                                        PROJ_PROGRESS,
                                    ),
                                impact = "spotanim.vfx_hitanim_dagonhai_attack_ranged_01",
                                hit =
                                    hit {
                                        damage(0..DagonhaiArena.RANGED_MAX).roll()
                                        type(Ranged)
                                    },
                            ),
                        ),
                    ),
                )

            fun magicElement(index: Int, impact: String): Effect =
                sequence(
                    anim("seq.npc_dagonhai_attack_magic_0$index"),
                    spotanim("spotanim.vfx_dagonhai_attack_magic_0$index"),
                    onEach(
                        playersIn(splash),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.vfx_dagonhai_attack_magic_04_projanim",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        MAGIC_DELAY,
                                        MAGIC_FLIGHT,
                                        PROJ_ANGLE,
                                        PROJ_PROGRESS,
                                    ),
                                impact = impact,
                                hit =
                                    hit {
                                        damage(0..DagonhaiArena.MAGIC_MAX).roll()
                                        type(Magic)
                                    },
                            ),
                        ),
                    ),
                )

            val magic =
                oneOf(
                    magicElement(1, "spotanim.vfx_dagonhai_attack_magic_fire_01"),
                    magicElement(2, "spotanim.vfx_dagonhai_attack_magic_water_01"),
                    magicElement(3, "spotanim.vfx_dagonhai_attack_magic_air_01"),
                    magicElement(4, "spotanim.vfx_dagonhai_attack_magic_earth_01"),
                )

            ability("ranged", sequence(ranged, nextAttackIn(DagonhaiArena.ATTACK_CYCLE)))
            ability("magic", sequence(magic, nextAttackIn(DagonhaiArena.ATTACK_CYCLE)))

            ability("channel", sequence(*DagonhaiAbilities.channelEffects().toTypedArray()))
            ability("channel_end", sequence(*DagonhaiAbilities.channelEndEffects().toTypedArray()))
            ability(
                "channel_stop",
                sequence(*DagonhaiAbilities.channelStopEffects().toTypedArray()),
            )
            ability("restore", sequence(*DagonhaiAbilities.restoreEffects().toTypedArray()))
            ability("empower", sequence(*DagonhaiAbilities.empowermentEffects().toTypedArray()))
            ability("barrage", DagonhaiAbilities.barrage(arena))
            ability("settle", sequence(transitionTo(COMBAT), setVarn(PHASE, 0)))

            ability(
                "begin",
                sequence(
                    song(MUSIC, arena),
                    setHp(varn(HP_MAX), varn(HP_MAX)),
                    setVarn(DEAD, 0),
                    setVarn(DONE, 0),
                    setVarn(PHASE, DagonhaiArena.PHASE_COMBAT),
                    transitionTo(COMBAT),
                    external("dagon.trio"),
                    external("dagon.bar"),
                    nextAttackIn(FIRST_ATTACK),
                ),
            )

            phase(IDLE, scriptedMovement = true, lockMovement = true) {}
            phase(COMBAT, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom {
                    +random("ranged", weight = 3)
                    +random("magic", weight = 2)
                }
            }
            phase(RITUAL, scriptedMovement = true, lockMovement = true) {}
            phase(EMPOWERED, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("barrage", weight = 1) }
            }
        }

    private companion object {
        const val ELDER = "npc.tfa_dagonhai_leader"
        const val ELDER_UNSHIELDED = "npc.tfa_dagonhai_leader_unshielded"
        const val IDLE = "idle"

        const val MUSIC = 17005

        const val FIRST_ATTACK = 6

        const val PROJ_START_HEIGHT = 470
        const val PROJ_END_HEIGHT = 120
        const val PROJ_ANGLE = 0
        const val PROJ_PROGRESS = 160

        const val RANGED_DELAY = 20
        const val RANGED_FLIGHT = 30
        const val MAGIC_DELAY = 30
        const val MAGIC_FLIGHT = 60
    }
}
