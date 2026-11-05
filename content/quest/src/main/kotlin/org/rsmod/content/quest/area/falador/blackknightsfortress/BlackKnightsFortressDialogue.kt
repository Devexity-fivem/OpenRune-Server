package org.rsmod.content.quest.area.falador.blackknightsfortress

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.CoinReward
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageAccepted
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageComplete
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageOverheard
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Black Knights' Fortress dialogue, transcribed from LostCity's
 * quest_blackknight.rs2 / sir_amik_varze.rs2. `varp.spy` tracks progress:
 * 0 unstarted, 1 mission accepted, 2 weapon overheard, 3 potion ruined,
 * 4 complete.
 */

val bkfQuest: Quest
    get() = checkNotNull(Quest.get(BlackKnightsFortress.KEY)) { "quest not registered" }

var Player.spyStage by intVarp("varp.spy")
private val Player.questPoints by intVarp("varp.qp")

private const val RequiredQuestPoints = 12

object BlackKnightsFortressDialogue {

    // =========================================================== Sir Amik Varze

    suspend fun ProtectedAccess.amikDialogue(npc: Npc) = startDialogue(npc) {
        when {
            player.spyStage >= StageComplete -> {
                chatPlayer(happy, "Hello Sir Amik.")
                chatNpc(happy, "Hello, friend!")
            }
            player.spyStage >= StageAccepted -> amikInProgress()
            else -> amikPrequest()
        }
    }

    private suspend fun Dialogue.amikPrequest() {
        chatNpc(quiz, "I am the leader of the White Knights of Falador.")
        chatNpc(quiz, "Why do you seek my audience?")
        if (choice2("I seek a quest!", true, "I don't, I'm just looking around.", false)) {
            chatPlayer(neutral, "I seek a quest.")
            if (player.questPoints < RequiredQuestPoints) {
                chatNpc(
                    neutral,
                    "Well I do have a task, but it is very dangerous and it's critical " +
                        "to us that no mistakes are made. I couldn't possibly let an " +
                        "unexperienced quester like yourself go.",
                )
                access.mes(
                    "You need at least 12 quest points before you may attempt this quest."
                )
            } else {
                amikMeetsRequirements()
            }
        } else {
            chatPlayer(neutral, "I don't, I'm just looking around.")
            chatNpc(neutral, "Ok, don't break anything.")
        }
    }

    private suspend fun Dialogue.amikMeetsRequirements() {
        chatNpc(
            neutral,
            "Well, I need some spy work doing.|It's quite dangerous.|" +
                "You will need to go into the Black Knight's fortress.",
        )
        if (
            choice2(
                "I laugh in the face of danger!",
                true,
                "I go and cower in a corner at the first sign of danger!",
                false,
            )
        ) {
            chatPlayer(happy, "I laugh in the face of danger.")
            chatNpc(neutral, "Well that's good. Don't get too overconfident though.")
            amikTask()
            return
        }
        chatPlayer(quiz, "I go and cower in a corner at the first sign of danger!")
        chatNpc(shocked, "Err...")
        chatNpc(confused, "Well.")
        chatNpc(quiz, "Spy work does involve a little hiding in corners I suppose.")
        if (choice2("Oh, I suppose I'll give it a go then.", true, "No, I'm not convinced.", false)) {
            amikTask()
        } else {
            chatPlayer(neutral, "No, I'm not convinced.")
        }
    }

    private suspend fun Dialogue.amikTask() {
        chatNpc(
            neutral,
            "You've come along just right actually.|" +
                "All of my knights are already known to the Black Knights.",
        )
        chatNpc(sad, "Subtlety isn't exactly our strong point.")
        chatPlayer(quiz, "So what needs doing?")
        chatNpc(
            angry,
            "Well the Black Knights have started making strange threats to us. " +
                "Demanding large amounts of money and land, and threatening to " +
                "invade Falador if we don't pay.",
        )
        chatNpc(neutral, "Now normally this wouldn't be a problem.")
        chatNpc(angry, "But they claim to have a powerful new secret weapon.")
        chatNpc(
            neutral,
            "What I want you to do is get inside their fortress.|" +
                "Find out what their secret weapon is.|And then sabotage it. " +
                "You will be well paid.",
        )
        chatPlayer(neutral, "Ok, I'll give it a try.")
        player.spyStage = StageAccepted
    }

