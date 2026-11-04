package org.rsmod.content.quest.area.draynor.vampyreslayer

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Vampyre Slayer dialogue shared between the quest script and the Draynor Village ambient
 * scripts. Morgan delegates here while the quest is incomplete.
 */
object VampyreSlayerDialogue {

    private val quest
        get() = checkNotNull(Quest.get(VampyreSlayer.KEY)) { "quest not registered" }

    private var Player.givenBeer by intVarBit("varbit.harlow_given_beer")
    private var Player.givenStake by intVarBit("varbit.harlow_given_stake")

    suspend fun ProtectedAccess.morganDialogue(npc: Npc) = startDialogue(npc) { morgan() }

    suspend fun ProtectedAccess.harlowDialogue(npc: Npc) = startDialogue(npc) { harlow() }

    suspend fun ProtectedAccess.harlowBeerHandIn(npc: Npc) {
        if (!quest.isQuestInProgress(player) || player.givenBeer != 0) {
            startDialogue(npc) {
                chatNpc(drunk, "Thanks, but I've had enough to drink already.")
            }
            return
        }
        if (invDel(inv, Beer).success) {
            player.givenBeer = 1
            startDialogue(npc) { harlowAdvice() }
        }
    }

    suspend fun Dialogue.morgan() {
        when {
            quest.isQuestNotStarted(player) -> {
                chatNpc(worried, "Please, please help us, bold adventurer!")
                chatPlayer(quiz, "What's the matter?")
                chatNpc(
                    worried,
                    "Our village is plagued by a vampyre! He lives in the basement " +
                        "of the manor to the north. We need someone to get rid of him!",
                )
                val choice = choice2("Ok, I'm up for an adventure.", 1, "No, vampyres are scary!", 2)
                if (choice == 2) {
                    chatPlayer(worried, "No, vampyres are scary!")
                    chatNpc(sad, "I don't blame you.")
                    return
                }
                chatPlayer(happy, "Ok, I'm up for an adventure.")
                chatNpc(
                    neutral,
                    "I think you will need to seek help with that task. I believe a " +
                        "retired vampire hunter lives in the Blue Moon Inn in Varrock. " +
                        "His name is Dr Harlow. He may be able to teach you how to kill the fiend.",
                )
                chatNpc(
                    neutral,
                    "In the meantime, feel free to search my house for supplies. " +
                        "There's a cupboard upstairs where I keep garlic - garlic is " +
                        "dangerous for vampyres.",
                )
                quest.setQuestStage(access, STAGE_STARTED)
            }
            else -> {
                chatNpc(worried, "Please hurry and slay that vampyre!")
                chatPlayer(
                    quiz,
                    "Remind me, what should I be doing?",
                )
                chatNpc(
                    worried,
                    "Seek out Dr Harlow in the Blue Moon Inn in Varrock. He knows " +
                        "how to kill vampyres. And don't forget the garlic - the cupboard " +
                        "upstairs keeps some.",
                )
            }
        }
    }

    private suspend fun Dialogue.harlow() {
        when {
            quest.isQuestCompleted(player) -> {
                chatPlayer(happy, "I killed the vampyre!")
                chatNpc(drunk, "Well done! Have another drink on me.")
            }
            !quest.isQuestInProgress(player) -> {
                chatNpc(drunk, "Buy me a beer and I'll tell you anything you want to know.")
            }
            player.givenBeer == 0 -> {
                chatPlayer(neutral, "Morgan said you could teach me how to slay a vampyre.")
                chatNpc(
                    drunk,
                    "A vampyre? I used to hunt those, back in the day. I'll tell " +
                        "you what you need to know... but it'll cost you a beer.",
                )
                if (invTotal(access.inv, Beer) > 0) {
                    chatPlayer(happy, "Here you go.")
                    chatNpc(drunk, "Cheers!")
                    if (access.invDel(access.inv, Beer).success) {
                        player.givenBeer = 1
                        harlowAdvice()
                    }
                } else {
                    chatNpc(drunk, "Buy me a drink from the bar and we'll talk.")
                }
            }
            else -> harlowAdvice()
        }
    }

    private suspend fun Dialogue.harlowAdvice() {
        chatNpc(
            neutral,
            "To kill a vampyre you must drive a stake through its heart, and " +
                "you'll need a hammer to drive it in.",
        )
        if (player.givenStake == 0 && invTotal(access.inv, Stake) == 0) {
            chatNpc(drunk, "Here, take this stake. I don't need it any more.")
            if (access.invAdd(access.inv, Stake).success) {
                player.givenStake = 1
            }
        }
        chatNpc(
            drunk,
            "And bring garlic with you. Vampyres hate the stuff - it'll weaken " +
                "him. Now go, before he finishes off the whole village!",
        )
        if (quest.isQuestInProgress(player)) {
            quest.setQuestStage(access, STAGE_SPOKE_TO_HARLOW)
        }
    }

    private const val Beer = "obj.beer"
    private const val Stake = "obj.stake"

    private const val STAGE_STARTED = 1
    private const val STAGE_SPOKE_TO_HARLOW = 2
}
