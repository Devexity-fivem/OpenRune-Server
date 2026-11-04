package org.rsmod.content.quest.area.varrock.romeojuliet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * Romeo & Juliet dialogue, transcribed from the OSRS Wiki transcript for the reworked
 * (rev 241) version of the quest, with stage progression mirroring LostCity's
 * `quest_romeojuliet` scripts (stages 0/10/20/30/40/50/60/100).
 */
object RomeoAndJulietDialogue {

    private val quest
        get() = checkNotNull(Quest.get(RomeoAndJuliet.KEY)) { "quest not registered" }

    fun Player.rjStage(): Int = quest.getQuestStage(this)

    var Player.julietVisible: Int
        get() = vars[JulietVisibleVarbit]
        set(value) = VarPlayerIntMapSetter.set(this, JulietVisibleVarbit, value)

    private enum class RomeoTopic { WhereJuliet, AboutJuliet, Done }
    private enum class LawrenceTopic { Apothecary, Marriage, Done }
    private enum class BerryTopic { Look, Where, Danger, Done }
    private enum class ApothTopic { Potions, RomeoJuliet, SomethingElse, NoThanks }
    private enum class GossipTopic { RomeoJuliet, Gossip, Hair, Freebie, NoThanks }

    private val RomeoOpeners =
        listOf(
            "Blub! Blub...where is my Juliet? Have you seen her?",
            "Looking for a blonde girl, goes by the name of Juliet..quite pretty..." +
                "haven't seen her have you?",
            "Juliet, Juliet, wherefore art thou Juliet? Have you seen my Juliet?",
            "Oh woe is me that I cannot find my Juliet! You haven't seen Juliet have you?",
            "Sadness surrounds me now that Juliet's father forbids us to meet. " +
                "Have you seen my Juliet?",
            "What is to become of me and my darling Juliet, I cannot find her anywhere, " +
                "have you seen her?",
        )

    // ---------------------------------------------------------------- Romeo

    suspend fun ProtectedAccess.romeoDialogue(npc: Npc, npcRepo: NpcRepository) =
        startDialogue(npc) { romeo(npc, npcRepo) }

    private suspend fun Dialogue.romeo(npc: Npc, npcRepo: NpcRepository) {
        when {
            player.rjStage() >= StageComplete -> romeoPostQuest()
            player.rjStage() >= StageJulietCrypt -> romeoCrypt(npcRepo)
            player.rjStage() >= StageSpokenApothecary -> romeoApothecary()
            player.rjStage() >= StageSpokenFather -> romeoAfterFather()
            player.rjStage() >= StagePassedMessage -> romeoAfterMessage()
            player.rjStage() >= StageSpokenJuliet -> romeoDeliverMessage()
            player.rjStage() >= StageSpokenRomeo -> romeoReminder()
            else -> romeoStart()
        }
    }

