package org.rsmod.content.quest.area.draynor.princealirescue

import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.Beer
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.BlondWig
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.BronzeBar
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.KeyPrint
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.PinkSkirt
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.PrincesKey
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.Rope
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.SkinPaste
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StageComplete
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StageGuardDrunk
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StagePrepFinished
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StageSaved
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StageSpokenOsman
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StageStarted
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescue.Companion.StageTiedKeli
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Prince Ali Rescue dialogue, transcribed from the LostCity authentic scripts.
 * Stage model on `varp.princequest` (273): 10 started, 20 spoken to Osman,
 * 30 equipment gathered (Leela confirms), 40 guard drunk, 50 Keli tied,
 * 100 prince saved, 110 complete. `varp.prince_keystatus` tracks the copied key:
 * 0 none, 1 Osman made it (collect from Leela), 2 claimed.
 */

internal val princeAliQuest
    get() = checkNotNull(Quest.get(PrinceAliRescue.KEY)) { "quest not registered" }

fun Player.paStage(): Int = princeAliQuest.getQuestStage(this)

internal var Player.princeKeyStatus by intVarp("varp.prince_keystatus")

private const val KeyMade = 1
private const val KeyClaimed = 2

private const val Coins = "obj.coins"
private const val JugWater = "obj.jug_water"
private const val SoftClay = "obj.softclay"
private const val BallOfWool = "obj.ball_of_wool"
private const val PlainWig = "obj.plainwig"
private const val Redberries = "obj.redberries"
private const val PotFlour = "obj.pot_flour"
private const val Ashes = "obj.ashes"
private const val BucketWater = "obj.bucket_water"
private const val LadyKeli = "npc.lady_keli_vis"

object PrinceAliRescueDialogue {

    private fun Dialogue.manWoman(): String = if (access.isBodyTypeA()) "man" else "woman"

    private fun Player.hasAnywhere(obj: String): Boolean =
        inv.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

    // ================================================================== Hassan

    suspend fun ProtectedAccess.hassanDialogue(npc: Npc) = startDialogue(npc) { hassan() }

    private suspend fun Dialogue.hassan() {
        when (player.paStage()) {
            0 -> hassanStart()
            StageStarted ->
                chatNpc(
                    quiz,
                    "Have you found the spymaster, Osman, yet? You cannot proceed in " +
                        "your task without reporting to him.",
                )
            StageSpokenOsman ->
                chatNpc(
                    happy,
                    "I understand the Spymaster has hired you. I will pay the reward " +
                        "only when the Prince is rescued.",
                )
            StageSaved -> hassanComplete()
            StageComplete ->
                chatNpc(
                    happy,
                    "You are a friend of the town of Al-Kharid.|If we have more tasks to " +
                        "complete, we will ask you.|Please, keep in contact.|" +
                        "Good employees are not easy to find.",
                )
            else ->
                chatNpc(
                    happy,
                    "I see you are getting on well with the rescue. Obtaining a key " +
                        "must have saved you some time.",
                )
        }
    }

    private suspend fun Dialogue.hassanStart() {
        chatNpc(neutral, "Greetings I am Hassan, Chancellor to the Emir of Al- Kharid.")
        when (
            choice3(
                "Can I help you? You must need some help here in the desert.",
                1,
                "It's just too hot here. How can you stand it?",
                2,
                "Do you mind if I just kill your warriors?",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(happy, "Can I help you? You must need some help here in the desert.")
                chatNpc(
                    neutral,
                    "I need the services of someone, yes. If you are interested, see the " +
                        "spymaster, Osman. I manage the finances here. Come to me when " +
                        "you need payment.",
                )
                princeAliQuest.setQuestStage(access, StageStarted)
            }
            2 -> {
                chatPlayer(sad, "It's just too hot here. How can you stand it?")
                chatNpc(
                    neutral,
                    "We manage, in our humble way. We are a wealthy town and we have " +
                        "water. It cures many thirsts.",
                )
                if (access.invAdd(access.inv, JugWater).success) {
                    objbox(JugWater, "The chancellor hands you some water.")
                }
            }
            else -> {
                chatPlayer(quiz, "Do you mind if I just kill your warriors?")
                chatNpc(
                    neutral,
                    "You are welcome. They are not expensive. We have them here to stop " +
                        "the elite guard being bothered. They are a little harder to kill.",
                )
            }
        }
    }

    private suspend fun Dialogue.hassanComplete() {
        chatNpc(
            happy,
            "You have the eternal gratitude of the Emir for rescuing his son. " +
                "I am authorised to pay you 700 coins.",
        )
        princeAliQuest.completeQuest(access)
    }

    // ==================================================================== Osman

    suspend fun ProtectedAccess.osmanDialogue(npc: Npc) = startDialogue(npc) { osman() }

    suspend fun ProtectedAccess.osmanItemUse(npc: Npc) {
        if (player.paStage() != StageSpokenOsman) {
            mes(constants.dm_default)
            return
        }
        startDialogue(npc) { osmanItems() }
    }

    private suspend fun Dialogue.osman() {
        when (player.paStage()) {
            0 -> osmanPrequest()
            StageStarted -> osmanInstructions()
            StageSaved ->
                chatNpc(
                    shifty,
                    "The prince is safe and on his way home with Leela.|You can pick up " +
                        "your payment from the chancellor.",
                )
            StageComplete ->
                chatNpc(
                    shifty,
                    "Well done, A great rescue.|I will remember you if I have anything " +
                        "dangerous to do.",
                )
            else -> osmanItems()
        }
    }

