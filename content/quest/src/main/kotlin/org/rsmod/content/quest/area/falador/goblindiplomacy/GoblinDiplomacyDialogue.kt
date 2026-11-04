package org.rsmod.content.quest.area.falador.goblindiplomacy

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MesAnimType
import kotlin.random.Random
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Goblin Diplomacy dialogue shared between the quest and the goblin-village ambient scripts.
 * The village op1 handlers delegate here while the quest is incomplete, and fall back to
 * ambient banter once it is done.
 */
object GoblinDiplomacyDialogue {

    private val quest
        get() = checkNotNull(Quest.get(GoblinDiplomacy.KEY)) { "quest not registered" }

    suspend fun ProtectedAccess.generalsDialogue(npc: Npc) = startDialogue(npc) { generals() }

    suspend fun ProtectedAccess.grubfootDialogue(npc: Npc) = startDialogue(npc) { grubfoot() }

    suspend fun ProtectedAccess.useMailDialogue(npc: Npc, mail: String, grubfootVis: Int) {
        val wanted = wantedMail(player)
        if (wanted == null || wanted.first != mail) {
            startDialogue(npc) { wrongColourDialogue() }
            return
        }
        startDialogue(npc) {
            wartface(neutral, "Grubfoot!")
            grubfootSpecific(neutral, "Yes General Wartface?")
            wartface(neutral, "Put on this armour!")
            if (!takeMail(mail)) return@startDialogue
            VarPlayerIntMapSetter.set(player, GRUBFOOT_VIS_VARBIT, grubfootVis)
            grubfootSpecific(neutral, "What do you think?")
            when (grubfootVis) {
                VIS_ORANGE -> {
                    wartface(neutral, "No I don't like that much.")
                    bentnoze(neutral, "It clashes with skin colour.")
                    wartface(neutral, "We need darker colour, like blue.")
                    bentnoze(neutral, "Yeah blue might be good.")
                    wartface(neutral, "Human! Get us blue armour!")
                    quest.setQuestStage(access, STAGE_GAVE_ORANGE)
                }
                VIS_BLUE -> {
                    bentnoze(neutral, "That not right. Not goblin colour at all.")
                    wartface(neutral, "Goblins wear dark earthy colours like brown.")
                    bentnoze(neutral, "Yeah brown might be good.")
                    wartface(neutral, "Human! Get us brown armour!")
                    chatPlayer(
                        quiz,
                        "But I thought brown was the armour you were changing from... " +
                            "Never mind, anything is worth a try.",
                    )
                    quest.setQuestStage(access, STAGE_GAVE_BLUE)
                }
                else -> {
                    wartface(neutral, "That colour quite nice. Me can see myself wearing that.")
                    bentnoze(neutral, "It a deal then. Brown armour it is.")
                    wartface(neutral, "Thank you for sorting out argument, human. You have reward now.")
                    chatPlayer(neutral, "Thanks... I think...")
                    quest.completeQuest(access)
                }
            }
        }
    }

    suspend fun ProtectedAccess.wrongColourDialogue(npc: Npc) {
        startDialogue(npc) { wrongColourDialogue() }
    }