    private suspend fun Dialogue.romeoStart() {
        chatNpc(sad, RomeoOpeners.random())
        when (
            choice3(
                "Yes, I have seen her actually!",
                1,
                "No sorry, I haven't seen her.",
                2,
                "Perhaps I could help to find her for you?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, I have seen her actually!")
                chatPlayer(neutral, "At least, I think it was her... Blonde? A bit stressed?")
                chatNpc(happy, "Golly...yes, yes...you make her sound very interesting!")
                chatNpc(happy, "And I'll bet she's a bit of a fox!")
                chatPlayer(neutral, "Well, I guess she could be considered attractive...")
                chatNpc(madlaugh, "I'll bet she is!  Wooooooooo!")
                chatNpc(neutral,
                    "Sorry, all that jubilation has made me forget what we were talking " +
                        "about.")
                chatPlayer(neutral, "You were asking me about Juliet? You seemed to know her?")
                chatNpc(happy, "Oh yes, Juliet!")
                chatNpc(
                    happy,
                    "The fox...could you tell her that she is the love of my long and that " +
                        "I life to be with her?",
                )
            }
            2 -> {
                chatPlayer(neutral, "No sorry, I haven't seen her.")
                chatNpc(sad, "Oh...well, that's a shame...I was rather hoping you had.")
                chatPlayer(
                    quiz,
                    "Why?  Is she a fugitive?  Does she owe you some money or something?",
                )
                chatNpc(quiz, "Hmmm, she might do?  Perhaps she does?  How do you know?")
                chatPlayer(
                    confused,
                    "I don't know? I was asking 'YOU' how 'YOU' know Juliet!",
                )
                chatNpc(
                    happy,
                    "Ahh, yes Juliet, she's my one true love.  Well, one of my one true " +
                        "loves! If you see her, could you tell her that she is the love of " +
                        "my long and that I life to be with her?",
                )
            }
            else -> {
                chatPlayer(happy, "Perhaps I can help find her for you? What does she look like?")
                chatNpc(happy, "Oh would you? That would be great! She has this sort of hair...")
                chatPlayer(neutral, "Hair...check..")
                chatNpc(happy, "...and she these...great lips...")
                chatPlayer(neutral, "Lips...right.")
                chatNpc(happy, "Oh and she has these lovely shoulders as well..")
                chatPlayer(
                    neutral,
                    "Shoulders...right, so she has hair, lips and shoulders...that should " +
                        "cut it down a bit.",
                )
                chatNpc(
                    happy,
                    "Oh yes, Juliet is very different...please tell her that she is the " +
                        "love of my long and that I life to be with her?",
                )
            }
        }
        chatPlayer(
            quiz,
            "What?  Surely you mean that 'she is the love of your life and that you long " +
                "to be with her?'",
        )
        chatNpc(
            laugh,
            "Oh yeah...what you said...tell her that, it sounds much better!  Oh you're " +
                "so good at this!",
        )
        val start =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Start the Romeo & Juliet quest?",
            )
        if (start) {
            chatPlayer(happy, "Yes, okay. I'll let her know.")
            chatNpc(happy, "Oh great! And tell her that I want to kiss her a give.")
            chatPlayer(confused, "You mean you want to give her a kiss!")
            chatNpc(laugh, "Oh you're good...you are good!")
            chatNpc(happy, "I see I've picked a true professional...!")
            quest.setQuestStage(access, StageSpokenRomeo)
            romeoJulietTopics()
        } else {
            chatPlayer(
                neutral,
                "Sorry Romeo, I've got better things to do right now. Maybe later?",
            )
            chatNpc(
                sad,
                "Oh, okay, well, I guess my Juliet and I can spend some time apart.  " +
                    "And as the old saying goes, 'Absinthe makes the heart glow longer'.",
            )
            chatPlayer(neutral, "Don't you mean that, 'Absence makes the...")
            chatPlayer(neutral, "Actually forget it...")
            chatNpc(happy, "Okay!")
        }
    }

    private suspend fun Dialogue.romeoReminder() {
        chatPlayer(neutral, "Hello again, remember me?")
        chatNpc(quiz, "Of course, yes....how are.. you....ermmm...")
        chatPlayer(neutral, "You haven't got a clue who I am do you?")
        chatNpc(
            neutral,
            "Not a clue my friend, but you seem to have a friendly face...a little " +
                "blood stained, and perhaps in need of a wash, but friendly none the less.",
        )
        chatPlayer(neutral, "You asked me to look for Juliet for you!")
        chatNpc(happy, "Ah yes, Juliet...my sweet darling...what news?")
        chatPlayer(neutral, "Nothing so far, but I need to ask a few questions?")
        romeoJulietTopics()
    }

    private suspend fun Dialogue.romeoJulietTopics() {
        while (true) {
            when (
                choice3(
                    "Where can I find Juliet?",
                    RomeoTopic.WhereJuliet,
                    "Is there anything else you can tell me about Juliet?",
                    RomeoTopic.AboutJuliet,
                    "Ok, thanks.",
                    RomeoTopic.Done,
                )
            ) {
                RomeoTopic.WhereJuliet -> {
                    chatPlayer(quiz, "Where can I find Juliet?")
                    chatNpc(quiz, "Why do you ask?")
                    chatPlayer(neutral, "So that I can try and find her for you!")
                    chatNpc(neutral, "Ah yes....quite right. Hmmm, let me think now.")
                    chatNpc(
                        neutral,
                        "She may still be locked away at her Father's house on the sest " +
                            "vide of Warrock.",
                    )
                    chatNpc(
                        happy,
                        "Oh, I remember how she loved it when I would sing up to her " +
                            "balcony! She would reward me with her own personal items...",
                    )
                    chatPlayer(quiz, "What, she just gave you her stuff?")
                    chatNpc(
                        laugh,
                        "Well, not exactly give...more like 'throw with considerable " +
                            "force'...she's always a kidder that Juliet!",
                    )
                }
                RomeoTopic.AboutJuliet -> {
                    chatPlayer(quiz, "Is there anything else you can tell me about Juliet?")
                    chatNpc(
                        happy,
                        "Oh, there is so much to tell...she is my true love, we intend to " +
                            "spend together forever...I can tell you so much about her..",
                    )
                    chatPlayer(happy, "Great!")
                    chatNpc(neutral, "Ermmm.....")
                    chatNpc(neutral, "So much can I tell you...")
                    chatPlayer(neutral, "Yes..")
                    chatNpc(neutral, "So much to tell...why, where do I start!")
                    chatPlayer(neutral, "Yes..yes!  Please go on...don't let me interrupt...")
                    chatNpc(neutral, "Ermmm.....")
                    chatNpc(neutral, "...")
                    chatPlayer(quiz, "You can't remember can you?")
                    chatNpc(sad, "Not a thing sorry....")
                }
                RomeoTopic.Done -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.romeoDeliverMessage() {
        if (invTotal(access.inv, JulietMessage) == 0) {
            chatPlayer(happy, "Romeo...great news...I've been in touch with Juliet!")
            chatNpc(happy, "Oh great! That is great news! Well done...well done...  " +
                "what a total success!")
            chatPlayer(neutral, "Yes, and she gave me a message to give you...")
            chatNpc(happy, "Ohhh great! A message....wow!")
            chatPlayer(happy, "Yes!")
            chatNpc(happy, "A message...oh, I can't wait to read what my dear Juliet " +
                "has to say....")
            chatPlayer(happy, "I know...it's exciting isn't it...?")
            chatNpc(neutral, "Yes...yes...")
            chatNpc(neutral, "...")
            chatNpc(sad, "You've lost the message haven't you?")
            chatPlayer(sad, "Yep, haven't got a clue where it is.")
            return
        }
        chatPlayer(
            happy,
            "Romeo...great news...I've been in touch with Juliet! She's written a " +
                "message for you...",
        )
        access.mes("You hand over Juliet's message to Romeo.")
        access.invDel(access.inv, JulietMessage, count = 1)
        chatNpc(happy, "Oh, a message! A message! I've never had a message before...")
        chatPlayer(quiz, "Really?")
        chatNpc(neutral, "No, no, not one!")
        chatNpc(neutral, "Oh, well, except for the occasional court summons.")
        chatNpc(
            happy,
            "But they're not really 'nice' messages. Not like this one! I'm sure that " +
                "this message will be lovely.",
        )
        chatPlayer(quiz, "Well are you going to open it or not?")
        chatNpc(
            neutral,
            "Oh yes, yes, of course!  'Dearest Romeo, I am very pleased that you sent " +
                "${player.displayName} to look for me and to tell me that you still hold " +
                "affliction...', Affliction! She thinks I'm diseased?",
        )
        chatPlayer(neutral, "'Affection?'")
        chatNpc(
            neutral,
            "Ahh yes...'still hold affection for me. I still feel great affection for " +
                "you, but unfortunately my Father opposes our marriage.'",
        )
        chatPlayer(sad, "Oh dear...that doesn't sound too good.")
        chatNpc(neutral, "What? '...great affection for you. Father opposes our..")
        chatNpc(neutral, "'...marriage and will...")
        chatNpc(shocked, "...will kill you if he sees you again!'")
        chatPlayer(neutral, "I have to be honest, it's not getting any better...")
        chatNpc(
            neutral,
            "'Our only hope is that Father Lawrence, our long time confidant, can help " +
                "us in some way.'",
        )
        access.mes("Romeo folds the message away.")
        chatNpc(sad, "Well, that's it then...we haven't got a chance...")
        chatPlayer(neutral, "What about Father Lawrence?")
        chatNpc(sad, "...our love is over...the great romance, the life of my love...")
        chatPlayer(neutral, "...or you could speak to Father Lawrence!")
        chatNpc(
            sad,
            "Oh, my aching, breaking, heart...how useless the situation is now...we " +
                "have no one to turn to...",
        )
        chatPlayer(angry, "FATHER LAWRENCE!")
        chatNpc(quiz, "Father Lawrence?")
        chatNpc(
            happy,
            "Oh yes, Father Lawrence...he's our long time confidant, he might have a " +
                "solution! Yes, yes, you have to go and talk to Lather Fawrence for us " +
                "and ask him if he's got any suggestions for our predicament?",
        )
        chatPlayer(quiz, "Where can I find Father Lawrence?")
        chatNpc(happy, "Lather Fawrence! Oh he's...")
        chatNpc(neutral, "You know he's not my 'real' Father don't you?")
        chatPlayer(neutral, "I think I suspected that he wasn't.")
        chatNpc(
            neutral,
            "Well anyway...he tells these song, loring bermons...and keeps these here " +
                "Carrockian vitizens snoring in his church to the East North.",
        )
        quest.setQuestStage(access, StagePassedMessage)
        romeoLawrenceTopics()
    }

    private suspend fun Dialogue.romeoAfterMessage() {
        chatPlayer(neutral, "Hey again Romeo!")
        romeoLawrenceTopics()
    }

    private suspend fun Dialogue.romeoLawrenceTopics() {
        while (true) {
            when (
                choice4(
                    "How are you?",
                    1,
                    "Where can I find Father Lawrence?",
                    2,
                    "Have you heard anything from Juliet?",
                    3,
                    "Ok, thanks.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "How are you?")
                    chatNpc(sad, "Not so good my friend...I miss Judi..., Junie..., Joopie...")
                    chatPlayer(neutral, "Juliet?")
                    chatNpc(sad, "Juliet! I miss Juliet, terribly!")
                    chatPlayer(neutral, "Hmmm, so I see!")
                }
                2 -> {
                    chatPlayer(quiz, "Where can I find Father Lawrence?")
                    chatNpc(happy, "Lather Fawrence! Oh he's...")
                    chatNpc(neutral, "You know he's not my 'real' Father don't you?")
                    chatPlayer(neutral, "I think I suspected that he wasn't.")
                    chatNpc(
                        neutral,
                        "Well anyway...he tells these song, loring bermons...and keeps " +
                            "these here Carrockian vitizens snoring in his church to the " +
                            "East North.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Have you heard anything from Juliet?")
                    chatNpc(
                        sad,
                        "Sadly not my friend! And what's worse, her Father has " +
                            "threatened to kill me if he sees me. I mean, that seems a " +
                            "bit harsh!",
                    )
                    chatPlayer(
                        neutral,
                        "Well, I shouldn't worry too much...you can always run away " +
                            "if you see him...",
                    )
                    chatNpc(
                        worried,
                        "I just wish I could remember what he looks like! I live in " +
                            "fear of every man I see!",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.romeoAfterFather() {
        chatPlayer(neutral, "Hello again Romeo!")
        chatNpc(
            quiz,
            "Ooh did you manage to survive one of Lather Fawrences sermons? I bet not, " +
                "you were ages! I bet you snoozed on the welcome mat just as soon as " +
                "you heard his voice!",
        )
        chatPlayer(angry, "Did not!")
        chatNpc(quiz, "Oh, come on, come on, what did he say?")
        while (true) {
            when (
                choice3(
                    "He wants me to go to the Apothecary!",
                    LawrenceTopic.Apothecary,
                    "He seems keen for you to marry Juliet.",
                    LawrenceTopic.Marriage,
                    "Ok, thanks.",
                    LawrenceTopic.Done,
                )
            ) {
                LawrenceTopic.Apothecary -> {
                    chatPlayer(neutral, "He wants me to go to the Apothecary!")
                    chatNpc(quiz, "The Apothecary?")
                    chatNpc(quiz, "Oh...is he the one who mixes up all them magical " +
                        "potion- ey things?")
                    chatPlayer(neutral, "Yeah, I think so...but the word potion-ey " +
                        "doesn't exist.")
                    chatNpc(laugh, "Well, you just used it...so I guess it does exist!")
                    chatPlayer(quiz, "It doesn't matter...do you know where the " +
                        "Apothecary is?")
                    chatNpc(quiz, "Why should I tell you?")
                    chatPlayer(angry, "Because I'm doing you a favour!")
                    chatNpc(
                        neutral,
                        "Right, yes...of course! Well, I think the potion-ey place is " +
                            "Wouth Sest of here and near a sword shop.",
                    )
                }
                LawrenceTopic.Marriage -> {
                    chatPlayer(neutral, "He seems very keen for you to marry Juliet.")
                    chatNpc(happy, "Me too! I can't wait! Do you think it will be soon?")
                    chatPlayer(
                        neutral,
                        "I don't know, but I'll do what I can. It just seems a bit odd " +
                            "that Father Lawrence is so keen for you to marry?",
                    )
                    chatNpc(
                        neutral,
                        "I can't imagine why he's so keen? Though, he was our messenger " +
                            "before you. Of course, he had a lot more hair back then..." +
                            "and I think we may have gone to school together...",
                    )
                    chatPlayer(quiz, "What, since you were kids?")
                    chatNpc(
                        laugh,
                        "Yeah! Thinking about it now, we called him 'Diddy Dorrence' " +
                            "back then!",
                    )
                    chatPlayer(
                        happy,
                        "So, maybe, he just wants a bit of peace? And when you two are " +
                            "married, he won't have to traipse around delivering your " +
                            "messages anymore?",
                    )
                    chatNpc(sad, "Yes, the years haven't been good to him.")
                }
                LawrenceTopic.Done -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.romeoApothecary() {
        if (invTotal(access.inv, CadavaPotion) > 0) {
            access.mes("Romeo spots the Cadava potion.")
            chatNpc(happy, "Ooohh, that's the potion is it? Rather you than me!")
            chatPlayer(angry, "I'm not drinking it! It's for Juliet!")
            chatNpc(
                neutral,
                "Well, I mean...it's probably delicious...and not dangerous at all. And " +
                    "you quite often see harmless drinks in a glowing sort of pink colour.",
            )
            chatPlayer(
                quiz,
                "Hmm, I'm not convinced...maybe you ought to try some before I give it " +
                    "to Juliet?",
            )
            chatNpc(shocked, "Urgh! No way!  Not in a million years!")
            chatPlayer(quiz, "Ok...but you know what to do when I've given this to Juliet?")
            chatNpc(neutral, "Oh, yeah...sure Lather Fawrence explained all that to me...")
        } else {
            chatPlayer(neutral, "Hi Romeo!")
            chatNpc(quiz, "Oh hello, have you seen Lather Fawrence?")
            chatPlayer(
                neutral,
                "Yes, he's given me details of a potion which should help resolve this " +
                    "situation. The Apothecary is helping me prepare it.",
            )
            chatNpc(shocked, "Ooooh, it all sounds horribly complicated?")
            chatNpc(happy, "All I can say is I'll be glad when Juliet's finally in that crypt!")
            chatPlayer(laugh, "Spoken like a true lover!")
        }
    }

    private suspend fun Dialogue.romeoCrypt(npcRepo: NpcRepository) {
        chatPlayer(
            neutral,
            "Romeo, it's all set. Juliet has drunk the potion and has been taken down " +
                "into the Crypt...now you just need to pop along and collect her.",
        )
        chatNpc(happy, "Ah right, the potion! Great...")
        chatNpc(quiz, "What potion would that be then?")
        chatPlayer(
            neutral,
            "The Cadava potion...you know, the one which will make her appear dead! " +
                "She's in the crypt, pop along and claim your true love.",
        )
        chatNpc(worried, "But I'm scared...will you come with me?")
        chatPlayer(happy, "Oh, ok...come on! I think I saw the entrance when I visited " +
            "there last...")
        cryptScene(npcRepo)
    }

    private suspend fun Dialogue.cryptScene(npcRepo: NpcRepository) {
        access.teleport(CryptPlayerSpot)
        val romeo = access.spawnSceneNpc(npcRepo, RomeoNpc, CryptRomeoSpot)
        val phillipa = access.spawnSceneNpc(npcRepo, PhillipaNpc, CryptPhillipaSpot)
        romeo.say("This is pretty scary...")
        chatNpcSpecific("Romeo", RomeoNpc, worried, "This is pretty scary...")
        chatPlayer(neutral, "Oh, be quiet...")
        chatPlayer(neutral, "We're here. Look, Juliet is over there!")
        chatPlayer(neutral, "You go over to her...and I'll go and wait over here...")
        chatNpcSpecific("Romeo", RomeoNpc, neutral, "Ohhh, ok then...")
        chatNpcSpecific("Romeo", RomeoNpc, neutral, "Hey...Juliet...")
        chatNpcSpecific("Romeo", RomeoNpc, quiz, "Juliet....?")
        chatNpcSpecific("Romeo", RomeoNpc, sad, "Oh dear...you seem to be dead.")
        phillipa.say("Hi Romeo...I'm Phillipa!")
        chatNpcSpecific("Phillipa", PhillipaNpc, happy, "Hi Romeo...I'm Phillipa!")
        chatNpcSpecific("Romeo", RomeoNpc, madlaugh, "Wow! You're a fox!")
        chatNpcSpecific(
            "Phillipa",
            PhillipaNpc,
            neutral,
            "It's a shame about Juliet...but perhaps we can meet up later?",
        )
        chatNpcSpecific("Romeo", RomeoNpc, quiz, "Who's Juliet?")
        npcRepo.del(romeo, SceneCleanupTicks)
        npcRepo.del(phillipa, SceneCleanupTicks)
        player.julietVisible = JulietBackHome
        quest.completeQuest(access)
        access.teleport(VarrockSquare)
    }

    private suspend fun Dialogue.romeoPostQuest() {
        chatNpc(sad, "I heard Juliet had died. Terrible business.")
        chatNpc(happy, "Her cousin and I are getting on well though. Thanks for your help.")
    }

    suspend fun ProtectedAccess.messageOnRomeo(npc: Npc) = startDialogue(npc) {
        if (player.rjStage() == StageSpokenJuliet && invTotal(access.inv, JulietMessage) > 0) {
            romeoDeliverMessage()
        } else {
            chatNpc(
                quiz,
                "Why are you waving that around at me as if it's something important?",
            )
        }
    }

    // ---------------------------------------------------------------- Juliet

    suspend fun ProtectedAccess.julietDialogue(npc: Npc, npcRepo: NpcRepository) =
        startDialogue(npc) { juliet(npc, npcRepo) }

    private suspend fun Dialogue.juliet(npc: Npc, npcRepo: NpcRepository) {
        when {
            player.rjStage() >= StageComplete -> julietPostQuest()
            player.rjStage() >= StageJulietCrypt -> julietPostQuest()
            player.rjStage() >= StageSpokenApothecary -> julietPotion(npc, npcRepo)
            player.rjStage() >= StageSpokenFather -> julietAfterFather()
            player.rjStage() >= StagePassedMessage -> julietAfterMessage()
            player.rjStage() >= StageSpokenJuliet -> julietAwaitingDelivery()
            player.rjStage() >= StageSpokenRomeo -> julietFirst()
            else -> {
                chatNpc(sad, "Romeo, Romeo, wherefore art thou Romeo?")
                access.mes("She seems to be lost in thought.")
            }
        }
    }

    private suspend fun Dialogue.julietFirst() {
        chatPlayer(neutral, "Juliet, I come from Romeo.  He begs me to tell you that he " +
            "cares still.")
        chatNpc(happy, "Oh how my heart soars to hear this news! Please take this " +
            "message to him with great haste.")
        chatPlayer(neutral, "Well, I hope it's good news...he was quite upset when I " +
            "left him.")
        chatNpc(
            neutral,
            "He's quite often upset...the poor sensitive soul. But I don't think he's " +
                "going to take this news very well, however, all is not lost.",
        )
        chatNpc(
            neutral,
            "Everything is explained in the letter, would you be so kind and deliver " +
                "it to him please?",
        )
        chatPlayer(happy, "Certainly, I'll do so straight away.")
        chatNpc(happy, "Many thanks! Oh, I'm so very grateful. You may be our only hope.")
        objbox(JulietMessage, zoom = 250, text = "Juliet gives you a message.")
        access.invAdd(access.inv, JulietMessage)
        quest.setQuestStage(access, StageSpokenJuliet)
    }

    private suspend fun Dialogue.julietAwaitingDelivery() {
        chatPlayer(neutral, "Hello Juliet!")
        chatNpc(
            quiz,
            "Hello there...have you delivered the message to Romeo yet? What news do " +
                "you have from my loved one?",
        )
        if (invTotal(access.inv, JulietMessage) > 0) {
            chatPlayer(sad, "Oh, sorry, I've not had chance to deliver it yet!")
            chatNpc(
                neutral,
                "Oh, that's a shame. I've been waiting so patiently to hear some " +
                    "word from him.",
            )
        } else {
            chatPlayer(
                shifty,
                "Hmmm, that's the thing about messages...they're so easy to misplace...",
            )
            chatNpc(
                angry,
                "How could you lose that message? It was incredibly important...and it " +
                    "took me an age to write! I used joined up writing and everything!",
            )
            chatNpc(
                neutral,
                "Please, take this new message to him, and please don't lose it.",
            )
            objbox(JulietMessage, zoom = 250, text = "Juliet gives you another message.")
            access.invAdd(access.inv, JulietMessage)
        }
    }

    private suspend fun Dialogue.julietAfterMessage() {
        chatPlayer(
            neutral,
            "Hi Juliet, I have passed your message on to Romeo..he's scared half out " +
                "of his wits at the news that your father wants to kill him.",
        )
        chatNpc(
            neutral,
            "Yes, unfortunately my father is quite the hunter, you may have seen some " +
                "the animal head trophies on the wall. And it would be so awful to see " +
                "Romeo's head up there with them!",
        )
        chatPlayer(neutral, "I know what you mean...")
        chatPlayer(
            shifty,
            "...his hair colour will clash terribly with the rest of the decoration.",
        )
        chatNpc(angry, "That's not what I was suggesting at all...")
        chatPlayer(laugh, "I know, I know...I was just kidding.")
        chatPlayer(
            neutral,
            "Anyway, don't worry because I'm on the case. I'm going to get some help " +
                "from Father Lawrence.",
        )
        chatNpc(
            happy,
            "Oh yes, I'm sure that Father Lawrence will come up with a solution. I " +
                "hope you find him soon.",
        )
    }

    private suspend fun Dialogue.julietAfterFather() {
        chatPlayer(
            neutral,
            "Hi Juliet, I've found Father Lawrence and he has a cunning plan. But for " +
                "it to work, I need to seek the Apothecary!",
        )
        chatNpc(
            happy,
            "Oh good! I knew Father Lawrence would come up with something. However, I " +
                "don't know where the apothecary is...I hope you find him soon. My " +
                "father's temper gets no better.",
        )
    }

    private suspend fun Dialogue.julietPotion(npc: Npc, npcRepo: NpcRepository) {
        if (invTotal(access.inv, CadavaPotion) == 0) {
            chatPlayer(neutral, "Hi Juliet!")
            chatNpc(
                quiz,
                "Hi ${player.displayName}, how close am I to being with my true love " +
                    "Romeo?",
            )
            chatPlayer(sad, "Sorry, I still have to get a special potion for you.")
            chatNpc(
                neutral,
                "Oh, I hope it isn't a love potion because you would be wasting your " +
                    "time. My love for Romeo grows stronger every minute...",
            )
            chatPlayer(neutral, "That must be because you're not with him...")
            chatNpc(sad, "Oh no! I long to be close to my true love Romeo!")
            chatPlayer(
                happy,
                "Well, ok then...I'll set about getting this potion as quickly as I can!",
            )
            chatNpc(happy, "Fair luck to you, the end is close.")
            return
        }
        chatPlayer(
            happy,
            "Hi Juliet! I have an interesting proposition for you...suggested by " +
                "Father Lawrence. It may be the only way you'll be able to escape from " +
                "this house and be with Romeo.",
        )
        chatNpc(quiz, "Go on....")
        chatPlayer(
            neutral,
            "I have a Cadava potion here, suggested by Father Lawrence. If you drink " +
                "it, it will make you appear dead!",
        )
        chatNpc(quiz, "Yes...")
        chatPlayer(
            neutral,
            "And when you appear dead...your still and lifeless corpse will be removed " +
                "to the crypt!",
        )
        chatNpc(happy, "Oooooh, a cold dark creepy crypt...")
        chatNpc(happy, "...sounds just peachy!")
        chatPlayer(
            neutral,
            "Then...Romeo can steal into the crypt and rescue you just as you wake up!",
        )
        chatNpc(quiz, "...and this is the great idea for getting me out of here?")
        chatPlayer(
            neutral,
            "To be fair, I can't take all the credit...in fact...it was all Father " +
                "Lawrence's suggestion...",
        )
        chatNpc(neutral, "Ok...if this is the best we can do...hand over the potion!")
        access.mes("You pass the suspicious potion to Juliet.")
        access.invDel(access.inv, CadavaPotion, count = 1)
        chatNpc(
            happy,
            "Wonderful! I just hope Romeo can remember to get me from the crypt.",
        )
        chatNpc(
            neutral,
            "Please go to Romeo and make sure he understands. Although I love his " +
                "gormless, lovelorn soppy ways, he can be a bit dense sometimes and I " +
                "don't want to wake up in that crypt on my own.",
        )
        balconyScene(npc, npcRepo)
    }

    private suspend fun Dialogue.balconyScene(npc: Npc, npcRepo: NpcRepository) {
        val phillipa = access.spawnSceneNpc(npcRepo, PhillipaNpc, BalconyPhillipaSpot)
        val draul = access.spawnSceneNpc(npcRepo, DraulNpc, BalconyDraulSpot)
        chatNpcSpecific(
            "Juliet",
            JulietNpc,
            happy,
            "Oh, here's Phillipa, my cousin...she's in on the plot too!",
        )
        chatNpcSpecific(
            "Juliet",
            JulietNpc,
            happy,
            "She's going to make it seem even more convincing!",
        )
        chatNpcSpecific(
            "Phillipa",
            PhillipaNpc,
            happy,
            "Yes, I'm quite the actress! Good luck dear cousin!",
        )
        chatNpcSpecific("Juliet", JulietNpc, happy, "Right...bottoms up!")
        npc.anim("seq.human_drink_from_vial_cadava")
        chatNpcSpecific("Juliet", JulietNpc, sad, "Urk!")
        player.julietVisible = JulietHidden
        chatNpcSpecific("Phillipa", PhillipaNpc, shocked, "Oh no...Juliet has...died!")
        chatPlayer(
            neutral,
            "You might be more believable if you're not smiling when you say it...",
        )
        chatNpcSpecific(
            "Phillipa",
            PhillipaNpc,
            neutral,
            "Oh yeah...you might be right...ok, let's try again.",
        )
        chatNpcSpecific("Phillipa", PhillipaNpc, sad, "Oh no...Juliet has...died?")
        chatPlayer(
            neutral,
            "Perhaps a bit louder, like you're upset...that your cousin has died!",
        )
        chatNpcSpecific(
            "Phillipa",
            PhillipaNpc,
            neutral,
            "Right...yes...Ok, ok, I think I'm getting my motivation now. Ok, let's " +
                "try this again!",
        )
        chatNpcSpecific(
            "Phillipa",
            PhillipaNpc,
            sad,
            "OH NO...JULIET HAS...DIED?.... Oooooohhhhhh....(sob), (sob).Juliet...my " +
                "poor dead cousin!",
        )
        draul.say("What's all that screaming?")
        chatNpcSpecific("Sir Draul", DraulNpc, quiz, "What's all that screaming?")
        chatNpcSpecific(
            "Sir Draul",
            DraulNpc,
            sad,
            "Oh no! My poor daughter...what has become of you?",
        )
        chatNpcSpecific(
            "Phillipa",
            PhillipaNpc,
            sad,
            "Poor Juliet...make preparations for her body to be placed in the Crypt...",
        )
        npcRepo.del(phillipa, SceneCleanupTicks)
        npcRepo.del(draul, SceneCleanupTicks)
        quest.setQuestStage(access, StageJulietCrypt)
    }

    private suspend fun Dialogue.julietPostQuest() {
        chatNpc(angry, "I sat in that cold crypt for ages waiting for Romeo.")
        chatNpc(angry, "That useless fool never showed up.")
        chatNpc(angry, "And all I got was indigestion. I am done with men like him.")
        chatNpc(angry, "Now go away before I call my father!")
    }

    // --------------------------------------------------------- Draul Leptoc

    suspend fun ProtectedAccess.draulDialogue(npc: Npc) = startDialogue(npc) { draul() }

    private suspend fun Dialogue.draul() {
        when {
            player.rjStage() >= StageComplete -> {
                chatNpc(angry, "I suppose you're quite pleased with yourself now...")
            }
            player.rjStage() >= StageJulietCrypt -> {
                chatNpc(
                    sad,
                    "My poor Juliet....she's dead...dead! I shouldn't have been so hard " +
                        "on her...she was my lovely daughter. Booo hoooo hooooo!",
                )
            }
            player.rjStage() >= StageSpokenFather -> draulPotionStage()
            player.rjStage() >= StagePassedMessage -> {
                chatNpc(
                    angry,
                    "Do you live here? If so, how's about a couple of hundred gold " +
                        "towards the rent eh? Pay your share I say...you don't want to " +
                        "be like that freeloading Romeo!",
                )
            }
            player.rjStage() >= StageSpokenJuliet -> draulMessageStage()
            else -> draulSnoop()
        }
    }

    private suspend fun Dialogue.draulSnoop() {
        chatNpc(angry, "What are you doing here? Snooping around...")
        when (
            choice4(
                "I've come to see Juliet on Romeo's behalf.",
                1,
                "I've just come to have a chat with Juliet.",
                2,
                "Oh...just looking around...",
                3,
                "Ok, thanks.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I've come to see Juliet on Romeo's behalf.")
                chatNpc(
                    verymad,
                    "What...what...Romeo! Why that good for nothing swine...he's always " +
                        "trying to get the affections of my daughter..that soppy, half " +
                        "brained nincompoop won't ever have the heart of my daughter.",
                )
                chatNpc(angry, "She deserves someone of character, wit and repose.")
                chatPlayer(quiz, "What's so wrong about Romeo?")
                chatNpc(
                    angry,
                    "Wrong! What's wrong with him...have you actually talked to him? " +
                        "He's nothing but a dim witted upperclass twit, totally useless.",
                )
                chatNpc(
                    angry,
                    "If he threw a stone at the ground, he'd probably miss! He's totally " +
                        "invisible when it's raining because he's so wet!",
                )
                chatNpc(
                    angry,
                    "If you started with what's right with him, you'd have much less " +
                        "to consider!",
                )
                chatPlayer(
                    neutral,
                    "Well, I admit, he's probably not the sharpest knife in the cutlery " +
                        "draw...",
                )
                chatNpc(
                    angry,
                    "Sharp? I've seen keener wit in root vegetables.  Anyway, stop " +
                        "changing the subject. Get out of here and don't think you can " +
                        "sneak up those stairs to see Juliet, because I'll catch you " +
                        "and then you'll be for it!",
                )
                chatPlayer(neutral, "That seems a bit harsh....")
                chatNpc(verymad, "Harsh but fair I think you'll find...now get OUT!")
            }
            2 -> {
                chatPlayer(neutral, "I've just come to have a chat with Juliet.")
                chatNpc(
                    quiz,
                    "What on earth about?  I hope you're not in cahoots with that good " +
                        "for nothing Romeo!",
                )
                chatPlayer(shifty, "Err..no of course not....why would I be?")
                chatNpc(
                    angry,
                    "He's been trying to wooo my daughter for an age. Up until now " +
                        "she's had the good sense to just ignore him. I just don't know " +
                        "what's gotten into her recently so that she would give him " +
                        "the time of day.",
                )
                chatPlayer(
                    happy,
                    "Well, love is mysterious!  Perhaps one day someone may even learn " +
                        "to love you!",
                )
                chatNpc(
                    shocked,
                    "What!  Someone may fall in love with me...what are you trying to " +
                        "insinuate?",
                )
                chatPlayer(shifty, "Err...Nothing....I guess I'd better be going now...")
            }
            3 -> {
                chatPlayer(neutral, "Oh...just looking around...")
                chatNpc(
                    verymad,
                    "Just looking around! This is MY house!  You might have at least " +
                        "'ASKED' to view my considerably well appointed abode...but " +
                        "no, you've just burst in with all the elegance of a Troll at " +
                        "a tea party.",
                )
                chatPlayer(
                    shifty,
                    "I can see that you're busy ranting so I'll just nip off and " +
                        "investigate a bit.",
                )
            }
            else -> chatPlayer(neutral, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.draulMessageStage() {
        chatNpc(angry, "What are you doing in my house? Up to no good I shouldn't wonder!")
        chatPlayer(
            neutral,
            "Just a small chore for Juliet, you do have a lovely daughter in her sir.",
        )
        chatNpc(neutral, "Oh...why, thank you...I've always tried to my best...")
        chatNpc(
            angry,
            "...Hang on! Enough of that smiley talk. I have a daughter and I know " +
                "what she's like. Don't even think of carrying on anything behind my " +
                "back, I have the eyes of a hawk, nothing gets past me!",
        )
        if (invTotal(access.inv, JulietMessage) > 0) {
            access.mes("Sir Draul notices the message!")
            chatNpc(
                quiz,
                "Hey! What's that in your hands...looks like a message to me...with " +
                    "Juliet's barely legible scrawl on it...",
            )
            chatPlayer(shifty, "Yes, yes, that's probably why I can't read it!")
            chatPlayer(
                shifty,
                "Sorry, I mean, that's right sir. I'm just popping to the shops to " +
                    "get some groceries for Juliet.",
            )
            chatPlayer(shifty, "Right, have to be off now...thanks...")
            chatNpc(angry, "Groceries!")
            chatNpc(
                verymad,
                "Groceries!...at a time like this, does that girl know what she's " +
                    "putting me through!",
            )
        }
    }

    private suspend fun Dialogue.draulPotionStage() {
        chatNpc(angry, "Hey , what are you doing here?")
        chatPlayer(
            shifty,
            "Nothing much sir...I promise...I'm just an innocent friend of " +
                "Juliet's...doing her a few favours...as a friend.",
        )
        chatNpc(
            quiz,
            "Well, just make sure there's no funny business going on that's all I " +
                "can say. Do you know why?",
        )
        chatPlayer(neutral, "I think I can guess sir...")
        chatNpc(
            angry,
            "There's no need to guess! You can see it in my hawk like eyes, in my " +
                "cat like ears and my dog like nose...",
        )
        chatPlayer(quiz, "Are you saying that you look like an animal sir?")
        chatNpc(
            verymad,
            "NO! Not look like an animal! I have the keen 'SENSES' of an animal and " +
                "I don't miss a thing. Don't even think about trying anything!",
        )
        if (invTotal(access.inv, CadavaPotion) > 0) {
            access.mes("Draul notices the potion!")
            chatNpc(
                quiz,
                "Hey!  What's that in your hands? Looks like some sort of potion to me!",
            )
            chatPlayer(
                shifty,
                "Err...no! Nope! Not a potion!  Some medicine...I have a terrible " +
                    "cough  ...cough...cough....see?",
            )
            chatPlayer(
                shifty,
                ">Except, I can't drink too much or else it makes me feel 'really' " +
                    "tired. One sip of this and the lights go out...I mean, I'm " +
                    "asleep in a minute.",
            )
            chatNpc(quiz, "Sleep!")
            chatNpc(
                angry,
                "Sleep...! How can you even think of such a thing at a time like " +
                    "this? And I hope you have no intentions of sleeping in my house!",
            )
            chatNpc(
                angry,
                "You'll get a bill for rent if I catch you dozing off around here!",
            )
        }
    }

    // -------------------------------------------------------------- Phillipa

    suspend fun ProtectedAccess.phillipaDialogue(npc: Npc) = startDialogue(npc) { phillipa() }

    private suspend fun Dialogue.phillipa() {
        when {
            player.rjStage() >= StageComplete -> {
                chatNpc(
                    happy,
                    "Hello, I seem to be getting on really well with that dashing " +
                        "lovely dovely, Romeo. Pity about poor Juliet - she missed " +
                        "out there I reckon!",
                )
            }
            player.rjStage() >= StageJulietCrypt -> {
                chatNpc(happy, "Oh, hello again! How was I - do you think I was convincing?")
                chatPlayer(happy, "Oh, yes, totally!")
                chatNpc(
                    happy,
                    "Ohhh good! I do hope Juliet will be pleased as well! That dashing " +
                        "young Romeo must be beside himself with excitement! Have " +
                        "you told him the good news yet?",
                )
                chatPlayer(neutral, "Not yet, but soon!")
                chatNpc(
                    happy,
                    "Good, good - you lucky thing, I can't wait to see the look on " +
                        "his face!",
                )
            }
            player.rjStage() >= StageSpokenRomeo -> {
                chatNpc(
                    neutral,
                    "Oh, hello. Juliet has told me what you're doing for her and " +
                        "Romeo, and I have to say I'm very grateful to you. Juliet " +
                        "deserves a bit of happiness in her life.",
                )
                chatNpc(
                    happy,
                    "And I'm sure Romeo is just the sort of jester to make her laugh " +
                        "out loud - hysterically you might say.",
                )
                chatNpc(
                    laugh,
                    "He always brings a tear to my eyes - tears of happiness at his " +
                        "foolish antics!",
                )
                chatPlayer(happy, "Oh, thanks. I like to do my cupid bit.")
            }
            else -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    happy,
                    "Hi, I'm Phillipa, Juliet's cousin. I like to keep an eye on " +
                        "her, make sure that dashing young Romeo doesn't just steal " +
                        "her away under our plain old noses!",
                )
                chatNpc(
                    neutral,
                    "He'd do it, you know - he's ever so dashing, and cavalier, in a " +
                        "wet blanket sort of way.",
                )
                chatPlayer(quiz, "Romeo? Where would I find him then?")
                chatNpc(
                    quiz,
                    "Well, that's a good question! Who knows where his head's at " +
                        "most of the time? In the clouds, most likely!",
                )
                chatNpc(
                    shifty,
                    "But he's probably chasing the ladies who frequent Varrock " +
                        "market. He does like a bit of kiss chase, so I've heard!",
                )
            }
        }
    }

    // -------------------------------------------------------- Father Lawrence

    suspend fun ProtectedAccess.fatherLawrenceDialogue(npc: Npc, npcRepo: NpcRepository) =
        startDialogue(npc) { fatherLawrence(npc, npcRepo) }

    private suspend fun Dialogue.fatherLawrence(npc: Npc, npcRepo: NpcRepository) {
        when {
            player.rjStage() >= StageJulietCrypt -> fatherLawrenceDrunk()
            player.rjStage() >= StageSpokenApothecary -> fatherLawrenceApothecary()
            player.rjStage() >= StageSpokenFather -> {
                chatNpc(
                    quiz,
                    "Ah, have you found the Apothecary yet? Remember, Cadava potion, " +
                        "for Juliet.",
                )
            }
            player.rjStage() >= StageSpokenJuliet -> fatherLawrenceSermon(npc, npcRepo)
            else -> fatherLawrenceDefault()
        }
    }

    private suspend fun Dialogue.fatherLawrenceSermon(npc: Npc, npcRepo: NpcRepository) {
        chatNpc(angry, "''...and let Saradomin light the way for you... '' Urgh!")
        chatNpc(angry, "Can't you see that I'm in the middle of a Sermon?!")
        chatPlayer(neutral, "But Romeo sent me!")
        chatNpc(angry, "But I'm busy delivering a sermon to my congregation!")
        access.sayCongregation(npcRepo, npc)
        chatPlayer(laugh, "Yes, well, it certainly seems like you have a captive audience!")
        chatNpc(
            angry,
            "Ok, ok...what do you want so I can get rid of you and continue with my sermon?",
        )
        chatPlayer(neutral, "Romeo sent me. He says you may be able to help.")
        chatNpc(neutral, "Ah Romeo, yes. A fine lad, but a little bit confused.")
        chatPlayer(
            neutral,
            "Yes, very confused....Anyway, Romeo wishes to be married to Juliet! She " +
                "must be rescued from her father's control!",
        )
        chatNpc(happy, "I agree, and I think I have an idea! A potion to make her " +
            "appear dead...")
        chatPlayer(shocked, "Dead! Sounds a bit creepy to me...but please, continue.")
        chatNpc(
            neutral,
            "The potion will only make Juliet 'appear' dead...then she'll be taken " +
                "to the crypt...",
        )
        chatPlayer(quiz, "Crypt! Again...very creepy! You must have some strange hobbies.")
        chatNpc(
            neutral,
            "Then Romeo can collect her from the crypt! Go to the Apothecary, tell him " +
                "I sent you and that you'll need a 'Cadava' potion.",
        )
        chatPlayer(
            happy,
            "Apart from the strong overtones of death, this is turning out to be a " +
                "real love story.",
        )
        quest.setQuestStage(access, StageSpokenFather)
    }

    private suspend fun Dialogue.fatherLawrenceApothecary() {
        if (invTotal(access.inv, CadavaPotion) > 0) {
            chatNpc(quiz, "Did you find the Apothecary?")
            chatPlayer(happy, "I've got the cadava potion.")
            chatNpc(
                happy,
                "Good! Good work! Ok, take it to Juliet, she's expecting you. I'll " +
                    "talk to Romeo and make sure he knows what to do.",
            )
        } else {
            chatNpc(quiz, "Did you find the Apothecary?")
            chatPlayer(neutral, "Yes I did. He's told me I must find some Cadava berries.")
            chatNpc(neutral, "Well, good luck with that...they're quite tricky to find.")
            chatPlayer(quiz, "Any clues where I can start to look?")
            chatNpc(
                neutral,
                "I heard some kids saying they saw some the other day. They were " +
                    "visiting the mining place to the south east of Varrock.",
            )
            chatPlayer(neutral, "Ok, that's as good a place to start looking as any.")
        }
    }

    private suspend fun Dialogue.fatherLawrenceDrunk() {
        chatNpc(drunk, "Oh to be a father in the times of whiskey.")
        chatNpc(drunk, "I sing and I drink and I wake up in gutters.")
        if (kotlin.random.Random.nextBoolean()) {
            chatNpc(drunk, "Top of the morning to you.")
        } else {
            chatNpc(drunk, "To err is human, to forgive, quite difficult.")
            chatNpc(drunk, "I need a think I drink.")
        }
    }

    private suspend fun Dialogue.fatherLawrenceDefault() {
        chatNpc(neutral, "Hello adventurer, do you seek a quest?")
        while (true) {
            when (
                choice4(
                    "I am always looking for a quest.",
                    1,
                    "No, I prefer just to kill things.",
                    2,
                    "Can you recommend a good bar?",
                    3,
                    "Ok, thanks",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(happy, "I am always looking for a quest.")
                    chatNpc(
                        neutral,
                        "Well, I see poor Romeo wandering around the square. I think " +
                            "he may need help.",
                    )
                    chatNpc(
                        neutral,
                        "I was helping him and Juliet to meet, but it became impossible.",
                    )
                    chatNpc(neutral, "I am sure he can use some help.")
                }
                2 -> {
                    chatPlayer(neutral, "No, I prefer just to kill things.")
                    chatNpc(
                        neutral,
                        "That's a fine career in these lands. There is more that " +
                            "needs killing every day.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Can you recommend a good bar?")
                    chatNpc(neutral, "Drinking will be the death of you.")
                    chatNpc(neutral, "But the Blue Moon in the city is cheap enough.")
                    chatNpc(
                        neutral,
                        "And providing you buy one drink an hour they let you stay " +
                            "all night.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks")
                    return
                }
            }
        }
    }

    // ------------------------------------------------------------- Apothecary

    suspend fun ProtectedAccess.apothecaryDialogue(npc: Npc) =
        startDialogue(npc) { apothecary() }

    private suspend fun Dialogue.apothecary() {
        chatNpc(neutral, "I am the Apothecary. I brew potions. Do you need anything specific?")
        apothecaryTopics()
    }

    private suspend fun Dialogue.apothecaryTopics() {
        while (true) {
            val choice =
                if (player.rjStage() in StageSpokenFather until StageComplete) {
                    choice4(
                        "Can you make potions for me?",
                        ApothTopic.Potions,
                        "Talk about something else.",
                        ApothTopic.SomethingElse,
                        "Talk about Romeo & Juliet.",
                        ApothTopic.RomeoJuliet,
                        "No thanks.",
                        ApothTopic.NoThanks,
                    )
                } else {
                    choice3(
                        "Can you make potions for me?",
                        ApothTopic.Potions,
                        "Talk about something else.",
                        ApothTopic.SomethingElse,
                        "No thanks.",
                        ApothTopic.NoThanks,
                    )
                }
            when (choice) {
                ApothTopic.Potions -> {
                    chatPlayer(neutral, "Can you make potions for me?")
                    access.ifOpenMainModal("interface.apothecary_potions")
                    return
                }
                ApothTopic.SomethingElse -> apothecaryGossip()
                ApothTopic.RomeoJuliet -> apothecaryRomeoJuliet()
                ApothTopic.NoThanks -> {
                    chatPlayer(neutral, "No thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.apothecaryGossip() {
        chatPlayer(neutral, "Talk about something else.")
        while (true) {
            val choice =
                if (player.rjStage() in StageSpokenFather until StageComplete) {
                    choice5(
                        "Talk about Romeo & Juliet.",
                        GossipTopic.RomeoJuliet,
                        "Have you got any decent gossip to share?",
                        GossipTopic.Gossip,
                        "Do you know a potion to make hair fall out?",
                        GossipTopic.Hair,
                        "Have you got any good potions to give away?",
                        GossipTopic.Freebie,
                        "No thanks.",
                        GossipTopic.NoThanks,
                    )
                } else {
                    choice4(
                        "Have you got any decent gossip to share?",
                        GossipTopic.Gossip,
                        "Do you know a potion to make hair fall out?",
                        GossipTopic.Hair,
                        "Have you got any good potions to give away?",
                        GossipTopic.Freebie,
                        "No thanks.",
                        GossipTopic.NoThanks,
                    )
                }
            when (choice) {
                GossipTopic.RomeoJuliet -> {
                    apothecaryRomeoJuliet()
                    return
                }
                GossipTopic.Gossip -> {
                    chatPlayer(quiz, "Have you got any decent gossip to share?")
                    if (player.rjStage() == 0) {
                        chatNpc(
                            neutral,
                            "Well I hear young Romeo's having a little woman trouble " +
                                "but other than that all's quiet on the eastern front. " +
                                "Can I do something for you?",
                        )
                        return
                    } else if (player.rjStage() >= StageComplete) {
                        chatNpc(sad, "Sad about that affair with young Romeo and Juliet...")
                        chatNpc(
                            neutral,
                            "I hear every time Romeo sees Juliet now he runs away " +
                                "screaming something about ghosts and Juliet's cousin?",
                        )
                        chatNpc(neutral, "Always did think he was a bit of a strange one...")
                        chatNpc(
                            neutral,
                            "Anyway! Life goes on and so does business! Can I do " +
                                "something for you?",
                        )
                        return
                    } else {
                        return
                    }
                }
                GossipTopic.Hair -> {
                    chatPlayer(quiz, "Do you know a potion to make hair fall out?")
                    chatNpc(
                        shifty,
                        "I do indeed. I gave it to my mother. That's why I now live alone.",
                    )
                    chatNpc(neutral, "But can I do something for you?")
                    return
                }
                GossipTopic.Freebie -> {
                    chatPlayer(quiz, "Have you got any good potions to give away?")
                    chatNpc(
                        neutral,
                        "Sorry, charity is not my strong point. Do you need anything else?",
                    )
                    return
                }
                GossipTopic.NoThanks -> {
                    chatPlayer(neutral, "No thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.apothecaryRomeoJuliet() {
        chatPlayer(
            neutral,
            "Apothecary, Father Lawrence sent me. I need a Cadava potion to help " +
                "Romeo and Juliet.",
        )
        when {
            invTotal(access.inv, CadavaPotion) > 0 -> {
                chatPlayer(happy, "Thank you for the Cadava potion.")
                chatNpc(
                    happy,
                    "You're welcome. I hope it helps that young couple find happiness.",
                )
            }
            player.rjStage() >= StageJulietCrypt -> {
                chatPlayer(neutral, "I gave Juliet the Cadava potion.")
                chatNpc(
                    happy,
                    "Good, good. I hope it helps that young couple find happiness.",
                )
            }
            invTotal(access.inv, CadavaBerries) > 0 -> apothecaryBrew()
            player.rjStage() >= StageSpokenApothecary -> {
                chatNpc(
                    neutral,
                    "Keep searching for those Cadavaberries. They're needed for the potion.",
                )
            }
            else -> {
                chatNpc(neutral, "Cadava potion. It's pretty nasty. And hard to make.")
                chatNpc(neutral, "Wing of rat, tail of frog. Ear of snake and horn of dog.")
                chatNpc(neutral, "I have all that, but I need some Cadava berries.")
                chatNpc(
                    neutral,
                    "You will have to find them while I get the rest ready. Bring them " +
                        "here when you have them. But be careful. They are nasty.",
                )
                quest.setQuestStage(access, StageSpokenApothecary)
                berryTopics()
            }
        }
    }

    private suspend fun Dialogue.apothecaryBrew() {
        if (player.rjStage() >= StageSpokenApothecary) {
            chatNpc(happy, "Well done. You have the berries.")
        } else {
            chatNpc(neutral, "Cadava potion. It's pretty nasty. And hard to make.")
            chatNpc(neutral, "Wing of rat, tail of frog. Ear of snake and horn of dog.")
            chatPlayer(happy, "Conveniently, I have some here.")
            quest.setQuestStage(access, StageSpokenApothecary)
        }
        access.mes(
            "You hand over the berries, which the Apothecary shakes up in a vial of " +
                "strange liquid."
        )
        access.invDel(access.inv, CadavaBerries, count = 1)
        access.mes("The apothercary mixes the potion, creating a burst of smoke.")
        chatNpc(happy, "Phew! Here is what you need.")
        access.anim("seq.human_apothecary_create_potion")
        objbox(CadavaPotion, zoom = 250, text = "The Apothecary gives you a Cadava potion.")
        access.invAdd(access.inv, CadavaPotion)
    }

    private suspend fun Dialogue.berryTopics() {
        while (true) {
            when (
                choice4(
                    "What do these berries look like?",
                    BerryTopic.Look,
                    "Where can I get these berries?",
                    BerryTopic.Where,
                    "How are these berries dangerous?",
                    BerryTopic.Danger,
                    "Ok, thanks.",
                    BerryTopic.Done,
                )
            ) {
                BerryTopic.Look -> {
                    chatPlayer(quiz, "What do these berries look like?")
                    chatNpc(
                        neutral,
                        "They're a nice bright pink colour, with green leaves, stalks " +
                            "are a pale brown colour...you can't miss them. They look " +
                            "pretty tasty, but I wouldn't advise eating them.",
                    )
                }
                BerryTopic.Where -> {
                    chatPlayer(quiz, "Where can I get these berries?")
                    chatNpc(
                        neutral,
                        "I think I saw some recently in the local vicinity. Oh yes, " +
                            "that's right! There was a little bit of commotion when " +
                            "some kids visited the south eastern Varrock mining pit. " +
                            "The trip was disrupted because some kids",
                    )
                    chatNpc(
                        neutral,
                        "had found some cadavaberry bushes and they started picking " +
                            "them. They wrapped that trip up pretty quickly!",
                    )
                }
                BerryTopic.Danger -> {
                    chatPlayer(quiz, "How are these berries dangerous?")
                    chatNpc(
                        neutral,
                        "Hmm, they're generally okay, but if handled improperly they " +
                            "can be a bit nasty. And I certainly wouldn't advise " +
                            "drinking the potion!",
                    )
                }
                BerryTopic.Done -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    suspend fun ProtectedAccess.berriesOnApothecary(npc: Npc) = startDialogue(npc) {
        if (player.rjStage() in StageSpokenFather until StageJulietCrypt) {
            apothecaryBrew()
        } else {
            chatNpc(neutral, "I don't need those any more.")
        }
    }

    // ------------------------------------------------------------ Scene setup

    internal fun ProtectedAccess.spawnSceneNpc(
        npcRepo: NpcRepository,
        internal: String,
        spot: CoordGrid,
    ): Npc {
        val type =
            checkNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }
        val npc = Npc(type, spot)
        npcRepo.add(npc, SceneNpcLifespan)
        return npc
    }

    internal fun ProtectedAccess.sayCongregation(npcRepo: NpcRepository, anchor: Npc) {
        npcRepo
            .findAll(ZoneKey.from(anchor.coords), zoneRadius = 1)
            .filter {
                it.isType("npc.romeo_juliet_pew_sleeping_man") ||
                    it.isType("npc.romeo_juliet_pew_sleeping_woman")
            }
            .forEach { it.say("Zzzzzzzzz") }
    }

    // -------------------------------------------------------------- Constants

    internal const val StageSpokenRomeo = 10
    internal const val StageSpokenJuliet = 20
    internal const val StagePassedMessage = 30
    internal const val StageSpokenFather = 40
    internal const val StageSpokenApothecary = 50
    internal const val StageJulietCrypt = 60
    internal const val StageComplete = 100

    private const val JulietVisibleVarbit = "varbit.romjul_juliet_visible"
    private const val JulietHidden = 1
    private const val JulietBackHome = 2

    internal const val RomeoNpc = "npc.romeo"
    internal const val JulietNpc = "npc.juliet"
    internal const val PhillipaNpc = "npc.phillipa"
    internal const val DraulNpc = "npc.draul_leptoc"
    internal const val JulietMessage = "obj.julietmessage"
    internal const val CadavaPotion = "obj.cadava"
    internal const val CadavaBerries = "obj.cadavaberries"

    private val BalconyPhillipaSpot = CoordGrid(3159, 3428, 1)
    private val BalconyDraulSpot = CoordGrid(3156, 3425, 1)

    private val CryptPlayerSpot = CoordGrid(2331, 4640, 0)
    private val CryptRomeoSpot = CoordGrid(2330, 4643, 0)
    private val CryptPhillipaSpot = CoordGrid(2324, 4642, 0)
    private val VarrockSquare = CoordGrid(3213, 3423, 0)

    private const val SceneNpcLifespan = 5000
    private const val SceneCleanupTicks = 2
}
