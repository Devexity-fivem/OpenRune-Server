package org.rsmod.content.raids.tfa.raid.encounter.moia

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.hit.NpcHitpointsFloorHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

internal val MOIA_ID by lazy { "npc.tfa_moia".asRSCM(RSCMType.NPC) }

/** Moia stays at 1 hp until her spec runs the death sequence; binders die normally. */
internal class MoiaHpFloor : NpcHitpointsFloorHook {
    override fun floor(npc: Npc, hit: Hit): Int = if (npc.visType.id == MOIA_ID) 1 else 0
}

/** Once Moia retires (dead) she can't be attacked again. */
internal class MoiaAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.visType.id == MOIA_ID && npc.ignoreCombatInteractions) {
            NpcAttackValidateResult.Deny("It's already dead.")
        } else {
            NpcAttackValidateResult.Pass
        }
}
