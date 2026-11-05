package org.rsmod.content.areas.misc.blackknights

import dev.openrune.util.Wearpos
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The guards patrolling the Black Knights' Fortress. */
class FortressGuard : PluginScript() {
    override fun ScriptContext.startup() {
        for (guard in GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { guardChat() } }
        }
    }

    // LostCity fortress_guard.rs2: ununiformed players are turned away, a bronze
    // med helm + iron chainbody disguise is mistaken for a fellow guard.
    private suspend fun Dialogue.guardChat() {
        val uniformed =
            player.worn[Wearpos.Hat.slot]?.isType("obj.bronze_med_helm") == true &&
                player.worn[Wearpos.Torso.slot]?.isType("obj.iron_chainbody") == true
        if (!uniformed) {
            chatNpc(angry, "Get lost. This is private property.")
            return
        }
        chatNpc(angry, "Hey! Get back on duty!")
        chatPlayer(confused, "Uh...")
    }

    private companion object {
        private val GUARDS =
            listOf(
                "npc.fortressguard",
                "npc.fortressguard_01",
                "npc.fortressguard_02",
                "npc.fortressguard_03",
                "npc.fortressguard_04",
            )
    }
}
