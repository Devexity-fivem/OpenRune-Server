package org.rsmod.content.minigames.chompyhunting

import dev.openrune.ServerCacheManager
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType

internal class ChompyAttackGate @Inject constructor() : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!npc.type.isType(CHOMPY)) {
            return NpcAttackValidateResult.Pass
        }
        val weapon = player.worn[Wearpos.RightHand.slot]
        if (weapon == null || (!weapon.isType(OGRE_BOW) && !weapon.isType(COMP_OGRE_BOW))) {
            return NpcAttackValidateResult.Deny(DENY_BOW_MESSAGE)
        }
        val quiver = player.worn[Wearpos.Quiver.slot]?.let { ServerCacheManager.getItem(it.id) }
        if (quiver == null || !quiver.isCategoryType(OGRE_ARROWS)) {
            return NpcAttackValidateResult.Deny(DENY_ARROW_MESSAGE)
        }
        return NpcAttackValidateResult.Pass
    }

    private companion object {
        const val CHOMPY = "npc.chompybird"
        const val OGRE_BOW = "obj.ogre_bow"
        const val COMP_OGRE_BOW = "obj.zogre_bow"
        const val OGRE_ARROWS = "category.ogre_arrows"
        const val DENY_BOW_MESSAGE = "You need an ogre or comp ogre bow to hunt chompy birds."
        const val DENY_ARROW_MESSAGE = "The bird is too quick for those arrows. You need ogre arrows."
    }
}
