package org.rsmod.content.quest.area.varrock.dragonslayer

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.CrandorBeach
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.ShipPrice
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageBoughtShip
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageComplete
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageElvargSlain
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageNedGivenMap
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageRepairedShip
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageSailed
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageSpokenToGuildmaster
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.StageSpokenToOziach
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayer.Companion.WormbrainPrice
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

var Player.dragonQuest by intVarp("varp.dragonquest")

private val Player.questPoints by intVarp("varp.qp")

internal var Player.dsUsedSilk by boolVarBit("varbit.dragonslayer_used_silk")
internal var Player.dsUsedBowl by boolVarBit("varbit.dragonslayer_used_bowl_unfired")
internal var Player.dsUsedLobsterPot by boolVarBit("varbit.dragonslayer_used_lobster_pot")
internal var Player.dsUsedMindBomb by boolVarBit("varbit.dragonslayer_used_wizards_mind_bomb")
internal var Player.dsAskedNed by boolVarBit("varbit.dragonslayer_asked_ned")
internal var Player.dsNedKnowsFixed by boolVarBit("varbit.dragonslayer_ned_knows_ship_is_fixed")
internal var Player.dsAskedGenerals by boolVarBit("varbit.dragonslayer_asked_generals")
internal var Player.dsInstructionsMelzar by boolVarBit("varbit.dragonslayer_instructions_melzar")
internal var Player.dsInstructionsOracle by boolVarBit("varbit.dragonslayer_instructions_oracle")
internal var Player.dsInstructionsWormbrain by
    boolVarBit("varbit.dragonslayer_instructions_wormbrain")
internal var Player.dsInstructionsShip by boolVarBit("varbit.dragonslayer_instructions_ship")
internal var Player.dsInstructionsShield by boolVarBit("varbit.dragonslayer_instructions_shield")
internal var Player.dsShipFixed1 by boolVarBit("varbit.dragonslayer_ship_onethird_fixed")
internal var Player.dsShipFixed2 by boolVarBit("varbit.dragonslayer_ship_twothird_fixed")
internal var Player.dsShipFixed3 by boolVarBit("varbit.dragonslayer_ship_fullyfixed")
internal var Player.dsFoundSecretDoor by
    boolVarBit("varbit.dragonslayer_crandor_found_secret_door")
internal var Player.dsSecretTold by boolVarBit("varbit.dragonslayer_secret_told")
internal var Player.dsChampionExplained by
    boolVarBit("varbit.dragonslayer_champion_explained_elvarg")

val Player.gettingMapParts: Boolean
    get() = dragonQuest >= StageSpokenToOziach && dragonQuest < StageNedGivenMap

val Player.knowsAboutOracle: Boolean
    get() = dsInstructionsOracle

val Player.magicDoorOpen: Boolean
    get() = dsUsedSilk && dsUsedBowl && dsUsedLobsterPot && dsUsedMindBomb

fun Player.hasAnywhere(obj: String): Boolean =
    inv.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

val Player.shipFixed: Boolean
    get() = dsShipFixed1 && dsShipFixed2 && dsShipFixed3

private const val RequiredQuestPoints = 32

object DragonSlayerDialogue {

    // ------------------------------------------------------------- Guildmaster

    suspend fun ProtectedAccess.guildmasterDialogue(npc: Npc) {
        startDialogue(npc) { guildmaster() }
    }

    private suspend fun Dialogue.guildmaster() {
        chatNpc(neutral, "Greetings!")
        when (player.dragonQuest) {
            0 -> guildmasterStart()
            StageSpokenToGuildmaster -> guildmasterReminder()
            in StageSpokenToOziach until StageSailed -> {
                when (
                    choice3(
                        "What is this place?",
                        1,
                        "I talked to Oziach...",
                        2,
                        "About my quest to kill the dragon...",
                        3,
                    )
                ) {
                    1 -> whatIsThisPlace()
                    else -> aboutDragonQuest()
                }
            }
            StageElvargSlain -> {
                when (choice2("What is this place?", 1, "I killed the dragon!", 2)) {
                    1 -> whatIsThisPlace()
                    2 -> {
                        chatPlayer(happy, "I killed the dragon!")
                        chatNpc(happy, "Really? That's amazing!")
                        chatNpc(
                            happy,
                            "You are a true hero. There's no way that Oziach can refuse " +
                                "to sell you rune platemail now. You should tell him at once!",
                        )
                    }
                }
            }
            else -> whatIsThisPlace()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            neutral,
            "This is the Champions' Guild. Only adventurers who have proved themselves " +
                "worthy by gaining influence from quests are allowed in here.",
        )
    }