    private suspend fun Dialogue.amikInProgress() {
        when (player.spyStage) {
            StageAccepted -> {
                chatNpc(quiz, "How's the mission going?")
                chatPlayer(sad, "I haven't managed to find what the secret weapon is yet.")
            }
            StageOverheard -> {
                chatNpc(quiz, "How's the mission going?")
                chatPlayer(
                    neutral,
                    "I've found out what the Black Knight's secret weapon is. " +
                        "It's a potion of invincibility.",
                )
                chatNpc(
                    angry,
                    "That is bad news.|If you can sabotage it somehow, you will be paid well.",
                )
            }
            else -> amikComplete()
        }
    }

    private suspend fun Dialogue.amikComplete() {
        chatPlayer(
            happy,
            "I have ruined the Black Knight's invincibility potion.|" +
                "That should put a stop to your problem.",
        )
        chatNpc(
            neutral,
            "Yes we have just received a message from the Black Knights saying " +
                "they withdraw their demands. Which confirms your story.",
        )
        chatPlayer(quiz, "You said you were going to pay me.")
        chatNpc(happy, "Yes, that's right.")
        access.mes("Sir Amik hands you $CoinReward coins.")
        bkfQuest.completeQuest(access)
    }

    // ======================================================== The door guards

    suspend fun Dialogue.entranceGuard() {
        chatNpc(angry, "Hey, you can't come in here!|This is a high security military installation.")
        when (
            choice3(
                "Yes, but I work here!",
                1,
                "Oh sorry.",
                2,
                "So who does it belong to?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(angry, "Yes, but I work here!")
                chatNpc(
                    angry,
                    "Well this the guards entrance. I might be new here but I can tell " +
                        "you're not a guard.|You're not even wearing proper guards uniform!",
                )
                if (choice2("Pleaassee let me in!", true, "So what is this uniform?", false)) {
                    chatPlayer(sad, "Pleaassee let me in!")
                    chatNpc(angry, "Go away, you're getting annoying.")
                } else {
                    chatPlayer(neutral, "So what is this uniform?")
                    chatNpc(
                        confused,
                        "Well you can see me wearing it.|" +
                            "It's iron chain mail and a medium bronze helmet.",
                    )
                }
            }
            2 -> {
                chatPlayer(neutral, "Oh sorry.")
                chatNpc(angry, "Don't let it happen again.")
            }
            else -> {
                chatPlayer(neutral, "So who does it belong to?")
                chatNpc(
                    angry,
                    "This fortress belongs to the order of Black Knights known as the Kinshra.",
                )
            }
        }
    }

    // Returns true when the player barges in anyway.
    suspend fun Dialogue.banquetGuard(): Boolean {
        chatNpc(
            neutral,
            "I wouldn't go in there if I woz you.|Those Black Knights are in an " +
                "important meeting;|They said they'd kill anyone who went in there!",
        )
        if (choice2("Ok I won't.", true, "I don't care. I'm going in anyway.", false)) {
            chatPlayer(neutral, "Ok I won't.")
            return false
        }
        chatPlayer(neutral, "I don't care. I'm going in anyway.")
        return true
    }

    // ======================================================= The grill eavesdrop

    suspend fun Dialogue.eavesdropDialogue() {
        chatNpcSpecific(
            "Black Knight",
            "npc.grillknight",
            quiz,
            "So, how's the secret weapon coming along?",
        )
        chatNpcSpecific("Witch", "npc.fortwitch", happy, "The invincibility potion is almost ready...")
        chatNpcSpecific(
            "Witch",
            "npc.fortwitch",
            happy,
            "It's taken me five years but it's almost ready.",
        )
        chatNpcSpecific(
            "Witch",
            "npc.fortwitch",
            neutral,
            "Greldo the Goblin here|is just going to fetch the last ingredient for me.",
        )
        chatNpcSpecific(
            "Witch",
            "npc.fortwitch",
            neutral,
            "It's a specially grown cabbage.|Grown by my cousin Helda who lives in Draynor Manor.",
        )
        chatNpcSpecific(
            "Witch",
            "npc.fortwitch",
            neutral,
            "The soil there is slightly magical.|And it gives the cabbages slight magic properties.",
        )
        chatNpcSpecific("Witch", "npc.fortwitch", shocked, "Not to mention the trees!")
        chatNpcSpecific(
            "Witch",
            "npc.fortwitch",
            neutral,
            "Now remember Greldo, only a Draynor Manor| cabbage will do! Don't get lazy|" +
                " and bring any old cabbage, THAT| would ENTIRELY wreck the potion!",
        )
        chatNpcSpecific("Greldo", "npc.greldo", neutral, "Yes mithreth.")
    }
}
