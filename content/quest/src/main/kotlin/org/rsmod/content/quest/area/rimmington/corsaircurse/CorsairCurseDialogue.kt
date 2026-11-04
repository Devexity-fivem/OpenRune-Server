package org.rsmod.content.quest.area.rimmington.corsaircurse

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The Corsair Curse dialogue, transcribed from the OSRS Wiki transcript. Stage model on
 * `varbit.corscurs_progress` (bits 0-5 of `varp.corscurs`): 10 accepted, 15 arrived,
 * 20 met Ithoi, 45 theories ruled out, 55 Ithoi confessed, 57 exposed/kill phase,
 * 59 burning driftwood (transient), 60/61 complete.
 */
object CorsairCurseDialogue {

    internal val quest
        get() = checkNotNull(Quest.get(CorsairCurse.KEY)) { "quest not registered" }

    fun Player.ccStage(): Int = quest.getQuestStage(this)

    internal var Player.evidGnocci by boolVarBit("varbit.cc_evid_gnocci")
    internal var Player.evidArsen by boolVarBit("varbit.cc_evid_arsen")
    internal var Player.evidColin by boolVarBit("varbit.cc_evid_colin")
    internal var Player.tockTold by boolVarBit("varbit.cc_tock_told")
    internal var Player.proveReady by boolVarBit("varbit.cc_prove_ready")
    internal var Player.killOrdered by boolVarBit("varbit.cc_kill_ordered")
    internal var Player.ithoiDead by boolVarBit("varbit.cc_ithoi_dead")

    internal var Player.thiefBits by intVarBit("varbit.corscurs_thief")
    internal var Player.cookBits by intVarBit("varbit.corscurs_cook")
    internal var Player.colinBits by intVarBit("varbit.corscurs_cabinboy")

    internal fun Player.setProgress(value: Int) {
        VarPlayerIntMapSetter.set(this, "varbit.corscurs_progress", value)
    }

    internal fun Player.allTheoriesDisproven(): Boolean =
        thiefBits == ThiefReported && colinBits == ColinReported && cookBits == CookReported

    internal fun Player.allEvidenceHeard(): Boolean = evidGnocci && evidArsen && evidColin

    // ------------------------------------------------------------------ Titles

    private const val ThiefHeard = 1
    private const val ThiefGotRelic = 2
    private const val ThiefTessDone = 3
    private const val ThiefReported = 4

    private const val ColinHeard = 1
    private const val ColinSawOgre = 2
    private const val ColinReported = 3

    private const val CookHeard = 1
    private const val CookDug = 2
    private const val CookReported = 3

    private const val OgreRelic = "obj.corscurs_relic"

    private enum class TockTopic {
        CrewSpoken,
        Theories,
        GetRelic,
        RuledOut,
        BeganAfterDinner,
        Confessed,
        SawRunning,
        KilledIthoi,
        TakeMeBack,
        Later,
    }

    // ============================================================ Captain Tock

    suspend fun ProtectedAccess.tockDialogue(npc: Npc) = startDialogue(npc) { tock(npc) }

    private suspend fun Dialogue.tock(npc: Npc) {
        when {
            player.ccStage() <= 0 -> tockIntro()
            player.ccStage() < CorsairCurse.StageAtCove -> tockAtRimmingtonDock()
            else -> tockHub()
        }
    }

