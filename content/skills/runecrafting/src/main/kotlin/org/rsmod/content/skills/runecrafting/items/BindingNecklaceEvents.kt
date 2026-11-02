package org.rsmod.content.skills.runecrafting.items

import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn1
import org.rsmod.content.skills.runecrafting.bindingNecklaceCharges
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BindingNecklaceEvents : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld3(BindingNecklace.ITEM) {
            checkCharges()
        }

        onOpHeld4(BindingNecklace.ITEM) {
            destroyForReset(it)
        }

        onOpWorn1(BindingNecklace.ITEM) {
            checkCharges()
        }

        onWearposChange {
            if (wearpos != Wearpos.Front) {
                return@onWearposChange
            }

            val equipped = player.worn[wearpos.slot] ?: return@onWearposChange
            if (!equipped.isType(BindingNecklace.ITEM)) {
                return@onWearposChange
            }

            if (player.bindingNecklaceCharges <= 0) {
                player.bindingNecklaceCharges = BindingNecklace.MAX_CHARGES
            }
        }
    }

    private fun ProtectedAccess.checkCharges() {
        val charges = player.bindingNecklaceCharges.coerceAtLeast(0)
        mes("You have $charges uses left before your necklace disintegrates.")
    }

    private suspend fun ProtectedAccess.destroyForReset(event: HeldObjEvents.Op4) {
        if (inv[event.slot]?.isType(BindingNecklace.ITEM) != true) {
            return
        }

        val type = event.type
        val header = type.param(params.destroy_note_title)
        val text = type.param(params.destroy_note_desc)
        if (!confirmDestroy(BindingNecklace.ITEM, event.obj.count, header, text)) {
            return
        }

        if (invDel(inv, BindingNecklace.ITEM, 1, slot = event.slot).success) {
            player.bindingNecklaceCharges = BindingNecklace.MAX_CHARGES
        }
    }
}