    private suspend fun Dialogue.osmanPrequest() {
        chatNpc(shifty, "Hello, I am Osman. With what can I assist you?")
        when (
            choice3(
                "You don't seem very tough. Who are you?",
                1,
                "I hear wild rumours about a prince.",
                2,
                "I am just being nosy.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(neutral, "You don't seem very tough. Who are you?")
                chatNpc(shifty, "I am in the employ of the Emir.|That is all you need to know.")
            }
            2 -> {
                chatPlayer(neutral, "I hear wild rumours about a prince.")
                chatNpc(
                    shifty,
                    "The prince is not here. He is...away.|If you can be trusted, speak " +
                        "to the chancellor, Hassan.",
                )
            }
            else -> {
                chatPlayer(shifty, "I am just being nosy.")
                chatNpc(
                    shifty,
                    "That bothers me not.|The secrets of Al-Kharid protect themselves.",
                )
            }
        }
    }

    private suspend fun Dialogue.osmanInstructions() {
        chatPlayer(neutral, "The chancellor trusts me. I have come for instructions.")
        chatNpc(
            shifty,
            "Our prince is captive by the Lady Keli.|We just need to make the rescue.|" +
                "There are two things we need you to do.",
        )
        when (
            choice2(
                "What is the first thing I must do?",
                1,
                "What is the second thing you need?",
                2,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(neutral, "What is the first thing I must do?")
                osmanFirstThing()
            }
            else -> {
                chatPlayer(neutral, "What is the second thing you need?")
                osmanSecondThing()
            }
        }
    }

    private suspend fun Dialogue.osmanFirstThing() {
        chatNpc(
            shifty,
            "The prince is guarded by some stupid guards and a clever woman. The woman " +
                "is our only way to get the prince out. Only she can walk freely about " +
                "the area.",
        )
        chatNpc(
            shifty,
            "I think you will need to tie her up.|One coil of rope should do for that.|" +
                "Then, disguise the prince as her to get him out without suspicion.",
        )
        chatPlayer(neutral, "How good must the disguise be?")
        chatNpc(
            shifty,
            "Only enough to fool the guards at a distance.|Get a skirt like hers. Same " +
                "colour, same style.|We will only have a short time.",
        )
        chatNpc(
            shifty,
            "Get a blonde wig, too. That is up to you to make or find. Something to " +
                "colour the skin of the prince.",
        )
        chatNpc(
            shifty,
            "My daughter and top spy, Leela, can help you. She has sent word that she " +
                "has discovered where they are keeping the prince.",
        )
        chatNpc(
            shifty,
            "It's near Draynor Village. She is lurking somewhere near there now.",
        )
        when (
            choice3(
                "Explain the first thing again.",
                1,
                "What is the second thing you need?",
                2,
                "Okay, I better go find some things.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(neutral, "What is the first thing I must do?")
                osmanFirstThing()
            }
            2 -> {
                chatPlayer(neutral, "What is the second thing you need?")
                osmanSecondThing()
            }
            else -> osmanBetterGo()
        }
    }

    private suspend fun Dialogue.osmanSecondThing() {
        chatNpc(
            shifty,
            "We need the key, or we need a copy made.|If you can get some soft clay " +
                "then you can copy the key...",
        )
        chatNpc(
            shifty,
            "...If you can convince Lady Keli to show it to you|for a moment. She is " +
                "very boastful.|It should not be too hard.",
        )
        chatNpc(shifty, "Bring the imprint to me, with a bar of bronze.")
        when (
            choice3(
                "What is the first thing I must do?",
                1,
                "What exactly is the second thing you need?",
                2,
                "Okay, I better go find some things.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(neutral, "What is the first thing I must do?")
                osmanFirstThing()
            }
            2 -> {
                chatPlayer(neutral, "What exactly is the second thing you need?")
                osmanSecondThing()
            }
            else -> osmanBetterGo()
        }
    }

    private suspend fun Dialogue.osmanBetterGo() {
        chatPlayer(neutral, "Okay, I had better go find some things.")
        chatNpc(
            shifty,
            "May good luck travel with you. Don't forget to find Leela. It can't be " +
                "done without her help.",
        )
        princeAliQuest.setQuestStage(access, StageSpokenOsman)
    }

    private suspend fun Dialogue.osmanItems() {
        if (
            player.princeKeyStatus == KeyClaimed &&
                invTotal(access.inv, PrincesKey) == 0 &&
                !player.hasAnywhere(PrincesKey)
        ) {
            chatPlayer(sad, "I'm afraid I lost that key you gave me.")
            chatNpc(angry, "Foolish ${manWoman()}! A new key will cost 15gp.")
            if (invTotal(access.inv, Coins) < 15) {
                chatPlayer(sad, "I haven't got 15 coins with me.")
                chatNpc(neutral, "Then come back to me when you do.")
                return
            }
            chatPlayer(neutral, "Here, I have 15gp.")
            access.invDel(access.inv, Coins, 15)
            player.princeKeyStatus = KeyMade
            mesbox("That is good. I will have another key made. You may collect it from Leela.")
        } else if (
            player.princeKeyStatus == 0 &&
                invTotal(access.inv, KeyPrint) >= 1 &&
                invTotal(access.inv, BronzeBar) >= 1
        ) {
            chatNpc(shifty, "Well done; we can make the key now.")
            access.invDel(access.inv, KeyPrint, 1)
            access.invDel(access.inv, BronzeBar, 1)
            player.princeKeyStatus = KeyMade
            mesbox("Osman takes the key imprint and the bronze bar.")
            chatNpc(shifty, "Pick the key up from Leela.")
            when (
                choice2(
                    "Thank you. I will try to find the other items.",
                    1,
                    "Can you tell me what I still need to get?",
                    2,
                    title = "What would you like to say?",
                )
            ) {
                1 -> chatPlayer(neutral, "Thank you. I will try to find the other items.")
                else -> osmanStillNeed()
            }
        } else if (player.paStage() >= StagePrepFinished) {
            chatNpc(
                shifty,
                "I hear from Leela that you are well on the way with the rescue. " +
                    "I am always kept informed, you know.",
            )
        } else {
            osmanStillNeed()
        }
    }

