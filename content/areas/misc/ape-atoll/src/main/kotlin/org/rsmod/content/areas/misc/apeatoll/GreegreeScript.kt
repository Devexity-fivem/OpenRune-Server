package org.rsmod.content.areas.misc.apeatoll

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GreegreeScript @Inject constructor(
    private val areaChecker: AreaChecker,
    private val unequipOp: WornUnequipOp,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (greegree in GREEGREES) {
            onOpHeld1(greegree) { holdGreegree(it.slot, it.inventory) }
            onOpHeld2(greegree) { holdGreegree(it.slot, it.inventory) }
        }
        onPlayerCoordsChanged { releaseGreegree(player) }
    }

    private fun ProtectedAccess.holdGreegree(slot: Int, inventory: Inventory) {
        if (isInCombat()) {
            mes("You can't do that in combat.")
            return
        }
        if (!areaChecker.inArea(APE_ATOLL, player.coords)) {
            mes("The greegree only works on Ape Atoll.")
            return
        }
        invEquip(slot, inventory)
    }

    private fun releaseGreegree(player: Player) {
        val obj = player.worn[Wearpos.RightHand.slot] ?: return
        if (getInvObj(obj).paramOrNull(params.greegree) == null) {
            return
        }
        if (areaChecker.inArea(APE_ATOLL, player.coords)) {
            return
        }
        unequipOp.unequip(player, Wearpos.RightHand.slot, player.worn, player.inv)
    }

    private companion object {
        const val APE_ATOLL = "area.ape_atoll"

        val GREEGREES =
            listOf(
                "obj.mm2_kruk_greegree",
                "obj.mm_monkey_greegree_for_ancient_monkey_skull",
                "obj.mm_monkey_greegree_for_bearded_gorilla",
                "obj.mm_monkey_greegree_for_large_zombie_monkey",
                "obj.mm_monkey_greegree_for_medium_ninja_monkey",
                "obj.mm_monkey_greegree_for_normal_gorilla",
                "obj.mm_monkey_greegree_for_normal_monkey",
                "obj.mm_monkey_greegree_for_small_ninja_monkey",
                "obj.mm_monkey_greegree_for_small_zombie_monkey",
            )
    }
}
