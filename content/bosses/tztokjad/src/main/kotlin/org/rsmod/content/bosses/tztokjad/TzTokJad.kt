package org.rsmod.content.bosses.tztokjad

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.spawnOwnedNpc
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.ScriptContext

/**
 * TzTok-Jad (npc.tzhaar_fightcave_swarm_boss).
 *
 * OSRS: magic and ranged attacks from up to 15 tiles away, telegraphed by distinct
 * animations so players can prayer-switch; instant melee bite when adjacent — and melee
 * attacks land every 4 ticks rather than the usual 8. Below 150 hp Jad summons four
 * Yt-HurKot that channel heals into him; they keep healing whenever close enough, even
 * while fighting the player. Healers killed before Jad is healed to full never respawn;
 * if the wave does heal him to full, dead healers return on the next crossing.
 */
class TzTokJad @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val healerWaves = IdentityHashMap<Npc, WaveState>()
    private val healers = IdentityHashMap<Npc, MutableList<Npc>>()

    private class WaveState {
        var crossed = false
        var everSpawned = false
        var reachedFull = false
        var nextHealTick = 0
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onCombatTick = { combatTick(npc, it) })
        BossCombat.register(this, healerSpec, deps)

        healSpot = SpotanimType(HEAL_SPOT.asRSCM(RSCMType.SPOTANIM))

        onEvent<NpcStateEvents.Spawn>(JAD.asRSCM(RSCMType.NPC).toLong()) {
            npc.apRangeOverride = ATTACK_RANGE
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.type.id == JAD.asRSCM(RSCMType.NPC)) {
                healerWaves.remove(npc)
                healers.remove(npc)
            }
        }

        val healerType =
            ServerCacheManager.getNpc(HEALER.asRSCM(RSCMType.NPC))
                ?: error("Missing npc type: $HEALER")
        onNpcHit(healerType) {
            if (!hit.isFromPlayer) return@onNpcHit
            val attacker = hit.resolvePlayerSource(deps.playerList) ?: return@onNpcHit
            if (npc.isSlotAssigned && npc.hitpoints > 0) {
                npc.mode = NpcMode.ApPlayer2
                npc.assignSpawnOwner(attacker, deps.mapClock.cycle)
                npc.apPlayer2(attacker, deps.playerInteractions)
            }
        }
    }

    private fun combatTick(boss: Npc, target: Player) {
        deps.encounter(boss).attackRateOverride =
            if (boss.isWithinDistance(target, 1)) MELEE_ATTACK_RATE else ATTACK_RATE
        tickHealers(boss)
    }

    private fun tickHealers(boss: Npc) {
        val wave = healerWaves.getOrPut(boss) { WaveState() }
        val living = healers[boss]?.filter { it.isSlotAssigned && it.hitpoints > 0 }.orEmpty()

        if (boss.hitpoints >= boss.baseHitpointsLvl && living.isNotEmpty()) {
            wave.reachedFull = true
        }
        if (boss.hitpoints > HEALER_THRESHOLD) {
            wave.crossed = false
        } else if (!wave.crossed) {
            wave.crossed = true
            if (living.isEmpty() && (!wave.everSpawned || wave.reachedFull)) {
                wave.everSpawned = true
                wave.reachedFull = false
                spawnHealers(boss)
            }
        }

        val now = deps.mapClock.cycle
        if (now < wave.nextHealTick) return
        wave.nextHealTick = now + HEAL_INTERVAL
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        for (healer in living) {
            if (healer.coords.chebyshevDistance(centre) > HEAL_RANGE) continue
            boss.heal(HEAL_MIN + deps.random.of(HEAL_ROLL), showHitsplat = true)
            healer.anim(HEAL_SEQ, 0, 0)
            deps.worldRepo.spotanimMap(healSpot, centre)
        }
    }

    private fun spawnHealers(boss: Npc) {
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        for ((dx, dz) in HEALER_OFFSETS) {
            val healer = deps.spawnOwnedNpc(boss, HEALER, centre.translate(dx, dz)) ?: continue
            healer.mode = NpcMode.None
            healers.getOrPut(boss) { mutableListOf() } += healer
        }
    }

    private var healSpot: SpotanimType = SpotanimType(0)

    override val spec =
        boss(JAD) {
            stats(attackRate = ATTACK_RATE)

            val melee =
                ability("melee") {
                    anim("seq.lordmagmus_attack")
                    hit {
                        damage((0..MELEE_MAX).roll())
                        type(Melee)
                    }
                }

            val magic =
                ability("magic") {
                    anim("seq.lordmagmus_fire")
                    projectile(
                        spotanim = "spotanim.tzhaar_fire_launch_travel",
                        impact = "spotanim.tzhaar_fire_launch_impact",
                        hit = Effect.Hit(damage = (0..MAGIC_MAX).roll(), type = Magic),
                    )
                }

            val ranged =
                ability("ranged") {
                    anim("seq.lordmagmus_smash")
                    mapSpotanim("spotanim.tzhaar_rock_smash", at = CurrentTargetTile)
                    hit {
                        damage((0..RANGED_MAX).roll())
                        type(Ranged)
                        delay = BOULDER_DELAY
                    }
                }

            phase("combat") {
                attackRate = ATTACK_RATE
                weightedSelectorRandom {
                    +random(melee, weight = 4, requires = WithinMeleeRange)
                    +random(magic, weight = 3)
                    +random(ranged, weight = 3)
                }
            }
        }

    private val healerSpec =
        boss(HEALER) {
            stats(attackRate = HEALER_ATTACK_RATE)

            val melee =
                ability("melee") {
                    anim("seq.lizard_cleric_attack")
                    hit {
                        damage((0..HEALER_MAX).roll())
                        type(Melee)
                    }
                }

            phase("combat") {
                weightedSelectorRandom { +random(melee, weight = 1) }
            }
        }

    private companion object {
        private const val JAD = "npc.tzhaar_fightcave_swarm_boss"
        private const val HEALER = "npc.tzhaar_fightcave_swarm_boss_cleric"

        private const val HEAL_SPOT = "spotanim.tzhaar_heal"
        private const val HEAL_SEQ = "seq.lizard_cleric_heal"

        private const val ATTACK_RANGE = 15
        private const val ATTACK_RATE = 8
        private const val MELEE_ATTACK_RATE = 4

        private const val MELEE_MAX = 97
        private const val RANGED_MAX = 97
        private const val MAGIC_MAX = 95
        private const val BOULDER_DELAY = 60

        private const val HEALER_THRESHOLD = 150
        private const val HEALER_COUNT = 4
        private const val HEALER_ATTACK_RATE = 4
        private const val HEALER_MAX = 14
        private const val HEAL_INTERVAL = 4
        private const val HEAL_MIN = 1
        private const val HEAL_ROLL = 2
        private const val HEAL_RANGE = 3

        private val HEALER_OFFSETS = listOf(0 to 5, 5 to 0, 0 to -5, -5 to 0)
    }
}
