package org.rsmod.content.quest.area.varrock.demonslayer

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Demon Slayer dialogue, transcribed from the OSRS Wiki transcript for the reworked
 * (rev 241) quest. Stage model on `varbit.demonslayer_main`: 0 not started, 1 started,
 * 2 has Silverlight, 3 complete.
 */
object DemonSlayerDialogue {

    internal val quest
        get() = checkNotNull(Quest.get(DemonSlayer.KEY)) { "quest not registered" }

    fun Player.dsStage(): Int = quest.getQuestStage(this)

    internal var Player.bonesGiven by intVarBit("varbit.ds_bones_given")
    internal var Player.traibornKeyDone by boolVarBit("varbit.ds_traiborn_key")
    internal var Player.lampsClaimed by boolVarBit("varbit.ds_lamps_claimed")
    internal var Player.prysinBriefed by boolVarBit("varbit.ds_prysin_briefed")
    internal var Player.caseOpen by boolVarBit("varbit.delrith_silverlight_case")
    internal var Player.drainKeyVar by intVarBit("varbit.delrith_drain_key")
    internal var Player.seenSummoning by boolVarBit("varbit.delrith_seen_summoning_cutscene")

    internal val INCANTATION_WORDS = listOf("Aber", "Gabindo", "Purchai", "Camerinthum", "Carlem")

    internal fun Player.incantationSlot(slot: Int): Int =
        vars["varbit.delrith_incantation_${slot + 1}"]

    internal fun Player.ensureIncantation() {
        val slots = (0..4).map { incantationSlot(it) }
        if (slots.toSet().size == 5 && slots.all { it in 0..4 }) {
            return
        }
        val order = (0..4).shuffled()
        for (slot in 0..4) {
            VarPlayerIntMapSetter.set(
                this,
                "varbit.delrith_incantation_${slot + 1}",
                order[slot],
            )
        }
    }

    internal fun Player.incantation(): List<String> {
        ensureIncantation()
        return (0 until 5).map { INCANTATION_WORDS[incantationSlot(it).coerceIn(0, 4)] }
    }

    internal fun ProtectedAccess.hasSilverlight(): Boolean =
        invTotal(inv, Silverlight) > 0 || invTotal(worn, Silverlight) > 0

    internal fun ProtectedAccess.hasKey(key: String): Boolean =
        invTotal(inv, key) > 0 || invTotal(bank, key) > 0

    internal fun ProtectedAccess.keyCount(): Int = KEYS.count { hasKey(it) }

    private enum class ArisTopic { Fight, WhereDemon, WallyName, WallyKill }
    private enum class ArisTopic2 { Fight, WhereDemon, Incantation, Silverlight, Done }
    private enum class ArisInProgressTopic { Incantation, Silverlight, StopCalling, Done }
    private enum class ArisPostQuestTopic { HowKnow, Thanks, StopCalling }
    private enum class PrysinKeyTopic { YourKey, Rovin, Wizard, Hunt }
    private enum class RovinReason { Demon, Guards, King }
    private enum class TraibornTopic { Quest, Other }

    // ------------------------------------------------------------------ Aris

    suspend fun ProtectedAccess.arisDialogue(npc: Npc) = startDialogue(npc) { aris(npc) }

    private suspend fun Dialogue.aris(npc: Npc) {
        when {
            player.dsStage() >= DemonSlayer.StageComplete -> arisPostQuest()
            player.dsStage() >= DemonSlayer.StageHasSilverlight -> arisHasSword()
            player.dsStage() >= DemonSlayer.StageStarted -> arisInProgress()
            else -> arisStart(npc)
        }
    }

