package org.rsmod.content.areas.misc.apeatoll

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

internal class GreegreeAttackBlock @Inject constructor() : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val obj = player.worn[Wearpos.RightHand.slot] ?: return NpcAttackValidateResult.Pass
        val type = getInvObj(obj)
        if (type.paramOrNull(params.greegree) == null) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("You can't attack in monkey form.")
    }
}
