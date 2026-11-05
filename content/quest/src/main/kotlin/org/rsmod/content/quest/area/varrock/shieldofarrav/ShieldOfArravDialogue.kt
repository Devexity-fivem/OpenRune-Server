package org.rsmod.content.quest.area.varrock.shieldofarrav

import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.BlackArmCharlieHint
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.BlackArmComplete
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.BlackArmJoined
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.BlackArmKatrineTask
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.CertLeft
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.CertRight
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.Certificate
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.CoinReward
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.Crossbow
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.IntelReport
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixBookRead
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixComplete
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixJoined
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixLocationKnown
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.PhoenixStravenTask
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.ShieldLeft
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.ShieldRight
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.StageComplete
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.WeaponKey
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Shield of Arrav dialogue, transcribed from the modern OSRS transcript with
 * LostCity's state model: `varp.phoenixgang` (1 book, 2 has book, 3 read,
 * 4 location known, 8 straven task, 9 member, 10 complete) and
 * `varp.blackarmgang` (1 charlie hint, 2 katrine task, 3 member, 4 complete).
 * `varbit.shieldofarrav` tracks the coarse quest stage (0/1/2) for the journal.
 */

val soaQuest: Quest
    get() = checkNotNull(Quest.get(ShieldOfArrav.KEY)) { "quest not registered" }

fun Player.soaStage(): Int = soaQuest.getQuestStage(this)

var Player.phoenixGang by intVarp("varp.phoenixgang")
var Player.blackArmGang by intVarp("varp.blackarmgang")

var Player.soaPhoenixSource by boolVarBit("varbit.soa_phoenix_source")
var Player.soaCharlieMet by boolVarBit("varbit.soa_charlie_met")
var Player.soaCharliePaid by boolVarBit("varbit.soa_charlie_paid")
var Player.soaCharlieHint by boolVarBit("varbit.soa_charlie_hint")
var Player.soaShieldGiven by intVarBit("varbit.soa_shield_given")
var Player.soaStravenShieldChat by boolVarBit("varbit.soa_straven_shield_chat")

private const val ShieldGivenPhoenix = 1
private const val ShieldGivenBlackArm = 2

private const val Coins = "obj.coins"

object ShieldOfArravDialogue {

    fun Player.hasAnywhere(obj: String): Boolean =
        inv.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

    // ================================================================ Straven

    suspend fun ProtectedAccess.stravenDialogue(npc: Npc) = startDialogue(npc) {
        when {
            player.blackArmGang >= BlackArmJoined ->
                chatNpc(angry, "Stay away from here! Black Arm dog!")
            player.phoenixGang >= PhoenixJoined -> gangMemberDialogue()
            player.phoenixGang == PhoenixStravenTask -> missionReminder()
            else -> cantEnter()
        }
    }

    suspend fun Dialogue.cantEnter() {
        chatPlayer(quiz, "What's through that door?")
        chatNpc(
            neutral,
            "You can't go in there. Only authorised personnel of the VTAM " +
                "Corporation are allowed beyond this point.",
        )
        val choice =
            menu(
                buildList {
                    if (player.soaPhoenixSource) add("I know who you are!" to 1)
                    add("How do I get a job with the VTAM corporation?" to 2)
                    add("Fair enough." to 3)
                }
            )
        when (choice) {
            1 -> knowWhoYouAre()
            2 -> {
                chatPlayer(quiz, "How do I get a job with the VTAM corporation?")
                chatNpc(
                    neutral,
                    "Get a copy of the Varrock Herald. If we have any positions " +
                        "right now, they'll be advertised in there.",
                )
            }
            3 -> chatPlayer(neutral, "Fair enough.")
        }
    }

    private suspend fun Dialogue.knowWhoYouAre() {
        chatPlayer(angry, "I know who you are!")
        chatNpc(quiz, "Really? Well? Who are we then?")
        chatPlayer(
            neutral,
            "This is the headquarters of one of the most dangerous crime " +
                "syndicates this city has ever seen! The Phoenix Gang!",
        )
        chatNpc(
            neutral,
            "No, this is a legitimate business run by legitimate individuals. " +
                "However... supposing we were this crime gang, what would you want with us?",
        )
        when (
            menu(
                "I'd like to offer you my services." to 1,
                "I want nothing. I was just making sure you were them." to 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'd like to offer you my services.")
                chatNpc(
                    neutral,
                    "You mean you'd like to join the Phoenix Gang? Well, obviously " +
                        "I can't speak for them, but the Phoenix Gang doesn't let people " +
                        "join just like that. First, you'd need to prove your loyalty.",
                )
                chatPlayer(quiz, "How would I go about doing that?")
                chatNpc(
                    neutral,
                    "Obviously, I would have no idea. Although, having said that, a " +
                        "rival gang of ours, er, theirs, called the Black Arm Gang is " +
                        "supposedly meeting a contact from Port Sarim today.",
                )
                chatNpc(
                    neutral,
                    "Apparently the meeting is going to take place in the Blue Moon " +
                        "Inn, and supposedly the contact is none other than the notorious " +
                        "mass murderer turned pirate captain, Jonny the Beard.",
                )
                chatNpc(
                    neutral,
                    "Now, obviously I know nothing about the dealings of the Phoenix " +
                        "Gang, but I bet if somebody were to arrange an accident for Jonny " +
                        "and bring back his intelligence report, the gang would be very " +
                        "pleased.",
                )
                chatPlayer(
                    neutral,
                    "I see... I'm sure I can help sort something out for them. " +
                        "I'll get right on it.",
                )
                player.phoenixGang = PhoenixStravenTask
            }
            2 -> {
                chatPlayer(neutral, "I want nothing. I was just making sure you were them.")
                chatNpc(angry, "Well then get lost and stop wasting my time.")
            }
        }
    }

