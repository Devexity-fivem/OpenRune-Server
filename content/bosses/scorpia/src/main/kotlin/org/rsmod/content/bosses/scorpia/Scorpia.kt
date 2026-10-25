package org.rsmod.content.bosses.scorpia

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

// OSRS wiki: melee stab (max 16), poisons for 20, and drains 2 prayer points
// per attack (hit or splash) when the target has Protect from Melee active.
// The next attack after she reaches 99 hp summons two guardians that each
// heal her 8 hp every 3 ticks within 3 tiles; a guardian despawns after 15
// seconds without healing her. Guardians spawn once per fight.
class Scorpia @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val guardianMisses = HashMap<Npc, Int>()

    init {
        deps.extensionRegistry.register(GUARDIAN_HEAL) { access, guardian, _, _ ->
            val scorpia = access?.npc ?: return@register
            guardianMisses[guardian] = 0
            scheduleGuardianHeal(scorpia, guardian)
        }
        deps.extensionRegistry.register(PRAYER_DRAIN) { _, _, target, _ ->
            drainProtectedMeleePrayer(target)
        }
    }

    override val spec: BossSpec = boss("npc.scorpia") {
        stats(attackRate = 4)

        val stab =
            ability("stab") {
                anim("seq.scorpion_update_attack_tail")
                include(external(PRAYER_DRAIN))
                hit {
                    damage(0..16).roll()
                    type(Melee)
                }
                // No published NPC proc rate; 1/4 matches the documented melee weapon-poison
                // chance. Ability-level so it can apply on misses and through prayers, per the
                // wiki's NPC-poison rule.
                poison(20, chance = 1, outOf = 4)
            }

        val summonGuardians =
            ability("summon_guardians") {
                summon(
                    "npc.scorpia_guardian",
                    count = 2,
                    radius = 5,
                    owned = true,
                    onSummon = GUARDIAN_HEAL,
                )
            }

        phase("combat") {
            // Once per fight: her next attack after falling below half health summons the
            // guardians. forceWhen runs through the attack loop so `once` is actually honoured.
            forceWhen(Condition.HpBelow(0.5), summonGuardians, once = true)
            weightedSelectorRandom { +random(stab, weight = 1) }
        }
    }

    private fun scheduleGuardianHeal(scorpia: Npc, guardian: Npc) {
        deps.worldQueues.add(GUARDIAN_HEAL_TICKS) {
            if (!guardian.isSlotAssigned || guardian.hitpoints <= 0) {
                guardianMisses.remove(guardian)
                return@add
            }
            if (!scorpia.isSlotAssigned || scorpia.hitpoints <= 0) {
                removeGuardian(guardian)
                return@add
            }
            if (guardian.coords.chebyshevDistance(scorpia.coords) <= GUARDIAN_HEAL_RANGE) {
                guardianMisses[guardian] = 0
                deps.bossProjectile(
                    spotanim = HEAL_PROJECTILE,
                    src = guardian.coords,
                    target = scorpia.coords,
                    startHeight = 43,
                    endHeight = 35,
                    delay = 0,
                    travel = 2,
                    curve = 5,
                )
                scorpia.heal(GUARDIAN_HEAL_AMOUNT, showHitsplat = true)
            } else {
                val misses = (guardianMisses[guardian] ?: 0) + 1
                if (misses * GUARDIAN_HEAL_TICKS >= GUARDIAN_DESPAWN_TICKS) {
                    removeGuardian(guardian)
                    return@add
                }
                guardianMisses[guardian] = misses
            }
            scheduleGuardianHeal(scorpia, guardian)
        }
    }

    private fun removeGuardian(guardian: Npc) {
        guardianMisses.remove(guardian)
        if (guardian.isSlotAssigned) deps.npcRepo.del(guardian, Int.MAX_VALUE)
    }

    private fun drainProtectedMeleePrayer(target: Player) {
        if (target.vars["varbit.prayer_protectfrommelee"] <= 0) return
        val drain = minOf(PRAYER_DRAIN_AMOUNT, target.prayerLvl)
        if (drain > 0) target.statSub("stat.prayer", constant = drain, percent = 0)
    }

    private companion object {
        const val GUARDIAN_HEAL = "scorpia.guardian_heal"
        const val PRAYER_DRAIN = "scorpia.prayer_drain"

        const val GUARDIAN_HEAL_TICKS = 3
        const val GUARDIAN_HEAL_AMOUNT = 8
        const val GUARDIAN_HEAL_RANGE = 3
        const val GUARDIAN_DESPAWN_TICKS = 25
        const val PRAYER_DRAIN_AMOUNT = 2

        val HEAL_PROJECTILE = "spotanim.curse_travel".asRSCM(RSCMType.SPOTANIM)
    }
}
