package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PuroPuroDialogues
@Inject
constructor(private val objRepo: ObjRepository, private val puro: PuroPuroManager) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ELNOCK) { startDialogue(it.npc) { elnockDialogue() } }
        onOpNpc1(IMMENIZZ) { startDialogue(it.npc) { immenizzDialogue() } }
        onOpNpc1(AERYKA) { startDialogue(it.npc) { aerykaDialogue() } }
        onOpNpc1(LOST_IMPLING) { startDialogue(it.npc) { wanderingDialogue() } }
        onOpNpc3(LOST_IMPLING) { checkGates() }
    }

    private suspend fun Dialogue.elnockDialogue() {
        if (access.vars[ELNOCK_VARBIT] == 0) {
            chatPlayer(quiz, "What's a gnome doing here?")
            chatNpc(neutral, "I'm an investigator. I'm watching the implings.")
            chatPlayer(quiz, "Why would you want to do that?")
            chatNpc(
                neutral,
                "My client has asked me to find out where certain missing items " +
                    "have been going.",
            )
            chatPlayer(quiz, "Who is this client?")
            chatNpc(
                neutral,
                "I'm not at liberty to discuss that. Investigator-client " +
                    "confidentiality don't you know.",
            )
            access.vars[ELNOCK_VARBIT] = 1
        } else {
            chatNpc(neutral, "Ah, good day, it's you again. What can I do for you?")
        }
        elnockOptions()
    }

    private suspend fun Dialogue.elnockOptions() {
        while (true) {
            when (
                access.menu(
                    "What do you want to ask?",
                    "Where is this place?",
                    "So what are these implings?",
                    "Can you remind me how to catch implings?",
                    "Can I buy some impling jars?",
                    "Tell me about the magical wheat.",
                    "Nothing, thanks.",
                )
            ) {
                0 -> {
                    chatPlayer(quiz, "Where is this place?")
                    chatNpc(
                        neutral,
                        "The fairies call it Puro-Puro. It seems to be the home " +
                            "plane of the implings.",
                    )
                    chatNpc(
                        neutral,
                        "I don't think these creatures have a name for it. As you " +
                            "can see there isn't a lot else here other than wheat.",
                    )
                }
                1 -> {
                    chatPlayer(quiz, "So, what are these implings?")
                    chatNpc(
                        neutral,
                        "That's a very interesting question. My best guess is that " +
                            "they are relatives to imps, which is why there are imps " +
                            "here as well.",
                    )
                    chatNpc(
                        neutral,
                        "Implings do appear to like collecting objects and, as my " +
                            "clients have noted, have no concept of ownership. However, " +
                            "I do not sense any malicious intent.",
                    )
                    chatNpc(
                        neutral,
                        "It is my observation that they collect things that other " +
                            "creatures want, rather than they want them themselves. It " +
                            "seems to provide them with sustenance.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Can you remind me how to catch implings again?")
                    howToCatch()
                }
                3 -> buyJars()
                4 -> wheatToggle()
                else -> {
                    chatPlayer(neutral, "Nothing, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.howToCatch() {
        chatNpc(
            neutral,
            "Firstly you will need a butterfly net in which to catch them and at " +
                "least one special impling jar to store an impling.",
        )
        chatNpc(
            neutral,
            "You will also require some experience as a Hunter since these " +
                "creatures are elusive. The more immature implings require less " +
                "experience, but some of the rarer implings are extraordinarily " +
                "hard to find and catch.",
        )
        chatNpc(
            neutral,
            "Once you have caught one, you may break the jar open and obtain the " +
                "object the impling is carrying. Alternatively, you may exchange " +
                "certain combinations of jars with me for equipment that may help " +
                "you hunt implings more effectively.",
        )
        chatNpc(
            neutral,
            "Also, beware. Those imps walking around the maze do not like the " +
                "fact that their kindred spirits are being captured and will " +
                "attempt to steal any full jars you have on you, setting the " +
                "implings free.",
        )
        if (access.vars[FREE_STUFF_VARBIT] == 0) {
            chatNpc(
                happy,
                "Here, let me give you some equipment to get you started.",
            )
            access.invAddOrDrop(objRepo, BUTTERFLY_NET, 1)
            access.invAddOrDrop(objRepo, IMPLING_JAR, FREE_JARS)
            access.invAddOrDrop(objRepo, IMPLING_SCROLL, 1)
            access.vars[FREE_STUFF_VARBIT] = 1
        } else if (player.inv.count(IMPLING_SCROLL) == 0) {
            chatNpc(
                neutral,
                "Ah, I notice you don't own an impling collector's scroll. " +
                    "Would you like another?",
            )
            if (choice2("Yes, please.", true, "No, thanks.", false)) {
                access.invAddOrDrop(objRepo, IMPLING_SCROLL, 1)
                chatNpc(
                    neutral,
                    "If you check it whilst in the maze, you will see how many " +
                        "of each impling you have captured.",
                    )
            }
        }
    }

    private suspend fun Dialogue.buyJars() {
        val bought = access.vars[DAILY_JARS_VARBIT]
        val left = DAILY_JAR_LIMIT - bought
        if (left <= 0) {
            chatNpc(
                neutral,
                "I'm afraid that's all the jars I can spare today. Come back " +
                    "tomorrow and I should have more.",
            )
            return
        }
        chatPlayer(quiz, "Can I buy some impling jars?")
        chatNpc(
            neutral,
            "Well I may be able to let you have a few - if it means you will " +
                "start hunting these implings - although I do not have an " +
                "infinite supply. They're $JAR_PRICE coins each and I can only " +
                "sell you $left more today.",
        )
        if (!choice2("Buy an impling jar ($JAR_PRICE coins).", true, "No thanks.", false)) {
            return
        }
        if (player.inv.count(COINS) < JAR_PRICE) {
            chatNpc(angry, "You don't have enough coins.")
            return
        }
        access.invDel(access.inv, COINS, JAR_PRICE)
        access.invAddOrDrop(objRepo, IMPLING_JAR, 1)
        access.vars[DAILY_JARS_VARBIT] = bought + 1
    }

    private suspend fun Dialogue.wheatToggle() {
        chatPlayer(quiz, "Tell me about the magical wheat.")
        chatNpc(
            neutral,
            "The magical wheat is what separates this plane from your own. " +
                "Pushing through it is hard work, but good exercise.",
        )
        val disabled = access.vars[STRENGTH_XP_OFF_VARBIT] == 1
        val enableText =
            if (disabled) {
                "I'd like to gain Strength experience from the wheat again."
            } else {
                "I'd rather not gain Strength experience from the wheat."
            }
        if (choice2(enableText, true, "Never mind.", false)) {
            access.vars[STRENGTH_XP_OFF_VARBIT] = if (disabled) 0 else 1
            chatNpc(
                neutral,
                if (disabled) {
                    "Very well, you'll find the exercise does you good once more."
                } else {
                    "As you wish. Your muscles will get a rest while you push through."
                },
            )
        }
    }

    private suspend fun Dialogue.immenizzDialogue() {
        chatPlayer(quiz, "Who are you? What are you doing here?")
        chatNpc(
            neutral,
            "I am Immenizz, of course. Elnock asked me to keep an eye on things " +
                "down here while he investigates.",
        )
        chatPlayer(quiz, "Is there anything you can tell me about this place?")
        chatNpc(
            neutral,
            "The implings here are forever slipping through the wheat. Push " +
                "through it yourself if you want to reach the centre of the " +
                "maze, but watch out for the defenders.",
        )
        chatPlayer(neutral, "I'll bear that in mind.")
    }

    private suspend fun Dialogue.aerykaDialogue() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(
            happy,
            "I'm Fairy Aeryka. I watch the crop circles for the other fairies. " +
                "Did you know implings slip through them into the maze?",
        )
        chatPlayer(quiz, "Into the maze?")
        chatNpc(
            neutral,
            "Puro-Puro! The implings' home. Step into the crop circle if you " +
                "want to visit - you'll pop out inside the maze.",
        )
        chatPlayer(neutral, "Thanks for the warning.")
    }

    private suspend fun Dialogue.wanderingDialogue() {
        chatNpc(
            neutral,
            "The wandering impling glances at you, then gestures vaguely towards " +
                "the wheat fields of the world.",
        )
    }

    private fun ProtectedAccess.checkGates() {
        val field = puro.currentField
        mes(
            "The wandering impling tells you that the gates currently lead to " +
                "a field near ${field.name}.",
            ChatType.GameMessage,
        )
    }

    companion object {
        const val ELNOCK = "npc.ii_elnock"
        const val IMMENIZZ = "npc.ii_immenizz"
        const val AERYKA = "npc.ii_fairy"
        const val LOST_IMPLING = "npc.ii_lost_impling"

        const val ELNOCK_VARBIT = "varbit.ii_elnock"
        const val FREE_STUFF_VARBIT = "varbit.ii_elnock_given_freestuff"
        const val DAILY_JARS_VARBIT = "varbit.ii_daily_jars_purchased"
        const val STRENGTH_XP_OFF_VARBIT = "varbit.ii_strength_xp_off"

        const val BUTTERFLY_NET = "obj.hunting_butterfly_net"
        const val IMPLING_JAR = "obj.ii_impling_jar"
        const val IMPLING_SCROLL = "obj.ii_impling_scroll"
        const val COINS = "obj.coins"

        const val FREE_JARS = 7
        const val DAILY_JAR_LIMIT = 10
        const val JAR_PRICE = 2000
    }
}
