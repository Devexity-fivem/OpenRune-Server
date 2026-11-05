package org.rsmod.content.areas.city.varrock.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixBookRead
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixHasBook
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixSeekBook
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.ShieldLeft
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.ShieldRight
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.soaStage
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO(content): Correct mesanims. The dialogue is from the wiki transcript.
class Reldo
@Inject
constructor(
    private val knightsSword: KnightsSwordQuest,
    private val shieldOfArrav: ShieldOfArrav,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.reldo") { startDialogue(it.npc) { reldo() } }
    }

    // TODO(content): [quest:the_lost_tribe] brooch dialogue
    // TODO(content): [quest:a_tail_of_two_cats] Robert the Strong dialogue
    // TODO(content): [quest:dragon_slayer_ii] Tristan Corvo and the census records
    // TODO(content): [system:wise_old_man_tasks] delivering the Wise Old Man's message
    // TODO(content): [system:treasure_trails] beginner hot and cold clue
    private suspend fun Dialogue.reldo() {
        val soaNotStarted = player.phoenixGang == 0 && player.soaStage() == 0
        if (vars[RELDO_MET] == 0 && soaNotStarted) {
            chatNpc(neutral, "Hello, stranger.")
            chatPlayer(
                quiz,
                "Hello there! The name's ${player.displayName}. Who are you?",
            )
            chatNpc(neutral, "My name is Reldo. I'm the palace librarian.")
            chatPlayer(neutral, "Ah, that explains why you're in the library then.")
            chatNpc(
                neutral,
                "Well, truth be told, I would probably be here even if it wasn't where I " +
                    "worked. I very much like reading, not to mention sharing what I learn with " +
                    "all who are interested.",
            )
            vars[RELDO_MET] = 1
            chatPlayer(neutral, "Sounds like a very noble goal!")
        } else {
            chatNpc(happy, "Hello there, ${player.displayName}.")
        }
        val imcando =
            knightsSword.stage(player) in
                KnightsSwordQuest.STAGE_STARTED until KnightsSwordQuest.STAGE_COMPLETE ||
                QuestRequirements.isOnQuest(player, GIANT_DWARF)
        val hasShieldHalf = access.inv.contains(ShieldLeft) || access.inv.contains(ShieldRight)
        val option =
            menu(
                buildList {
                    when {
                        soaNotStarted -> add("I'm in search of a quest." to 1)
                        player.phoenixGang == PhoenixSeekBook ->
                            add("About that book... Where is it again?" to 5)
                        player.phoenixGang == PhoenixHasBook -> add("I found that book." to 6)
                        player.phoenixGang == PhoenixBookRead ->
                            add("Okay, I've read that book about the Shield of Arrav." to 7)
                    }
                    if (hasShieldHalf) add("I've found half of the Shield of Arrav!" to 8)
                    if (imcando) add("What do you know about the Imcando dwarves?" to 4)
                    add("Do you have anything to trade?" to 2)
                    add("I'd better get going." to 3)
                }
            )
        when (option) {
            4 -> reldoImcando()
            5 -> {
                chatPlayer(quiz, "About that book... Where is it again?")
                chatNpc(
                    neutral,
                    "I'm not sure exactly... but I'm sure it's around here somewhere.",
                )
            }
            6 -> {
                chatPlayer(neutral, "I found that book.")
                chatNpc(happy, "Very good. I suggest you give it a read.")
            }
            7 -> reldoShieldOfArravRead()
            8 -> reldoShieldHalf()
            1 -> {
                chatPlayer(quiz, "I'm in search of a quest.")
                chatNpc(neutral, "Hmm... I don't believe there are any here.")
                chatNpc(neutral, "Let me think actually...")
                chatNpc(
                    happy,
                    "Ah, yes. I think I have something, if you're definitely interested?",
                )
                if (player.combatLevel < MinCombatLevel) {
                    mesbox(
                        "Before starting this quest, be aware that you combat level is " +
                            "lower than the recommended level of $MinCombatLevel."
                    )
                }
                if (!choice2("Yes.", true, "No.", false, title = "Start the Shield of Arrav quest?")) {
                    chatPlayer(neutral, "Actually, I don't think I am. Goodbye.")
                    return
                }
                chatPlayer(happy, "Of course.")
                shieldOfArrav.quest.setQuestStage(access, 1)
                player.phoenixGang = PhoenixSeekBook
                chatNpc(
                    neutral,
                    "Well, if you look around for book called The Shield of Arrav, " +
                        "you'll find a quest in there.",
                )
                chatNpc(
                    neutral,
                    "I'm not sure where the book is mind you... but I'm sure it's " +
                        "around here somewhere.",
                )
                chatPlayer(happy, "Great! Thank you.")
            }
            2 -> {
                chatPlayer(quiz, "Do you have anything to trade?")
                chatNpc(happy, "Only knowledge.")
                chatPlayer(quiz, "How much do you want for that then?")
                chatNpc(laugh, "No, sorry, that was just my little joke. I'm not the trading type.")
                chatPlayer(sad, "Ah well.")
            }
            3 -> {
                chatPlayer(neutral, "I'd better get going.")
                chatNpc(happy, "Until next time.")
            }
        }
    }

    private suspend fun Dialogue.reldoImcando() {
        chatPlayer(quiz, "What do you know about the Imcando dwarves?")
        chatNpc(neutral, "The Imcando dwarves, you say? Let's see...")
        chatNpc(
            neutral,
            "Ah, yes... For many hundreds of years, they were the world's most skilled smiths. " +
                "They used secret smithing knowledge passed down from generation to generation.",
        )
        chatNpc(
            neutral,
            "Unfortunately, about a century ago, the once thriving group was wiped out during " +
                "the barbarian invasions of that time.",
        )
        chatNpc(
            neutral,
            "I believe a few of them survived, but with the bulk of their population destroyed, " +
                "their numbers dwindled even further. If any are left now, they'll no doubt keep " +
                "to themselves.",
        )
        chatNpc(
            shifty,
            "However, I have heard tales of their love for redberry pie. Perhaps one of those " +
                "could convince one to reveal their true heritage.",
        )
        chatPlayer(quiz, "So I just go around offering random dwarves redberry pie?")
        with(knightsSword) { access.learnAboutImcando() }
        chatNpc(
            neutral,
            "Well, not totally random. Apparently some of the surviving Imcando made a home for " +
                "themselves near the cliffs on the Asgarnian southern peninsula. That would be a " +
                "good place to start.",
        )
    }

    private suspend fun Dialogue.reldoShieldOfArravRead() {
        chatPlayer(neutral, "Okay, I've read that book about the Shield of Arrav.")
        chatNpc(happy, "Then perhaps you now have your quest?")
        chatPlayer(
            quiz,
            "I think I do. Do you know where I can find the Phoenix Gang or the " +
                "Black Arm Gang?",
        )
        chatNpc(
            neutral,
            "No, I don't. However, I hear Baraek, the fur trader in the market, " +
                "has had connections with the Phoenix Gang.",
        )
        chatPlayer(quiz, "And the Black Arm Gang?")
        chatNpc(
            neutral,
            "There are rumours that they are based in the south west corner of " +
                "the city. Charlie the Tramp might be able to tell you more. He " +
                "knows that part of Varrock well.",
        )
        chatPlayer(happy, "Thanks! I'll get to it!")
        chatNpc(happy, "Good luck.")
    }

    private suspend fun Dialogue.reldoShieldHalf() {
        chatPlayer(happy, "I've found half of the Shield of Arrav!")
        chatNpc(quiz, "You have? Which gang did you get it from?")
        if (access.inv.contains(ShieldLeft)) {
            chatPlayer(
                happy,
                "The Phoenix Gang had it. I managed to infiltrate their gang and " +
                    "steal it from them.",
            )
            chatNpc(
                neutral,
                "The Phoenix Gang? I'm impressed. I heard only their most " +
                    "trusted members know where they keep the Shield.",
            )
            chatPlayer(
                neutral,
                "Well, I don't think I'll have much luck infiltrating the " +
                    "Black Arm Gang now that I've joined their rivals, but maybe " +
                    "someone else will be able to.",
            )
        } else {
            chatPlayer(
                happy,
                "The Black Arm Gang had it. I managed to infiltrate their gang " +
                    "and steal it from them.",
            )
            chatNpc(
                neutral,
                "The Black Arm Gang? I'm impressed. I heard only their most " +
                    "trusted members know where they keep the Shield.",
            )
            chatPlayer(
                neutral,
                "Well, I did have a member of the Phoenix Gang help me... " +
                    "Maybe they can find the other half.",
            )
        }
        chatNpc(
            neutral,
            "Let's hope so. In the meantime, you should probably head over to " +
                "the museum so they can verify the authenticity of this half.",
        )
    }

    private companion object {
        const val RELDO_MET = "varbit.reldo_met"
        const val SHIELD_OF_ARRAV = "quest_shieldofarrav"
        const val GIANT_DWARF = "quest_giantdwarf"
        const val MinCombatLevel = 10
    }
}
