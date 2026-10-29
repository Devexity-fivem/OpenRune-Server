package org.rsmod.content.minigames.ketrak

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * JalTok-Jad (npc.jad_challenge_jad) — TzHaar-Ket-Rak's Challenges.
 *
 * OSRS: magic and ranged attacks telegraphed by distinct animations so the player
 * can prayer-switch; a stab melee when adjacent that lands every 4 ticks rather
 * than the usual 8. At half health each Jad calls Yt-HurKots that channel heals
 * into it (handled by [KetRakRuns.tickHealers]); they keep healing whenever close
 * enough, even while fighting the player.
 */
class JalTokJad @Inject constructor(deps: BossDeps, private val runs: KetRakRuns) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onCombatTick = { combatTick(npc, it) })

        val healerType =
            ServerCacheManager.getNpc(HEALER.asRSCM(RSCMType.NPC))
                ?: error("Missing npc type: $HEALER")
        onNpcHit(healerType) {
            if (!hit.isFromPlayer) return@onNpcHit
            val attacker = hit.resolvePlayerSource(deps.playerList) ?: return@onNpcHit
            if (!runs.isRunNpc(attacker, npc)) return@onNpcHit
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
    }

    override val spec =
        boss(JAD) {
            stats(attackRate = ATTACK_RATE)

            val melee =
                ability("melee") {
                    anim("seq.jaltokjad_attack_melee")
                    hit {
                        damage((0..MAX_HIT).roll())
                        type(Melee)
                    }
                }

            val magic =
                ability("magic") {
                    anim("seq.jaltokjad_attack_magic")
                    projectile(
                        spotanim = "spotanim.tzhaar_fire_launch_travel",
                        impact = "spotanim.tzhaar_fire_launch_impact",
                        hit = Effect.Hit(damage = (0..MAX_HIT).roll(), type = Magic),
                    )
                }

            val ranged =
                ability("ranged") {
                    anim("seq.jaltokjad_attack_ranged")
                    mapSpotanim("spotanim.tzhaar_rock_smash", at = CurrentTargetTile)
                    hit {
                        damage((0..MAX_HIT).roll())
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

    private companion object {
        private const val JAD = "npc.jad_challenge_jad"
        private const val HEALER = "npc.jad_challenge_healer"

        private const val ATTACK_RATE = 8
        private const val MELEE_ATTACK_RATE = 4
        private const val MAX_HIT = 113
        private const val BOULDER_DELAY = 4
    }
}