    private suspend fun Dialogue.arisStart(npc: Npc) {
        chatNpc(
            neutral,
            "Hello young one. Cross my palm with silver and the future will be revealed to you.",
        )
        when (
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Cross Aris's palm with silver? (Costs 1 coin)",
            )
        ) {
            false -> {
                chatPlayer(neutral, "No, I don't believe in that stuff.")
                chatNpc(neutral, "Ok suit yourself.")
            }
            else -> {
                if (invTotal(access.inv, Coins) <= 0) {
                    chatPlayer(sad, "Oh dear. I don't have any money.")
                    return
                }
                chatPlayer(happy, "Okay, here you go.")
                access.invDel(access.inv, Coins, count = 1)
                scry(npc)
                arisTopics(npc)
            }
        }
    }

    private suspend fun Dialogue.scry(npc: Npc) {
        chatNpc(
            neutral,
            "Come closer, and listen carefully to what the future holds for you, " +
                "as I peer into the swirling mists of the crystal ball.",
        )
        npc.anim(CrystalBallAnim)
        npc.spotanim(CrystalBallSpotanim, delay = 0, height = 0, slot = 0)
        chatNpc(neutral, "I can see images forming. I can see you.")
        chatNpc(
            neutral,
            "You are holding a very impressive looking sword. I'm sure I recognise " +
                "that sword...",
        )
        npc.spotanim(CrystalBallSpotanimMiddle, delay = 0, height = 0, slot = 0)
        chatNpc(neutral, "There is a big dark shadow appearing now.")
        npc.spotanim(CrystalBallSpotanimEnd, delay = 0, height = 0, slot = 0)
        chatNpc(shocked, "Aaargh!")
        chatPlayer(worried, "Are you all right?")
        chatNpc(shocked, "It's Delrith! Delrith is coming!")
        chatPlayer(quiz, "Who's Delrith?")
        chatNpc(neutral, "Delrith...")
        chatNpc(sad, "Delrith is a powerful demon.")
        chatNpc(
            worried,
            "Oh! I really hope he didn't see me looking at him through my crystal ball!",
        )
        chatNpc(
            neutral,
            "He tried to destroy this city 150 years ago. He was stopped just in time " +
                "by the great hero Wally.",
        )
        chatNpc(
            neutral,
            "Using his magic sword Silverlight, Wally managed to trap the demon in the " +
                "stone circle just south of this city.",
        )
        chatNpc(
            worried,
            "Ye gods! Silverlight was the sword you were holding in my vision! You are " +
                "the one destined to stop the demon this time.",
        )
    }

    private suspend fun Dialogue.arisTopics(npc: Npc) {
        while (true) {
            when (
                choice4(
                    "How am I meant to fight a demon who can destroy cities?",
                    ArisTopic.Fight,
                    "Okay, where is he? I'll kill him for you!",
                    ArisTopic.WhereDemon,
                    "Wally doesn't sound like a very heroic name.",
                    ArisTopic.WallyName,
                    "So how did Wally kill Delrith?",
                    ArisTopic.WallyKill,
                    title = "What would you like to say?",
                )
            ) {
                ArisTopic.Fight -> {
                    chatPlayer(worried, "How am I meant to fight a demon who can destroy cities?!")
                    chatNpc(
                        neutral,
                        "If you face Delrith while he is still weak from being summoned, " +
                            "and use the correct weapon, you will not find the task too arduous.",
                    )
                    chatNpc(
                        neutral,
                        "Do not fear. If you follow the path of the great hero Wally, " +
                            "then you are sure to defeat the demon.",
                    )
                }
                ArisTopic.WhereDemon -> {
                    chatPlayer(happy, "Okay, where is he? I'll kill him for you!")
                    chatNpc(happy, "Ah, the overconfidence of the young!")
                    chatNpc(
                        neutral,
                        "Delrith can't be harmed by ordinary weapons. You must face him " +
                            "using the same weapon that Wally used.",
                    )
                }
                ArisTopic.WallyName -> {
                    chatPlayer(neutral, "Wally doesn't sound a very heroic name.")
                    chatNpc(
                        neutral,
                        "Yes I know. Maybe that is why history doesn't remember him. " +
                            "However he was a very great hero.",
                    )
                    chatNpc(
                        neutral,
                        "Who knows how much pain and suffering Delrith would have " +
                            "brought forth without Wally to stop him!",
                    )
                    chatNpc(
                        neutral,
                        "It looks like you are going to need to perform similar heroics.",
                    )
                }
                ArisTopic.WallyKill -> {
                    chatPlayer(quiz, "So how did Wally kill Delrith?")
                    wallyVision()
                    arisTopics2()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.wallyVision() {
        chatNpc(
            neutral,
            "Wally managed to arrive at the stone circle just as Delrith was summoned " +
                "by a cult of chaos druids...",
        )
        chatNpcSpecific("Wally", WallyNpc, angry, "Die, foul demon!")
        chatNpcSpecific("Wally", WallyNpc, neutral, "Now, what was that incantation again?")
        val incantation = player.incantation()
        chatNpcSpecific(
            "Wally",
            WallyNpc,
            angry,
            "${incantation[0]}... ${incantation[1]}... ${incantation[2]}... " +
                "${incantation[3]}... ${incantation[4]}!",
        )
        chatNpcSpecific("Wally", WallyNpc, laugh, "I am the greatest demon slayer EVER!")
        chatNpc(
            neutral,
            "By reciting the correct magical incantation, and thrusting Silverlight " +
                "into Delrith while he was newly summoned, Wally was able to imprison " +
                "Delrith in the stone block in the centre of the circle.",
        )
        chatNpc(neutral, "Delrith will come forth from the stone circle again.")
        chatNpc(
            worried,
            "I would imagine an evil sorcerer is already starting on the rituals to " +
                "summon Delrith as we speak.",
        )
    }

    private suspend fun Dialogue.arisTopics2() {
        while (true) {
            when (
                choice5(
                    "How am I meant to fight a demon who can destroy cities?",
                    ArisTopic2.Fight,
                    "Okay, where is he? I'll kill him for you!",
                    ArisTopic2.WhereDemon,
                    "What is the magical incantation?",
                    ArisTopic2.Incantation,
                    "Where can I find Silverlight?",
                    ArisTopic2.Silverlight,
                    "Okay, thanks. I'll do my best to stop the demon.",
                    ArisTopic2.Done,
                    title = "What would you like to say?",
                )
            ) {
                ArisTopic2.Fight -> {
                    chatPlayer(worried, "How am I meant to fight a demon who can destroy cities?!")
                    chatNpc(
                        neutral,
                        "If you face Delrith while he is still weak from being summoned, " +
                            "and use the correct weapon, you will not find the task too arduous.",
                    )
                    chatNpc(
                        neutral,
                        "Do not fear. If you follow the path of the great hero Wally, " +
                            "then you are sure to defeat the demon.",
                    )
                }
                ArisTopic2.WhereDemon -> {
                    chatPlayer(happy, "Okay, where is he? I'll kill him for you!")
                    chatNpc(happy, "Ah, the overconfidence of the young!")
                    chatNpc(
                        neutral,
                        "Delrith can't be harmed by ordinary weapons. You must face him " +
                            "using the same weapon that Wally used.",
                    )
                }
                ArisTopic2.Incantation -> arisIncantation()
                ArisTopic2.Silverlight -> arisSilverlight()
                ArisTopic2.Done -> {
                    chatPlayer(happy, "Ok thanks. I'll do my best to stop the demon.")
                    chatNpc(happy, "Good luck, and may Guthix be with you!")
                    quest.setQuestStage(access, DemonSlayer.StageStarted)
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.arisIncantation() {
        chatPlayer(quiz, "What is the magical incantation?")
        chatNpc(neutral, "Oh yes, let me think a second...")
        val incantation = player.incantation()
        chatNpc(
            neutral,
            "Alright, I think I've got it now, it goes.... ${incantation[0]}... " +
                "${incantation[1]}... ${incantation[2]}... ${incantation[3]}... " +
                "${incantation[4]}. Have you got that?",
        )
        chatPlayer(neutral, "I think so, yes.")
    }

    private suspend fun Dialogue.arisSilverlight() {
        chatPlayer(quiz, "Where can I find Silverlight?")
        chatNpc(
            neutral,
            "Silverlight has been passed down through Wally's descendants. I believe " +
                "it is currently in the care of one of the King's knights called Sir Prysin.",
        )
        chatNpc(
            neutral,
            "He shouldn't be too hard to find. He lives in the royal palace in this " +
                "city. Tell him Aris sent you.",
        )
    }

    private suspend fun Dialogue.arisInProgress() {
        chatNpc(neutral, "Greetings. How goes thy quest?")
        chatPlayer(neutral, "I'm still working on it.")
        chatNpc(neutral, "Well if you need any advice I'm always here, young one.")
        arisInProgressTopics()
    }

    private suspend fun Dialogue.arisInProgressTopics() {
        while (true) {
            when (
                choice4(
                    "What is the magical incantation?",
                    ArisInProgressTopic.Incantation,
                    "Where can I find Silverlight?",
                    ArisInProgressTopic.Silverlight,
                    "Stop calling me that!",
                    ArisInProgressTopic.StopCalling,
                    "Well I'd better press on with it.",
                    ArisInProgressTopic.Done,
                    title = "What would you like to say?",
                )
            ) {
                ArisInProgressTopic.Incantation -> arisIncantation()
                ArisInProgressTopic.Silverlight -> arisSilverlight()
                ArisInProgressTopic.StopCalling -> arisStopCalling()
                ArisInProgressTopic.Done -> {
                    chatPlayer(neutral, "Well I'd better press on with it.")
                    chatNpc(neutral, "See you anon.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.arisStopCalling() {
        chatPlayer(angry, "Stop calling me that!")
        chatNpc(neutral, "In the scheme of things you are very young.")
        when (
            choice2(
                "Ok but how old are you?",
                true,
                "Oh if it's in the scheme of things that's ok.",
                false,
                title = "What would you like to say?",
            )
        ) {
            true -> {
                chatPlayer(quiz, "Ok, but how old are you?")
                chatNpc(
                    neutral,
                    "Count the number of legs on the stools in the Blue Moon inn, and " +
                        "multiply that number by seven.",
                )
                chatPlayer(neutral, "Er, yeah, whatever.")
            }
            else -> {
                chatPlayer(neutral, "Oh if it's in the scheme of things that's ok.")
                chatNpc(happy, "You show wisdom for one so young.")
            }
        }
    }

    private suspend fun Dialogue.arisHasSword() {
        chatNpc(neutral, "How goes the quest?")
        chatPlayer(neutral, "I have the sword now. I just need to kill the demon, I think.")
        chatNpc(neutral, "Yep, that's right.")
        while (true) {
            when (
                choice3(
                    "What is the magical incantation?",
                    0,
                    "Well I'd better press on with it.",
                    1,
                    "Where can I find the demon?",
                    2,
                    title = "What would you like to say?",
                )
            ) {
                0 -> arisIncantation()
                2 -> {
                    chatPlayer(quiz, "Where can I find the demon?")
                    chatNpc(
                        neutral,
                        "Just head south and you should find the stone circle just " +
                            "outside the city gate.",
                    )
                    return
                }
                else -> {
                    chatPlayer(neutral, "Well I'd better press on with it.")
                    chatNpc(neutral, "See you anon.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.arisPostQuest() {
        if (!player.lampsClaimed) {
            chatNpc(
                happy,
                "I have a little something here for you, young one. Consider it a " +
                    "gift for slaying that demon.",
            )
            access.invAdd(access.inv, AntiqueLamp, count = 2)
            if (invTotal(access.inv, AntiqueLamp) >= 2) {
                player.lampsClaimed = true
            }
            objbox(AntiqueLamp, zoom = 250, text = "Aris gives you two antique lamps.")
        }
        chatNpc(neutral, "Greetings young one.")
        chatNpc(happy, "You're a hero now. That was a good bit of demonslaying.")
        while (true) {
            when (
                choice3(
                    "How do you know I killed it?",
                    ArisPostQuestTopic.HowKnow,
                    "Thanks.",
                    ArisPostQuestTopic.Thanks,
                    "Stop calling me that!",
                    ArisPostQuestTopic.StopCalling,
                    title = "What would you like to say?",
                )
            ) {
                ArisPostQuestTopic.HowKnow -> {
                    chatPlayer(quiz, "How do you know I killed it?")
                    chatNpc(happy, "You forget. I'm good at knowing things.")
                    return
                }
                ArisPostQuestTopic.Thanks -> {
                    chatPlayer(happy, "Thanks.")
                    return
                }
                ArisPostQuestTopic.StopCalling -> arisStopCalling()
            }
        }
    }

    // -------------------------------------------------------------- Sir Prysin

    suspend fun ProtectedAccess.prysinDialogue(npc: Npc) = startDialogue(npc) { prysin(npc) }

    private suspend fun Dialogue.prysin(npc: Npc) {
        when {
            player.dsStage() >= DemonSlayer.StageComplete -> prysinIntro(questTalk = false)
            player.dsStage() >= DemonSlayer.StageHasSilverlight -> prysinPreFight()
            player.dsStage() >= DemonSlayer.StageStarted -> {
                if (access.keyCount() >= 3) {
                    prysinAllKeys(npc)
                } else if (player.prysinBriefed) {
                    prysinKeyHub()
                } else {
                    prysinIntro(questTalk = true)
                }
            }
            else -> prysinIntro(questTalk = false)
        }
    }

    private suspend fun Dialogue.prysinIntro(questTalk: Boolean) {
        chatNpc(quiz, "Hello, who are you?")
        val pick =
            if (questTalk) {
                choice3(
                    "I am a mighty adventurer. Who are you?",
                    1,
                    "I'm not sure, I was hoping you could tell me.",
                    2,
                    "Aris said I should come and talk to you.",
                    3,
                )
            } else {
                choice2(
                    "I am a mighty adventurer. Who are you?",
                    1,
                    "I'm not sure, I was hoping you could tell me.",
                    2,
                )
            }
        when (pick) {
            1 -> {
                chatPlayer(happy, "I am a mighty adventurer, who are you?")
                chatNpc(happy, "I am Sir Prysin. A bold and famous knight of the realm.")
            }
            2 -> {
                chatPlayer(neutral, "I was hoping you could tell me.")
                chatNpc(neutral, "Well I've never met you before.")
            }
            else -> {
                chatPlayer(neutral, "Aris said I should come and talk to you.")
                chatNpc(
                    quiz,
                    "Aris? Is she still alive? I remember her from when I was pretty " +
                        "young. Well what do you need to talk to me about?",
                )
                prysinSilverlightProblem()
            }
        }
    }

    private suspend fun Dialogue.prysinSilverlightProblem() {
        chatPlayer(neutral, "I need to find Silverlight.")
        chatNpc(quiz, "What do you need to find that for?")
        chatPlayer(neutral, "I need it to fight Delrith.")
        chatNpc(
            neutral,
            "Delrith? I thought the world was rid of him, thanks to my great-grandfather.",
        )
        when (
            choice2(
                "Well, Aris' crystal ball seems to think otherwise.",
                true,
                "He's back and unfortunately I've got to deal with him.",
                false,
            )
        ) {
            true -> {
                chatPlayer(neutral, "Well Aris' crystal ball seems to think otherwise.")
                chatNpc(neutral, "Well if the ball says so, I'd better help you.")
            }
            else -> {
                chatPlayer(sad, "He's back and unfortunately I've got to deal with him.")
                chatNpc(
                    neutral,
                    "You don't look up to much. I suppose Silverlight may be good " +
                        "enough to carry you through though.",
                )
            }
        }
        prysinKeysExplanation()
    }

    private suspend fun Dialogue.prysinKeysExplanation() {
        chatNpc(neutral, "The problem is getting Silverlight.")
        chatPlayer(quiz, "You mean you don't have it?")
        chatNpc(
            neutral,
            "Oh I do have it, but it is so powerful that the king made me put it in a " +
                "special box which needs three different keys to open it. That way it " +
                "won't fall into the wrong hands.",
        )
        chatPlayer(happy, "So give me the keys!")
        chatNpc(neutral, "Um, well, it's not so easy.")
        chatNpc(
            neutral,
            "I kept one of the keys. I gave the other two to other people for safe keeping.",
        )
        chatNpc(neutral, "One I gave to Rovin, the captain of the palace guard.")
        chatNpc(neutral, "I gave the other to the wizard Traiborn.")
        player.prysinBriefed = true
        prysinKeyTopics()
    }

    private suspend fun Dialogue.prysinKeyTopics() {
        while (true) {
            when (
                choice4(
                    "Can you give me your key?",
                    PrysinKeyTopic.YourKey,
                    "Where can I find Captain Rovin?",
                    PrysinKeyTopic.Rovin,
                    "Where does the wizard live?",
                    PrysinKeyTopic.Wizard,
                    "Well I'd better go key hunting.",
                    PrysinKeyTopic.Hunt,
                )
            ) {
                PrysinKeyTopic.YourKey -> {
                    chatPlayer(neutral, "Can you give me your key?")
                    chatNpc(neutral, "Um.... ah....")
                    chatNpc(neutral, "Well there's a problem there as well.")
                    chatNpc(
                        sad,
                        "I managed to drop the key in the drain just outside the palace " +
                            "kitchen. It is just inside and I can't reach it.",
                    )
                    when (
                        choice2(
                            "So what does the drain connect to?",
                            true,
                            "Well I'd better go key hunting.",
                            false,
                        )
                    ) {
                        true -> {
                            chatPlayer(quiz, "So what does the drain connect to?")
                            chatNpc(
                                neutral,
                                "It is the drain for the drainpipe running from the " +
                                    "sink in the kitchen down to the palace sewers.",
                            )
                        }
                        else -> {
                            chatPlayer(neutral, "Well I'd better go key hunting.")
                            chatNpc(neutral, "Ok, goodbye.")
                            return
                        }
                    }
                }
                PrysinKeyTopic.Rovin -> {
                    chatPlayer(quiz, "Where can I find Captain Rovin?")
                    chatNpc(
                        neutral,
                        "Captain Rovin lives at the top of the guards' quarters in " +
                            "the north-west wing of this palace.",
                    )
                }
                PrysinKeyTopic.Wizard -> {
                    chatPlayer(quiz, "Where does the wizard live?")
                    chatNpc(quiz, "Wizard Traiborn?")
                    chatNpc(
                        neutral,
                        "He is one of the wizards who lives in the tower on the little " +
                            "island just off the south coast. I believe his quarters are " +
                            "on the first floor of the tower.",
                    )
                }
                PrysinKeyTopic.Hunt -> {
                    chatPlayer(neutral, "Well I'd better go key hunting.")
                    chatNpc(neutral, "Ok, goodbye.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.prysinKeyHub() {
        chatNpc(neutral, "So how are you doing with getting the keys?")
        val held =
            buildList {
                if (access.hasKey(KeyRovin)) add("the key from Captain Rovin")
                if (access.hasKey(KeyTraiborn)) add("the key from Wizard Traiborn")
                if (access.hasKey(KeyDrain)) add("the key which you dropped down the drain")
            }
        if (held.isEmpty()) {
            val pick =
                choice2(
                    "Can you remind me where all the keys were again?",
                    true,
                    "I'm still looking.",
                    false,
                )
            if (pick) {
                chatPlayer(quiz, "Can you remind me where all the keys were again?")
                chatNpc(
                    neutral,
                    "I kept one of the keys. I gave the other two to other people " +
                        "for safe keeping.",
                )
                chatNpc(neutral, "One I gave to Rovin, the captain of the palace guard.")
                chatNpc(neutral, "I gave the other to the wizard Traiborn.")
                prysinKeyTopics()
            } else {
                chatPlayer(neutral, "I'm still looking.")
                chatNpc(neutral, "Ok, tell me when you've got them all.")
            }
        } else {
            chatPlayer(happy, "I've got ${held.joinToString(" and ")}.")
            prysinKeyTopics()
        }
    }

    private suspend fun Dialogue.prysinAllKeys(npc: Npc) {
        chatNpc(neutral, "So how are you doing with getting the keys?")
        chatPlayer(happy, "I've got all three keys!")
        chatNpc(happy, "Excellent! Now I can give you Silverlight.")
        silverlightScene(npc)
    }

    private suspend fun Dialogue.silverlightScene(npc: Npc) {
        for (key in KEYS) {
            access.invDel(access.inv, key, count = Int.MAX_VALUE)
        }
        npc.faceSquare(SilverlightCaseSpot)
        npc.anim(CaseOpenAnim)
        delay(2)
        player.caseOpen = true
        objbox(Silverlight, zoom = 250, text = "Sir Prysin hands you a very shiny sword.")
        access.invAdd(access.inv, Silverlight)
        access.anim(ReceiveSilverlightAnim)
        access.spotanim(SwordGlintSpotanim)
        chatNpc(
            neutral,
            "That sword belonged to my great-grandfather. Make sure you treat it " +
                "with respect!",
        )
        chatNpc(happy, "Now go kill that demon!")
        quest.setQuestStage(access, DemonSlayer.StageHasSilverlight)
    }

    private suspend fun Dialogue.prysinPreFight() {
        chatNpc(neutral, "Have you sorted that demon out yet?")
        if (access.hasSilverlight()) {
            chatPlayer(sad, "No, not yet.")
            chatNpc(
                angry,
                "Well get on with it. He'll be pretty powerful when he gets to " +
                    "full strength.",
            )
        } else {
            chatPlayer(sad, "Not yet. And I, um, lost Silverlight.")
            chatNpc(
                neutral,
                "Yes, I know, someone returned it to me. Take better care of it this time.",
            )
            objbox(Silverlight, zoom = 250, text = "Sir Prysin hands you Silverlight.")
            access.invAdd(access.inv, Silverlight)
        }
    }

    // ------------------------------------------------------------ Captain Rovin

    suspend fun ProtectedAccess.rovinDialogue(npc: Npc) = startDialogue(npc) { rovin(npc) }

    private suspend fun Dialogue.rovin(npc: Npc) {
        chatNpc(
            angry,
            "What are you doing up here? Only the palace guards are allowed up here.",
        )
        val pick =
            if (player.dsStage() in DemonSlayer.StageStarted until DemonSlayer.StageComplete) {
                choice5(
                    "I am one of the palace guards.",
                    1,
                    "What about the King?",
                    2,
                    "Yes I know, but this is important.",
                    3,
                    "Erm I forgot.",
                    4,
                    "The castle has just received its ale delivery.",
                    5,
                )
            } else {
                choice4(
                    "I am one of the palace guards.",
                    1,
                    "What about the King?",
                    2,
                    "Erm I forgot.",
                    4,
                    "The castle has just received its ale delivery.",
                    5,
                )
            }
        when (pick) {
            1 -> {
                chatPlayer(neutral, "I am one of the palace guards.")
                chatNpc(angry, "No you're not! Now get out of my sight!")
            }
            2 -> {
                chatPlayer(quiz, "What about the King?")
                chatNpc(
                    neutral,
                    "His majesty has his own guards who watch over him. My business is " +
                        "the security of the rest of the palace.",
                )
            }
            3 -> rovinQuest()
            4 -> {
                chatPlayer(sad, "Erm I forgot.")
                chatNpc(angry, "Then get out of my sight!")
            }
            else -> {
                chatPlayer(neutral, "The castle has just received its ale delivery.")
                chatNpc(
                    neutral,
                    "Yes, the kitchen staff are already aware. Now leave me to my duties.",
                )
            }
        }
    }

    private suspend fun Dialogue.rovinQuest() {
        chatPlayer(neutral, "Yes, I know, but this is important.")
        chatNpc(neutral, "Ok, I'm listening. Tell me what's so important.")
        when (
            choice3(
                "There's a demon who wants to invade this city.",
                RovinReason.Demon,
                "I am one of the palace guards.",
                RovinReason.Guards,
                "What about the King?",
                RovinReason.King,
            )
        ) {
            RovinReason.Guards -> {
                chatPlayer(neutral, "I am one of the palace guards.")
                chatNpc(angry, "No you're not! Now get out of my sight!")
            }
            RovinReason.King -> {
                chatPlayer(quiz, "What about the King?")
                chatNpc(
                    neutral,
                    "His majesty has his own guards who watch over him. My business is " +
                        "the security of the rest of the palace.",
                )
            }
            RovinReason.Demon -> {
                chatPlayer(neutral, "There's a demon who wants to invade the city.")
                rovinDemon()
            }
        }
    }

    private suspend fun Dialogue.rovinDemon() {
        if (player.dsStage() >= DemonSlayer.StageHasSilverlight ||
            invTotal(access.inv, KeyRovin) > 0 ||
            invTotal(access.bank, KeyRovin) > 0
        ) {
            chatNpc(neutral, "Yes, you said before, haven't you killed it yet?")
            chatPlayer(
                neutral,
                "Well I'm going to use the powerful sword Silverlight, which I " +
                    "believe you have one of the keys for?",
            )
            when {
                invTotal(access.inv, KeyRovin) > 0 ->
                    chatNpc(angry, "I already gave you my key. Check your pockets.")
                invTotal(access.bank, KeyRovin) > 0 ->
                    chatNpc(
                        angry,
                        "I already gave you my key. Maybe you left it somewhere. " +
                            "Have you checked your bank account?",
                    )
                else -> rovinGiveKey()
            }
            return
        }
        chatNpc(quiz, "Is it a powerful demon?")
        when (choice2("Not really.", false, "Yes, very.", true)) {
            false -> {
                chatPlayer(neutral, "Not really.")
                chatNpc(
                    neutral,
                    "Well, I'm sure the palace guards can deal with it, then. Thanks " +
                        "for the information.",
                )
            }
            else -> {
                chatPlayer(neutral, "Yes, very.")
                chatNpc(
                    worried,
                    "As good as the palace guards are, I don't know if they're up to " +
                        "taking on a very powerful demon.",
                )
                when (
                    choice2(
                        "Yeah, the palace guards are rubbish!",
                        false,
                        "It's not them who are going to fight the demon, it's me.",
                        true,
                    )
                ) {
                    false -> {
                        chatPlayer(happy, "Yeah, the palace guards are rubbish!")
                        chatNpc(neutral, "Yeah, they're--")
                        chatNpc(
                            angry,
                            "Wait! How dare you insult the palace guards? Get out " +
                                "of my sight!",
                        )
                    }
                    else -> {
                        chatPlayer(
                            neutral,
                            "It's not them who are going to fight the demon, it's me.",
                        )
                        chatNpc(
                            quiz,
                            "What, all by yourself? How are you going to do that?",
                        )
                        chatPlayer(
                            neutral,
                            "I'm going to use the powerful sword Silverlight, which " +
                                "I believe you have one of the keys for?",
                        )
                        chatNpc(quiz, "Yes, I do. But why should I give it to you?")
                        rovinPersuade()
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.rovinPersuade() {
        while (true) {
            when (
                choice3(
                    "Fortune-teller Aris said I was destined to kill the demon.",
                    1,
                    "Otherwise the demon will destroy the city!",
                    2,
                    "Sir Prysin said you would give me the key.",
                    3,
                )
            ) {
                1 -> {
                    chatPlayer(
                        neutral,
                        "Fortune-teller Aris said I was destined to kill the demon.",
                    )
                    chatNpc(
                        angry,
                        "A fortune-teller? Destiny? I don't believe in that stuff. " +
                            "I got where I am today by hard work, not by destiny! " +
                            "Why should I care what that mad old fortune-teller says?",
                    )
                }
                2 -> {
                    chatPlayer(angry, "Otherwise the demon will destroy the city!")
                    chatNpc(
                        angry,
                        "You can't fool me! How do I know you haven't just made that " +
                            "story up to get my key?",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Sir Prysin said you would give me the key.")
                    chatNpc(
                        angry,
                        "Oh, he did, did he? Well I don't report to Sir Prysin, " +
                            "I report directly to the king!",
                    )
                    chatNpc(
                        angry,
                        "I didn't work my way up through the ranks of the palace " +
                            "guards so I could take orders from an ill-bred moron " +
                            "who only has his job because his great-grandfather was " +
                            "a hero with a silly name!",
                    )
                    when (
                        choice2(
                            "Why did he give you one of the keys then?",
                            true,
                            "Aris said I was destined to kill the demon.",
                            false,
                        )
                    ) {
                        true -> {
                            chatPlayer(quiz, "Why did he give you one of the keys then?")
                            chatNpc(
                                neutral,
                                "Only because the king ordered him to! The king " +
                                    "couldn't get Sir Prysin to part with his precious " +
                                    "ancestral sword, but he made him lock it up so " +
                                    "he couldn't lose it.",
                            )
                            chatNpc(
                                neutral,
                                "I got one key and I think some wizard got another. " +
                                    "Now what happened to the third one?",
                            )
                            chatPlayer(neutral, "Sir Prysin dropped it down a drain!")
                            chatNpc(laugh, "Ha ha ha! The idiot!")
                            rovinGiveKey()
                            return
                        }
                        else -> continue
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.rovinGiveKey() {
        chatNpc(
            neutral,
            "Okay, I'll give you the key, just so that it's you that kills the " +
                "demon and not Sir Prysin!",
        )
        objbox(KeyRovin, zoom = 250, text = "Captain Rovin hands you a key.")
        access.invAdd(access.inv, KeyRovin)
    }

    // ------------------------------------------------------------ Traiborn

    suspend fun Dialogue.traibornEntry(generic: suspend Dialogue.() -> Unit) {
        if (player.dsStage() !in DemonSlayer.StageStarted until DemonSlayer.StageComplete) {
            generic()
            return
        }
        chatNpc(confused, "Ello young thingummywut.")
        when (
            choice2(
                "Talk about Demon Slayer.",
                TraibornTopic.Quest,
                "Talk about something else.",
                TraibornTopic.Other,
            )
        ) {
            TraibornTopic.Other -> generic()
            else -> traibornQuest()
        }
    }

    private suspend fun Dialogue.traibornQuest() {
        when {
            player.traibornKeyDone && !access.hasKey(KeyTraiborn) -> traibornLostKey()
            player.traibornKeyDone -> {
                chatNpc(
                    confused,
                    "Don't you have somewhere to be, young thingummywut? You still " +
                        "have that key you asked me for.",
                )
                chatPlayer(happy, "You're right. I've got a demon to slay.")
            }
            player.bonesGiven >= TraibornBonesNeeded -> {
                chatPlayer(neutral, "I need to get a key given to you by Sir Prysin.")
                traibornRitual()
            }
            player.bonesGiven > 0 || invTotal(access.inv, Bones) > 0 -> traibornBones()
            else -> traibornKeyAsk()
        }
    }

    private suspend fun Dialogue.traibornLostKey() {
        chatNpc(confused, "Ello young thingummywut.")
        chatPlayer(sad, "I've lost the key you gave to me.")
        chatNpc(
            neutral,
            "Yes I know, it was returned to me. If you want it back you're going " +
                "to have to collect another 25 sets of bones.",
        )
        player.bonesGiven = 0
        player.traibornKeyDone = false
    }

    private suspend fun Dialogue.traibornKeyAsk() {
        chatPlayer(neutral, "I need to get a key given to you by Sir Prysin.")
        chatNpc(confused, "Sir Prysin? Who's that? What would I want his key for?")
        when (
            choice3(
                "He told me you were looking after it for him.",
                1,
                "He's one of the King's knights.",
                2,
                "Well, have you got any keys knocking around?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "He told me you were looking after it for him.")
                chatNpc(
                    neutral,
                    "That wasn't very clever of him. I'd lose my head if it wasn't " +
                        "screwed on. Go and tell him to find someone else to look " +
                        "after his valuables in future.",
                )
                when (
                    choice2(
                        "Okay, I'll go and tell him that.",
                        true,
                        "Well, have you got any keys knocking around?",
                        false,
                    )
                ) {
                    true -> {
                        chatPlayer(neutral, "Okay, I'll go and tell him that.")
                        chatNpc(
                            happy,
                            "Oh that's great, if it wouldn't be too much trouble.",
                        )
                        traibornOffOrKeys()
                    }
                    else -> traibornKeysKnocking()
                }
            }
            2 -> {
                chatPlayer(neutral, "He's one of the King's knights.")
                chatNpc(
                    neutral,
                    "Say, I remember one of the King's knights. He had nice shoes...",
                )
                chatNpc(
                    happy,
                    "...and didn't like my homemade spinach rolls. Would you like " +
                        "a spinach roll?",
                )
                when (
                    choice2(
                        "Yes please.",
                        true,
                        "Just tell me if you have the key.",
                        false,
                    )
                ) {
                    true -> {
                        chatPlayer(happy, "Yes please.")
                        access.mes(
                            "Traiborn digs around in the pockets of his robes. " +
                                "After a few moments he triumphantly presents you " +
                                "with a spinach roll.",
                        )
                        access.invAdd(access.inv, SpinachRoll)
                        chatPlayer(happy, "Thank you very much.")
                        traibornOffOrKeys()
                    }
                    else -> {
                        chatPlayer(angry, "Just tell me if you have the key.")
                        chatNpc(quiz, "The key? The key to what?")
                        chatNpc(
                            neutral,
                            "There's more than one key in the world don't you know? " +
                                "Would be a bit odd if there was only the one.",
                        )
                        traibornKeyToWhat()
                    }
                }
            }
            else -> traibornKeysKnocking()
        }
    }

    private suspend fun Dialogue.traibornOffOrKeys() {
        when (
            choice2(
                "Err I'd better be off really.",
                true,
                "Well, have you got any keys knocking around?",
                false,
            )
        ) {
            true -> {
                chatPlayer(neutral, "Err I'd better be off really.")
                chatNpc(
                    happy,
                    "Oh ok, have a good time, and watch out for sheep! They're more " +
                        "cunning than they look.",
                )
            }
            else -> traibornKeysKnocking()
        }
    }

    private suspend fun Dialogue.traibornKeyToWhat() {
        when (
            choice3(
                "It's the key to get a sword called Silverlight.",
                1,
                "You've lost it, haven't you?",
                2,
                "Well, have you got any keys knocking around?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "It's the key to get a sword called Silverlight.")
                chatNpc(
                    quiz,
                    "Silverlight? Never heard of that. Sounds a good name for a ship. " +
                        "Are you sure it's not the name of a ship rather than a sword?",
                )
                when (
                    choice2(
                        "Yeah, pretty sure.",
                        true,
                        "Well, have you got any keys knocking around?",
                        false,
                    )
                ) {
                    true -> {
                        chatPlayer(neutral, "Yeah, pretty sure.")
                        chatNpc(sad, "That's a pity, waste of a name.")
                        traibornOffOrKeys()
                    }
                    else -> traibornKeysKnocking()
                }
            }
            2 -> {
                chatPlayer(angry, "You've lost it haven't you?")
                chatNpc(angry, "Me? Lose things? That's a nasty accusation.")
                traibornKeysKnocking()
            }
            else -> traibornKeysKnocking()
        }
    }

    private suspend fun Dialogue.traibornKeysKnocking() {
        chatPlayer(quiz, "Well, have you got any keys knocking around?")
        chatNpc(
            neutral,
            "Now you come to mention it, yes I do have a key. It's in my special " +
                "closet of valuable stuff. Now how do I get into that?",
        )
        chatNpc(
            neutral,
            "I sealed it using one of my magic rituals. So it would make sense " +
                "that another ritual would open it again.",
        )
        chatPlayer(quiz, "So do you know what ritual to use?")
        chatNpc(neutral, "Let me think a second.")
        chatNpc(
            neutral,
            "Yes a simple drazier style ritual should suffice. Hmm, main problem " +
                "with that is I'll need 25 sets of bones. Now where am I going to " +
                "get hold of something like that?",
        )
        when (
            choice2(
                "Hmm, that's too bad. I really need that key.",
                false,
                "I'll get the bones for you.",
                true,
            )
        ) {
            false -> {
                chatPlayer(sad, "Hmm, that's too bad. I really need that key.")
                chatNpc(sad, "Ah well, sorry I couldn't be any more help.")
            }
            else -> {
                chatPlayer(happy, "I'll help get the bones for you.")
                chatNpc(happy, "Ooh that would be very good of you.")
                chatPlayer(neutral, "Okay, I'll speak to you when I've got some bones.")
            }
        }
    }

    private suspend fun Dialogue.traibornBones() {
        chatNpc(neutral, "How are you doing finding bones?")
        val held = invTotal(access.inv, Bones)
        if (held <= 0) {
            chatPlayer(sad, "I haven't got any at the moment.")
            chatNpc(neutral, "Nevermind, keep working on it.")
            return
        }
        chatPlayer(neutral, "I have some bones.")
        chatNpc(happy, "Give 'em here then.")
        val needed = TraibornBonesNeeded - player.bonesGiven
        val giving = minOf(held, needed)
        access.invDel(access.inv, Bones, count = giving)
        player.bonesGiven += giving
        val sets = if (giving == 1) "set" else "sets"
        access.mes("You give Traiborn $giving $sets of bones.")
        if (player.bonesGiven >= TraibornBonesNeeded) {
            traibornRitual()
        } else {
            chatPlayer(neutral, "That's all of them.")
            chatNpc(neutral, "I still need ${TraibornBonesNeeded - player.bonesGiven} more.")
            chatPlayer(neutral, "Ok, I'll keep looking.")
        }
    }

    private suspend fun Dialogue.traibornRitual() {
        chatNpc(happy, "Hurrah! That's all 25 sets of bones.")
        npc?.anim(TraibornBoneMagicAnim)
        npc?.spotanim(TraibornBoneSpotanim, delay = 0, height = 0, slot = 0)
        chatNpc(
            neutral,
            "Wings of dark and colour too, Spreading in the morning dew; " +
                "Locked away I have a key; Return it now, please, unto me.",
        )
        access.mes("A weird-looking wardrobe pops out from the ground.")
        objbox(KeyTraiborn, zoom = 250, text = "Traiborn hands you a key.")
        access.invAdd(access.inv, KeyTraiborn)
        player.traibornKeyDone = true
        chatPlayer(happy, "Thank you very much.")
        chatNpc(happy, "Not a problem for a friend of Sir What's-his-face.")
    }

    // ------------------------------------------------------------- Constants

    internal const val Coins = "obj.coins"
    internal const val Bones = "obj.bones"
    internal const val SpinachRoll = "obj.spinach_roll"
    internal const val Silverlight = "obj.silverlight"
    internal const val KeyDrain = "obj.silverlight_key_1"
    internal const val KeyRovin = "obj.silverlight_key_2"
    internal const val KeyTraiborn = "obj.silverlight_key_3"
    internal const val AntiqueLamp = "obj.cluequest_lamp"
    internal val KEYS = listOf(KeyDrain, KeyRovin, KeyTraiborn)

    internal const val WallyNpc = "npc.qip_ds_wally"
    internal val SilverlightCaseSpot = CoordGrid(3204, 3469, 0)

    internal const val CrystalBallAnim = "seq.qip_ds_reading_crystalball"
    internal const val CrystalBallSpotanim = "spotanim.qip_ds_crystallball_spotanim_start"
    internal const val CrystalBallSpotanimMiddle = "spotanim.qip_ds_crystallball_spotanim_middle"
    internal const val CrystalBallSpotanimEnd = "spotanim.qip_ds_crystallball_spotanim_end"
    internal const val CaseOpenAnim = "seq.qip_ds_silverlight_case_open"
    internal const val ReceiveSilverlightAnim = "seq.qip_ds_recieving_silverlight"
    internal const val SwordGlintSpotanim = "spotanim.qip_ds_sword_glint_spotanim"
    internal const val TraibornBoneMagicAnim = "seq.qip_ds_bone_magic"
    internal const val TraibornBoneSpotanim = "spotanim.qip_ds_bone_spotanim"

    internal const val TraibornBonesNeeded = 25
}