    private suspend fun Dialogue.guildmasterStart() {
        when (choice2("What is this place?", 1, "Can I have a quest?", 2)) {
            1 -> whatIsThisPlace()
            2 -> {
                chatPlayer(quiz, "Can I have a quest?")
                if (access.player.questPoints < RequiredQuestPoints) {
                    chatNpc(
                        neutral,
                        "Yes, I have a quest for a brave adventurer, but you need to be " +
                            "more experienced first. Come back when you have 32 quest " +
                            "points.",
                    )
                    return
                }
                chatNpc(happy, "Aha!")
                chatNpc(
                    happy,
                    "Yes! A mighty and perilous quest fit only for the most powerful " +
                        "champions! And, at the end of it, you will earn the right to wear " +
                        "the legendary rune platebody!",
                )
                when (
                    choice2("Yes.", 1, "No.", 2, title = "Start the Dragon Slayer I quest?")
                ) {
                    1 -> {
                        chatPlayer(quiz, "So, what is this quest?")
                        chatNpc(
                            neutral,
                            "You'll have to speak to Oziach, the maker of rune armour. He " +
                                "sets the quests that champions must complete in order to " +
                                "wear it.",
                        )
                        chatNpc(
                            neutral,
                            "Oziach lives in a hut, by the cliffs to the west of Edgeville. " +
                                "He can be a little...odd...sometimes, though.",
                        )
                        access.player.dragonQuest = StageSpokenToGuildmaster
                    }
                    2 -> chatPlayer(sad, "Actually, I'll give it a miss.")
                }
            }
        }
    }

    private suspend fun Dialogue.guildmasterReminder() {
        when (choice2("What is this place?", 1, "Can I have a quest?", 2)) {
            1 -> whatIsThisPlace()
            2 -> {
                chatPlayer(quiz, "Can I have a quest?")
                chatNpc(
                    quiz,
                    "You're already on a quest for me, if I recall correctly. Have you " +
                        "talked to Oziach yet?",
                )
                chatPlayer(sad, "No, not yet.")
                chatNpc(
                    neutral,
                    "Well, he's the only one who can grant you the right to wear rune " +
                        "platemail. He lives in a hut, by the cliffs west of Edgeville.",
                )
                chatPlayer(neutral, "Okay, I'll go and talk to him.")
            }
        }
    }

    private suspend fun Dialogue.aboutDragonQuest() {
        chatPlayer(quiz, "About my quest to kill the dragon...")
        elvargStory()
        dragonQuestOptions()
    }

    private suspend fun Dialogue.elvargStory() {
        if (!player.dsChampionExplained) {
            chatNpc(quiz, "Oh? What did he tell you to do?")
            chatPlayer(neutral, "Defeat the dragon of Crandor.")
            chatNpc(shocked, "The dragon of Crandor?")
            chatPlayer(quiz, "Um, yes...")
            chatNpc(sad, "Goodness, he hasn't given you an easy job, has he?")
            chatPlayer(quiz, "What's so special about this dragon?")
            dragonStoryText()
            player.dsChampionExplained = true
        }
        chatNpc(
            neutral,
            "If you're serious about taking on Elvarg, first you'll need to get to " +
                "Crandor, and to my knowledge, the only way to do that is by sea.",
        )
        chatPlayer(quiz, "So I just sail over there? Seems easy enough.")
        chatNpc(
            neutral,
            "If only that were true. The island is surrounded by dangerous underwater " +
                "reefs. You'll need a map to guide you through them and a ship capable " +
                "of doing so.",
        )
        chatNpc(
            neutral,
            "And then of course there's the dragon herself. Once you reach Crandor, " +
                "you'll need some kind of protection against her breath.",
        )
    }

    private suspend fun Dialogue.dragonStoryText() {
        chatNpc(
            neutral,
            "Thirty years ago, Crandor was a thriving community with a great tradition of " +
                "mages and adventurers. Many Crandorians even earned the right to be part " +
                "of the Champions' Guild!",
        )
        chatNpc(
            neutral,
            "One of their adventurers went too far, however. He descended into the " +
                "volcano in the centre of Crandor and woke the dragon Elvarg.",
        )
        chatNpc(
            neutral,
            "He must have fought valiantly against the dragon because they say that, to " +
                "this day, she has a scar down her side,",
        )
        chatNpc(
            neutral,
            "but the dragon still won the fight. She emerged and laid waste to the whole " +
                "of Crandor with her fire breath!",
        )
        chatNpc(
            neutral,
            "Some refugees managed to escape in fishing boats. They landed on the coast, " +
                "north of Rimmington, and set up camp but the dragon followed them and " +
                "burned the camp to the ground.",
        )
        chatNpc(
            neutral,
            "Out of all the people of Crandor there were only three survivors: a trio of " +
                "wizards who used magic to escape. Their names were Thalzar, Lozar and " +
                "Melzar.",
        )
    }