    suspend fun Dialogue.missionReminder() {
        chatNpc(quiz, "How's your little mission going?")
        when {
            !player.hasAnywhere(IntelReport) -> {
                chatPlayer(sad, "I don't have it yet...")
                chatNpc(
                    neutral,
                    "Jonny the Beard is the person who has it. I imagine he will be " +
                        "in the Blue Moon Inn... Not that I'd know for sure, with me " +
                        "definitely not being a member of the Phoenix Gang and all.",
                )
            }
            !access.inv.contains(IntelReport) -> {
                chatPlayer(neutral, "I have the intelligence report!")
                chatNpc(neutral, "Let's see it then.")
                chatPlayer(neutral, "Let me go get it from the bank.")
                chatNpc(neutral, "I'll wait here.")
            }
            else -> {
                chatPlayer(neutral, "I have the intelligence report!")
                chatNpc(neutral, "Let's see it then.")
                mesbox("You show the report to Straven.")
                chatNpc(
                    happy,
                    "Very good... Very good... Okay, I'm satisfied. Welcome to the " +
                        "Phoenix Gang! I'm Straven, one of the gang leaders.",
                )
                chatPlayer(happy, "Nice to meet you. I'm ${player.displayName}.")
                chatNpc(
                    neutral,
                    "As a member of our gang, you now have access to our hideout and " +
                        "our weapons supply depot. The hideout is just through this door " +
                        "and the depot is two doors down from here. You'll need this key " +
                        "to get in.",
                )
                access.invDel(access.inv, IntelReport, 1)
                access.invAdd(access.inv, WeaponKey, 1)
                player.phoenixGang = PhoenixJoined
                objbox(WeaponKey, "Straven hands you a key.")
                chatNpc(happy, "Once again, welcome to the Phoenix Gang!")
            }
        }
    }

    private suspend fun Dialogue.gangMemberDialogue() {
        chatNpc(happy, "Greetings, fellow gang member.")
        if (!player.hasAnywhere(WeaponKey)) {
            chatPlayer(sad, "I'm afraid I lost that key you gave me...")
            chatNpc(
                neutral,
                "You really need to be more careful. We don't want that key falling " +
                    "into the wrong hands. Have this spare, but don't lose it this time!",
            )
            access.invAdd(access.inv, WeaponKey, 1)
            objbox(WeaponKey, "Straven hands you a key.")
            return
        }
        when (
            menu(
                "I've heard you've got some cool treasures in this place." to 1,
                "Any suggestions for where I can go thieving?" to 2,
                "Where's the Black Arm Gang hideout?" to 3,
                "I'd better get going." to 4,
            )
        ) {
            1 -> treasuresChat()
            2 -> {
                chatPlayer(quiz, "Any suggestions for where I can go thieving?")
                chatNpc(
                    neutral,
                    "You could always try the marketplace over in Ardougne. " +
                        "Lots of opportunity there!",
                )
            }
            3 -> {
                chatPlayer(quiz, "Where's the Black Arm Gang hideout?")
                chatNpc(
                    neutral,
                    "Interested in spying on the enemy, are you? It will be " +
                        "tricky. Their security is pretty good. Not as good as " +
                        "ours obviously, but still good.",
                )
                chatNpc(
                    neutral,
                    "Still, if you really want to go there, it's down an " +
                        "alleyway near the southern gate. One of our contacts, a " +
                        "tramp going by the name of Charlie, keeps an eye on it. " +
                        "He may be able to help you.",
                )
                chatPlayer(happy, "I see. Thanks for the help!")
            }
            4 -> chatPlayer(neutral, "I'd better get going.")
        }
    }

