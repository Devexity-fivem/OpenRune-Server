package org.rsmod.content.areas.misc.goblinvillage.npcs

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Village soldiers each cheer for their general's armour colour. */
class GoblinSoldiers : PluginScript() {
    override fun ScriptContext.startup() {
        for (soldier in RED_SOLDIERS) {
            onOpNpc1(soldier) { startDialogue(it.npc, red = true) }
        }
        for (soldier in GREEN_SOLDIERS) {
            onOpNpc1(soldier) { startDialogue(it.npc, red = false) }
        }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc, red: Boolean) {
        startDialogue(npc) {
            chatNpc(neutral, "${if (red) "Red" else "Green"} armour best!")
            if (choice2("Why is ${if (red) "red" else "green"} best?", true, "Err, okay.", false)) {
                // Wiki transcript: goblins always give the red answer, even when green-armoured.
                chatPlayer(quiz, "Why is red best?")
                chatNpc(neutral, "Cos General Bentnoze says so, and he bigger than me.")
            } else {
                chatPlayer(neutral, "Err, okay.")
            }
        }
    }

    private companion object {
        private val RED_SOLDIERS =
            listOf(
                "npc.goblin_red_soldier_2",
                "npc.goblin_red_soldier_4",
                "npc.goblin_red_soldier_5",
                "npc.goblin_red_soldier_6",
                "npc.goblin_red_soldier_8",
            )

        private val GREEN_SOLDIERS =
            listOf(
                "npc.goblin_green_soldier_2",
                "npc.goblin_green_soldier_3",
                "npc.goblin_green_soldier_4",
                "npc.goblin_green_soldier_5",
                "npc.goblin_green_soldier_7",
                "npc.goblin_green_soldier_8",
            )
    }
}
