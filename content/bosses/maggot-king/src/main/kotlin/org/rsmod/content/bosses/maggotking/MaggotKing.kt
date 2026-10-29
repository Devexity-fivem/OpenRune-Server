package org.rsmod.content.bosses.maggotking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.AT_WALL
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.BOSS
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.BOSS_SPAWN
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.CORPSE
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_APPROACH
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_BILE_POISONOUS
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_BILE_STICKY
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_CARRION
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_LARVA
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_LARVA_SWARM
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_SCREECH_BEGIN
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_SCREECH_END
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.EXT_SLAM
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.LARVA_PHASE_PERCENT
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.MODE
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.MODE_MELEE
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.MODE_STYLE
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.STYLE
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.STYLE_MAGIC
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.STYLE_RANGED
import org.rsmod.content.bosses.maggotking.MaggotKingEncounter.Companion.WALL_FRONT_Z
import org.rsmod.content.bosses.maggotking.MaggotKingLoot.Companion.KONAR_BIT
import org.rsmod.content.bosses.maggotking.MaggotKingLoot.Companion.LOOTED
import org.rsmod.content.drops.shouldDropBrimstoneKey
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Maggot King: a solo instanced boss fought across a bone wall. Ranged leaves poisonous bile,
 * magic leaves sticky bile, the periodic screech halves its Defence, lobs carrion (which wipes
 * bile) and launches ur-maggot larvae. Past ~65% it adds bone-wall slams; past ~20% it stops
 * retreating and only slams.
 */