    private suspend fun Dialogue.tockIntro() {
        chatNpc(
            neutral,
            "Ahoy, young ${ladLass()}. Might ye be able to help me, " +
                "perhaps? I be in a spot of bother.",
        )
        while (true) {
            when (
                choice3(
                    "Who are you?",
                    0,
                    "What kind of help do you need?",
                    1,
                    "No, I'm not available.",
                    2,
                    title = "What would you like to say?",
                )
            ) {
                0 -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        neutral,
                        "I be the Corsair Captain! Captain of the Corsairs of Corsair " +
                            "Cove! My gallant band of corsairs are famed in song and " +
                            "story among all seafarers.",
                    )
                    when (
                        choice2(
                            "I've never heard of you.",
                            true,
                            "That's very interesting; do tell me more.",
                            false,
                            title = "What would you like to say?",
                        )
                    ) {
                        true -> {
                            chatPlayer(neutral, "I've never heard of you.")
                            chatNpc(
                                neutral,
                                "Arrr... well, ye can boast of yer ignorance if ye " +
                                    "likes, but it don't make ye sound smarter.",
                            )
                            chatPlayer(neutral, "...")
                            chatNpc(
                                neutral,
                                "Well, as I were saying, young ${ladLass()}, I " +
                                    "be in a spot of bother, and I thought ye might be " +
                                    "able to help.",
                            )
                        }
                        else -> {
                            chatPlayer(neutral, "That's very interesting; do tell me more.")
                            chatNpc(
                                neutral,
                                "Well, as I were saying, young ${ladLass()}, I " +
                                    "be in a spot of bother, and I thought ye might be " +
                                    "able to help.",
                            )
                        }
                    }
                }
                1 -> {
                    tockIntroHelp()
                    return
                }
                else -> {
                    chatPlayer(neutral, "No, I'm not available.")
                    chatNpc(neutral, "Arrr. Some other time, perhaps...")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.tockIntroHelp() {
        chatPlayer(quiz, "What kind of help do you need?")
        chatNpc(
            neutral,
            "It be about my crew. They be a lazy bunch at times, but they be stout " +
                "in the heart and strong in the arm, and they don't deserve what's " +
                "happened to them...",
        )
        chatNpc(
            worried,
            "It be a curse! A curse has been laid upon my crew! That's the only " +
                "thing it can be.",
        )
        chatNpc(
            neutral,
            "So I need a young ${ladLass()} such as yerself to find a way to " +
                "lift the curse. Will ye help me?",
        )
        when (
            choice2(
                "Sure, I'll try to help with your curse.",
                true,
                "No, I'm not available.",
                false,
                title = "Will you help Captain Tock?",
            )
        ) {
            true -> {
                chatPlayer(happy, "Sure, I'll try to help with your curse.")
                chatNpc(
                    happy,
                    "Arrr! Thanks, young ${ladLass()}. And a worthy " +
                        "${ladLass()} ye must surely be, brave in the heart " +
                        "and stiff in the spine.",
                )
                chatNpc(
                    neutral,
                    "Now, I've a ship moored west of Rimmington. Come and meet me on " +
                        "board, and we'll set off to Corsair Cove.",
                )
                chatPlayer(
                    neutral,
                    "West of Rimmington, you say? Okay, I'll meet you on board your " +
                        "ship there.",
                )
                mesbox("Captain Tock swaggers off towards Rimmington.")
                quest.setQuestStage(access, CorsairCurse.StageAccepted)
            }
            else -> {
                chatPlayer(neutral, "No, I'm not available.")
                chatNpc(neutral, "Arrr. Some other time, perhaps...")
            }
        }
    }

    private suspend fun Dialogue.tockAtRimmingtonDock() {
        chatNpc(
            neutral,
            "Arrr, ye've found my ship - good ${ladLass()}. I used to command " +
                "a much larger vessel, sailing the seas with my crew of Corsairs, but " +
                "now we be settled in Corsair Cove.",
        )
        chatNpc(
            neutral,
            "My Corsairs be a good crew, but this curse has laid them low, all except " +
                "me. Sickly, they be. Too weak to stand. Ye'll see fer yerself soon " +
                "enough.",
        )
        chatNpc(
            neutral,
            "There be four of 'em afflicted by the curse - Gnocci, Ithoi, Arsen and " +
                "young Colin. All taken to their beds, a-mewlin' and a-pukin' like so " +
                "many babbies.",
        )
        chatNpc(
            neutral,
            "I've another crewman, Francois, but he be away on a mission. I hope he " +
                "be spared!",
        )
        chatNpc(
            neutral,
            "Now be ye ready to voyage to Corsair Cove to help 'em? I can sail ye " +
                "back here any time ye likes.",
        )
        when (
            choice2(
                "Okay, I'm ready go to Corsair Cove.",
                true,
                "Not just now, thanks.",
                false,
                title = "Travel to Corsair Cove?",
            )
        ) {
            true -> {
                chatPlayer(neutral, "Okay, I'm ready go to Corsair Cove.")
                mesbox("The ship sails to Corsair Cove...")
                sailToCove()
            }
            else -> {
                chatPlayer(neutral, "Not just now, thanks.")
                chatNpc(
                    neutral,
                    "That's fair enough, young ${ladLass()}, but perhaps ye'll " +
                        "return to help me soon...",
                )
            }
        }
    }

    private suspend fun Dialogue.sailToCove() {
        quest.setQuestStage(access, CorsairCurse.StageAtCove)
        access.teleport(CorsairCurse.CoveArrival)
    }

    private suspend fun Dialogue.sailToRimmington() {
        access.teleport(CorsairCurse.RimmingtonArrival)
    }

    /** Tock's option hub on board the ship / at the cove dock, 15+. */
    private suspend fun Dialogue.tockHub() {
        val stage = player.ccStage()
        chatNpc(
            neutral,
            if (stage >= CorsairCurse.StageExposed) {
                "So what's happening now?"
            } else {
                "Have ye any idea about how to lift the curse?"
            },
        )
        val options = mutableListOf<TockTopic>()
        when {
            stage >= CorsairCurse.StageKillOrder && player.ithoiDead ->
                options += TockTopic.KilledIthoi
            stage >= CorsairCurse.StageExposed && player.tockTold ->
                options += TockTopic.SawRunning
            stage >= CorsairCurse.StageConfessed -> options += TockTopic.Confessed
            stage >= CorsairCurse.StageRuledOut -> options += TockTopic.BeganAfterDinner
            player.allTheoriesDisproven() -> options += TockTopic.RuledOut
            stage in CorsairCurse.StageMetIthoi until CorsairCurse.StageRuledOut -> {
                options += TockTopic.Theories
                if (player.thiefBits == ThiefHeard) options += TockTopic.GetRelic
            }
            stage in CorsairCurse.StageAtCove until CorsairCurse.StageMetIthoi ->
                options += TockTopic.CrewSpoken
            else -> options += TockTopic.CrewSpoken
        }
        options += TockTopic.TakeMeBack
        options += TockTopic.Later

        val labels =
            options.map {
                when (it) {
                    TockTopic.CrewSpoken -> "I've spoken to some of your crew."
                    TockTopic.Theories -> "They all have different theories about the curse."
                    TockTopic.GetRelic -> "Arsen says he gave you a sacred ogre relic."
                    TockTopic.RuledOut -> "I've ruled out all the Corsairs' theories..."
                    TockTopic.BeganAfterDinner -> "You said it began after they'd all eaten."
                    TockTopic.Confessed -> "Ithoi admits he poisoned the crew!"
                    TockTopic.SawRunning -> "I've seen Ithoi running around. He's not sick at all."
                    TockTopic.KilledIthoi -> "I've killed Ithoi for poisoning your crew."
                    TockTopic.TakeMeBack -> "Please take me back to Rimmington."
                    TockTopic.Later -> "I'll talk to you later."
                }
            }
        val picked = pickOption(labels, "What would you like to say?") ?: return
        when (options[picked]) {
            TockTopic.CrewSpoken -> {
                chatPlayer(neutral, "I've spoken to some of your crew.")
                chatNpc(
                    neutral,
                    "Arrr! Make sure ye speaks to 'em all. They'll be in their huts, " +
                        "over to the west. Four of them, there are - Gnocci, Ithoi, " +
                        "Arsen and young Colin.",
                )
            }
            TockTopic.Theories -> {
                chatPlayer(neutral, "They all have different theories about the curse.")
                chatNpc(
                    neutral,
                    "Arrr, that be likely enough. Ithoi tells me there be many things " +
                        "in this world that can bring a curse upon an unwary seafarer.",
                )
                chatNpc(
                    neutral,
                    "Ye can maybe solve the problem fer each victim, an' see if it " +
                        "lifts the curse.",
                )
            }
            TockTopic.GetRelic -> tockGiveRelic()
            TockTopic.RuledOut -> tockRuledOut()
            TockTopic.BeganAfterDinner -> tockDinnerStory()
            TockTopic.Confessed -> {
                chatPlayer(neutral, "Ithoi admits he poisoned the crew! He's faked this whole curse!")
                chatNpc(
                    neutral,
                    "That can't be! Ithoi's sick, an' he may be babbling madness from " +
                        "his fevered brain. Ye'll have to prove he's not really sick.",
                )
                player.tockTold = true
            }
            TockTopic.SawRunning -> tockKillOrder()
            TockTopic.KilledIthoi -> tockFinale()
            TockTopic.TakeMeBack -> {
                chatPlayer(neutral, "Please take me back to Rimmington.")
                chatNpc(neutral, "Aye, hold tight, young ${ladLass()}...")
                sailToRimmington()
            }
            TockTopic.Later -> chatPlayer(neutral, "I'll talk to you later.")
        }
    }

    private suspend fun Dialogue.tockGiveRelic() {
        chatPlayer(neutral, "Arsen says he gave you a sacred ogre relic.")
        chatNpc(
            neutral,
            "Arrr, that he did. I'd sent him to explore the caves west of the cove, " +
                "an' he brings me back a bone lump, saying he stole it from the ogre " +
                "chief.",
        )
        chatPlayer(
            neutral,
            "He thinks the ogres have cursed you all because he stole one of their " +
                "sacred relics, and he wants me to take it back to them now.",
        )
        chatNpc(
            neutral,
            "Arrr, I've no use fer the silly thing. If it'll get the curse lifted, " +
                "ye can take it right to that ogre chief an' give it back.",
        )
        if (access.invAdd(access.inv, OgreRelic).success) {
            mesbox("Captain Tock gives you the loot that Arsen stole from the ogre chief.")
            player.thiefBits = ThiefGotRelic
        }
    }

    private suspend fun Dialogue.tockRuledOut() {
        chatPlayer(neutral, "I've ruled out all the Corsairs' theories...")
        chatPlayer(
            neutral,
            "Cabin-boy Colin thought he'd upset a mermaid, but there wasn't a " +
                "mermaid, just a wet ogre.",
        )
        chatPlayer(
            neutral,
            "Arsen the Thief thought he'd upset the ogre shamans by stealing a " +
                "sacred ogre relic, but it was just a tooth-pick, and the ogres " +
                "didn't care.",
        )
        chatPlayer(
            neutral,
            "Ithoi the Navigator said there were lots of things that could bring a " +
                "curse on a crew, and this could be any of them.",
        )
        chatPlayer(
            neutral,
            "And Gnocci the Cook thought he'd awoken a demon in a possessed doll, " +
                "but it wasn't possessed at all. It was clockwork.",
        )
        chatNpc(
            neutral,
            "Arrr, any sufficiently advanced technology be indistinguishable from " +
                "demonic possession, that it be.",
        )
        chatPlayer(quiz, "So what do I do now?")
        tockDinnerStory()
        quest.setQuestStage(access, CorsairCurse.StageRuledOut)
    }

    private suspend fun Dialogue.tockDinnerStory() {
        chatNpc(
            neutral,
            "Arrr, I don't rightly know. It began when I'd gone away for a parlay " +
                "with Ithoi; we came back ter find the crew all a-pukin' up their " +
                "dinner. Then Ithoi felt ill too, though he'd not eaten with 'em, so " +
                "it can't be the food.",
        )
        chatPlayer(
            neutral,
            "I see. You're saying it began after they'd all eaten, but Ithoi felt " +
                "ill even though he'd not had any of the food.",
        )
        chatNpc(neutral, "Arrr, that be right.")
        chatPlayer(neutral, "That meal must have been interesting...")
    }

    private suspend fun Dialogue.tockKillOrder() {
        if (!player.tockTold || player.ccStage() < CorsairCurse.StageExposed) {
            chatPlayer(neutral, "Can I give up and go home?")
            chatNpc(
                neutral,
                "If ye wants a ride back to Rimmington, I'll take ye there any " +
                    "time, but I do hope ye can help my poor crew.",
            )
            return
        }
        chatPlayer(neutral, "I've seen Ithoi running around. He's not sick at all.")
        chatNpc(
            angry,
            "Arrr! So he was never sick at all! An' here's me been feeling sorry " +
                "for him. A trickster he be indeed! Kill him fer me.",
        )
        player.setProgress(CorsairCurse.StageKillOrder)
        while (true) {
            when (
                choice3(
                    "Why don't you kill him yourself?",
                    0,
                    "I don't believe in capital punishment.",
                    1,
                    "I'll be back.",
                    2,
                    title = "What would you like to say?",
                )
            ) {
                0 -> {
                    chatPlayer(quiz, "Why don't you kill him yourself?")
                    chatNpc(
                        neutral,
                        "A captain shouldn't leave his ship in a time of crisis. " +
                            "Rule of the sea, that is. Even if the crisis ain't " +
                            "exactly on the ship.",
                    )
                    player.killOrdered = true
                    return
                }
                1 -> {
                    chatPlayer(neutral, "I don't believe in capital punishment.")
                    chatNpc(neutral, "I do.")
                }
                else -> {
                    chatPlayer(neutral, "I'll be back.")
                    player.killOrdered = true
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.tockFinale() {
        chatPlayer(neutral, "I've killed Ithoi for poisoning your crew.")
        chatNpc(
            neutral,
            "Thank ye fer dealing with that serpent. I suppose the rest of my crew " +
                "will feel better now ye've proven there be no curse on them.",
        )
        chatNpc(
            neutral,
            "Most of them deleterious effects probably be psychosomatic anyway, arrr!",
        )
        chatNpc(
            happy,
            "Anyways, ye've been a mighty help, young ${ladLass()}. Ye be " +
                "brave in the heart, strong in the arm and full in the bowels, an' I " +
                "be grateful to ye.",
        )
        chatNpc(
            neutral,
            "Tell ye what, I'll have a word with Yusuf, in the bank. Ye've earned " +
                "the right to use his services. An' thanks again, very much.",
        )
        quest.completeQuest(access)
        VarPlayerIntMapSetter.set(player, "varbit.corsair_cove_resource_entry", 1)
    }

    // ============================================================ Ithoi

    suspend fun ProtectedAccess.ithoiDialogue(npc: Npc) = startDialogue(npc) { ithoi(npc) }

    private suspend fun Dialogue.ithoi(npc: Npc) {
        val stage = player.ccStage()
        when {
            stage >= CorsairCurse.StageConfessed -> ithoiPostConfess()
            stage >= CorsairCurse.StageRuledOut -> ithoiAccusation()
            stage >= CorsairCurse.StageMetIthoi -> ithoiRecap()
            else -> ithoiIntro()
        }
    }

    private suspend fun Dialogue.ithoiIntro() {
        chatNpc(angry, "Arrr, we be cursed, we be CURSED! It be a curse upon us!")
        when (
            choice2(
                "I hear you've been cursed.",
                true,
                "I think I'll leave you to get on with it.",
                false,
                title = "What would you like to say?",
            )
        ) {
            true -> {
                chatPlayer(neutral, "I hear you've been cursed.")
                chatNpc(
                    neutral,
                    "Aye? D'ye think so? Really? Am I not a picture of health an' " +
                        "beauty?",
                )
                chatNpc(angry, "YES - WE'VE BEEN CURSED!")
                chatNpc(
                    neutral,
                    "I've sailed the seas all my life. I sailed the seas as a man, " +
                        "like I sailed 'em as a boy.",
                )
                chatNpc(
                    neutral,
                    "I even sailed the seas during that tricky phase in between, " +
                        "when ye'r not quite a man yet, but ye'r not really a boy " +
                        "anymore. But I don't want to talk about that bit.",
                )
                chatPlayer(neutral, "Um...")
                chatNpc(
                    neutral,
                    "The point I'm makin' is that I know the seas like I know the " +
                        "palm of my hand. I've been to strange lands an' seen things " +
                        "that'll make yer juices turn in yer veins.",
                )
                chatNpc(
                    neutral,
                    "I've seen dancing fish-women, monkey-kings, huge men in turbans " +
                        "from the far West selling treasure maps - you name it, an' " +
                        "I've seen it.",
                )
                chatNpc(
                    neutral,
                    "There be more things in the world than ye dreams of in yer " +
                        "fancy book-learnin'! An' most of 'em be hostile. Could be " +
                        "any number of things brought this curse upon us. Demons, " +
                        "evil spirits, anything!",
                )
                chatPlayer(
                    neutral,
                    "Okay, it doesn't sound like you know what's behind the curse. " +
                        "Thanks.",
                )
                chatNpc(angry, "Arrr!")
                if (player.ccStage() < CorsairCurse.StageMetIthoi &&
                    player.ccStage() >= CorsairCurse.StageAtCove
                ) {
                    quest.setQuestStage(access, CorsairCurse.StageMetIthoi)
                }
            }
            else -> {
                chatPlayer(neutral, "I think I'll leave you to get on with it.")
                chatNpc(neutral, "... arrr.")
            }
        }
    }

    private suspend fun Dialogue.ithoiRecap() {
        chatNpc(
            neutral,
            "Ye be back? Tell me, do those scurvy Corsairs take the curse seriously?",
        )
        chatPlayer(neutral, "I've been hearing about what might have caused it...")
        if (player.colinBits >= ColinHeard) {
            chatPlayer(
                neutral,
                "Colin thought he upset a mermaid, and she might have cursed you " +
                    "all in revenge.",
            )
            chatNpc(
                neutral,
                "Arrr! That'll teach him to meddle with such folk. Colin should " +
                    "stick to what he's good at.",
            )
        }
        if (player.cookBits >= CookHeard) {
            chatPlayer(
                neutral,
                "Gnocci fished a doll out of the water, and he thought it might " +
                    "have been possessed by an evil spirit.",
            )
            chatNpc(
                laugh,
                "Arrr chuckie chuckie chuckie! That'll teach him to play with " +
                    "dolls at his age. Dolls can be frightening.",
            )
        }
        if (player.thiefBits >= ThiefHeard) {
            chatPlayer(
                neutral,
                "Arsen stole what he thought was a sacred relic from the ogre " +
                    "cave, and he said their shamans might have cursed you in " +
                    "revenge.",
            )
            chatNpc(neutral, "Arrr! Ogre women be powerful, an' Arsen be a fool.")
        }
        chatPlayer(neutral, "I'll carry on trying to solve the curse.")
        chatNpc(neutral, "Arrr! Off ye goes...")
    }

    private suspend fun Dialogue.ithoiAccusation() {
        chatNpc(neutral, "Ye be back? Have ye been investigatin' the curse?")
        val labels = mutableListOf<String>()
        if (player.evidArsen || player.evidGnocci) {
            labels += "I hear you cooked the meal they ate before getting sick."
        }
        labels += "I hear it happened straight after dinner."
        labels += "I'm still looking into it."
        when (pickOption(labels, "What would you like to say?")) {
            null -> return
            labels.lastIndex -> {
                chatPlayer(neutral, "I'm still looking into it.")
            }
            labels.lastIndex - 1 -> {
                chatPlayer(neutral, "I hear it happened straight after dinner.")
                chatNpc(
                    neutral,
                    "Arrr! That be wrong! I were away for a parlay with Captain " +
                        "Tock, an' I didn't eat that dinner at all. The Captain can " +
                        "tell ye so himself.",
                )
                chatNpc(angry, "So it must be a curse. A CURSE!")
                chatPlayer(neutral, "Okay, thanks.")
            }
            else -> ithoiConfront()
        }
    }

    private suspend fun Dialogue.ithoiConfront() {
        chatPlayer(neutral, "I hear you cooked the meal they ate before getting sick.")
        chatNpc(
            neutral,
            "Arrr? And what if I did? Can't an old sailor help his crew without " +
                "interfering young land-lubbers making accusations?",
        )
        when (
            choice3(
                "Maybe because the Captain's thinking of firing you.",
                0,
                "Maybe you're just a nasty person.",
                1,
                "I don't know your reason.",
                2,
                title = "Why would Ithoi poison the crew?",
            )
        ) {
            0 -> {
                chatPlayer(
                    neutral,
                    "Maybe because the Captain's thinking of firing you - Arsen " +
                        "heard it from his brother. The Corsairs don't need a " +
                        "navigator anymore.",
                )
                chatNpc(
                    worried,
                    "Arrr! Too clever ye be... I heard the Captain might kick me off " +
                        "the crew, so I poisoned the dinner, an' acted sick too. Now " +
                        "they think they're cursed, so they'll want me around, wi' " +
                        "my knowledge o' such things!",
                )
                chatNpc(
                    shifty,
                    "But they all think I'm sick, so they'll never believe I'm " +
                        "behind it. Hahaha!",
                )
                chatPlayer(angry, "We'll see what they have to say about that!")
                quest.setQuestStage(access, CorsairCurse.StageConfessed)
            }
            1 -> {
                chatPlayer(neutral, "Maybe you're just a nasty person.")
                chatNpc(
                    angry,
                    "Bah! Ye can't even think of a reason why I'd want to poison " +
                        "the crew, so don't bother me with yer accusations.",
                )
            }
            else -> {
                chatPlayer(neutral, "I don't know your reason.")
                chatNpc(
                    angry,
                    "Bah! Ye can't even think of a reason why I'd want to poison " +
                        "the crew, so don't bother me with yer accusations.",
                )
            }
        }
    }

    private suspend fun Dialogue.ithoiPostConfess() {
        val stage = player.ccStage()
        chatNpc(neutral, "What d'ye want now?")
        val labels = mutableListOf<String>()
        if (stage >= CorsairCurse.StageExposed) {
            labels += "I saw you running around! You're not sick!"
        } else {
            labels += "I know you've faked the curse."
        }
        labels += "Um... nothing, really."
        when (pickOption(labels, "What would you like to say?")) {
            null -> return
            labels.lastIndex -> {
                chatPlayer(neutral, "Um... nothing, really.")
            }
            else -> {
                if (stage >= CorsairCurse.StageExposed) {
                    chatPlayer(angry, "I saw you running around! You're not sick!")
                    chatNpc(
                        shifty,
                        "So long as that old fool, Tock, believes it's a curse, " +
                            "I've nothing to fear from ye flapping yer mouth.",
                    )
                    chatPlayer(neutral, "We'll see what the Captain has to say about that.")
                } else {
                    chatPlayer(neutral, "I know you've faked the curse.")
                    chatNpc(
                        neutral,
                        "The Corsairs all think I'm sick too, even though I never " +
                            "ate the food. An' ye'll never make me prove that I'm " +
                            "healthy - I ain't gettin' up! So they'll keep " +
                            "believing in the curse.",
                    )
                    when (
                        choice2(
                            "I bet I can prove you're well enough to get up.",
                            true,
                            "I'll be back.",
                            false,
                            title = "What would you like to say?",
                        )
                    ) {
                        true -> {
                            chatPlayer(neutral, "I bet I can prove you're well enough to get up.")
                            chatNpc(
                                neutral,
                                "Ye'll never make me get up! I ain't afraid of cold, " +
                                    "nor of wet, nor of anythin' ye can do in here.",
                            )
                            player.proveReady = true
                        }
                        else -> chatPlayer(neutral, "I'll be back.")
                    }
                }
            }
        }
    }

    // ============================================================ Arsen

    suspend fun ProtectedAccess.arsenDialogue(npc: Npc) = startDialogue(npc) { arsen(npc) }

    private suspend fun Dialogue.arsen(npc: Npc) {
        val stage = player.ccStage()
        when {
            stage >= CorsairCurse.ProgressComplete -> arsenPostQuest()
            player.ithoiDead -> arsenPostKill()
            stage >= CorsairCurse.StageExposed -> arsenPostExpose()
            stage >= CorsairCurse.StageConfessed -> arsenPostConfession()
            stage >= CorsairCurse.StageRuledOut -> arsenEvidence()
            player.thiefBits == ThiefTessDone -> arsenReport()
            player.thiefBits == ThiefHeard -> arsenWaiting()
            stage >= CorsairCurse.StageMetIthoi -> arsenStory()
            stage >= CorsairCurse.StageAtCove -> arsenRedirect()
            else -> chatNpc(neutral, "Urgh!")
        }
    }

    private suspend fun Dialogue.arsenRedirect() {
        chatNpc(neutral, "Urgh!")
        chatPlayer(neutral, "I hear you've been cursed.")
        chatNpc(
            neutral,
            "I think so. That navigator, Ithoi, always warned us that this kind of " +
                "thing could happen, but I didn't think it could possibly be real. " +
                "But now this has happened, and I think it's my fault.",
        )
        chatNpc(
            neutral,
            "You should maybe see what Ithoi thinks. He's been cursed too, so I " +
                "guess he'll be in his hut, off to the south-west, near the shore.",
        )
    }

    private suspend fun Dialogue.arsenStory() {
        chatNpc(neutral, "Urgh!")
        chatPlayer(neutral, "I hear you've been cursed.")
        chatNpc(
            neutral,
            "I think so. That navigator, Ithoi, always warned us that this kind of " +
                "thing could happen.",
        )
        chatPlayer(
            neutral,
            "I've met him. He definitely thinks it's a curse, but wasn't able to " +
                "say exactly what caused it.",
        )
        chatNpc(worried, "I'm afraid it may be all my fault.")
        chatPlayer(quiz, "Oh?")
        chatNpc(
            neutral,
            "Captain Tock had me exploring some caves, west of the cove. I'm a " +
                "thief, see, like my brother Francois, and the Captain thought I " +
                "could sneak past the monsters without being noticed.",
        )
        chatNpc(
            neutral,
            "Well, I climbed down the vines, and found a big cave filled with " +
                "terrifying ogres!",
        )
        chatNpc(
            neutral,
            "I'd hoped I could use my amazing Thieving skills to avoid them seeing " +
                "me, but I was out of luck. By the time I'd finished clambering " +
                "down the ladder, they'd spotted me.",
        )
        chatPlayer(quiz, "So you were trying to explore that ogre cave, and the ogres saw you?")
        chatNpc(
            neutral,
            "Yes. I quickly grabbed some loot, so I wouldn't have to face the " +
                "Captain empty-handed, and fled up the ladder before the ogres " +
                "could kill me!",
        )
        chatNpc(
            worried,
            "But a couple of days later we all fell ill. I think what I stole must " +
                "have been some kind of sacred relic, and the shamans in the cave " +
                "have cursed us for stealing it.",
        )
        chatNpc(
            neutral,
            "Maybe you could get the relic back from the Captain, and return it to " +
                "the ogres? And tell them I'm very sorry. Then perhaps they'll " +
                "lift the curse.",
        )
        chatPlayer(neutral, "I see.")
        player.thiefBits = ThiefHeard
    }

    private suspend fun Dialogue.arsenWaiting() {
        chatNpc(neutral, "Have you got that relic back from Captain Tock? Maybe if you return it to the ogres, they'll lift the curse.")
        when {
            access.invTotal(access.inv, OgreRelic) > 0 -> {
                chatNpc(
                    neutral,
                    "Please take it back to the ogres for me, and tell them I'm sorry.",
                )
            }
            player.thiefBits == ThiefTessDone -> arsenReport()
            else -> {
                chatPlayer(neutral, "Nope, I've not done that.")
                chatNpc(sad, "Oh dear.")
            }
        }
    }

    private suspend fun Dialogue.arsenReport() {
        chatNpc(
            neutral,
            "Have you returned the sacred relic to the ogres? Will they forgive me " +
                "and lift the curse?",
        )
        chatPlayer(
            neutral,
            "Their chief, Tess, says that wasn't a sacred relic at all. It was her " +
                "tooth-pick.",
        )
        chatNpc(quiz, "Are you telling me the ogres cursed us all because I stole their chief's tooth-pick?")
        chatPlayer(
            neutral,
            "She didn't care - she's got lots of tooth-picks, and she just thought " +
                "you were some kind of weirdo when you stole one. They never cursed " +
                "you at all.",
        )
        chatNpc(
            neutral,
            "Oh. That's a relief, I guess. Um... thank you for your help. But I do " +
                "wonder where the curse really came from.",
        )
        chatPlayer(neutral, "I'm not done investigating yet.")
        player.thiefBits = ThiefReported
    }

    private suspend fun Dialogue.arsenEvidence() {
        if (player.thiefBits == ThiefTessDone) {
            arsenReport()
            return
        }
        chatNpc(neutral, "Do you know how we all got cursed?")
        val labels = mutableListOf<String>()
        if (!player.evidArsen) {
            labels += "I hear Ithoi cooked the meal you ate that night."
        }
        labels += "I'm still looking into it."
        when (pickOption(labels, "What would you like to say?")) {
            null -> return
            labels.lastIndex -> chatPlayer(neutral, "I'm still looking into it.")
            else -> {
                chatPlayer(neutral, "I hear Ithoi cooked the meal you ate that night.")
                chatNpc(
                    neutral,
                    "I suppose Ithoi's trying to be extra helpful at the moment. " +
                        "Don't tell anyone, but Ithoi might not be in our crew much " +
                        "longer.",
                )
                chatNpc(
                    neutral,
                    "My brother, Francois, says the Captain will make him " +
                        "second-in-command if he completes his mission. That's " +
                        "Ithoi's rank, but we don't need a navigator now we've " +
                        "settled here, and he can't do much else.",
                )
                when (
                    choice2(
                        "What is the mission Francois is doing?",
                        true,
                        "Okay, thanks.",
                        false,
                        title = "What would you like to say?",
                    )
                ) {
                    true -> {
                        chatPlayer(quiz, "What is the mission Francois is doing?")
                        chatNpc(shifty, "That's a secret.")
                        chatPlayer(neutral, "Okay, thanks.")
                    }
                    else -> chatPlayer(neutral, "Okay, thanks.")
                }
                player.evidArsen = true
                onEvidenceHeard()
            }
        }
    }

    private suspend fun Dialogue.arsenPostConfession() {
        chatNpc(neutral, "Do you know how we all got cursed?")
        chatPlayer(neutral, "Ithoi admits poisoning you all and faking the curse.")
        chatNpc(
            neutral,
            "He's had a lot to worry about, with losing his place on the crew, and " +
                "now he's sick from the curse too. He must have gone mad and " +
                "started babbling.",
        )
    }

    private suspend fun Dialogue.arsenPostExpose() {
        chatNpc(neutral, "What's happening now?")
        chatPlayer(neutral, "Ithoi's faked the curse. I've seen him running around, healthy.")
        chatNpc(angry, "Give him what he deserves!")
    }

    private suspend fun Dialogue.arsenPostKill() {
        chatNpc(neutral, "What's happening now?")
        chatPlayer(neutral, "Ithoi faked the curse, and I've killed him.")
        chatNpc(
            neutral,
            "I really didn't think he was like that. Still, if there isn't really " +
                "a curse, I suppose we'll just get better from whatever he did to " +
                "us. I don't feel so bad now.",
        )
    }

    private suspend fun Dialogue.arsenPostQuest() {
        chatNpc(happy, "I feel a lot better now. I think I might go back to thieving soon.")
    }

    // ============================================================ Colin

    suspend fun ProtectedAccess.colinDialogue(npc: Npc) = startDialogue(npc) { colin(npc) }

    private suspend fun Dialogue.colin(npc: Npc) {
        val stage = player.ccStage()
        when {
            stage >= CorsairCurse.ProgressComplete -> colinPostQuest()
            player.ithoiDead -> colinPostKill()
            stage >= CorsairCurse.StageExposed -> colinPostExpose()
            stage >= CorsairCurse.StageConfessed -> colinPostConfession()
            stage >= CorsairCurse.StageRuledOut -> colinEvidence()
            player.colinBits == ColinSawOgre -> colinReport()
            player.colinBits == ColinHeard -> colinWaiting()
            stage >= CorsairCurse.StageMetIthoi -> colinStory()
            stage >= CorsairCurse.StageAtCove -> colinRedirect()
            else -> chatNpc(worried, "I'm sorry! Tell her I'm sorry! Then maybe she'll make the pain stop...")
        }
    }

    private suspend fun Dialogue.colinRedirect() {
        chatNpc(worried, "I'm sorry! Tell her I'm sorry! Then maybe she'll make the pain stop...")
        chatPlayer(neutral, "I hear you've been cursed.")
        chatNpc(
            neutral,
            "Yes, that's what it must be. Ithoi, the navigator, always told us to " +
                "beware of curses. You should talk to him - he really knows about " +
                "these things.",
        )
        chatNpc(neutral, "Ithoi lives in the hut to the south-west, nearest to the sea.")
    }

    private suspend fun Dialogue.colinStory() {
        chatNpc(worried, "I'm sorry! Tell her I'm sorry! Then maybe she'll make the pain stop...")
        chatPlayer(neutral, "I hear you've been cursed.")
        chatNpc(
            neutral,
            "Yes, that's what it must be. Ithoi, the navigator, always told us to " +
                "beware of curses.",
        )
        chatPlayer(
            neutral,
            "I've met him. He definitely thinks it's a curse, but wasn't able to " +
                "say exactly what caused it.",
        )
        chatNpc(
            worried,
            "This is all my fault. But I didn't mean to upset her. I just thought " +
                "we could be friends. Now I must have upset her, and she's cursed " +
                "us all!",
        )
        chatNpc(
            neutral,
            "Ithoi always told us that bad things happen to sailors who get " +
                "involved with mermaids, but I didn't believe him. I should have " +
                "listened! But I'll tell you what happened...",
        )
        chatNpc(neutral, "I was using Ithoi's telescope...")
        mesbox("A flashback to a few nights ago...")
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, happy, "Such a beautiful night!")
        chatNpcSpecific(
            "Cabin Boy Colin",
            ColinTelescopeNpc,
            neutral,
            "What lands lie beyond the dim horizon, I wonder...",
        )
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, quiz, "Eh?")
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, neutral, "What was that? I'd better have another look.")
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, quiz, "Is that what I think it is?")
        chatNpcSpecific(
            "Cabin Boy Colin",
            ColinTelescopeNpc,
            neutral,
            "It's so dark out there, but I think... ... yes, I'm quite sure...",
        )
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, happy, "... yes, it's a mermaid! She's beautiful!")
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, happy, "Hello?")
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, happy, "Can you hear me?")
        chatNpcSpecific(
            "Cabin Boy Colin",
            ColinTelescopeNpc,
            happy,
            "You're the most lovely person I've ever seen!",
        )
        chatNpcSpecific(
            "Cabin Boy Colin",
            ColinTelescopeNpc,
            quiz,
            "I can see you moving your arms, but it's very dark. Are you waving to me?",
        )
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, happy, "*kiss*")
        chatNpcSpecific("Cabin Boy Colin", ColinTelescopeNpc, worried, "Oh, no, I think she's gone away. Did I put her off?")
        chatNpcSpecific(
            "Cabin Boy Colin",
            ColinTelescopeNpc,
            sad,
            "Maybe she doesn't like strangers shouting about her appearance and " +
                "blowing kisses at her. That might have been rude of me.",
        )
        chatNpcSpecific(
            "Cabin Boy Colin",
            ColinTelescopeNpc,
            worried,
            "I hope she's not angry. Ithoi says mermaids can be dangerous.",
        )
        mesbox("Cutscene ends.")
        chatNpc(
            worried,
            "Perhaps you can find the mermaid and tell her I'm very sorry I was " +
                "rude to her. Then maybe she'll lift the curse.",
        )
        player.colinBits = ColinHeard
    }

    private suspend fun Dialogue.colinWaiting() {
        chatNpc(neutral, "Have you found the mermaid yet? Will she forgive me?")
        when (
            choice2(
                "Where did you find this mermaid?",
                true,
                "Nope, I've not seen any mermaids round here.",
                false,
                title = "What would you like to say?",
            )
        ) {
            true -> {
                chatPlayer(quiz, "Where did you find this mermaid?")
                chatNpc(
                    neutral,
                    "I was using Ithoi's telescope to look out at the sea, imagining " +
                        "the strange lands and fantastic creatures Ithoi's always " +
                        "telling us he's seen, and I saw her.",
                )
            }
            else -> {
                chatPlayer(neutral, "Nope, I've not seen any mermaids round here.")
                chatNpc(sad, "Oh dear, oh dear. Maybe we'll be cursed forever!")
            }
        }
    }

    private suspend fun Dialogue.colinReport() {
        chatNpc(neutral, "Have you found the mermaid yet? Will she forgive me?")
        chatPlayer(
            neutral,
            "I've looked through that telescope myself. That wasn't a mermaid. " +
                "That was a wet ogre.",
        )
        chatPlayer(neutral, "He's definitely not a mermaid.")
        chatNpc(
            happy,
            "Oh. Oh, I see. Okay... I guess that means I didn't upset a mermaid " +
                "after all. So the curse isn't my fault! Thank you!",
        )
        chatPlayer(neutral, "How are you feeling?")
        chatNpc(
            neutral,
            "I still feel awful. But I'm glad you've proven it wasn't my fault " +
                "that we got cursed. I hope you can work out what brought it on us.",
        )
        chatPlayer(neutral, "I'll be on the case.")
        player.colinBits = ColinReported
    }

    private suspend fun Dialogue.colinEvidence() {
        chatNpc(neutral, "Have you found out how we all got cursed?")
        val labels = mutableListOf<String>()
        if (!player.evidColin) {
            labels += "I hear the Captain's thinking of firing Ithoi."
        }
        labels += "I hear it happened straight after dinner."
        labels += "I'm still looking into it."
        val picked = pickOption(labels, "What would you like to say?") ?: return
        when (labels[picked]) {
            "I'm still looking into it." -> chatPlayer(neutral, "I'm still looking into it.")
            "I hear it happened straight after dinner." -> {
                chatPlayer(neutral, "I hear it happened straight after dinner.")
                chatNpc(
                    neutral,
                    "That's right. Gnocci had cooked us some swordfish. It didn't " +
                        "taste as good as his usual cooking, but I didn't complain. " +
                        "The Captain doesn't like complainers.",
                )
                chatNpc(
                    neutral,
                    "Then we all started feeling sick and clammy. That was when the " +
                        "Captain and Ithoi came back from their parlay, and Ithoi " +
                        "looked sick too.",
                )
                chatPlayer(neutral, "Okay, thanks.")
            }
            else -> {
                chatPlayer(neutral, "I hear the Captain's thinking of firing Ithoi.")
                chatNpc(
                    neutral,
                    "Well, I suppose we don't need a navigator these days, and he's " +
                        "not very good at anything else. But I don't see why that's " +
                        "got anything to do with the curse.",
                )
                chatPlayer(neutral, "Okay, thanks.")
                player.evidColin = true
                onEvidenceHeard()
            }
        }
    }

    private suspend fun Dialogue.colinPostConfession() {
        chatNpc(neutral, "Have you found out how we all got cursed?")
        chatPlayer(neutral, "Ithoi admits poisoning you all and faking the curse.")
        chatNpc(
            worried,
            "That can't be right - he looks as sick as the rest of us. Maybe he's " +
                "delerious and doesn't know what he's saying.",
        )
    }

    private suspend fun Dialogue.colinPostExpose() {
        chatNpc(neutral, "So what's going on now?")
        chatPlayer(neutral, "Ithoi's faked the curse. I've seen him running around, healthy.")
        chatNpc(
            neutral,
            "It sounds like there isn't a curse after all! Maybe I'll recover soon " +
                "from whatever he did to me.",
        )
    }

    private suspend fun Dialogue.colinPostKill() {
        chatNpc(neutral, "So what's going on now?")
        chatPlayer(neutral, "Ithoi faked the curse, and I've killed him.")
        chatNpc(
            happy,
            "Who'd have thought Ithoi would do such a thing? I guess if there " +
                "isn't really a curse, I'll get better soon. In fact I feel better " +
                "already.",
        )
    }

    private suspend fun Dialogue.colinPostQuest() {
        chatNpc(happy, "I feel right as rain now, thanks to you.")
    }

    // ============================================================ Gnocci

    suspend fun ProtectedAccess.gnocciDialogue(npc: Npc) = startDialogue(npc) { gnocci(npc) }

    private suspend fun Dialogue.gnocci(npc: Npc) {
        val stage = player.ccStage()
        when {
            stage >= CorsairCurse.ProgressComplete -> gnocciPostQuest()
            player.ithoiDead -> gnocciPostKill()
            stage >= CorsairCurse.StageExposed -> gnocciPostExpose()
            stage >= CorsairCurse.StageConfessed -> gnocciPostConfession()
            stage >= CorsairCurse.StageRuledOut -> gnocciEvidence()
            player.cookBits == CookDug -> gnocciReport()
            player.cookBits == CookHeard -> gnocciWaiting()
            stage >= CorsairCurse.StageMetIthoi -> gnocciStory()
            stage >= CorsairCurse.StageAtCove -> gnocciRedirect()
            else -> chatNpc(worried, "My stomach!")
        }
    }

    private suspend fun Dialogue.gnocciRedirect() {
        chatNpc(worried, "My stomach!")
        chatPlayer(neutral, "I hear you've been cursed.")
        chatNpc(
            neutral,
            "Yes, I think She did this to us. I should have left Her alone. Ithoi " +
                "told us to beware of this kind of thing.",
        )
        chatNpc(neutral, "Ithoi's been cursed too. You can probably find him in his hut, south-west from here.")
    }

    private suspend fun Dialogue.gnocciStory() {
        chatNpc(worried, "My stomach!")
        chatPlayer(neutral, "I hear you've been cursed.")
        chatNpc(
            neutral,
            "Yes, I think She did this to us. I should have left Her alone. Ithoi " +
                "told us to beware of this kind of thing.",
        )
        chatPlayer(
            neutral,
            "I've met him. He definitely thinks it's a curse, but wasn't able to " +
                "say exactly what caused it.",
        )
        chatNpc(worried, "It's all my fault, I know it is. I've awoken Her, and now She's done this to us!")
        chatPlayer(quiz, "Who are you talking about?")
        chatNpc(neutral, "I'll explain... See, I'm the cook, and I have to find the food as well as cooking it...")
        chatNpc(neutral, "I was fishing...")
        mesbox("A flashback to a few days ago...")
        chatNpcSpecific("Gnocci the Cook", CookFishingNpc, neutral, "Let's see what I can find for the boys tonight.")
        chatNpcSpecific("Gnocci the Cook", CookFishingNpc, happy, "C'mon little lobsters, come to Gnocci!")
        chatNpcSpecific("Gnocci the Cook", CookFishingNpc, happy, "Obby-lobby obby-lobby oi oi oi!")
        chatPlayer(quiz, "'obby-lobby' - what?")
        chatNpcSpecific(
            "Gnocci the Cook",
            CookFishingNpc,
            neutral,
            "It's a traditional lobster-fishing chant handed down by my ancestors. " +
                "Don't interrupt.",
        )
        chatNpcSpecific(
            "Gnocci the Cook",
            CookFishingNpc,
            neutral,
            "C'mon little lobsters. We don't want to keep the Captain waiting! " +
                "He'll get angry with me again.",
        )
        chatNpcSpecific("Gnocci the Cook", CookFishingNpc, quiz, "Ooh, what's this? This isn't a lobster.")
        chatNpcSpecific(
            "Gnocci the Cook",
            CookFishingNpc,
            quiz,
            "Looks more like a child's toy. But - no, it can't be...",
        )
        chatNpcSpecific("Gnocci the Cook", CookFishingNpc, quiz, "Is it moving?")
        chatNpcSpecific("Gnocci the Cook", CookFishingNpc, shocked, "She's moving! SHE'S POSSESSED! HELP!")
        mesbox("He buried the doll beneath a nearby tree, and ran away.")
        chatNpc(
            worried,
            "I buried Her in the sand, by that tree, to the west. But I still feel " +
                "Her eyes staring into my soul. Please find Her, and kill the demon " +
                "inside!",
        )
        player.cookBits = CookHeard
    }

    private suspend fun Dialogue.gnocciWaiting() {
        chatNpc(worried, "Have you tried to destroy Her yet? Her eyes, those terrible eyes!")
        when (
            choice2(
                "Where did you say this happened?",
                true,
                "No, I've not.",
                false,
                title = "What would you like to say?",
            )
        ) {
            true -> {
                chatPlayer(quiz, "Where did you say this happened?")
                chatNpc(
                    neutral,
                    "On the beach, to the west of the cove. Near a big tree. I " +
                        "buried Her in the sand, but I can still feel her eyes " +
                        "staring into my soul. Please find Her, and kill the demon " +
                        "inside!",
                )
            }
            else -> {
                chatPlayer(neutral, "No, I've not.")
                chatNpc(sad, "Oh dear, I feel terrible. Please help us soon.")
            }
        }
    }

    private suspend fun Dialogue.gnocciReport() {
        chatNpc(worried, "Have you tried to destroy Her yet? Her eyes, those terrible eyes!")
        chatPlayer(
            neutral,
            "Well, sort of. That doll wasn't possessed at all. It was a clockwork " +
                "toy. They're supposed to move.",
        )
        chatNpc(
            neutral,
            "Clockwork? Oh, that would explain how it moved. Ahem. Well, I suppose " +
                "the curse didn't come from Her.",
        )
        chatNpc(neutral, "I hope you can find the real cause soon.")
        chatPlayer(neutral, "Still trying.")
        player.cookBits = CookReported
    }

    private suspend fun Dialogue.gnocciEvidence() {
        if (player.cookBits == CookDug) {
            gnocciReport()
            return
        }
        chatNpc(neutral, "Have you solved the curse yet?")
        val labels = mutableListOf<String>()
        if (!player.evidGnocci) {
            labels += "I hear it happened straight after dinner."
        }
        labels += "So you say Ithoi prepared that meal?"
        labels += "I'm still looking into it."
        val picked = pickOption(labels, "What would you like to say?") ?: return
        when (labels[picked]) {
            "I'm still looking into it." -> chatPlayer(neutral, "I'm still looking into it.")
            "So you say Ithoi prepared that meal?" -> {
                chatPlayer(neutral, "So you say Ithoi prepared that meal?")
                chatNpc(
                    neutral,
                    "Yes, he volunteered to do all the fishing and cooking for me, " +
                        "even though he knew he'd miss dinner for his parlay with " +
                        "Captain Tock. Ithoi's a good soul.",
                )
                chatPlayer(neutral, "Okay, thanks.")
            }
            else -> {
                chatPlayer(neutral, "I hear it happened straight after dinner.")
                chatNpc(
                    neutral,
                    "Hey, don't go blaming the cook just because the curse began " +
                        "after dinner. I didn't even cook that meal. I'd been too " +
                        "scared of the possessed doll...",
                )
                chatPlayer(quiz, "You mean the clockwork doll?")
                chatNpc(neutral, "It's an easy mistake to make!")
                chatNpc(
                    neutral,
                    "Anyway I didn't dare try to fish after that, and Ithoi " +
                        "volunteered to do all the fishing and cooking instead, " +
                        "before he went off for his parlay with Captain Tock.",
                )
                chatNpc(neutral, "Anyway, Ithoi got sick without even eating it, so it can't be the food.")
                player.evidGnocci = true
                onEvidenceHeard()
            }
        }
    }

    private suspend fun Dialogue.gnocciPostConfession() {
        chatNpc(neutral, "Have you solved the curse yet?")
        chatPlayer(neutral, "Ithoi admits poisoning you all and faking the curse.")
        chatNpc(
            worried,
            "If Ithoi's been afflicted by the curse as badly as I have, he might be " +
                "half out of his mind with pain. I'm sure he doesn't know what " +
                "he's saying.",
        )
    }

    private suspend fun Dialogue.gnocciPostExpose() {
        chatNpc(neutral, "Can you tell me what's going on now?")
        chatPlayer(neutral, "Ithoi's faked the curse. I've seen him running around, healthy.")
        chatNpc(angry, "Make sure he gets what's coming to him. No-one poisons my crew!")
    }

    private suspend fun Dialogue.gnocciPostKill() {
        chatNpc(neutral, "Can you tell me what's going on now?")
        chatPlayer(neutral, "Ithoi faked the curse, and I've killed him.")
        chatNpc(
            neutral,
            "You think you know a guy, and then he does a thing like this! Oh well, " +
                "I hope I recover soon from whatever he did to me.",
        )
    }

    private suspend fun Dialogue.gnocciPostQuest() {
        chatNpc(happy, "I'm cooking up a storm again, thanks to you.")
    }

    // ============================================================ Chief Tess

    suspend fun ProtectedAccess.tessDialogue(npc: Npc) = startDialogue(npc) { tess(npc) }

    private suspend fun Dialogue.tess(npc: Npc) {
        chatNpc(neutral, "What you want, skinny creature?")
        val labels = mutableListOf<String>()
        val hasRelic = access.invTotal(access.inv, OgreRelic) > 0
        if (hasRelic) {
            labels += "I've come to return what Arsen stole."
        } else {
            labels += "A man called Arsen told me he'd visited your cave."
        }
        labels += "What is this place?"
        labels += "I'll leave you alone, sorry."
        when (pickOption(labels, "What would you like to say?")) {
            null -> return
            labels.lastIndex -> {
                chatPlayer(neutral, "I'll leave you alone, sorry.")
                chatNpc(neutral, "Bye bye, skinny creature.")
            }
            labels.lastIndex - 1 -> {
                chatPlayer(quiz, "What is this place?")
                chatNpc(
                    neutral,
                    "Dis be cave of da ogres. Tess is da chief here. You not " +
                        "welcome, skinny creature.",
                )
            }
            else -> {
                if (hasRelic) {
                    tessReturnRelic()
                } else {
                    chatPlayer(neutral, "A man called Arsen told me he'd visited your cave.")
                    chatNpc(laugh, "Yah, skinny man climb down vine the other day. Little man, big idiot! Hahahaha...")
                    chatPlayer(neutral, "He says he's very sorry...")
                    chatNpc(laugh, "He very silly!")
                    chatPlayer(neutral, "Perhaps I should come back another time.")
                }
            }
        }
    }

    private suspend fun Dialogue.tessReturnRelic() {
        chatPlayer(neutral, "I've come to return what Arsen stole.")
        chatPlayer(neutral, "Arsen says he's very sorry for stealing from you. Here it is...")
        mesbox("You present Tess with Arsen's loot.")
        access.invDel(access.inv, OgreRelic, count = 1)
        chatNpc(
            laugh,
            "Yah, dat little man is big idiot. Climbed down vine, grabbed tooth-pick " +
                "off Tess and ran away like scared kebbit.",
        )
        chatPlayer(
            quiz,
            "You're saying this is a tooth-pick? Not a sacred relic of the ogres? " +
                "Did your shamans curse the Corsairs for stealing it?",
        )
        chatNpc(
            neutral,
            "Dis is tooth-pick. Tess got plenty more. If skinny idiot man want " +
                "Tess' old tooth-pick, maybe he sick in his head. But Tess not " +
                "having shamans curse anyone.",
        )
        chatPlayer(
            neutral,
            "Oh. Well, I guess Arsen will be pleased to hear he didn't bring the " +
                "curse upon the Corsairs.",
        )
        if (player.thiefBits in ThiefHeard..ThiefGotRelic) {
            player.thiefBits = ThiefTessDone
        }
    }

    // ============================================================ Ferry Colin (post-quest)

    suspend fun ProtectedAccess.colinFerryDialogue(npc: Npc) = startDialogue(npc) { colinFerry(npc) }

    private suspend fun Dialogue.colinFerry(npc: Npc) {
        chatNpc(happy, "Ahoy! I'm keeping the ship in order while the Captain's busy.")
        when (
            choice2(
                "Please take me back to Rimmington.",
                true,
                "I'll talk to you later.",
                false,
                title = "What would you like to say?",
            )
        ) {
            true -> {
                chatPlayer(neutral, "Please take me back to Rimmington.")
                sailToRimmington()
            }
            else -> chatPlayer(neutral, "I'll talk to you later.")
        }
    }

    // ============================================================ Yusuf the banker

    suspend fun ProtectedAccess.yusufDialogue(npc: Npc) = startDialogue(npc) { yusuf(npc) }

    private suspend fun Dialogue.yusuf(npc: Npc) {
        if (player.ccStage() >= CorsairCurse.ProgressComplete) {
            chatNpc(neutral, "Welcome to the Corsair Cove bank. How may I help you?")
        } else {
            chatNpc(neutral, "The Captain told me not to serve strangers. No offence.")
        }
    }

    // ============================================================ helpers

    private suspend fun Dialogue.onEvidenceHeard() {
        if (player.allEvidenceHeard() && player.ccStage() < CorsairCurse.StageEvidence) {
            quest.setQuestStage(access, CorsairCurse.StageEvidence)
        }
    }

    private suspend fun Dialogue.pickOption(labels: List<String>, title: String): Int? {
        return when (labels.size) {
            0 -> null
            1 -> {
                chatPlayer(neutral, labels[0])
                -1
            }
            2 -> choice2(labels[0], 0, labels[1], 1, title = title)
            3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
            4 ->
                choice4(
                    labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3,
                    title = title,
                )
            else ->
                choice5(
                    labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4,
                    title = title,
                )
        }
    }

    private fun Dialogue.ladLass(): String = if (access.isBodyTypeA()) "lad" else "lass"

    private const val ColinTelescopeNpc = "npc.corscurs_cabinboy_telescope"
    private const val CookFishingNpc = "npc.corscurs_cook_fishing"
}