    private suspend fun Dialogue.osmanStillNeed() {
        chatPlayer(neutral, "Can you tell me what I still need to get?")
        if (player.paStage() >= StagePrepFinished) {
            if (invTotal(access.inv, PrincesKey) > 0) {
                chatNpc(shifty, "You have the key, good.")
            } else {
                chatNpc(shifty, "You can collect the key from Leela.")
            }
        } else {
            chatNpc(
                shifty,
                "A print of the key in soft clay and a bronze bar.|Then, collect the " +
                    "key from Leela.",
            )
        }
        if (invTotal(access.inv, BlondWig) >= 1) {
            chatNpc(shifty, "The wig you have got, well done.")
        } else {
            chatNpc(shifty, "You need to make a blonde wig somehow. Leela may help.")
        }
        if (invTotal(access.inv, PinkSkirt) >= 1) {
            chatNpc(shifty, "You have got the skirt, good.")
        } else {
            chatNpc(shifty, "You will need a skirt that looks the same as Keli's")
        }
        if (invTotal(access.inv, SkinPaste) >= 1) {
            chatNpc(shifty, "You have the skin paint, well done.|I thought you would " +
                "struggle to make that.")
        } else {
            chatNpc(shifty, "Something to make the prince's skin appear lighter.")
        }
        if (invTotal(access.inv, Rope) >= 1) {
            chatNpc(shifty, "Yes, you have the rope.")
        } else {
            chatNpc(shifty, "A rope with which to tie Keli up.")
        }
        chatNpc(shifty, "Once you have everything, go to Leela.|She must be ready to " +
            "get the prince away.")
    }

    // ==================================================================== Leela

    suspend fun ProtectedAccess.leelaDialogue(npc: Npc) = startDialogue(npc) { leela() }

    suspend fun Dialogue.leelaQuestDialogue() = leela()

    private suspend fun Dialogue.leela() {
        when {
            player.princeKeyStatus == KeyMade -> {
                if (access.inv.isFull()) {
                    mesbox("You don't have space for the key.")
                    return
                }
                chatNpc(neutral, "My father sent this key for you.|Be careful not to lose it.")
                mesbox("Leela gives you a copy of the key to the prince's door.")
                access.invAdd(access.inv, PrincesKey)
                player.princeKeyStatus = KeyClaimed
            }
            player.princeKeyStatus == KeyClaimed && !player.hasAnywhere(PrincesKey) -> {
                chatPlayer(sad, "I'm afraid I lost that key you gave me.")
                chatNpc(angry, "Foolish ${manWoman()}! A new key will cost 15 coins.")
                if (invTotal(access.inv, Coins) < 15) {
                    chatPlayer(sad, "I haven't got 15 coins with me.")
                    chatNpc(neutral, "Then come back to me when you do.")
                    return
                }
                if (access.inv.isFull()) {
                    mesbox("You don't have space for the key.")
                    return
                }
                chatPlayer(neutral, "Here, I have 15 coins.")
                mesbox("Leela gives you another key.")
                access.invAdd(access.inv, PrincesKey)
                access.invDel(access.inv, Coins, 15)
            }
            player.paStage() == StageGuardDrunk -> {
                chatNpc(
                    neutral,
                    "Great! The guard is now harmless. Now you just need to use the " +
                        "rope on Keli to remove her. Then you can go in and give " +
                        "everything to the prince.",
                )
            }
            player.paStage() == StageTiedKeli -> {
                chatNpc(
                    neutral,
                    "You should carry on, get in and rescue the prince. Keli is sharp, " +
                        "she won't stay tied up long.",
                )
            }
            player.paStage() == StageSpokenOsman && hasAllGear() -> {
                princeAliQuest.setQuestStage(access, StagePrepFinished)
                chatNpc(
                    shifty,
                    "Good, you have all the basic equipment. Next to deal with the " +
                        "guard on the door. He is talkative, try to find a weakness " +
                        "in him.",
                )
            }
            player.paStage() == StagePrepFinished -> leelaGuardPlans()
            else -> leelaHelp()
        }
    }

    private fun Dialogue.hasAllGear(): Boolean =
        invTotal(access.inv, PrincesKey) >= 1 &&
            invTotal(access.inv, BlondWig) >= 1 &&
            invTotal(access.inv, PinkSkirt) >= 1 &&
            invTotal(access.inv, SkinPaste) >= 1

