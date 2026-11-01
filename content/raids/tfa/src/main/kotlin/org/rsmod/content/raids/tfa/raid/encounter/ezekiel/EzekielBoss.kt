package org.rsmod.content.raids.tfa.raid.encounter.ezekiel

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.TileSet
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.CHARGE
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.DEAD
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.MELEE
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.PATTERN
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.STUNNED
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class EzekielBoss @Inject constructor(deps: BossDeps, private val fights: EzekielEncounter) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onHit = { fights.of(npc)?.updateBar() })
        BossCombat.register(this, swordSpec, deps)
    }

    /** Player hits on a sword feed its launch charge; they resist ranged, fold to air spells. */
    private val swordSpec: BossSpec =
        boss(SWORD) {
            stats(attackRate = 99)
            val chargeUp = ability("charge_up", addVarn(CHARGE, -CHARGE_PER_HIT))
            incoming {
                rule(Condition.Always) {
                    scalePercent(EzekielArena.SWORD_RANGED_RESIST, Ranged)
                    run(chargeUp)
                }
            }
            phase("idle", scriptedMovement = true, lockMovement = true) {}
        }

    override val spec: BossSpec =
        boss(BOSS) {
            stats(attackRate = 1)

            val arena = EzekielArena.AREA
            val swords = customNpcs { _, _, owned -> owned.filter { it.visType.id == SWORD_ID } }

            fun originOf(npc: Npc): CoordGrid? = fights.at(npc.coords)?.origin

            fun laneSet(slot: Int): TileSet =
                customTiles(arena) { npc, _, _ ->
                    originOf(npc)?.let { EzekielArena.laneTiles(it, slot) } ?: emptyList()
                }

            // Varn effects/conditions always target the encounter boss, so per-sword charge lives
            // in the fight: kotlin ticks it down, the spec waves telegraph/launch off sword varns.
            val swordsTelegraphing =
                Condition.Custom { npc, _ ->
                    val f = fights.at(npc.coords) ?: return@Custom false
                    f.swordList().any { it.vars[CHARGE] == DODGE_TICKS }
                }

            val swordsLaunching =
                Condition.Custom { npc, _ ->
                    val f = fights.at(npc.coords) ?: return@Custom false
                    f.swordList().any { it.vars[CHARGE] <= 0 }
                }

            fun lanesWhere(predicate: (Npc) -> Boolean): TileSet =
                customTiles(arena) { npc, _, _ ->
                    val f = fights.at(npc.coords) ?: return@customTiles emptyList()
                    f.swordList()
                        .filter(predicate)
                        .flatMap {
                            f.slotOf(it)?.let { slot -> EzekielArena.laneTiles(f.origin, slot) }
                                ?: emptyList()
                        }
                }

            val telegraphLanes = lanesWhere { it.vars[CHARGE] == DODGE_TICKS }
            val launchLanes = lanesWhere { it.vars[CHARGE] <= 0 }

            val expiredSwords =
                customNpcs { _, _, owned ->
                    owned.filter { it.visType.id == SWORD_ID && it.vars[CHARGE] <= 0 }
                }

            val inCleave =
                Condition.Custom { npc, target ->
                    val f = fights.at(npc.coords) ?: return@Custom false
                    val targetTile = target?.coords ?: return@Custom false
                    val tankTile = f.tank?.coords ?: targetTile
                    EzekielArena.cleaveTiles(f.origin, tankTile).contains(targetTile)
                }

            val tankOutOfReach =
                Condition.Custom { npc, _ ->
                    val f = fights.at(npc.coords) ?: return@Custom true
                    val tank = f.tank
                    tank == null ||
                        EzekielArena.footprintDistance(f.origin, tank.coords) >
                            EzekielArena.CLEAVE_REACH
                }

            val enraging =
                Condition.Custom { npc, _ ->
                    val hpMax = npc.vars[HP_MAX]
                    npc.vars[DEAD] == 0 &&
                        npc.vars[PATTERN] < 0 &&
                        hpMax > 0 &&
                        npc.hitpoints * FULL_PERCENT <= EzekielArena.ENRAGE_PERCENT * hpMax
                }

            // ---- melee, magic, lightning --------------------------------------------------------

            val melee =
                sequence(
                    anim("seq.npc_ezekiel_melee01"),
                    onEach(
                        playersIn(arena),
                        whenever(
                            inCleave,
                            hit {
                                damage(0..EzekielArena.MELEE_MAX).roll()
                                type(Melee)
                            },
                        ),
                    ),
                )

            val magic =
                sequence(
                    anim("seq.npc_ezekiel_magic01"),
                    onEach(
                        playersIn(arena),
                        sequence(
                            faceTarget(),
                            projectile(
                                spotanim = "spotanim.ezekiel_magic_attack_vfx01",
                                config =
                                    ProjectileConfig.fixed(
                                        PROJ_START_HEIGHT,
                                        PROJ_END_HEIGHT,
                                        MAGIC_DELAY,
                                        FLIGHT,
                                        MAGIC_ANGLE,
                                        MAGIC_PROGRESS,
                                    ),
                                impact = "spotanim.ezekiel_magic_attack_impact_vfx01",
                                hit =
                                    hit {
                                        damage(0..EzekielArena.MAGIC_MAX).roll()
                                        type(Magic)
                                    },
                            ),
                        ),
                    ),
                )

            val crossSet =
                customTiles(arena) { npc, _, _ ->
                    originOf(npc)?.let { EzekielArena.crossTiles(it) } ?: emptyList()
                }

            fun lightningBurst() =
                onTiles(
                    crossSet,
                    onEach(
                        playersOn(CurrentTile),
                        hit {
                            damage(EzekielArena.LIGHTNING_MIN..EzekielArena.LIGHTNING_MAX).roll()
                            type(Magic)
                            hazard()
                        },
                    ),
                )

            ability(
                "lightning",
                sequence(
                    anim("seq.npc_ezekiel_special01"),
                    onTiles(
                        crossSet,
                        mapSpotanim("spotanim.ezekiel_special_crack_vfx01", CurrentTile),
                    ),
                    after(LIGHTNING_TELEGRAPH, lightningBurst()),
                    after(LIGHTNING_TELEGRAPH + BURST_GAP, lightningBurst()),
                    external("ezekiel.swords"),
                ),
            )

            // ---- attack cycle -------------------------------------------------------------------

            ability(
                "cycle",
                whenever(
                    varnIs(STUNNED, 1),
                    nextAttackIn(1),
                    whenever(
                        tankOutOfReach,
                        sequence(magic, nextAttackIn(ATTACK_CYCLE)),
                        sequence(
                            melee,
                            addVarn(MELEE, 1),
                            whenever(
                                varnAtLeast(MELEE, MELEES_PER_LIGHTNING),
                                sequence(setVarn(MELEE, 0), run("lightning")),
                            ),
                            nextAttackIn(ATTACK_CYCLE),
                        ),
                    ),
                ),
            )

            ability(
                "begin",
                sequence(
                    song(MUSIC, arena),
                    setHp(varn(HP_MAX), varn(HP_MAX)),
                    setVarn(MELEE, 0),
                    setVarn(STUNNED, 0),
                    setVarn(PATTERN, -1),
                    setVarn(DEAD, 0),
                    nextAttackIn(FIRST_ATTACK),
                    external("ezekiel.bar"),
                ),
            )

            ability(
                "stunned",
                sequence(
                    interrupt(),
                    anim("seq.npc_ezekiel_idle_to_weaken01"),
                    anim("seq.npc_ezekiel_weaken01"),
                    setVarn(STUNNED, 1),
                    after(
                        EzekielArena.SWORD_STUN_TICKS,
                        sequence(
                            anim("seq.npc_ezekiel_weaken_to_idle01"),
                            setVarn(STUNNED, 0),
                            nextAttackIn(1),
                        ),
                    ),
                ),
            )

            ability(
                "healed",
                sequence(
                    anim("seq.npc_ezekiel_heal01"),
                    spotanim("spotanim.ezekiel_heal_spotanim_vfx01"),
                    external("ezekiel.bar"),
                ),
            )

            // ---- enrage -------------------------------------------------------------------------

            /** Strikes two of the three sword lanes; the untouched lane is the safe one. */
            fun enrageStrike(first: Int, second: Int): Effect =
                sequence(
                    anim("seq.npc_ezekiel_special01"),
                    onTiles(
                        laneSet(EzekielArena.ENRAGE_LANES[first]) +
                            laneSet(EzekielArena.ENRAGE_LANES[second]),
                        mapSpotanim("spotanim.ezekiel_special_crack_vfx01", CurrentTile),
                    ),
                    after(
                        LIGHTNING_TELEGRAPH,
                        onTiles(
                            laneSet(EzekielArena.ENRAGE_LANES[first]) +
                                laneSet(EzekielArena.ENRAGE_LANES[second]),
                            onEach(
                                playersOn(CurrentTile),
                                hit {
                                    damage(
                                            EzekielArena.LIGHTNING_MIN..EzekielArena.LIGHTNING_MAX
                                        )
                                        .roll()
                                    type(Magic)
                                    hazard()
                                },
                            ),
                        ),
                    ),
                )

            ability(
                "enrage_strike",
                sequence(
                    switch(
                        PATTERN,
                        0 to enrageStrike(1, 2),
                        1 to enrageStrike(2, 0),
                        2 to enrageStrike(0, 1),
                    ),
                    switch(
                        PATTERN,
                        0 to setVarn(PATTERN, 1),
                        1 to setVarn(PATTERN, 2),
                        2 to setVarn(PATTERN, 0),
                    ),
                    nextAttackIn(EzekielArena.ENRAGE_CYCLE),
                ),
            )

            ability(
                "enrage_start",
                sequence(
                    interrupt(),
                    transitionTo(ENRAGE),
                    anim("seq.tfa_ezekiel_strafe"),
                    teleport(
                        customTile { npc, _ ->
                            originOf(npc)?.translate(ENRAGE_SIDE_DX, 0) ?: npc.coords
                        }
                    ),
                    setVarn(PATTERN, 0),
                    setVarn(MELEE, 0),
                    external("ezekiel.enrage_swords"),
                    onEach(playersIn(arena), external("ezekiel.pull")),
                    nextAttackIn(EzekielArena.ENRAGE_CYCLE),
                ),
            )

            // ---- death --------------------------------------------------------------------------

            ability(
                "die",
                sequence(
                    interrupt(),
                    retire(),
                    transitionTo(DEAD_PHASE),
                    setVarn(DEAD, 1),
                    anim("seq.npc_ezekiel_death01"),
                    spotanim("spotanim.vfx_ezekiel_star_explosion01"),
                    external("ezekiel.bar_close"),
                    asNpc(swords, sequence(lockMovement(), despawnSelf())),
                    after(DEATH_TICKS, run("complete")),
                ),
            )

            ability(
                "complete",
                sequence(setVarn(DONE, 1), external("ezekiel.complete"), despawnSelf()),
            )

            phase(P1, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("cycle", weight = 1) }
            }
            phase(ENRAGE, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("enrage_strike", weight = 1) }
            }
            phase(DEAD_PHASE, scriptedMovement = true, lockMovement = true) {}

            // swords charge down each tick (kotlin side); telegraph a dodge window, then launch
            every(1, external("ezekiel.sword_tick"))
            every(
                1,
                whenever(
                    swordsTelegraphing,
                    onTiles(
                        telegraphLanes,
                        parallel(
                            mapSpotanim(
                                "spotanim.vfx_ezekiel_sword_telegraph01",
                                CurrentTile,
                            ),
                            onEach(
                                playersOn(CurrentTile),
                                external("ezekiel.sword_mark", at = CurrentTile),
                            ),
                        ),
                    ),
                ),
            )
            every(
                1,
                whenever(
                    swordsLaunching,
                    sequence(
                        onTiles(
                            launchLanes,
                            onEach(
                                playersOn(CurrentTile),
                                hit {
                                    damage(EzekielArena.SWORD_MIN..EzekielArena.SWORD_MAX).roll()
                                    type(Melee)
                                    hazard()
                                },
                            ),
                        ),
                        external("ezekiel.sword_resolve"),
                        asNpc(
                            expiredSwords,
                            sequence(
                                spotanim("spotanim.vfx_ezekiel_sword_despawn01"),
                                after(1, despawnSelf()),
                            ),
                        ),
                    ),
                ),
            )
            every(1, whenever(enraging, run("enrage_start")))
            every(1, whenever(Condition.HpExact(1) and !varnIs(DEAD, 1), run("die")))
        }

    private companion object {
        const val BOSS = "npc.tfa_ezekiel"
        const val SWORD = "npc.tfa_ezekiel_sword"
        val SWORD_ID by lazy { SWORD.asRSCM(RSCMType.NPC) }

        const val P1 = "p1"
        const val ENRAGE = "enrage"
        const val DEAD_PHASE = "dead"

        const val MUSIC = 17003
        const val FULL_PERCENT = 100

        const val FIRST_ATTACK = 6
        const val ATTACK_CYCLE = 5
        const val MELEES_PER_LIGHTNING = 2
        const val LIGHTNING_TELEGRAPH = 3
        const val BURST_GAP = 2
        const val DODGE_TICKS = 2
        const val DEATH_TICKS = 6
        const val ENRAGE_SIDE_DX = 7
        const val CHARGE_PER_HIT = 2

        const val PROJ_START_HEIGHT = 470
        const val PROJ_END_HEIGHT = 120
        const val FLIGHT = 60
        const val MAGIC_DELAY = 30
        const val MAGIC_ANGLE = 0
        const val MAGIC_PROGRESS = 160
    }
}
