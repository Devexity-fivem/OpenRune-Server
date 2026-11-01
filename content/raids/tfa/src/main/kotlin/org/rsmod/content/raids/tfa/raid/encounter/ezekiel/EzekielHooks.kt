package org.rsmod.content.raids.tfa.raid.encounter.ezekiel

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

internal val EZEKIEL_ID by lazy { "npc.tfa_ezekiel".asRSCM(RSCMType.NPC) }

internal val EZEKIEL_HELPERS by lazy {
    listOf(
            "npc.tfa_ezekiel_sword",
            "npc.tfa_ezekiel_sword_noop",
            "npc.tfa_ezekiel_finale_vfx",
            "npc.tfa_ezekiel_death_sword",
        )
        .map { it.asRSCM(RSCMType.NPC) }
        .toSet()
}

/** Keeps Ezekiel and his swords at 1 hp so the spec, not the hit, decides when each dies. */
internal class EzekielHpFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int =
        if (npc.visType.id == EZEKIEL_ID || npc.visType.id in EZEKIEL_HELPERS) 1 else 0
}

/**
 * Swords are born with [Npc.ignoreCombatInteractions] so they never retaliate; Ezekiel is retired
 * the same way once he is dead. The inert fence/visual swords can't be attacked at all.
 */
internal class EzekielAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val id = npc.visType.id
        val dead =
            when {
                id == EZEKIEL_ID -> npc.ignoreCombatInteractions
                id == "npc.tfa_ezekiel_sword_noop".asRSCM(RSCMType.NPC) ||
                    id == "npc.tfa_ezekiel_finale_vfx".asRSCM(RSCMType.NPC) ||
                    id == "npc.tfa_ezekiel_death_sword".asRSCM(RSCMType.NPC) -> true
                else -> false
            }
        return if (dead) {
            NpcAttackValidateResult.Deny("It's already dead.")
        } else {
            NpcAttackValidateResult.Pass
        }
    }
}