    private suspend fun Dialogue.treasuresChat() {
        chatPlayer(neutral, "I've heard you've got some cool treasures in this place.")
        chatNpc(
            happy,
            "Oh yeah, we've all stolen some stuff in our time. Our candlesticks " +
                "down here, for example, were quite a challenge to get out of the palace.",
        )
        chatPlayer(quiz, "And the Shield of Arrav?")
        player.soaStravenShieldChat = true
        if (player.soaStage() >= StageComplete) {
            chatNpc(neutral, "Once one of our most prized possessions.")
            chatPlayer(quiz, "Once?")
            chatNpc(
                neutral,
                "It seems the Black Arm Gang weren't content with having one half. " +
                    "They recently stole the other half of the shield from us as well!",
            )
            chatPlayer(
                neutral,
                "Oh no! I'll be sure to keep an eye out for any of their members " +
                    "sneaking around.",
            )
        } else {
            chatNpc(
                shocked,
                "Woah... that's a blast from the past! We stole that years and " +
                    "years ago!",
            )
            chatNpc(
                neutral,
                "Truth be told, we don't even have the whole shield anymore. A few " +
                    "years ago there was a big fight within the gang and the shield " +
                    "got broken in half as a result.",
            )
            chatNpc(
                neutral,
                "After the fight, some gang members split off and formed their own " +
                    "group. They took one half of the shield with them.",
            )
            chatPlayer(quiz, "This other group? The Black Arm Gang?")
            chatNpc(neutral, "Indeed.")
        }
    }

    // ================================================================ Katrine

    suspend fun ProtectedAccess.katrineDialogue(npc: Npc) = startDialogue(npc) {
        when {
            player.phoenixGang >= PhoenixJoined ->
                chatNpc(
                    angry,
                    "You've got some guts coming here, Phoenix scum! Get lost, or " +
                        "I'll make sure you haven't got those guts anymore!",
                )
            player.blackArmGang >= BlackArmJoined -> katrineMemberDialogue()
            player.blackArmGang == BlackArmKatrineTask -> katrineTaskDialogue()
            else -> katrineIntro()
        }
    }