    private suspend fun Dialogue.dragonQuestOptions() {
        while (true) {
            when (
                choice4(
                    "Where can I find a map to Crandor?",
                    1,
                    "What kind of ship do I need?",
                    2,
                    "How can I protect myself from the dragon's breath?",
                    3,
                    "Okay, I'll get going!",
                    4,
                )
            ) {
                1 -> mapOptions()
                2 -> {
                    chatPlayer(quiz, "What kind of ship do I need?")
                    chatNpc(
                        neutral,
                        "Quite a specific one I'm afraid. Even with a map, very few ships " +
                            "will be able to navigate the reefs around the island. You're " +
                            "going to need one of Crandorian design.",
                    )
                    chatNpc(
                        neutral,
                        "If there's still one in existence, it's probably in Port Sarim. " +
                            "However, a ship will also need a captain, and I'm not sure " +
                            "where you'd find one willing to sail to Crandor!",
                    )
                    player.dsInstructionsShip = true
                }
                3 -> {
                    chatPlayer(quiz, "How can I protect myself from the dragon's breath?")
                    chatNpc(
                        neutral,
                        "That part shouldn't be too difficult, actually. I believe the " +
                            "Duke of Lumbridge has a special shield in his armoury that is " +
                            "enchanted against dragon's breath.",
                    )
                    player.dsInstructionsShield = true
                }
                4 -> {
                    chatPlayer(neutral, "Okay, I'll get going!")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.mapOptions() {
        chatPlayer(quiz, "Where can I find a map to Crandor?")
        if (!player.dsInstructionsMelzar &&
            !player.dsInstructionsOracle &&
            !player.dsInstructionsWormbrain
        ) {
            chatNpc(
                neutral,
                "After what happened, people were afraid of enraging the dragon. Most maps " +
                    "to the island were destroyed, but I have heard that one survived. It " +
                    "was held by Melzar, Thalzar and Lozar.",
            )
            chatNpc(
                neutral,
                "However, the three apparently split the map into three pieces, with each " +
                    "of them taking one piece.",
            )
        }
        while (true) {
            when (
                choice3(
                    "Where is Melzar's map piece?",
                    1,
                    "Where is Thalzar's map piece?",
                    2,
                    "Where is Lozar's map piece?",
                    3,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Where is Melzar's map piece?")
                    chatNpc(
                        neutral,
                        "Melzar built a castle on the site of the Crandorian refugee camp, " +
                            "north of Rimmington. He's locked himself in there and no one's " +
                            "seen him for years.",
                    )
                    chatNpc(
                        neutral,
                        "The inside of his castle is like a maze, and is populated by " +
                            "undead monsters. Maybe, if you could get all the way through " +
                            "the maze, you could find his piece of the map.",
                    )
                    if (!player.hasAnywhere("obj.melzarkey")) {
                        chatNpc(
                            neutral,
                            "Adventurers sometimes go in there to prove themselves, so I " +
                                "can give you this key to Melzar's Maze.",
                        )
                        objbox("obj.melzarkey", "The Guildmaster hands you a key.")
                        access.invAdd(access.inv, "obj.melzarkey")
                    }
                    player.dsInstructionsMelzar = true
                }
                2 -> {
                    chatPlayer(quiz, "Where is Thalzar's map piece?")
                    chatNpc(
                        neutral,
                        "Thalzar was the most paranoid of the three wizards. He hid his map " +
                            "piece and took the secret of its location to his grave.",
                    )
                    chatNpc(
                        neutral,
                        "I don't think you'd be able to find out where it is by ordinary " +
                            "means. You'll need to talk to the Oracle on Ice Mountain.",
                    )
                    player.dsInstructionsOracle = true
                }
                3 -> {
                    chatPlayer(quiz, "Where is Lozar's map piece?")
                    chatNpc(
                        neutral,
                        "A few weeks ago, I'd have told you to speak to Lozar herself, in " +
                            "her house across the river from Lumbridge.",
                    )
                    chatNpc(
                        neutral,
                        "Unfortunately, goblin raiders killed her and stole everything. One " +
                            "of the goblins from the Goblin Village probably has the map " +
                            "piece now.",
                    )
                    player.dsInstructionsWormbrain = true
                }
            }
            return
        }
    }

    // ----------------------------------------------------------------- Oziach

    suspend fun ProtectedAccess.oziachQuest(npc: Npc) {
        startDialogue(npc) { oziachStart() }
    }

    private suspend fun Dialogue.oziachStart() {
        chatNpc(shifty, "Aye, 'tis a fair day my friend.")
        when (choice3("Can you sell me a rune platebody?", 1, "I'm not your friend.", 2, "Yes, it's a very nice day.", 3)) {
            1 -> {
                chatPlayer(quiz, "Can you sell me a rune platebody?")
                chatNpc(quiz, "So, how does thee know I 'ave some?")
                when (choice2("The Guildmaster of the Champions' Guild told me.", 1, "I am a master detective.", 2)) {
                    1 -> {
                        chatPlayer(neutral, "The Guildmaster of the Champions' Guild told me.")
                        chatNpc(
                            angry,
                            "Yes, I suppose he would, wouldn't he? He's always sending you " +
                                "fancy-pants 'heroes' up to bother me. Telling me I'll give " +
                                "them a quest or sommat like that.",
                        )
                    }
                    2 -> {
                        chatPlayer(neutral, "I am a master detective.")
                    }
                }
                chatNpc(
                    angry,
                    "Well, I'm not going to let just anyone wear my rune platemail. It's " +
                        "only for heroes. So, leave me alone.",
                )
                when (choice2("I thought you were going to give me a quest.", 1, "That's a pity, I'm not a hero.", 2)) {
                    1 -> oziachGiveQuest()
                    2 -> {
                        chatPlayer(sad, "That's a pity, I'm not a hero.")
                        chatNpc(angry, "Aye, I ken tell!")
                    }
                }
            }
            2 -> {
                chatPlayer(angry, "I'm not your friend.")
                chatNpc(angry, "I'm surprised if you're anyone's friend with those kind of manners.")
            }
            3 -> {
                chatPlayer(neutral, "Yes, it's a very nice day.")
                chatNpc(neutral, "Aye, may the gods walk by yer side.")
            }
        }
    }

    private suspend fun Dialogue.oziachGiveQuest() {
        chatPlayer(quiz, "I thought you were going to give me a quest.")
        chatNpc(angry, "*Sigh*")
        chatNpc(neutral, "All right, I'll give ye a quest. I'll let ye wear my rune platemail if ye...")
        chatNpc(angry, "Slay the dragon of Crandor!")
        when (
            choice2(
                "A dragon, that sounds like fun.",
                1,
                "I may be a champion, but I don't think I'm up to dragon-killing yet.",
                2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "A dragon, that sounds like fun!")
                chatNpc(
                    shifty,
                    "Hah, yes, you are a typical reckless adventurer, aren't you? Now go " +
                        "kill the dragon and get out of my face.",
                )
                chatPlayer(quiz, "But how can I defeat the dragon?")
                chatNpc(
                    angry,
                    "Go talk to the Guildmaster in the Champions' Guild. He'll help ye " +
                        "out if yer so keen on doing a quest. I'm not going to be " +
                        "handholding any adventurers.",
                )
                access.player.dragonQuest = StageSpokenToOziach
            }
            2 -> {
                chatPlayer(sad, "I may be a champion, but I don't think I'm up to dragon-killing yet.")
                chatNpc(neutral, "Yes, I can understand that. Yer a coward.")
            }
        }
    }

    suspend fun ProtectedAccess.oziachMidQuest(npc: Npc) {
        startDialogue(npc) {
            chatNpc(quiz, "Have ye slayed that dragon yet?")
            if (access.player.dragonQuest >= StageElvargSlain) {
                chatPlayer(happy, "Yes, I have!")
                if (access.player.inv.contains("obj.dragon_slayer_qip_elvargs_head")) {
                    chatPlayer(neutral, "I have its head here.")
                }
                chatNpc(shocked, "You actually did it?")
                chatNpc(sad, "I underestimated ye, adventurer. I apologise.")
                chatNpc(happy, "Yer a true hero, and I'd be happy to sell ye rune platebodies.")
                access.player.dragonQuest = StageComplete
            } else {
                chatPlayer(sad, "Um... no.")
                chatNpc(angry, "Be off with ye then.")
            }
        }
    }

    // ---------------------------------------------------------------- Klarense

    suspend fun ProtectedAccess.klarenseDialogue(npc: Npc) {
        startDialogue(npc) { klarense() }
    }

    private suspend fun Dialogue.klarense() {
        when {
            player.dragonQuest >= StageSailed -> {
                chatNpc(
                    shocked,
                    "Wow! You sure are lucky! Seems the Lady Lumbridge just washed right " +
                        "up into the dock by herself!",
                )
                chatNpc(sad, "She's pretty badly damaged, though...")
                klarenseCaptainMenu()
            }
            player.dragonQuest >= StageBoughtShip -> {
                chatNpc(
                    happy,
                    "Hello, captain! Here to inspect your new ship? Just a little work " +
                        "and she'll be seaworthy again.",
                )
                klarenseCaptainMenu()
            }
            else -> klarenseSell()
        }
    }

    private suspend fun Dialogue.klarenseSell() {
        chatNpc(
            neutral,
            "Hello there! Interested in buying this fine ship? I'm sure I can offer her " +
                "to you for a much better price than any of those shipwrights ask for!",
        )
        chatPlayer(quiz, "A much better price? What's the catch?")
        chatNpc(neutral, "Well, she's not quite seaworthy right now...")
        chatPlayer(quiz, "Not seaworthy? Why would I want to buy her then?")
        chatNpc(
            happy,
            "Because this isn't just any ship! This is the Lady Lumbridge, one of the " +
                "last Crandorian ships! Despite her size, she's one of the most agile " +
                "ships around!",
        )
        chatPlayer(quiz, "Except she's not seaworthy...")
        chatNpc(happy, "No, but a bit of work will quickly sort that out!")
        when (
            choice3(
                "I'd like to buy her.",
                1,
                "Why is she damaged?",
                2,
                "I think I'll pass.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "I'd like to buy her.")
                chatNpc(happy, "Of course! I'm sure the work needed to do on it wouldn't be too expensive.")
                chatNpc(
                    happy,
                    "How does 2,000 gold sound? I'll even throw in my cabin boy, Jenkins, " +
                        "for free! He'll swab the decks and splice the mainsails for you!",
                )
                when (choice2("Yep, sounds good.", 1, "I'm not paying that much for a broken boat!", 2)) {
                    1 -> {
                        chatPlayer(happy, "Yep, sounds good.")
                        if (access.invTotal(access.inv, "obj.coins") < ShipPrice) {
                            chatPlayer(sad, "...except I don't have that kind of money on me...")
                        } else {
                            access.invDel(access.inv, "obj.coins", ShipPrice)
                            access.player.dragonQuest = StageBoughtShip
                            chatNpc(happy, "Okey-dokey! She's all yours!")
                        }
                    }
                    2 -> {
                        chatPlayer(angry, "I'm not paying that much for a broken boat!")
                        chatNpc(sad, "That's fair enough, I suppose.")
                    }
                }
            }
            2 -> {
                chatPlayer(quiz, "Why is she damaged?")
                chatNpc(
                    neutral,
                    "Oh, there was no particular accident, just years of wear and tear. " +
                        "It's been a long time since Crandor was destroyed and she's had " +
                        "several owners since then. Unfortunately, not all of them looked " +
                        "after her.",
                )
            }
            3 -> chatPlayer(sad, "I think I'll pass.")
        }
    }

    private suspend fun Dialogue.klarenseCaptainMenu() {
        while (true) {
            when (
                choice4(
                    "So, what needs fixing on the ship?",
                    1,
                    "Would you sail this ship to Crandor for me?",
                    2,
                    "What are you going to do now you don't have a ship?",
                    3,
                    "Can I board the ship now?",
                    4,
                )
            ) {
                1 -> {
                    if (player.shipFixed) {
                        chatNpc(quiz, "I dunno. It looks fine to me.")
                        chatPlayer(quiz, "But you said it needed fix-")
                        chatNpc(happy, "Yes, but that was before you fixed it!")
                    } else {
                        chatPlayer(quiz, "So, what needs fixing on this ship?")
                        chatNpc(
                            neutral,
                            "Well, the big gaping hole in the hold is the problem. You'll " +
                                "need a few wooden planks hammered in with steel nails " +
                                "to fix it.",
                        )
                    }
                }
                2 -> {
                    chatPlayer(quiz, "So would you like to sail this ship to Crandor for me?")
                    chatNpc(shocked, "Crandor? You're joking, right?")
                    when (choice2("Yes. Ha ha ha!", 1, "No. I want to go to Crandor.", 2)) {
                        1 -> {
                            chatPlayer(happy, "Yes. Ha ha ha!")
                            chatNpc(
                                angry,
                                "Crandor's not something we sailors joke about. You can't " +
                                    "sail from here to Catherby, or Entrana, or Ardougne " +
                                    "without going past that accursed island.",
                            )
                            chatNpc(
                                neutral,
                                "You can't get close to it because of the reefs, but you " +
                                    "can always see it.",
                            )
                            chatNpc(
                                neutral,
                                "Sometimes you can see a dark shape in the sky, circling " +
                                    "the island. That's when you have to sail on as quick " +
                                    "as you can and pray it's not hungry. Every year, more " +
                                    "ships are lost to that dragon.",
                            )
                        }
                        2 -> {
                            chatPlayer(neutral, "No. I want to go to Crandor.")
                            chatNpc(shocked, "Then you must be crazy.")
                            chatNpc(
                                neutral,
                                "That island is surrounded by reefs that would rip this " +
                                    "ship to shreds. Even if you found a map, you'd need " +
                                    "an experienced captain to stand a chance of getting " +
                                    "through and, even if I could get to it, there's no way " +
                                    "I'm going any closer to that dragon than I have to. " +
                                    "They say it can destroy whole ships with one bite.",
                            )
                        }
                    }
                }
                3 -> {
                    chatPlayer(quiz, "What are you going to do now you don't have a ship?")
                    chatNpc(happy, "Oh, I'll be fine. I've got work as Port Sarim's first lifeguard!")
                }
                4 -> {
                    chatPlayer(quiz, "Can I board the ship now?")
                    chatNpc(happy, "Sure thing, she's all yours.")
                    return
                }
            }
        }
    }

    // ----------------------------------------------------------------- Ned

    suspend fun Dialogue.nedSail() {
        chatPlayer(quiz, "You're a sailor? Could you take me to the island of Crandor?")
        if (!access.player.dsAskedNed) {
            access.player.dsAskedNed = true
            chatNpc(
                neutral,
                "Well, I was a sailor. I've not been able to get work at sea these days, " +
                    "though. They say I'm too old.",
            )
            chatNpc(quiz, "Sorry, where was it you said you wanted to go?")
            chatPlayer(neutral, "To the island of Crandor.")
            chatNpc(shocked, "Crandor?")
            chatNpc(
                neutral,
                "But... It would be a chance to sail a ship once more. I'd sail anywhere " +
                    "if it was a chance to sail again.",
            )
            chatNpc(neutral, "Then again, no captain in his right mind would sail to that island...")
            chatNpc(happy, "Ah, you only live once! I'll do it!")
        } else {
            chatNpc(neutral, "I said I would and old Ned is a man of his word!")
        }
        chatNpc(quiz, "So, where's your ship?")
        if (access.player.dragonQuest < StageBoughtShip) {
            chatPlayer(sad, "Well, I don't actually have a ship yet.")
            chatNpc(neutral, "Ah, well. Let me know when you do. The place to look would be Port Sarim.")
            return
        }
        chatPlayer(neutral, "It's the Lady Lumbridge, in Port Sarim.")
        chatNpc(quiz, "That old pile of junk? Last I heard, she wasn't seaworthy.")
        if (!access.player.shipFixed) {
            chatPlayer(sad, "Yes, good point. I'd better go and fix her up.")
            return
        }
        access.player.dsNedKnowsFixed = true
        chatPlayer(happy, "I fixed her up!")
        chatNpc(happy, "You did? Excellent!")
        chatNpc(neutral, "Just show me the map and we can get ready to go!")
        when {
            access.player.inv.contains("obj.dragonmap") -> {
                chatPlayer(neutral, "Here you go.")
                mesbox("You hand the map to Ned.")
                access.invDel(access.inv, "obj.dragonmap", 1)
                access.player.dragonQuest = StageNedGivenMap
                chatNpc(happy, "Excellent! I'll meet you at the ship, then.")
            }
            access.player.inv.contains("obj.mappart1") &&
                access.player.inv.contains("obj.mappart2") &&
                access.player.inv.contains("obj.mappart3") -> {
                chatPlayer(neutral, "Here you go.")
                mesbox("You hand the map parts to Ned.")
                access.invDel(access.inv, "obj.mappart1", 1)
                access.invDel(access.inv, "obj.mappart2", 1)
                access.invDel(access.inv, "obj.mappart3", 1)
                access.player.dragonQuest = StageNedGivenMap
                chatNpc(happy, "Excellent! I'll meet you at the ship, then.")
            }
            else -> {
                chatPlayer(sad, "Uh...yeah...about that. I don't actually have a map on me.")
                chatNpc(
                    neutral,
                    "You'd better go find it, then. I'm not going to go sailing off " +
                        "without a map to show me where to head!",
                )
            }
        }
    }

    suspend fun Dialogue.nedOnShip() {
        chatNpc(
            happy,
            "Ah, it's good to be on board a ship again! No matter how long I live on " +
                "land, a ship will always seem better. Are you ready to depart?",
        )
        if (choice2("Yes, let's go!", true, "No, I'm not quite ready yet.", false)) {
            chatPlayer(happy, "Yes, let's go!")
            sailToCrandor()
        } else {
            chatPlayer(sad, "No, I'm not quite ready yet.")
            chatNpc(
                neutral,
                "Well, you go do whatever you need to do. I'll wait here for you until " +
                    "you're ready.",
            )
        }
    }

    private suspend fun Dialogue.sailToCrandor() {
        mesbox("The ship sets sail...")
        access.delay(3)
        mesbox("The ship is sailing across the ocean...")
        access.delay(3)
        mesbox("You hear a loud crack as the ship comes to a juddering halt.")
        chatNpc(neutral, "Um... I think we're there. I probably should have paid closer attention.")
        chatPlayer(angry, "Gee.. you think?")
        access.player.dragonQuest = StageSailed
        access.telejump(CrandorBeach)
    }

    suspend fun Dialogue.nedOnCrandor() {
        chatNpc(
            sad,
            "Now I see why all the other captains said I'd be mad to go near Crandor. " +
                "It looks like we're stranded.",
        )
        if (access.player.dsFoundSecretDoor && !access.player.dsSecretTold) {
            access.player.dsSecretTold = true
            chatPlayer(
                neutral,
                "Don't worry about that. I found a secret passage in the dragon's lair " +
                    "that leads off the island!",
            )
            chatNpc(
                happy,
                "Oh, that's good. I'll be able to get back to Draynor somehow, if I can " +
                    "sneak past all the skeletons and demons on this island!",
            )
        }
    }

    // ---------------------------------------------------------------- Jenkins

    suspend fun ProtectedAccess.jenkinsDialogue(npc: Npc) {
        startDialogue(npc) { jenkins() }
    }

    private suspend fun Dialogue.jenkins() {
        if (access.player.dragonQuest >= StageNedGivenMap && access.random.of(0, 1) == 0) {
            captainBanter()
            return
        }
        chatNpc(happy, "Ahoy! What d'ye think of yer ship then?")
        when (
            choice3(
                "I'm ready to go back to shore.",
                1,
                "I'd like to inspect her some more.",
                2,
                "Can you sail this ship to Crandor?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'm ready to go back on shore.")
                mesbox("You disembark from the ship.")
            }
            2 -> {
                chatPlayer(neutral, "I'd like to inspect her some more.")
                chatNpc(happy, "Aye.")
            }
            3 -> {
                chatPlayer(quiz, "Can you sail this ship to Crandor?")
                chatNpc(neutral, "Not me, sir! I'm just an 'umble cabin boy. You'll need a proper cap'n.")
                chatPlayer(quiz, "Where can I find a captain?")
                chatNpc(
                    neutral,
                    "The cap'ns round 'ere seem to be a mite scared of Crandor. I ask 'em " +
                        "why and they just say it was afore my time,",
                )
                chatNpc(
                    neutral,
                    "but there is one cap'n I reckon might 'elp. I 'eard there's a retired " +
                        "'un who lives in Draynor Village who's so desperate to sail again " +
                        "'e'd take any job.",
                )
                chatNpc(neutral, "I can't remember 'is name, but 'e lives in Draynor Village an' makes rope.")
            }
        }
    }

    private suspend fun Dialogue.captainBanter() {
        when (access.random.of(0, 4)) {
            0 -> {
                chatNpc(happy, "Arr, Jim lad!")
                chatPlayer(neutral, "It's Jenkins, cap'n!")
                chatNpc(happy, "Arr, Jenkins lad!")
            }
            1 -> {
                chatNpc(happy, "Shiver me timbers!")
                chatPlayer(happy, "Aye aye, cap'n!")
            }
            2 -> {
                chatNpc(happy, "Splice the mainsail!")
                chatPlayer(happy, "Aye aye, cap'n!")
            }
            3 -> {
                chatNpc(happy, "Swab the deck!")
                chatPlayer(happy, "Aye aye, cap'n!")
            }
            else -> {
                chatNpc(happy, "Weigh the anchor!")
                chatPlayer(sad, "It's too heavy, cap'n!")
            }
        }
    }

    // ------------------------------------------------------------------ Oracle

    suspend fun Dialogue.oracleMapPiece() {
        chatPlayer(neutral, "I seek a piece of the map to the island of Crandor.")
        chatNpc(
            neutral,
            "The map's behind a door below, but entering is rather tough. This is what " +
                "you need to know: You must use the following stuff.",
        )
        chatNpc(
            neutral,
            "First, a drink used by a mage. Next, some worm string, changed to sheet. " +
                "Then, a small crustacean cage. Last, a bowl that's not seen heat.",
        )
    }

    // ---------------------------------------------------------- Goblin generals

    suspend fun Dialogue.generalsMapPiece() {
        chatPlayer(quiz, "I've heard that one of your number has got hold of part of a map.")
        chatNpc(happy, "Aha, that'd be Wormbrain.")
        chatPlayer(quiz, "Where would he be?")
        chatNpc(neutral, "Wormbrain steals too much. He got caught. Now he lives in Port Sarim town jail.")
        access.player.dsAskedGenerals = true
    }

    // --------------------------------------------------------------- Wormbrain

    suspend fun ProtectedAccess.wormbrainDialogue(npc: Npc) {
        startDialogue(npc) {
            chatNpc(quiz, "Whut you want?")
            val canAskMap =
                player.gettingMapParts &&
                    player.dsInstructionsWormbrain &&
                    !player.hasAnywhere("obj.mappart2") &&
                    !player.hasAnywhere("obj.dragonmap")
            if (canAskMap) {
                when (
                    choice3(
                        "I believe you've got a piece of map that I need.",
                        1,
                        "What are you in for?",
                        2,
                        "Sorry, thought this was a zoo.",
                        3,
                    )
                ) {
                    1 -> wormbrainMap()
                    2 -> wormbrainInFor()
                    3 -> wormbrainZoo()
                }
            } else {
                when (choice2("What are you in for?", 1, "Sorry, thought this was a zoo.", 2)) {
                    1 -> wormbrainInFor()
                    2 -> wormbrainZoo()
                }
            }
        }
    }

    private suspend fun Dialogue.wormbrainInFor() {
        chatPlayer(quiz, "What are you in for?")
        chatNpc(confused, "Me not sure. Me pick some stuff up and take it away.")
        chatPlayer(neutral, "Well, did the stuff belong to you?")
        chatNpc(confused, "Umm...no.")
        chatPlayer(neutral, "Well, that would be why then.")
        chatNpc(sad, "Oh, right.")
    }

    private suspend fun Dialogue.wormbrainZoo() {
        chatPlayer(neutral, "Sorry, thought this was a zoo.")
    }

    private suspend fun Dialogue.wormbrainMap() {
        chatPlayer(quiz, "I believe you've got a piece of map that I need.")
        chatNpc(quiz, "So? Why should I be giving it to you? What you do for Wormbrain?")
        wormbrainChoices()
    }

    private suspend fun Dialogue.wormbrainChoices() {
        when (
            choice4(
                "I'm not going to do anything for you. Forget it.",
                1,
                "I'll let you live. I could just kill you.",
                2,
                "I suppose I could pay you for the map piece...",
                3,
                "Where did you get the map piece from?",
                4,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'm not going to do anything for you. Forget it.")
                chatNpc(neutral, "Be dat way then.")
            }
            2 -> {
                chatPlayer(angry, "I'll let you live. I could just kill you.")
                chatNpc(laugh, "Ha! Me in here and you out dere. You not get map piece.")
            }
            3 -> wormbrainPay()
            4 -> {
                wormbrainStory()
                when (
                    choice3(
                        "I'm not going to do anything for you. Forget it.",
                        1,
                        "I'll let you live. I could just kill you.",
                        2,
                        "I suppose I could pay you for the map piece...",
                        3,
                    )
                ) {
                    1 -> {
                        chatPlayer(neutral, "I'm not going to do anything for you. Forget it.")
                        chatNpc(neutral, "Be dat way then.")
                    }
                    2 -> {
                        chatPlayer(angry, "I'll let you live. I could just kill you.")
                        chatNpc(laugh, "Ha! Me in here and you out dere. You not get map piece.")
                    }
                    3 -> wormbrainPay()
                }
            }
        }
    }

    private suspend fun Dialogue.wormbrainStory() {
        chatPlayer(quiz, "Where did you get the map piece from?")
        chatNpc(laugh, "We rob house of stupid wizard. She very old, not put up much fight at all. Hahaha!")
        chatPlayer(angry, "Uh... Hahaha.")
        chatNpc(neutral, "Her house full of pictures of a city on island and old pictures of people. Me not recognise island.")
        chatNpc(shifty, "Me find map piece. Me not know what it is, but it in locked box so me figure it important.")
        chatNpc(sad, "But, by the time me get box open, other goblins gone. Then me not run fast enough and guards catch me.")
        chatNpc(happy, "But now you want map piece so must be special! What you do for me to get it?")
    }

    private suspend fun Dialogue.wormbrainPay() {
        chatPlayer(shifty, "I suppose I could pay you for the map piece. Say, 500 coins?")
        chatNpc(laugh, "Me not stoopid, it worth at least 10,000 coins!")
        when (choice2("You must be joking! Forget it.", 1, "Alright then, 10,000 it is.", 2)) {
            1 -> {
                chatPlayer(angry, "You must be joking! Forget it.")
                chatNpc(neutral, "Fine, you not get map piece.")
            }
            2 -> {
                chatPlayer(neutral, "Alright then, 10,000 coins it is.")
                if (access.invTotal(access.inv, "obj.coins") < WormbrainPrice) {
                    chatPlayer(sad, "Oops, I don't have enough on me.")
                    chatNpc(neutral, "Comes back when you has enough.")
                    return
                }
                access.invDel(access.inv, "obj.coins", WormbrainPrice)
                access.invAdd(access.inv, "obj.mappart2")
                mesbox("You buy the map piece from Wormbrain.")
                chatNpc(laugh, "Fank you very much! Now me can bribe da guards, hehehe.")
            }
        }
    }

}