class MaggotKing
@Inject
constructor(
    deps: BossDeps,
    private val encounter: MaggotKingEncounter,
    private val instances: InstanceManager,
    private val npcDeath: NpcDeath,
    private val areaChecker: AreaChecker,
) : BossPluginScript(deps) {

    private val protectionPrayed: Condition =
        TargetPraying(HitType.Melee) or TargetPraying(HitType.Ranged) or TargetPraying(HitType.Magic)

    private val punishWindow =
        Condition.Custom { npc, _ -> encounter.inPunishWindow(npc) }

    override val spec =
        boss(BOSS) {
            stats(attackRate = MaggotKingEncounter.ATTACK_RATE)

            val rangedAttack =
                ability("ranged") {
                    anim("seq.maggot_king_rangeattack")
                    projectile {
                        spotanim = "spotanim.maggot_king_poison_projectile"
                        hit {
                            damage(0..MAGIC_RANGED_MAX).roll()
                            type(HitType.Ranged)
                        }
                    }
                    include(external(EXT_BILE_POISONOUS))
                    poison(POISON_SEVERITY)
                }

            val magicAttack =
                ability("magic") {
                    anim("seq.maggot_king_mageattack")
                    projectile {
                        spotanim = "spotanim.maggot_king_blood_projectile_small"
                        hit {
                            damage(0..MAGIC_RANGED_MAX).roll()
                            type(HitType.Magic)
                        }
                    }
                    include(external(EXT_BILE_STICKY))
                }

            val approach =
                ability("approach") {
                    include(external(EXT_APPROACH))
                }

            val slam =
                ability("slam") {
                    anim("seq.maggot_king_meleeattack")
                    include(external(EXT_SLAM))
                }

            val screech =
                ability("screech") {
                    attackDelay = SCREECH_TICKS + 1
                    include(external(EXT_SCREECH_BEGIN))
                    anim("seq.maggot_king_screech")
                    spotanim("spotanim.vfx_maggot_king_screech_01", slot = 0)
                    spotanim("spotanim.vfx_maggot_king_screech_02", slot = 1)
                    include(
                        repeat(
                            SCREECH_TICKS,
                            gap = 1,
                            whenever(
                                protectionPrayed,
                                org.rsmod.api.bosses.dsl.hit {
                                    damage(SCREECH_HIT).roll()
                                    type(HitType.Typeless)
                                    hazard()
                                },
                            ),
                        ),
                    )
                    include(external(EXT_CARRION))
                    include(
                        whenever(
                            Condition.HpBelow(LARVA_PHASE_PERCENT / 100.0, inclusive = true),
                            external(EXT_LARVA),
                        ),
                    )
                    include(external(EXT_SCREECH_END))
                }

            val swarm =
                ability("swarm") {
                    include(external(EXT_LARVA_SWARM))
                }

            // Attacks landing while the head is extended get the melee punish: +50% damage and
            // no attack delay on the follow-up (see onModifyHit).
            incoming {
                rule(punishWindow) {
                    scalePercent(150, HitType.Melee)
                }
            }

            phase(
                "combat",
                lockMovement = true,
                idleAnim = "seq.npc_maggot_king_idle_01",
            ) {
                weightedSelectorRandom {
                    +random(rangedAttack, 1, requires = varnIs(STYLE, STYLE_RANGED))
                    +random(magicAttack, 1, requires = varnIs(STYLE, STYLE_MAGIC))
                }
                forceEveryAttacks(SCREECH_MIN, SCREECH_MAX, screech)
            }

            phase("melee", entryHp = MELEE_PHASE_FRACTION, lockMovement = true) {
                weightedSelectorRandom {
                    +random(slam, 1, requires = varnIs(AT_WALL, 1) and varnIs(MODE, MODE_MELEE))
                    +random(
                        rangedAttack,
                        1,
                        requires = varnIs(MODE, MODE_STYLE) and varnIs(STYLE, STYLE_RANGED),
                    )
                    +random(
                        magicAttack,
                        1,
                        requires = varnIs(MODE, MODE_STYLE) and varnIs(STYLE, STYLE_MAGIC),
                    )
                }
                forceWhen(varnIs(MODE, MODE_MELEE) and Condition.Not(varnIs(AT_WALL, 1)), approach)
                forceEveryAttacks(SCREECH_MIN, SCREECH_MAX, screech)
            }

            phase("final", entryHp = FINAL_PHASE_FRACTION, lockMovement = true) {
                entry = "swarm"
                weightedSelectorRandom {
                    +random(slam, 1, requires = varnIs(AT_WALL, 1))
                }
                forceWhen(Condition.Not(varnIs(AT_WALL, 1)), approach)
            }
        }

    override fun ScriptContext.startup() {
        encounter.register(this)
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { punish(npc, hit) },
        )

        ServerCacheManager.getNpc(BOSS.asRSCM(RSCMType.NPC))?.let { type ->
            onNpcQueue(type, "queue.death") { maggotKingDeath() }
        }

        // BossCombat wipes the wall-fight overrides on a standard respawn; this runs after its
        // reset (unbound handlers fire in registration order) so a respawned king keeps them.
        onEvent<NpcStateEvents.Respawn> {
            if (npc.type.id == BOSS_ID) MaggotKingEncounter.wallFight(npc)
        }
    }

    // Same-tick melee on an extended head: free swing at boosted power (the "Dharok's punish").
    private fun punish(npc: Npc, hit: org.rsmod.game.hit.HitBuilder) {
        if (!encounter.inPunishWindow(npc)) return
        val attacker = hit.playerAttacker() ?: return
        attacker.clearAttackDelay()
        if (
            hit.type == org.rsmod.game.hit.HitType.Melee &&
            hit.damage == 0 &&
            deps.random.of(100) < PUNISH_REROLL
        ) {
            // UNVERIFIED: the real bonus is an 85%-reduced crush defence roll; rerolling a
            // miss into a mid-range hit approximates it without the defence table.
            hit.damage = deps.random.of(1, PUNISH_REROLL_MAX)
        }
    }

    private suspend fun StandardNpcAccess.maggotKingDeath() {
        val respawnType = ServerCacheManager.getNpc(CORPSE.asRSCM(RSCMType.NPC)) ?: return
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        for (seq in DEATH_ANIMS) {
            val type = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)) ?: continue
            anim(seq)
            delay(type)
        }
        spotanim("spotanim.vfx_maggot_king_death_01")
        npcDeath.spawnDrops(this)
        findHero(deps.playerList)
            ?.takeIf { it.shouldDropBrimstoneKey(npc, areaChecker) }
            ?.let { npc.vars[LOOTED] = npc.vars[LOOTED] or KONAR_BIT }
        // A king killed in the nest slumps onto the bone wall: the nest stays sealed, so a
        // corpse left past wall-front would be unlootable.
        val wallFront = npc.spawnCoords.z - (BOSS_SPAWN.z - WALL_FRONT_Z)
        if (npc.coords.z > wallFront) {
            npc.telejump(deps.collision, npc.coords.copy(z = wallFront))
        }
        changeType(respawnType, Int.MAX_VALUE)
        // The death sequence hides all ops and leaves the npc at 0hp; the corpse needs both
        // restored or the client won't offer its loot options.
        showAllOps()
        npc.baseHitpointsLvl = maxOf(1, respawnType.hitpoints)
        npc.hitpoints = npc.baseHitpointsLvl
    }

    private fun org.rsmod.game.hit.HitBuilder.playerAttacker(): org.rsmod.game.entity.Player? =
        if (isFromPlayer) sourceUid?.let { org.rsmod.game.entity.player.PlayerUid(it).resolve(deps.playerList) } else null

    private fun org.rsmod.game.entity.Player.clearAttackDelay() {
        actionDelay = currentMapClock
    }

    companion object {
        private val BOSS_ID = BOSS.asRSCM(RSCMType.NPC)
        private val DEATH_ANIMS =
            listOf(
                "seq.npc_maggot_king_death_01",
                "seq.npc_maggot_king_death_02",
                "seq.npc_maggot_king_death_03",
            )

        private const val MAGIC_RANGED_MAX = 46
        private const val POISON_SEVERITY = 8
        private val SCREECH_HIT = 15..25
        private const val SCREECH_MIN = 4
        private const val SCREECH_MAX = 8
        const val SCREECH_TICKS = 3
        private const val MELEE_PHASE_FRACTION = 0.653
        private const val FINAL_PHASE_FRACTION = 0.203
        private const val PUNISH_REROLL = 85
        private const val PUNISH_REROLL_MAX = 30
    }
}