    private suspend fun Dialogue.katrineIntro() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(neutral, "It's a private business. Can I help you at all?")
        when (
            menu(
                "I've heard you're the Black Arm Gang." to 1,
                "What sort of business?" to 2,
                "I'm looking for fame and riches." to 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I've heard you're the Black Arm Gang.")
                chatNpc(quiz, "Who told you that?")
                when (
                    menu(
                        "I'd rather not reveal my sources." to 1,
                        "It was Charlie, the tramp outside." to 2,
                        "Everyone knows. It's no great secret." to 3,
                    )
                ) {
                    1 -> {
                        chatPlayer(neutral, "I'd rather not reveal my sources.")
                        chatNpc(
                            neutral,
                            "Hmm... I suppose I can understand that. So, say we " +
                                "were the Black Arm Gang, what would you want with us?",
                        )
                        wantWithUs()
                    }
                    2 -> {
                        chatPlayer(neutral, "It was Charlie, the tramp outside.")
                        chatNpc(
                            neutral,
                            "Is that guy still out there? He's getting to be a " +
                                "nuisance. Might be time for him to have a little " +
                                "accident...",
                        )
                        chatNpc(
                            neutral,
                            "Anyway, say we were the Black Arm Gang, what would " +
                                "you want with us?",
                        )
                        wantWithUs()
                    }
                    3 -> {
                        chatPlayer(neutral, "Everyone knows. It's no great secret.")
                        chatNpc(shocked, "What?! You must be joking!")
                        chatPlayer(
                            happy,
                            "Oh no, not at all... It's so obvious! Even the town " +
                                "guard have caught on...",
                        )
                        chatNpc(
                            neutral,
                            "I see... Well... say we were the Black Arm Gang, " +
                                "what would you want with us?",
                        )
                        wantWithUs()
                    }
                }
            }
            2 -> {
                chatPlayer(quiz, "What sort of business?")
                chatNpc(
                    neutral,
                    "A small, family business. We give financial advice to other " +
                        "companies. Now, if you'll excuse me.",
                )
            }
            3 -> {
                chatPlayer(neutral, "I'm looking for fame and riches.")
                chatNpc(
                    neutral,
                    "And you expect to find that up the back streets of Varrock? " +
                        "Keep dreaming.",
                )
            }
        }
    }

    private suspend fun Dialogue.wantWithUs() {
        when (
            menu(
                "I want to become a member of your gang." to 1,
                "I want some hints on becoming a thief." to 2,
                "I'm looking for the door out of here." to 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I want to become a member of your gang.")
                howUnusual()
            }
            2 -> {
                chatPlayer(neutral, "I want some hints on becoming a thief.")
                chatNpc(
                    quiz,
                    "Do you now? And what makes you think I'll provide anything " +
                        "like that?",
                )
                chatPlayer(
                    neutral,
                    "Well, how about I become a member of your gang in return?",
                )
                howUnusual()
            }
            3 -> {
                chatPlayer(neutral, "I'm looking for the door out of here.")
                chatNpc(quiz, "Try... the one you just came through?")
            }
        }
    }

    private suspend fun Dialogue.howUnusual() {
        chatNpc(
            neutral,
            "How unusual... Okay, let's say we are who you believe we are. Do you " +
                "think we'd be recruiting people who just waltz in here saying " +
                "'hello, I'd like to play'?",
        )
        chatPlayer(quiz, "I don't know. What would be the alternative?")
        chatNpc(
            neutral,
            "Watching local thugs and thieves in action, perhaps? How do I know " +
                "I can just trust someone who's wandered in here.",
        )
        when (
            menu(
                "Well, you can give me a try can't you?" to 1,
                "Well, people tell me I have an honest face." to 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Well, you can give me a try can't you?")
                chatNpc(neutral, "I'm not so sure...")
            }
            2 -> {
                chatPlayer(neutral, "Well, people tell me I have an honest face.")
                chatNpc(
                    quiz,
                    "Someone honest wanting to join a gang of thieves? Excuse me " +
                        "if I remain unconvinced...",
                )
            }
        }
        katrineTaskOffer()
    }

    private suspend fun Dialogue.katrineTaskOffer() {
        chatNpc(
            neutral,
            "Thinking about it... I may have a solution actually. You may have " +
                "heard of the Phoenix Gang. I hear they have a very nice weapons " +
                "stash not too far from here.",
        )
        chatNpc(
            neutral,
            "We're fresh out of crossbows, but apparently they have plenty. If " +
                "someone were to acquire a couple for us, it would be very much " +
                "appreciated.",
        )
        chatPlayer(quiz, "Sounds simple enough. Any particular reason you need them?")
        chatNpc(
            neutral,
            "Let's just say there's a local merchant who is refusing to pay some " +
                "very reasonable 'keep-your-life-pleasant' insurance rates.",
        )
        chatPlayer(quiz, "So you're planning to murder him?")
        chatNpc(
            neutral,
            "Him? No. However, I hear that the local law might soon be finding a " +
                "crossbow belonging to the Phoenix Gang in his home...",
        )
        chatNpc(
            neutral,
            "I also hear that a similar type of crossbow might soon be used in a " +
                "murder on the other side of the city...",
        )
        chatNpc(
            quiz,
            "Now, are you going to keep asking questions, or are you going to go " +
                "get those crossbows?",
        )
        when (
            menu(
                "Okay, no problem." to 1,
                "Sounds a little tricky. Got anything easier?" to 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Okay, no problem.")
                acceptKatrineTask()
            }
            2 -> {
                chatPlayer(neutral, "Sounds a little tricky. Got anything easier?")
                chatNpc(
                    neutral,
                    "If you're not up for a little bit of danger, I don't think " +
                        "you've got anything to offer us.",
                )
                when (
                    menu(
                        "Okay, I'll do it." to 1,
                        "It's a no from me then." to 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(neutral, "Okay, I'll do it.")
                        acceptKatrineTask()
                    }
                    2 -> {
                        chatPlayer(neutral, "It's a no from me then.")
                        chatNpc(angry, "Then get lost!")
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.acceptKatrineTask() {
        chatNpc(
            happy,
            "Great! Apparently the Phoenix Gang's weapons stash is somewhere to " +
                "the east. Ask that tramp outside. I'm sure he'll know more.",
        )
        player.blackArmGang = BlackArmKatrineTask
    }

    private suspend fun Dialogue.katrineTaskDialogue() {
        chatNpc(quiz, "Have you got those crossbows for me yet?")
        when {
            invTotal(access.inv, Crossbow) >= 2 -> {
                chatPlayer(happy, "Yes, I have.")
                mesbox("You give the crossbows to Katrine.")
                chatNpc(
                    happy,
                    "It seems you're more skilled than I originally gave you " +
                        "credit for. Well done. Consider yourself a member of the " +
                        "Black Arm Gang. I'd suggest you now go and familiarise " +
                        "yourself with the rest of the ganghouse.",
                )
                access.invDel(access.inv, Crossbow, 2)
                player.blackArmGang = BlackArmJoined
            }
            invTotal(access.inv, Crossbow) == 1 -> {
                chatPlayer(neutral, "I have one...")
                chatNpc(neutral, "Well, I need two. Come back when you have them.")
            }
            else -> {
                chatPlayer(
                    sad,
                    "No, I haven't found them yet. Do you know where I should " +
                        "be looking.",
                )
                chatNpc(
                    neutral,
                    "Apparently the Phoenix Gang's weapons stash is somewhere to " +
                        "the east. Ask that tramp outside. I'm sure he'll know more.",
                )
            }
        }
    }

    private suspend fun Dialogue.katrineMemberDialogue() {
        chatPlayer(neutral, "Hey.")
        chatNpc(neutral, "Hey.")
        when (
            menu(
                "So I'm a part of the gang now?" to 1,
                "Who are all those people in there?" to 2,
                "Teach me to be a top class criminal!" to 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "So I'm part of the gang now?")
                if (player.soaStage() >= StageComplete) {
                    chatNpc(
                        neutral,
                        "You are, but don't make me regret it. In case you didn't " +
                            "hear, someone recently stole something very important " +
                            "from us. We need to be more careful about who we let in.",
                    )
                    chatPlayer(happy, "Imagine doing something like that!")
                } else {
                    chatNpc(
                        neutral,
                        "You are. Feel free to familiarise yourself with the " +
                            "rest of the ganghouse.",
                    )
                    chatPlayer(happy, "Will do! Thanks!")
                }
            }
            2 -> {
                chatPlayer(quiz, "Who are all those people in there?")
                chatNpc(neutral, "They're just various rogues and thieves.")
                chatPlayer(quiz, "They don't say a lot...")
                chatNpc(neutral, "Nope.")
            }
            3 -> {
                chatPlayer(neutral, "Teach me to be a top class criminal.")
                chatNpc(neutral, "Teach yourself.")
            }
        }
    }

    // ================================================================= Baraek

    suspend fun ProtectedAccess.baraekDialogue(npc: Npc) = startDialogue(npc) {
        val options =
            buildList {
                add("Can you sell me some furs?" to 1)
                if (player.phoenixGang in PhoenixBookRead until PhoenixJoined) {
                    add("Can you tell me where I can find the Phoenix Gang?" to 2)
                }
                add("Hello. I am in search of a quest." to 3)
            }
        when (menu(options)) {
            1 -> {
                chatPlayer(quiz, "Can you sell me some furs?")
                chatNpc(neutral, "Maybe, I've got a few here.")
            }
            2 -> phoenixLocation()
            3 -> {
                chatPlayer(quiz, "Hello. I am in search of a quest.")
                chatNpc(neutral, "Sorry, no quests here.")
            }
        }
    }

    private suspend fun Dialogue.phoenixLocation() {
        chatPlayer(quiz, "Can you tell me where I can find the Phoenix Gang?")
        chatNpc(
            worried,
            "Shh, shh, shh! Not so loud! You don't want to get me in trouble, " +
                "do you?",
        )
        chatPlayer(quiz, "So I take it that you do know where they are.")
        chatNpc(
            shifty,
            "I may do... but I don't want to get into trouble for revealing " +
                "their hideout. Of course, if I was, say 20 coins richer, I may " +
                "happen to be more inclined to take that sort of risk...",
        )
        when (
            menu(
                "Okay. Have 20 gold coins." to 1,
                "No, I don't like things like bribery." to 2,
                "Yes. I'd like to be 20 coins richer too." to 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Okay. Have 20 gold coins.")
                if (invTotal(access.inv, Coins) >= BaraekBribe) {
                    access.invDel(access.inv, Coins, BaraekBribe)
                    mesbox("You give 20 coins to Baraek.")
                    chatNpc(
                        neutral,
                        "Okay, to get to the gang hideout, head to the southern " +
                            "gates of the city and then head down the alleyway " +
                            "past the Blue Moon Inn.",
                    )
                    chatNpc(
                        neutral,
                        "Down there you'll find a building owned by the VTAM " +
                            "corporation. That's where you'll find the Phoenix " +
                            "Gang. Just be careful. They ain't the types to be " +
                            "messed about.",
                    )
                    chatPlayer(happy, "Thanks!")
                    player.soaPhoenixSource = true
                    if (player.phoenixGang < PhoenixLocationKnown) {
                        player.phoenixGang = PhoenixLocationKnown
                    }
                } else {
                    chatPlayer(sad, "Uh... oops. I don't actually have 20 coins. Silly me.")
                }
            }
            2 -> {
                chatPlayer(neutral, "No, I don't do things like bribery.")
                chatNpc(
                    neutral,
                    "If you wanna deal with the Phoenix Gang, they're involved in " +
                        "much worse than a bit of bribery.",
                )
            }
            3 -> chatPlayer(neutral, "Yes, I'd like to be 20 coins richer too.")
        }
    }

    // ======================================================== Charlie the Tramp

    suspend fun ProtectedAccess.charlieDialogue(npc: Npc) = startDialogue(npc) {
        player.soaCharlieMet = true
        chatNpc(neutral, "Spare some change, guv?")
        val options =
            buildList {
                if (player.phoenixGang >= PhoenixBookRead && player.blackArmGang < BlackArmJoined) {
                    add("I'm looking for the Black Arm Gang." to 2)
                }
                if (player.blackArmGang == BlackArmKatrineTask) {
                    add("Any idea where the Phoenix Gang are based?" to 3)
                }
                if (size < 2) add("Who are you?" to 1)
                add("Okay, here you go." to 4)
                add("Sorry, I haven't got any." to 5)
                add("Go get a job!" to 6)
            }
        when (menu(options)) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(neutral, "Just an old man down on his luck, guv.")
            }
            2 -> blackArmLocation()
            3 -> phoenixInfo()
            4 -> {
                chatPlayer(neutral, "Okay, here you go.")
                if (invTotal(access.inv, Coins) >= 1) {
                    access.invDel(access.inv, Coins, 1)
                    chatNpc(happy, "Thanks, guv!")
                } else {
                    chatNpc(neutral, "You ain't got nothing either. Life, eh.")
                }
            }
            5 -> {
                chatPlayer(sad, "Sorry, I haven't got any.")
                chatNpc(sad, "Ah well, can't blame a guy for trying.")
            }
            6 -> {
                chatPlayer(neutral, "Go get a job!")
                chatNpc(angry, "Cheeky beggar...")
            }
        }
    }

    private suspend fun Dialogue.blackArmLocation() {
        chatPlayer(neutral, "I'm looking for the Black Arm Gang.")
        if (player.phoenixGang >= PhoenixJoined) {
            if (!player.soaCharlieHint) {
                chatNpc(
                    neutral,
                    "Funny you should mention that... Rumour has it that they " +
                        "have their headquarters right down this alleyway. Though " +
                        "if I were you, I'd be careful about messing with them...",
                )
            } else {
                chatNpc(
                    neutral,
                    "Like I told you, the rumour is that they have their " +
                        "headquarters right down this alleyway. Though if I were " +
                        "you, I'd be careful about messing with them...",
                )
            }
            player.soaCharlieHint = true
            chatPlayer(quiz, "Do you think they would let me join?")
            chatNpc(
                neutral,
                "No. You're a collaborator with the Phoenix Gang. There's no way " +
                    "they'll let you join.",
            )
            when (
                menu(
                    "How did you know I was in the Phoenix Gang?" to 1,
                    "Any ideas how I could get in there then?" to 2,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "How did you know I was in the Phoenix Gang?")
                    chatNpc(
                        neutral,
                        "I've been known to have dealings with the Phoenix Gang. " +
                            "I wouldn't be very good at my job if I didn't know " +
                            "their members.",
                    )
                    chatPlayer(neutral, "I see. So, any ideas how I could get in there?")
                    findAnAdventurer()
                }
                2 -> {
                    chatPlayer(neutral, "Any ideas how I could get in there then?")
                    findAnAdventurer()
                }
            }
            return
        }
        if (!player.soaCharlieHint) {
            chatNpc(
                neutral,
                "Funny you should mention that... Rumour has it that they have " +
                    "their headquarters right down this alleyway. Though if I were " +
                    "you, I'd be careful about messing with them...",
            )
        } else {
            chatNpc(
                neutral,
                "Like I told you, the rumour is that they have their headquarters " +
                    "right down this alleyway. Though if I were you, I'd be careful " +
                    "about messing with them...",
            )
        }
        player.soaCharlieHint = true
        chatPlayer(quiz, "Do you think they would let me join?")
        chatNpc(
            neutral,
            "You never know. You'll find a lady down there called Katrine. Speak " +
                "to her. But whatever you do, don't upset her.",
        )
        if (player.blackArmGang < BlackArmCharlieHint) {
            player.blackArmGang = BlackArmCharlieHint
        }
    }

    private suspend fun Dialogue.findAnAdventurer() {
        chatNpc(
            neutral,
            "Your best bet would probably be to find someone who isn't a member " +
                "of the Phoenix Gang and get them to infiltrate the ranks of the " +
                "Black Arm Gang for you.",
        )
        chatPlayer(quiz, "Someone? Like who?")
        chatNpc(
            neutral,
            "There's plenty of other adventurers about besides yourself. I'm sure " +
                "if you asked one of them nicely they would help you.",
        )
        chatPlayer(sad, "I see...")
    }

    private suspend fun Dialogue.phoenixInfo() {
        if (player.soaCharliePaid) {
            charliePhoenixLocation()
            return
        }
        chatPlayer(quiz, "Any idea where the Phoenix Gang are based?")
        chatNpc(
            neutral,
            "Sure, I know everything there is to know about this city. I'd be " +
                "happy to tell you for ten coins.",
        )
        when (
            menu(
                "Okay, that sounds fair." to 1,
                "Ten coins? That's too much! I'll give you five." to 2,
                "Never mind. I'll find it myself." to 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Okay, that sounds fair.")
                if (payCharlie(10)) {
                    charliePhoenixLocation()
                    charliePartnerHint()
                }
            }
            2 -> {
                chatPlayer(neutral, "Ten coins? That's too much! I'll give you five.")
                chatNpc(neutral, "I guess that'll have to do then.")
                if (payCharlie(5)) {
                    chatNpc(neutral, "Great, thanks.")
                    chatPlayer(quiz, "So where can I find the Phoenix Gang then?")
                    chatNpc(neutral, "If you pay me five more coins, I'll tell you.")
                    when (
                        menu(
                            "That's not fair, but I guess I don't have a choice." to 1,
                            "You thieving gutter-scum! You'd better watch your back!" to 2,
                        )
                    ) {
                        1 -> {
                            chatPlayer(
                                sad,
                                "That's not fair, but I guess I don't have a choice.",
                            )
                            if (payCharlie(5)) {
                                charliePhoenixLocation()
                                charliePartnerHint()
                            }
                        }
                        2 -> {
                            chatPlayer(
                                angry,
                                "You thieving gutter-scum! You'd better watch your back!",
                            )
                            chatNpc(neutral, "Yeah, sure, whatever. You don't frighten me, pal.")
                        }
                    }
                }
            }
            3 -> {
                chatPlayer(neutral, "Never mind. I'll find it myself.")
                chatNpc(neutral, "Well, you know where to find me if you get lost!")
            }
        }
    }

    private suspend fun Dialogue.payCharlie(amount: Int): Boolean {
        if (invTotal(access.inv, Coins) < amount) {
            chatPlayer(sad, "Uh... oops. I don't actually have that many coins.")
            return false
        }
        access.invDel(access.inv, Coins, amount)
        mesbox("You give $amount coins to Charlie.")
        return true
    }

    private suspend fun Dialogue.charliePhoenixLocation() {
        if (!player.soaCharliePaid) {
            chatPlayer(quiz, "Any idea where the Phoenix Gang are based?")
        }
        player.soaCharliePaid = true
        player.soaPhoenixSource = true
        chatNpc(
            neutral,
            "Perfect! Now listen closely. The Phoenix Gang can be found east of " +
                "here. Take the southern alley past the Blue Moon Inn and look for " +
                "a building owned by the VTAM corporation.",
        )
        chatNpc(
            neutral,
            "Their main hideout is in that building, with their weapon stash " +
                "being two buildings east of it. Good luck getting into either " +
                "without being a member of their gang though.",
        )
    }

    private suspend fun Dialogue.charliePartnerHint() {
        chatPlayer(quiz, "Without being a member of their gang? That might be problematic.")
        chatNpc(
            neutral,
            "Well, your best bet would probably be to find someone who isn't a " +
                "member of the Black Arm Gang and get them to infiltrate the " +
                "ranks of the Phoenix Gang for you.",
        )
        chatPlayer(quiz, "Someone? Like who?")
        chatNpc(
            neutral,
            "There's plenty of other adventurers about besides yourself. I'm sure " +
                "if you asked one of them nicely they would help you.",
        )
        chatPlayer(sad, "I see...")
    }

    // ========================================================== Weaponsmaster

    suspend fun ProtectedAccess.weaponsmasterDialogue(npc: Npc) {
        if (player.phoenixGang < PhoenixJoined) {
            startDialogue(npc) {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    angry,
                    "Hey! Who are you? I'm gonna teach you not to stick your " +
                        "nose where it don't belong!",
                )
            }
            npc.queueCombatRetaliate(player)
            return
        }
        startDialogue(npc) {
            chatPlayer(neutral, "Hello.")
            chatNpc(happy, "Hello fellow Phoenix! What are you after?")
            when (
                menu(
                    "I'm after a weapon or two." to 1,
                    "I'm looking for treasure." to 2,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "I'm after a weapon or two.")
                    chatNpc(neutral, "No problem. Feel free to look around.")
                }
                2 -> {
                    chatPlayer(neutral, "I'm looking for treasure.")
                    chatNpc(
                        neutral,
                        "Aren't we all? We've not got any up here. Go mug someone " +
                            "somewhere if you want some treasure.",
                    )
                }
            }
        }
    }

    // ================================================================ Curator

    suspend fun ProtectedAccess.curatorDialogue(npc: Npc) = startDialogue(npc) {
        val hasShield = access.inv.contains(ShieldLeft) || access.inv.contains(ShieldRight)
        when {
            hasShield -> curatorShieldTurnIn()
            player.soaShieldGiven != 0 -> curatorRepeat()
            else -> chatNpc(neutral, "Welcome to the museum of Varrock.")
        }
    }

    private suspend fun Dialogue.curatorShieldTurnIn() {
        val left = access.inv.contains(ShieldLeft)
        chatPlayer(neutral, "Hello there. I'm here about the Shield of Arrav.")
        chatNpc(
            neutral,
            "The Shield of Arrav? The Museum has been searching for that for " +
                "years! The late King Roald II offered a generous reward for its " +
                "return!",
        )
        chatPlayer(neutral, "Well, I'm here to claim it.")
        chatNpc(quiz, "You've found the shield? Let's have a look!")
        mesbox("You show the shield half to the curator.")
        chatNpc(shocked, "This is incredible! But where's the other half?")
        val (found, other) = if (left) "Phoenix Gang" to "Black Arm Gang" else "Black Arm Gang" to "Phoenix Gang"
        chatPlayer(
            neutral,
            "I obtained this half from the $found. The $other might have the " +
                "other half.",
        )
        chatNpc(
            neutral,
            "That does sound plausible. We will have to hope that another " +
                "adventurer can recover that half.",
        )
        chatPlayer(quiz, "So will I be rewarded for recovering half of the shield?")
        chatNpc(
            neutral,
            "I'm afraid the reward is for the recovery of the full shield. " +
                "However, what I'll do is write you out two half-certificates. " +
                "If another adventurer returns the other shield half, I'll do " +
                "the same for them.",
        )
        chatNpc(
            neutral,
            "The two of you can then swap one of the half-certificates to make " +
                "a full one each. Take that to the King and he will grant you " +
                "your reward.",
        )
        val half = if (left) CertLeft else CertRight
        access.invDel(access.inv, if (left) ShieldLeft else ShieldRight, 1)
        access.invAdd(access.inv, half, 2)
        player.soaShieldGiven = if (left) ShieldGivenPhoenix else ShieldGivenBlackArm
        mesbox("The curator gives you two half-certificates.")
    }

    private suspend fun Dialogue.curatorRepeat() {
        chatNpc(neutral, "Welcome to the museum of Varrock.")
        chatPlayer(neutral, "Hello there. I'm here about the Shield of Arrav.")
        chatNpc(
            neutral,
            "I gave you two half-certificates for the half you returned. If " +
                "another adventurer returns the other shield half, I'll do the " +
                "same for them.",
        )
        chatNpc(
            neutral,
            "The two of you can then swap one of the half-certificates to make " +
                "a full one each. Take that to the King and he will grant you " +
                "your reward.",
        )
        chatPlayer(quiz, "Would it be okay to get more half-certificates?")
        chatNpc(
            neutral,
            "I suppose so. It's not like you can do anything with them without " +
                "the other half.",
        )
        val half = if (player.soaShieldGiven == ShieldGivenPhoenix) CertLeft else CertRight
        access.invAdd(access.inv, half, 2)
        mesbox("The curator gives you two half-certificates.")
    }

    // ============================================================= King Roald

    suspend fun ProtectedAccess.roaldDialogue(npc: Npc) = startDialogue(npc) {
        chatPlayer(neutral, "Greetings, your majesty.")
        chatNpc(neutral, "Yes, citizen. Do you need something?")
        when (
            menu(
                "I'm here about the Shield of Arrav." to 1,
                "Not really." to 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'm here about the Shield of Arrav.")
                roaldShieldOfArrav()
            }
            2 -> chatPlayer(neutral, "Not really.")
        }
    }

    suspend fun Dialogue.roaldCertificate() {
        if (access.inv.contains(Certificate)) {
            roaldClaimReward()
        } else {
            mesbox("You show the certificate half to the king.")
            chatNpc(
                neutral,
                "I'm afraid that's only half the reward certificate. You'll " +
                    "have to get the other half and join them together if you " +
                    "want to claim the reward.",
            )
        }
    }

    private suspend fun Dialogue.roaldShieldOfArrav() {
        when {
            access.inv.contains(Certificate) -> roaldClaimReward()
            access.inv.contains(CertLeft) || access.inv.contains(CertRight) -> {
                mesbox("You show the certificate half to the king.")
                chatNpc(
                    neutral,
                    "I'm afraid that's only half the reward certificate. You'll " +
                        "have to get the other half and join them together if " +
                        "you want to claim the reward.",
                )
            }
            access.inv.contains(ShieldLeft) || access.inv.contains(ShieldRight) -> {
                chatNpc(
                    neutral,
                    "The Shield of Arrav? Yes, I do recall my father put a " +
                        "reward out for that.",
                )
                chatPlayer(happy, "Well, I've managed to find half the shield!")
                chatNpc(
                    neutral,
                    "You have? Well, before we go any further, you'll need to " +
                        "get the authenticity of the shield verified by the " +
                        "curator at the museum.",
                )
                chatPlayer(neutral, "Okay. I'll head over there.")
            }
            else ->
                chatNpc(
                    neutral,
                    "The Shield of Arrav? Yes, I do recall my father put a " +
                        "reward out for that.",
                )
        }
    }

    private suspend fun Dialogue.roaldClaimReward() {
        mesbox("You show the certificate to the king.")
        chatNpc(
            shocked,
            "My goodness! This is for the reward offered by my father many " +
                "years ago! I never thought I would live to see the day when " +
                "someone came forward to claim it!",
        )
        chatPlayer(
            neutral,
            "It was quite the undertaking. I wouldn't have been able to do it " +
                "without help from a fellow adventurer.",
        )
        chatNpc(
            happy,
            "Well, in that case, it's only right that you both be rewarded. " +
                "I will grant half of the reward to you and half to your " +
                "companion. That means $CoinReward coins for you. Enjoy it! " +
                "You've very much earned it!",
        )
        access.invDel(access.inv, Certificate, 1)
        if (player.phoenixGang >= PhoenixJoined) {
            player.phoenixGang = PhoenixComplete
        } else {
            player.blackArmGang = BlackArmComplete
        }
        soaQuest.completeQuest(access)
    }

    private const val BaraekBribe = 20
}