    private suspend fun Dialogue.leelaGuardPlans() {
        chatNpc(
            neutral,
            "Ok now, you have all the basic equipment.|What are your plans to stop " +
                "the guard interfering?",
        )
        when (
            choice4(
                "I haven't spoken to him yet.",
                1,
                "I was going to attack him.",
                2,
                "I hoped to get him drunk.",
                3,
                "Maybe I could bribe him to leave.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I haven't spoken to him yet.")
                chatNpc(
                    neutral,
                    "Well, speaking to him may find a weakness he has.|See if there's " +
                        "something that could stop him bothering us.",
                )
            }
            2 -> {
                chatPlayer(neutral, "I was going to attack him.")
                chatNpc(
                    bored,
                    "I don't think you should. If you do the rest of the gang and Keli " +
                        "would attack you. The door guard should be removed first, " +
                        "to make it easy.",
                )
            }
            3 -> {
                chatPlayer(neutral, "I hoped to get him drunk.")
                chatNpc(
                    neutral,
                    "Well, that's possible. These guards do like a drink.|I would " +
                        "think it will take at least 3 beers to do it well.|You would " +
                        "probably have to do it all at the same time too.|The effects " +
                        "of the local beer wear off quickly.",
                )
            }
            else -> {
                chatPlayer(neutral, "Maybe I could bribe him to leave.")
                chatNpc(
                    shifty,
                    "You could try. I don't think the emir will pay anything towards " +
                        "it. And we did bribe one of their guards once.",
                )
                chatNpc(
                    shifty,
                    "Keli killed him in front of the other guards, as a deterrent. " +
                        "It would probably take a lot of gold.",
                )
            }
        }
        chatNpc(
            neutral,
            "Good luck with the guard. When the guard is out you can tie up Keli.",
        )
    }

    private suspend fun Dialogue.leelaHelp() {
        chatPlayer(neutral, "I am here to help you free the prince.")
        chatNpc(
            neutral,
            "Your employment is known to me.|Now, do you know all that we need to " +
                "make the break?",
        )
        leelaTopics()
    }

    private suspend fun Dialogue.leelaTopics() {
        while (true) {
            when (
                choice4(
                    "I must make a disguise. What do you suggest?",
                    1,
                    "I need to get the key made.",
                    2,
                    "What can I do with the guards?",
                    3,
                    "I will go and get the rest of the escape equipment.",
                    4,
                    title = "What would you like to say?",
                )
            ) {
                1 -> leelaDisguise()
                2 -> leelaNeedKey()
                3 -> leelaGuards()
                else -> {
                    chatPlayer(neutral, "I will go and get the rest of the escape equipment.")
                    chatNpc(shifty, "Good, I shall await your return with everything.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.leelaDisguise() {
        chatPlayer(quiz, "I must make a disguise. What do you suggest?")
        chatNpc(
            bored,
            "Only the lady Keli, can wander about outside the jail. The guards will " +
                "shoot to kill if they see the prince out so we need a disguise good " +
                "enough to fool them at a distance.",
        )
        if (invTotal(access.inv, BlondWig) >= 1) {
            chatNpc(neutral, "The wig you have got, well done.")
        } else {
            chatNpc(
                neutral,
                "You need a wig, maybe made from wool. If you find someone who can " +
                    "work with wool ask them about it. There's a witch nearby may be " +
                    "able to help you dye it.",
            )
        }
        if (invTotal(access.inv, PinkSkirt) >= 1) {
            chatNpc(neutral, "You have got the skirt, good.")
        } else {
            chatNpc(neutral, "You will need to get a pink skirt, same as Keli's.")
        }
        if (invTotal(access.inv, SkinPaste) >= 1) {
            chatNpc(neutral, "You have the skin paint, well done.|I thought you would " +
                "struggle to make that.")
        } else {
            chatNpc(
                neutral,
                "We still need something to colour the Prince's skin lighter. There's " +
                    "a witch close to here. She knows about many things. She may know " +
                    "some way to make the skin lighter.",
            )
        }
        if (hasAllGear()) {
            chatNpc(neutral, "You do have everything for the disguise.")
        }
        if (invTotal(access.inv, Rope) >= 1) {
            chatNpc(shifty, "You have the rope I see, to tie up Keli.|That will be the " +
                "most dangerous part of the plan.")
        } else {
            chatNpc(
                shifty,
                "You will still need some rope to tie up Keli, of course. I heard " +
                    "that there was a good rope maker around here...",
            )
        }
    }

    private suspend fun Dialogue.leelaNeedKey() {
        chatPlayer(neutral, "I need to get the key made.")
        chatNpc(
            neutral,
            "Yes, that is most important.|There is no way you can get the real key.|" +
                "It is on a chain around Keli's neck. Almost impossible to steal.",
        )
        chatNpc(
            shifty,
            "Get some soft clay and get her to show you the key somehow. Then take " +
                "the print, with bronze, to my father.",
        )
    }

    private suspend fun Dialogue.leelaGuards() {
        chatPlayer(neutral, "What can I do with the guards?")
        chatNpc(
            bored,
            "Most of the guards will be easy. The disguise will get past them. The " +
                "only guard who will be a problem will be the one at the door.",
        )
        chatNpc(bored, "We can discuss this more when you have the rest of the escape kit.")
    }

    // ====================================================================== Joe

    suspend fun ProtectedAccess.joeDialogue(npc: Npc) = startDialogue(npc) { joe() }

    private suspend fun Dialogue.joe() {
        when (player.paStage()) {
            StagePrepFinished -> joeDistract()
            StageGuardDrunk, StageTiedKeli -> joeDrunk()
            StageSaved, StageComplete -> joePostquest()
            else -> joePrequest()
        }
    }

    private suspend fun Dialogue.joePrequest() {
        chatNpc(neutral, "Hi, I'm Joe, door guard for Lady Keli.")
        chatPlayer(quiz, "Hi, who are you guarding here?")
        chatNpc(
            shifty,
            "Can't say, all very secret. You should get out of here. I am not " +
                "supposed to talk while I guard.",
        )
    }

    private suspend fun Dialogue.joeDistract() {
        when (
            choice4(
                "I have some beer here, fancy one?",
                1,
                "Tell me about the life of a guard.",
                2,
                "What did you want to be when you were a boy?",
                3,
                "I had better leave, I don't want trouble.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> joeBeer()
            2 -> joeLife()
            3 -> joeBoy()
            else -> joeLeave()
        }
    }

    private suspend fun Dialogue.joeBeer() {
        chatPlayer(happy, "I have some beer here, fancy one?")
        chatNpc(happy, "Ah, that would be lovely,|just one now, just to wet my throat.")
        chatPlayer(neutral, "Of course, it must be tough being here without a drink.")
        if (invTotal(access.inv, Beer) <= 0) {
            chatPlayer(sad, "Oh dear, seems like I don't have any beer.")
            return
        }
        mesbox("You hand a beer to the guard, he drinks it in seconds.")
        access.invDel(access.inv, Beer, 1)
        chatNpc(happy, "That was perfect, I can't thank you enough.")
        chatPlayer(happy, "How are you? Still ok? Not too drunk?")
        if (invTotal(access.inv, Beer) < 2) {
            chatNpc(
                happy,
                "No, I don't get drunk with only one drink. You would need a few to " +
                    "do that. But thanks for the beer.",
            )
            return
        }
        chatPlayer(happy, "Would you care for another, my friend?")
        chatNpc(bored, "I better not, I don't want to be drunk on duty.")
        chatPlayer(happy, "Here, just keep these for later,|I hate to see a thirsty guard.")
        access.invDel(access.inv, Beer, 2)
        princeAliQuest.setQuestStage(access, StageGuardDrunk)
        mesbox("You hand two more beers to the guard.|He takes a sip of one, and then " +
            "he drinks them both.")
        chatNpc(
            confused,
            "Franksh, that wash just what I need to shtay on guard. No more beersh, " +
                "I don't want to get drunk.",
        )
        mesbox("The guard is drunk, and no longer a problem.")
    }

    private suspend fun Dialogue.joeLife() {
        chatPlayer(neutral, "Tell me about the life of a guard.")
        chatNpc(happy, "Well, the hours are good.....")
        chatNpc(
            sad,
            ".... But most of those hours are a drag. If only I had spent more time " +
                "in Knight school when I was a young boy. Maybe I wouldn't be here " +
                "now, scared of Keli.",
        )
        joeSubmenu()
    }

    private suspend fun Dialogue.joeBoy() {
        chatPlayer(neutral, "What did you want to be when you were a boy?")
        chatNpc(
            bored,
            "Well, I loved to sit by the lake, with my toes in the water and shoot " +
                "the fish with my bow and arrow.",
        )
        chatPlayer(confused, "That was a strange hobby for a little boy.")
        chatNpc(
            happy,
            "It kept us from goblin hunting, which was what most boys did. What are " +
                "you here for?",
        )
        joeSubmenu()
    }

    private suspend fun Dialogue.joeSubmenu() {
        when (
            choice4(
                "Hey, chill out, I won't cause you trouble.",
                1,
                "Tell me about the life of a guard.",
                2,
                "What did you want to be when you were a boy?",
                3,
                "I had better leave, I don't want trouble.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> joeChillOut()
            2 -> joeLife()
            3 -> joeBoy()
            else -> joeLeave()
        }
    }

    private suspend fun Dialogue.joeChillOut() {
        chatPlayer(happy, "Hey, chill out, I won't cause you trouble. I was just " +
            "wondering what you do to relax.")
        chatNpc(
            neutral,
            "You never relax with these people, but it's a good career for a young " +
                "man and some of the shouting I rather like.",
        )
        chatNpc(angry, "RESISTANCE IS USELESS!")
        when (
            choice4(
                "So what do you buy with these great wages?",
                1,
                "Tell me about the life of a guard.",
                2,
                "Would you be interested in making a little more money?",
                3,
                "I had better leave, I don't want trouble.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> joeWages()
            2 -> joeLife()
            3 -> joeMoney()
            else -> joeLeave()
        }
    }

    private suspend fun Dialogue.joeWages() {
        chatPlayer(happy, "So what do you buy with these great wages?")
        chatNpc(
            bored,
            "Really, after working here, there's only time for a drink or three. " +
                "All us guards go to the same bar and drink ourselves stupid.",
        )
        chatNpc(
            happy,
            "It's what I enjoy these days, that fade into unconciousness. I can't " +
                "resist the sight of a really cold beer.",
        )
        joeSubmenu2()
    }

    private suspend fun Dialogue.joeMoney() {
        chatPlayer(happy, "Would you be interested in making a little more money?")
        chatNpc(
            angry,
            "WHAT?! Are you trying to bribe me? I may not be a great guard, but I am " +
                "loyal. How DARE you try to bribe me!",
        )
        chatPlayer(
            shocked,
            "No, no, you've got the wrong idea, totally. I just wondered if you " +
                "wanted some part-time bodyguard work.",
        )
        chatNpc(neutral, "Oh... sorry. No, I don't need money. As long as you were " +
            "not offering me a bribe.")
        joeSubmenu2()
    }

    private suspend fun Dialogue.joeSubmenu2() {
        when (
            choice3(
                "Tell me about the life of a guard.",
                2,
                "What did you want to be when you were a boy?",
                3,
                "I had better leave, I don't want trouble.",
                4,
                title = "What would you like to say?",
            )
        ) {
            2 -> joeLife()
            3 -> joeBoy()
            else -> joeLeave()
        }
    }

    private suspend fun Dialogue.joeLeave() {
        chatPlayer(neutral, "I had better leave, I don't want trouble.")
        chatNpc(
            happy,
            "Thanks, I appreciate that. Talking on duty can be punishable by having " +
                "your mouth stitched up. These are tough people, no mistake.",
        )
    }

    private suspend fun Dialogue.joeDrunk() {
        chatNpc(confused, "Halt! Who goes there?")
        chatPlayer(quiz, "Hello friend, I am just rescuing the prince, is that ok?")
        chatNpc(confused, "Thatsh a funny joke. You are lucky I am shober. Go in peace, friend.")
    }

    private suspend fun Dialogue.joePostquest() {
        chatNpc(angry, "Halt! Who goes there? Friend or foe?")
        chatPlayer(neutral, "Hi friend, I am just checking out things here.")
        chatNpc(
            shifty,
            "The Prince got away, I am in trouble. I better not talk to you, they " +
                "are not sure I was drunk.",
        )
        chatPlayer(neutral, "I won't say anything, your secret is safe with me.")
    }

    suspend fun ProtectedAccess.joeBeerHandIn(npc: Npc) = startDialogue(npc) {
        if (player.paStage() == StagePrepFinished) {
            joeBeer()
        } else {
            chatPlayer(
                neutral,
                "I don't see any need to give the guard my beer. I'll keep it for myself.",
            )
        }
    }

    // ================================================================= Lady Keli

    suspend fun ProtectedAccess.keliDialogue(npc: Npc) = startDialogue(npc) { keli() }

    private suspend fun Dialogue.keli() {
        chatPlayer(
            happy,
            "Are you the famous Lady Keli? Leader of the toughest gang of mercenary " +
                "killers around?",
        )
        chatNpc(bored, "I am Keli, you have heard of me then?")
        when (
            choice4(
                "Heard of you? You are famous in RuneScape!",
                1,
                "I have heard a little, but I think Katrine is tougher.",
                2,
                "I have heard rumours that you kill people.",
                3,
                "No I have never really heard of you.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliFamous()
            2 -> keliKatrine()
            3 -> keliKill()
            else -> keliNever()
        }
    }

    private suspend fun Dialogue.keliFamous() {
        chatPlayer(
            happy,
            "The great Lady Keli, of course I have heard of you. You are famous in " +
                "RuneScape!",
        )
        chatNpc(
            happy,
            "That's very kind of you to say. Reputations are not easily earned. " +
                "I have managed to succeed where many fail.",
        )
        keliMainHub()
    }

    private suspend fun Dialogue.keliKill() {
        chatPlayer(neutral, "I have heard rumours that you kill people.")
        chatNpc(
            neutral,
            "There's always someone ready to spread rumours.|I hear all sort of " +
                "ridiculous things these days.",
        )
        keliMainHub()
    }

    private suspend fun Dialogue.keliKatrine() {
        chatPlayer(neutral, "I have heard a little, but I think Katrine is tougher.")
        keliKatrineResponse()
    }

    private suspend fun Dialogue.keliKatrineResponse() {
        chatNpc(
            angry,
            "Well you can think that all you like. I know those blackarm cowards " +
                "dare not leave the city. Out here, I am the toughest. You can tell " +
                "them that! Now get out of my sight, before I call my guards.",
        )
    }

    private suspend fun Dialogue.keliNever() {
        chatPlayer(neutral, "No I have never really heard of you.")
        chatNpc(
            shocked,
            "You must be new to this land then. EVERYONE knows of Lady Keli and her " +
                "prowess with the sword.",
        )
        when (
            choice4(
                "No, still doesn't ring a bell.",
                1,
                "Yes, of course I have heard of you.",
                2,
                "You must have trained a lot for this work.",
                3,
                "I should not disturb someone as tough as you.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliBell()
            2 -> keliFamous()
            3 -> keliTrained()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliBell() {
        chatPlayer(confused, "No, still doesn't ring a bell.")
        chatNpc(
            angry,
            "Well, you know of me now. I will wring your neck if you do not show respect.",
        )
        when (
            choice3(
                "I do not show respect to killers and hoodlums.",
                1,
                "You must have trained a lot for this work",
                2,
                "I should not disturb someone as tough as you, great lady",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliHoodlums()
            2 -> keliTrained()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliHoodlums() {
        chatPlayer(neutral, "I do not show respect to killers and hoodlums.")
        chatNpc(
            angry,
            "You should, you really should. I am wealthy enough to place a bounty " +
                "on your head. Or just remove your head myself. Now go, I am busy, " +
                "too busy to fight a would-be hoodlum.",
        )
    }

    private suspend fun Dialogue.keliTrained() {
        chatPlayer(happy, "You must have trained a lot for this work.")
        chatNpc(
            angry,
            "I have used a sword since I was a small girl.|I stabbed three people " +
                "before I was 6 years old.",
        )
        when (
            choice3(
                "What is your latest plan then?",
                1,
                "I should not disturb someone as tough as you.",
                2,
                "I think Katrine is still tougher.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliPlan()
            2 -> keliNotDisturb()
            else -> keliKatrineResponse()
        }
    }

    private suspend fun Dialogue.keliNotDisturb() {
        chatPlayer(happy, "I should not disturb someone as tough as you, great lady.")
        chatNpc(
            neutral,
            "I need to do a lot of work, goodbye. When you get a little tougher, " +
                "maybe I will give you a job.",
        )
    }

    private suspend fun Dialogue.keliMainHub() {
        when (
            choice4(
                "I think Katrine is still tougher.",
                1,
                "What is your latest plan then?",
                2,
                "You must have trained a lot for this work.",
                3,
                "I should not disturb someone as tough as you.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliKatrineResponse()
            2 -> keliPlan()
            3 -> keliTrained()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliPlan() {
        chatPlayer(
            neutral,
            "What is your latest plan then?|Of course, you need not go into " +
                "specific details.",
        )
        chatNpc(neutral, "Well, I can tell you I have a valuable prisoner here in my cells.")
        chatNpc(
            neutral,
            "I can expect a high reward to be paid very soon for this guy. I can't " +
                "tell you who he is, but he is a lot colder now.",
        )
        when (
            choice4(
                "Ah I see. You must have been very skillful.",
                1,
                "Thats great, are you sure they will pay?",
                2,
                "Can you be sure they will not try to get him out?",
                3,
                "I should not disturb someone as tough as you.",
                4,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliSkillful()
            2 -> keliPay()
            3 -> keliOut()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliSkillful() {
        chatPlayer(neutral, "You must have been very skillful.")
        chatNpc(neutral, "Yes. I did most of the work. We had to grab the Pr...")
        chatNpc(
            neutral,
            "Er, we had to grab him without his ten bodyguards noticing. It was a " +
                "stroke of genius.",
        )
        when (
            choice3(
                "Are you sure they will pay?",
                1,
                "Can you be sure they will not try to get him out?",
                2,
                "I should not disturb someone as tough as you.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliPay()
            2 -> keliOut()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliPay() {
        chatPlayer(quiz, "Are you sure they will pay?")
        chatNpc(neutral, "They will pay, or we will cut his hair off and send it to them.")
        chatPlayer(quiz, "How about trying something tougher? Maybe cut his finger off?")
        chatNpc(
            neutral,
            "That's a good idea, I could use talented people like you. I may call " +
                "on you if I need work doing.",
        )
        when (
            choice3(
                "You must have been very skillful.",
                1,
                "Can you be sure they will not try to get him out?",
                2,
                "I should not disturb someone as tough as you.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliSkillful()
            2 -> keliOut()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliOut() {
        chatPlayer(neutral, "Can you be sure they will not try to get him out?")
        chatNpc(
            neutral,
            "There is no way to release him.|The only key to the door is on a chain " +
                "around my neck|and the locksmith who made the lock|died suddenly " +
                "when he had finished.",
        )
        chatNpc(neutral, "There is not another key like this in the world.")
        when (
            choice3(
                "Could I see the key please?",
                1,
                "That is a good way to keep secrets.",
                2,
                "I should not disturb someone as tough as you.",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> keliKeyPlease()
            2 -> keliSecrets()
            else -> keliNotDisturb()
        }
    }

    private suspend fun Dialogue.keliSecrets() {
        chatPlayer(neutral, "That is a good way to keep secrets.")
        chatNpc(neutral, "It is the best way I know.|Dead men tell no tales.")
        chatPlayer(neutral, "I am glad I know none of your secrets, Keli.")
    }

    private suspend fun Dialogue.keliKeyPlease() {
        chatPlayer(
            neutral,
            "Could I see the key please? Just for a moment. It would be something " +
                "I can tell my grandchildren. When you are even more famous than " +
                "you are now.",
        )
        chatNpc(
            happy,
            "As you put it that way I am sure you can see it.|You cannot steal the " +
                "key, it is on a Runite chain.",
        )
        mesbox("Keli shows you a small key on a strong looking chain.")
        if (player.paStage() == StageSpokenOsman && invTotal(access.inv, SoftClay) >= 1) {
            when (
                choice2(
                    "Could I touch the key for a moment?",
                    true,
                    "I should not disturb someone as tough as you.",
                    false,
                    title = "What would you like to say?",
                )
            ) {
                true -> keliTouchKey()
                false -> keliNotDisturb()
            }
        } else {
            chatNpc(neutral, "There, run along now, I am very busy.")
        }
    }

    private suspend fun Dialogue.keliTouchKey() {
        chatPlayer(happy, "Could I touch the key a moment please?")
        chatNpc(neutral, "Only for a moment then.")
        if (access.inv.isFull()) {
            mesbox("You don't have space to take an imprint of the key.")
            return
        }
        mesbox(
            "You put a piece of your soft clay in your hand.|As you touch the key, " +
                "you take an imprint of it."
        )
        access.invDel(access.inv, SoftClay, 1)
        access.invAdd(access.inv, KeyPrint)
        chatPlayer(happy, "Thank you so much, you are too kind, o great Keli.")
        chatNpc(neutral, "You are welcome, run along now, I am very busy.")
    }

    // =============================================================== Prince Ali

    suspend fun ProtectedAccess.princeAliDialogue(npc: Npc): Boolean {
        var rescued = false
        startDialogue(npc) {
            if (player.paStage() == StageTiedKeli) {
                rescued = princeRescue()
            } else {
                chatNpc(
                    neutral,
                    "I owe you my life for that escape.|You cannot help me this time, " +
                        "they know who you are.|Go in peace, friend of Al-Kharid.",
                )
            }
        }
        return rescued
    }

    suspend fun ProtectedAccess.princeAliItems(npc: Npc): Boolean {
        if (player.paStage() != StageTiedKeli) {
            mes(constants.dm_default)
            return false
        }
        var rescued = false
        startDialogue(npc) { rescued = princeRescue() }
        return rescued
    }

    private suspend fun Dialogue.princeRescue(): Boolean {
        chatPlayer(happy, "Prince, I come to rescue you.")
        chatNpc(neutral, "That is very very kind of you, how do I get out?")
        chatPlayer(
            happy,
            "With a disguise. I have removed the Lady Keli.|She is tied up, but " +
                "will not stay tied up for long.",
        )
        if (
            invTotal(access.inv, PrincesKey) >= 1 &&
                invTotal(access.inv, BlondWig) >= 1 &&
                invTotal(access.inv, PinkSkirt) >= 1 &&
                invTotal(access.inv, SkinPaste) >= 1
        ) {
            chatPlayer(neutral, "Take this disguise, and this key.")
            mesbox("You hand over the disguise and key over to Prince Ali.")
            access.invDel(access.inv, PrincesKey, 1)
            access.invDel(access.inv, BlondWig, 1)
            access.invDel(access.inv, PinkSkirt, 1)
            access.invDel(access.inv, SkinPaste, 1)
            princeAliQuest.setQuestStage(access, StageSaved)
            chatNpcSpecific(
                "Prince Ali",
                LadyKeli,
                happy,
                "Thank you my friend, I must leave you now.|My father will pay you " +
                    "well for this.",
            )
            chatPlayer(happy, "Go to Leela, she is close to here.")
            mesbox(
                "The prince has escaped, well done!|You are now a friend of " +
                    "Al-Kharid|and may pass through the Al-Kharid toll gate for free."
            )
            return true
        }
        return false
    }

    // ================================================== Ned / Aggie delegation

    suspend fun Dialogue.nedOtherThings() {
        chatPlayer(quiz, "Ned, could you make other things from wool?")
        chatNpc(happy, "I am sure I can. What are you thinking of?")
        when (
            choice3(
                "Could you knit me a sweater?",
                1,
                "How about some sort of wig?",
                2,
                "Could you repair the arrow holes in the back of my shirt?",
                3,
                title = "What would you like to say?",
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Could you knit me a sweater?")
                chatNpc(
                    angry,
                    "Do I look like a member of a sewing circle?|Be off wi' you. " +
                        "I have fought monsters that would turn your hair blue.",
                )
                chatNpc(
                    angry,
                    "I don't need to be laughed at just 'cos I am getting a bit old.",
                )
            }
            2 -> nedWig()
            else -> {
                chatPlayer(quiz, "Could you repair the arrow holes in the back of my shirt?")
                chatNpc(
                    neutral,
                    "Ah yes, it's a tough world these days.|There's a few brave " +
                        "enough to attack from 10 metres away.",
                )
                mesbox("Ned pulls out a needle and attacks your shirt.")
                chatNpc(happy, "There you go, good as new.")
                chatPlayer(neutral, "Thanks Ned. Maybe next time they will attack me " +
                    "face to face.")
            }
        }
    }

    private suspend fun Dialogue.nedWig() {
        chatPlayer(quiz, "How about some sort of wig?")
        chatNpc(
            happy,
            "Well... That's an interesting thought.|Yes, I think I could do " +
                "something.|Give me 3 balls of wool and I might be able to do it.",
        )
        if (invTotal(access.inv, BallOfWool) < 3) {
            chatPlayer(happy, "Great, I will get some. I think a wig would be useful.")
            return
        }
        when (
            choice2(
                "I have that now. Please, make me a wig.",
                true,
                "I will come back when I need you to make me one.",
                false,
                title = "What would you like to say?",
            )
        ) {
            true -> {
                chatPlayer(neutral, "I have that now. Please, make me a wig.")
                chatNpc(neutral, "Okay, I will have a go.")
                mesbox("You hand Ned 3 balls of wool. Ned works with the wool.|His " +
                    "hands move with a speed you couldn't imagine.")
                if (access.invDel(access.inv, BallOfWool, 3).success) {
                    access.invAdd(access.inv, PlainWig)
                    chatNpc(happy, "Here you go, how's that for a quick effort?|Not " +
                        "bad I think!")
                    mesbox("Ned gives you a pretty good wig.")
                    chatPlayer(neutral, "Thanks Ned, there's more to you than meets the eye.")
                }
            }
            false -> chatPlayer(neutral, "I will come back when I need you to make me one.")
        }
    }

    suspend fun Dialogue.aggiePaste() {
        chatPlayer(quiz, "Could you think of a way to make skin paste?")
        if (
            invTotal(access.inv, Redberries) > 0 &&
                invTotal(access.inv, PotFlour) > 0 &&
                (invTotal(access.inv, BucketWater) > 0 || invTotal(access.inv, JugWater) > 0) &&
                invTotal(access.inv, Ashes) > 0
        ) {
            chatNpc(
                happy,
                "Yes I can, I see you already have the ingredients. Would you like " +
                    "me to mix some for you now?",
            )
            when (
                choice2(
                    "Yes please. Mix me some skin paste.",
                    true,
                    "No thank you, I don't need any skin paste right now.",
                    false,
                    title = "What would you like to say?",
                )
            ) {
                true -> aggieMixPaste()
                false -> {
                    chatPlayer(neutral, "No thank you, I don't need any skin paste right now.")
                    chatNpc(neutral, "Okay dearie, that's always your choice.")
                }
            }
        } else {
            chatNpc(
                happy,
                "Why, it's one of my most popular potions.|The women here, they like " +
                    "to have smooth looking skin.|And I must admit, some of the men " +
                    "buy it as well.",
            )
            chatNpc(happy, "I can make it for you, just get me what's needed.")
            chatPlayer(quiz, "What do you need to make it?")
            chatNpc(
                neutral,
                "Well dearie, you need a base for the paste.|That's a mix of ash, " +
                    "flour and water.|Then you need redberries to colour it as you " +
                    "want.|Bring me those four items and I will make you some.",
            )
        }
    }

    private suspend fun Dialogue.aggieMixPaste() {
        chatPlayer(happy, "Yes please. Mix me some skin paste.")
        chatNpc(happy, "That should be simple. Hand the things to Aggie then.")
        if (invTotal(access.inv, BucketWater) > 0) {
            access.invDel(access.inv, BucketWater, 1)
        } else {
            access.invDel(access.inv, JugWater, 1)
        }
        mesbox("You hand the ash, flour, water and redberries to Aggie.|Aggie tips " +
            "the ingredients into a cauldron|and mutters some words.")
        chatNpc(
            confused,
            "Tourniquet, Fenderbaum, Tottenham, Marshmallow, Marblearch.",
        )
        access.invDel(access.inv, Redberries, 1)
        access.invDel(access.inv, PotFlour, 1)
        access.invDel(access.inv, Ashes, 1)
        access.invAdd(access.inv, SkinPaste)
        mesbox("Aggie hands you the skin paste.")
        chatNpc(
            happy,
            "There you go dearie, your skin potion.|That will make you look good " +
                "at the Varrock dances.",
        )
    }
}
