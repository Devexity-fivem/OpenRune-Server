package org.rsmod.content.areas.city.varrock.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.spam
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BlueMoonBartender @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.bluemoon_bartender") { startDialogue(it.npc) { bartender() } }
    }

    private suspend fun Dialogue.bartender() {
        chatNpc(happy, "Welcome to the Blue Moon Inn. What can I do for you?")
        val option =
            choice3(
                "I'll have a beer please.",
                1,
                "Can you tell me about Dr Harlow?",
                2,
                "Nothing, I'm fine.",
                3,
            )
        when (option) {
            1 -> requestBeer()
            2 -> requestHarlow()
            3 -> nothing()
        }
    }

    private suspend fun Dialogue.requestBeer() {
        chatPlayer(happy, "I'll have a beer please.")
        chatNpc(happy, "That'll be two coins please.")
        if (!player.invTakeFee(fee = 2)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
        } else {
            player.spam("You buy a pint of beer.")
            player.invAddOrDrop(objRepo, "obj.beer")
        }
    }

    private suspend fun Dialogue.requestHarlow() {
        chatPlayer(quiz, "Can you tell me about Dr Harlow?")
        chatNpc(
            neutral,
            "The old man with the beer belly? He used to be a vampyre hunter " +
                "of some repute. These days he mostly just drinks. You'll find him " +
                "in the corner - good luck getting anything sensible out of him.",
        )
    }

    private suspend fun Dialogue.nothing() {
        chatPlayer(neutral, "Nothing, I'm fine.")
    }
}
