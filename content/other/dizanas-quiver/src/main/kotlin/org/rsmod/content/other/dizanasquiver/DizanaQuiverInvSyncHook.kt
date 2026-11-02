package org.rsmod.content.other.dizanasquiver

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.player.hook.PlayerInvUpdateHook
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

class DizanaQuiverInvSyncHook @Inject constructor() : PlayerInvUpdateHook {
    override fun onInvUpdated(player: Player, inv: Inventory) {
        if (inv === player.worn || inv === RangedAmmunition.quiverAmmoInvOrNull(player)) {
            RangedAmmunition.syncQuiverClient(player)
        }
    }
}