    private suspend fun Dialogue.generals() {
        if (quest.isQuestCompleted(player)) {
            wartface(neutral, "Now you've solved our argument we gotta think of something else to do.")
            bentnoze(neutral, "Yep, we bored now.")
            return
        }
        val wanted = wantedMail(player)
        if (wanted != null) {
            chatNpc(neutral, "Oh it you.")
            if (player.inv.count(wanted.first) > 0) {
                chatPlayer(neutral, "I have some ${wanted.second} armour.")
                chatNpc(neutral, "Then show us it, human!")
            } else {
                chatNpc(neutral, "Have you got some ${wanted.second} goblin armour yet?")
                chatPlayer(bored, "Err, no.")
                chatNpc(neutral, "Come back when you have some.")
            }
            return
        }
        when (Random.nextInt(5)) {
            0 -> {
                bentnoze(neutral, "All goblins should wear red armour!")
                wartface(neutral, "Not red! Red armour make you look fat.")
                bentnoze(neutral, "Everything make YOU look fat!")
                wartface(angry, "Shut up!")
                bentnoze(neutral, "Fatty!")
                wartface(verymad, "SHUT UP!")
                bentnoze(neutral, "Even this human think you look fat! Don't you, human?")
                chatPlayer(confused, "Um...")
                fatOptions()
            }
            1 -> {
                wartface(neutral, "I tell all goblins in village to wear green armour now!")
                bentnoze(neutral, "They not listen to you! I already tell them wear red armour!")
                wartface(neutral, "They listen to me not you! They know me bigger general!")
                bentnoze(neutral, "Me bigger general! They listen to me!")
                wartface(neutral, "Human! What colour armour they wearing out there?")
                chatPlayer(neutral, "Half of them are wearing red and half of them green.")
                wartface(
                    angry,
                    "Shut up human! They wearing green armour really! Human lying " +
                        "because he scared of you!",
                )
                bentnoze(neutral, "Human scared of me not you? Then you think me bigger general!")
                wartface(confused, "What? Me mean...")
                wartface(angry, "Shut up! Me bigger general!")
                mainMenu()
            }
            2 -> {
                wartface(neutral, "We should wear green armour!")
                bentnoze(neutral, "Green armour? Are you stupid?")
                wartface(neutral, "You stupid! Only stupid goblins think red armour better!")
                bentnoze(neutral, "No they don't! Me think red armour better!")
                wartface(neutral, "That because you stupid!")
                bentnoze(angry, "Me not stupid!")
                wartface(neutral, "Then why you not like green armour?")
                bentnoze(neutral, "Because red armour better!")
                wartface(angry, "Only stupid goblins think that! You stupid!")
                mainMenu()
            }
            3 -> {
                wartface(neutral, "Green armour best.")
                bentnoze(neutral, "No no red every time.")
                wartface(neutral, "Go away human, we busy.")
                mainMenu()
            }
            else -> {
                bentnoze(neutral, "Red armour best.")
                wartface(neutral, "No it has to be green!")
                bentnoze(neutral, "Go away human, we busy.")
                mainMenu()
            }
        }
    }

