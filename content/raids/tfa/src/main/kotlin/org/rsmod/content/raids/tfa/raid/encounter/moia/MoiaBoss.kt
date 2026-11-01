package org.rsmod.content.raids.tfa.raid.encounter.moia

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.COUGH_CD
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.DEAD
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.ENRAGED
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.ENRAGE_ROUND
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.TP_CD
import org.rsmod.plugin.scripts.ScriptContext

class MoiaBoss @Inject constructor(deps: BossDeps, private val fights: MoiaEncounter) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onHit = { fights.of(npc)?.updateBar() })
    }

    override val spec: BossSpec =
        boss(BOSS) {
            stats(attackRate = 1)

            val arena = MoiaArena.AREA

            val enraging =
                Condition.Custom { npc, _ ->
                    val hpMax = npc.vars[HP_MAX]
                    npc.vars[DEAD] == 0 &&
                        npc.vars[ENRAGED] == 0 &&
                        hpMax > 0 &&
                        npc.hitpoints * FULL_PERCENT <= MoiaArena.ENRAGE_PERCENT * hpMax
                }

            // Fast dagger projectile — lands before the slower blue magic shot even when fired
            // after it.
            val ranged =
                sequence(
                    anim("seq.moia_projectile_attack01"),
                    onEach(
                        playersIn(arena),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.moia_dagger",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        RANGED_DELAY,
                                        RANGED_FLIGHT,
                                        PROJ_ANGLE,
                                        PROJ_PROGRESS,
                                    ),
                                hit =
                                    hit {
                                        damage(0..MoiaArena.RANGED_MAX).roll()
                                        type(Ranged)
                                    },
                            ),
                        ),
                    ),
                )

            // Slow blue magic projectile she fires after each teleport.
            val magic =
                sequence(
                    anim("seq.moia_projectile_attack01"),
                    onEach(
                        playersIn(arena),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.moia_dagger_magic",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        MAGIC_DELAY,
                                        MAGIC_FLIGHT,
                                        PROJ_ANGLE,
                                        PROJ_PROGRESS,
                                    ),
                                impact = "spotanim.moia_dagger_magic_impact",
                                hit =
                                    hit {
                                        damage(0..MoiaArena.MAGIC_MAX).roll()
                                        type(Magic)
                                    },
                            ),
                        ),
                    ),
                )

            // The cough: a smoke cloud at one player — infection applies on landing.
            val cough =
                sequence(
                    anim("seq.moia_chant01"),
                    spotanim("spotanim.moia_infect_spotanim"),
                    onEach(
                        randomPlayer(arena),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.moia_infect_attack_projanim",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        COUGH_DELAY,
                                        COUGH_FLIGHT,
                                        PROJ_ANGLE,
                                        PROJ_PROGRESS,
                                    ),
                            ),
                            after(COUGH_LAND, external("moia.infect")),
                        ),
                    ),
                )

            // Teleport to another side and immediately launch the slow blue shot.
            val relocate =
                sequence(
                    anim("seq.moia_teleport01"),
                    spotanim("spotanim.moia_teleport_spotanim"),
                    after(
                        1,
                        sequence(
                            teleport(
                                customTile { npc, _ ->
                                    fights.at(npc.coords)?.nextSideTile() ?: npc.coords
                                }
                            ),
                            spotanim("spotanim.moia_teleport_in_spotanim"),
                            magic,
                        ),
                    ),
                )

            ability("cough", sequence(cough, nextAttackIn(ATTACK_CYCLE)))

            ability(
                "cycle",
                sequence(
                    addVarn(TP_CD, 1),
                    addVarn(COUGH_CD, 1),
                    whenever(
                        varnAtLeast(TP_CD, MoiaArena.TELEPORT_EVERY),
                        sequence(setVarn(TP_CD, 0), relocate),
                        whenever(
                            varnAtLeast(COUGH_CD, MoiaArena.COUGH_EVERY),
                            sequence(setVarn(COUGH_CD, 0), cough),
                            ranged,
                        ),
                    ),
                    nextAttackIn(ATTACK_CYCLE),
                ),
            )

            ability(
                "begin",
                sequence(
                    song(MUSIC, arena),
                    setHp(varn(HP_MAX), varn(HP_MAX)),
                    setVarn(DEAD, 0),
                    setVarn(ENRAGED, 0),
                    setVarn(TP_CD, 0),
                    setVarn(COUGH_CD, 0),
                    external("moia.binders"),
                    external("moia.bar"),
                    nextAttackIn(FIRST_ATTACK),
                ),
            )

            // Charged attack — plays each countdown round; the bar is synced to the kotlin
            // countdown ticking down in MoiaFight.
            ability(
                "enrage_charge",
                sequence(
                    anim("seq.moia_chant01"),
                    switch(
                        ENRAGE_ROUND,
                        0 to
                            headbar(
                                "headbar.standard_charge_100",
                                fromPercent = 100,
                                toPercent = 0,
                                cycles = 40 * HEADBAR_CYCLE,
                            ),
                        1 to
                            headbar(
                                "headbar.standard_charge_100",
                                fromPercent = 100,
                                toPercent = 0,
                                cycles = 35 * HEADBAR_CYCLE,
                            ),
                        2 to
                            headbar(
                                "headbar.standard_charge_100",
                                fromPercent = 100,
                                toPercent = 0,
                                cycles = 30 * HEADBAR_CYCLE,
                            ),
                        otherwise =
                            headbar(
                                "headbar.standard_charge_100",
                                fromPercent = 100,
                                toPercent = 0,
                                cycles = 20 * HEADBAR_CYCLE,
                            ),
                    ),
                ),
            )

            // Enrage: vanish, collapse binders into the diamond around the centre, reappear
            // there and start the countdown.
            ability(
                "enrage_start",
                sequence(
                    interrupt(),
                    transitionTo(ENRAGE_PHASE),
                    setVarn(ENRAGED, 1),
                    anim("seq.moia_teleport01"),
                    spotanim("spotanim.moia_teleport_spotanim"),
                    after(
                        1,
                        sequence(
                            external("moia.diamond"),
                            spotanim("spotanim.moia_teleport_in_spotanim"),
                        ),
                    ),
                    external("moia.enrage_charge"),
                    nextAttackIn(ATTACK_CYCLE),
                ),
            )

            ability(
                "enrage_cycle",
                sequence(ranged, nextAttackIn(ATTACK_CYCLE)),
            )

            ability(
                "die",
                sequence(
                    interrupt(),
                    retire(),
                    transitionTo(DEAD_PHASE),
                    setVarn(DEAD, 1),
                    anim("seq.moia_death01"),
                    spotanim("spotanim.moia_teleport_death_spotanim"),
                    external("moia.bar_close"),
                    after(DEATH_TICKS, run("complete")),
                ),
            )

            ability(
                "complete",
                sequence(setVarn(DONE, 1), external("moia.complete"), despawnSelf()),
            )

            phase(HOVER, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("cycle", weight = 1) }
            }
            phase(ENRAGE_PHASE, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("enrage_cycle", weight = 1) }
            }
            phase(DEAD_PHASE, scriptedMovement = true, lockMovement = true) {}

            every(1, external("moia.enrage_tick"))
            every(1, whenever(enraging, run("enrage_start")))
            every(1, whenever(Condition.HpExact(1) and !varnIs(DEAD, 1), run("die")))
        }

    private companion object {
        const val BOSS = "npc.tfa_moia"

        const val HOVER = "hover"
        const val ENRAGE_PHASE = "enrage"
        const val DEAD_PHASE = "dead"

        const val MUSIC = 17007
        const val FULL_PERCENT = 100

        const val FIRST_ATTACK = 6
        const val ATTACK_CYCLE = 5
        const val DEATH_TICKS = 6
        const val HEADBAR_CYCLE = 30

        const val PROJ_START_HEIGHT = 470
        const val PROJ_END_HEIGHT = 120
        const val PROJ_ANGLE = 0
        const val PROJ_PROGRESS = 160

        const val RANGED_DELAY = 20
        const val RANGED_FLIGHT = 30
        const val MAGIC_DELAY = 30
        const val MAGIC_FLIGHT = 70
        const val COUGH_DELAY = 40
        const val COUGH_FLIGHT = 80
        const val COUGH_LAND = 4
    }
}