    private suspend fun Dialogue.fatOptions() {
        when (
            choice3(
                "Yes, he looks fat.",
                1,
                "No, he doesn't look fat.",
                2,
                "I'll leave you to it.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Yes, he looks fat!")
                bentnoze(happy, "Ha ha! See, fatty? Even human think you fat!")
                wartface(angry, "Me not care what human think! Human ugly!")
                mainMenu()
            }
            2 -> {
                chatPlayer(neutral, "No, he doesn't look fat.")
                bentnoze(angry, "Shut up human! Wartface fat and human stupid!")
                wartface(angry, "Shut up Bentnoze!")
                mainMenu()
            }
        }
    }

    private suspend fun Dialogue.mainMenu() {
        when (
            choice4(
                "Why are you arguing about the colour of your armour?",
                1,
                "Wouldn't you prefer peace?",
                2,
                "Do you want me to pick an armour colour for you?",
                3,
                "I'll leave you to it.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(confused, "Why are you arguing about the colour of your armour?")
                bentnoze(
                    neutral,
                    "We decide to celebrate goblin new century by changing colour of our " +
                        "armour. Brown get boring after a bit. We want change.",
                )
                wartface(
                    neutral,
                    "Problem is, we can't agree on new colour. We think maybe we use old " +
                        "goblin tribe colours, but Bentnoze from rubbish Thorobshuun tribe " +
                        "and me from much better Garagorshuun tribe.",
                )
                bentnoze(
                    neutral,
                    "Thorobshuun tribe colour is red! Way better than silly green of " +
                        "Garagorshuun tribe.",
                )
                wartface(angry, "Shut up!")
                arguingMenu()
            }
            2 -> preferPeace()
            3 -> pickColour()
        }
    }

    private suspend fun Dialogue.arguingMenu() {
        when (
            choice4(
                "What is the goblin new century?",
                1,
                "Wouldn't you prefer peace?",
                2,
                "Do you want me to pick an armour colour for you?",
                3,
                "I'll leave you to it.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What is the goblin new century?")
                bentnoze(
                    neutral,
                    "Goblin century mark year of battle on Plain of Mud. That when " +
                        "Big High War God give goblin commandments.",
                )
                chatPlayer(quiz, "Big High War God?")
                wartface(
                    neutral,
                    "Big High War God is god for goblins. He take us and make us " +
                        "strong, not stupid like humans.",
                )
                bentnoze(neutral, "Me bet Big High War God would pick red armour.")
                wartface(angry, "Shut up! Big High War God would hate you!")
                arguingMenu()
            }
            2 -> {
                preferPeace()
                arguingMenu()
            }
            3 -> pickColour()
        }
    }

    private suspend fun Dialogue.preferPeace() {
        chatPlayer(confused, "Wouldn't you prefer peace?")
        wartface(
            neutral,
            "Goblins not so good at peace. Maybe peace be okay though, as long as it " +
                "peace wearing green armour.",
        )
        bentnoze(neutral, "But green too much like skin. Nearly make you look naked!")
    }

    private suspend fun Dialogue.pickColour() {
        chatPlayer(quiz, "Do you want me to pick an armour colour for you?")
        wartface(neutral, "Yes, as long as you pick green.")
        bentnoze(neutral, "No you have to pick red!")
        colourMenu()
    }

    private suspend fun Dialogue.colourMenu() {
        when (
            choice4(
                "You should wear red.",
                1,
                "You should wear green.",
                2,
                "What about a different colour?",
                3,
                "I'll leave you to it.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "You should wear red.")
                bentnoze(happy, "See? Even stupid human think red best. Now we all wear red!")
                wartface(
                    angry,
                    "Human not know anything! If we wear red then whole village be " +
                        "ugly like YOU!",
                )
                bentnoze(angry, "Go away human. You not helping.")
            }
            2 -> {
                chatPlayer(neutral, "You should wear green.")
                wartface(happy, "Green! We all wear green now, human has decided!")
                bentnoze(angry, "Why we have to do what human say? He not boss of us!")
                wartface(neutral, "No but he agree with me!")
                bentnoze(angry, "That prove you a filthy human-lover!")
                wartface(angry, "Me hate humans! This human just happen to be right!")
                bentnoze(angry, "Go away human. You not know anything.")
            }
            3 -> differentColour()
        }
    }

    private suspend fun Dialogue.differentColour() {
        chatPlayer(quiz, "What about a different colour?")
        bentnoze(neutral, "That would mean me wrong... but at least Wartface not right!")
        wartface(
            neutral,
            "Well Bentnoze never been right in his life. Still, maybe new colour " +
                "good, but will have to see armour before decide.",
        )
        bentnoze(neutral, "Human! You bring us armour in new colour!")
        if (!choice2("Yes.", true, "No.", false, title = "Start the Goblin Diplomacy quest?")) {
            chatPlayer(neutral, "Actually, I think I'll leave you to it.")
            return
        }
        quest.setQuestStage(access, STAGE_WILL_BRING_ARMOUR)
        chatPlayer(neutral, "I can do that.")
        wartface(neutral, "What colour we try?")
        bentnoze(neutral, "Orange armour might be good.")
        wartface(neutral, "Yep, bring us orange armour.")
        chatPlayer(confused, "How am I meant to get orange armour?")
        bentnoze(neutral, "Well first you get goblin armour...")
        wartface(neutral, "...and then you dye it orange!")
        bentnoze(neutral, "Even human should be able to work that out!")
        helpMenu()
    }

    private suspend fun Dialogue.helpMenu() {
        when (
            choice3(
                "Where do I get goblin armour?",
                1,
                "Where do I get dye?",
                2,
                "Okay, I'll be back soon.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Where do I get goblin armour?")
                wartface(neutral, "There some spare armour around village somewhere. You can take that.")
                bentnoze(neutral, "It in crates somewhere. Can't remember which crates now.")
                helpMenu()
            }
            2 -> {
                chatPlayer(quiz, "Where do I get dye?")
                bentnoze(neutral, "You go north of here into wilderness. There you find many ways to die!")
                chatPlayer(neutral, "No, D-Y-E, not D-I-E.")
                wartface(neutral, "Stupid Bentnoze, you not know how to spell!")
                bentnoze(angry, "Shut up Wartface!")
                chatPlayer(quiz, "Do you know where I can get dye?")
                bentnoze(neutral, "Me not know where dye come from.")
                chatPlayer(quiz, "Well where did you get your red and green dye from?")
                wartface(
                    neutral,
                    "Some goblin or other, he steal it. Say he steal it from old witch " +
                        "in Draynor Village.",
                )
                bentnoze(neutral, "Maybe you can get more dye from her?")
                helpMenu()
            }
            3 -> chatPlayer(neutral, "Okay, I'll be back soon.")
        }
    }

    private suspend fun Dialogue.wrongColourDialogue() {
        chatPlayer(quiz, "What do you think of this colour?")
        bentnoze(neutral, "That wrong colour.")
        wartface(neutral, "We tell you get ${wantedMail(player)?.second ?: "other"} armour.")
    }

    private suspend fun Dialogue.grubfoot() {
        when (player.vars[GRUBFOOT_VIS_VARBIT]) {
            VIS_ORANGE -> {
                chatNpc(neutral, "Me not like this orange armour. Make me look like that thing.")
                chatPlayer(quiz, "Look like what thing?")
                chatNpc(neutral, "That fruit thing. The one that orange. What it called?")
                chatPlayer(neutral, "An orange?")
                chatNpc(neutral, "That right. This armour make me look same colour as orange-fruit.")
            }
            VIS_BLUE -> {
                chatNpc(neutral, "Me not like this blue colour.")
                chatPlayer(quiz, "Why not?")
                chatNpc(neutral, "Me not know. It just make me feel...")
                chatPlayer(neutral, "Makes you feel blue?")
                chatNpc(neutral, "Makes me feel kind of sad.")
            }
            else -> {
                chatNpc(neutral, "Grubfoot wear red armour! Grubfoot wear green armour!")
                chatNpc(neutral, "Why they not make up their minds?")
            }
        }
        wartface(angry, "Shut up Grubfoot!")
    }

    private fun wantedMail(player: Player): Pair<String, String>? =
        when (quest.getQuestStage(player)) {
            STAGE_WILL_BRING_ARMOUR -> "obj.goblin_armour_orange" to "orange"
            STAGE_GAVE_ORANGE -> "obj.goblin_armour_darkblue" to "blue"
            STAGE_GAVE_BLUE -> "obj.goblin_armour" to "brown"
            else -> null
        }

    private fun Dialogue.takeMail(mail: String): Boolean =
        player.invTransaction(access.inv) {
            val inventory = select(access.inv)
            delete {
                from = inventory
                obj = mail.asRSCM()
                strictCount = 1
            }
        }.success

    private suspend fun Dialogue.wartface(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("General Wartface", "npc.general_wartface_green", mesanim, text)

    private suspend fun Dialogue.bentnoze(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("General Bentnoze", "npc.general_bentnoze_red", mesanim, text)

    private suspend fun Dialogue.grubfootSpecific(
        mesanim: MesAnimType,
        text: String,
    ) = chatNpcSpecific("Grubfoot", "npc.catwalk_goblin", mesanim, text)

    private const val STAGE_WILL_BRING_ARMOUR = 2
    private const val STAGE_GAVE_ORANGE = 3
    private const val STAGE_GAVE_BLUE = 4

    private const val VIS_BROWN = 0
    private const val VIS_ORANGE = 1
    private const val VIS_BLUE = 2

    private const val GRUBFOOT_VIS_VARBIT = "varbit.gobdip_grubfoot_vis"
}
