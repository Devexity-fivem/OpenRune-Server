package org.rsmod.content.areas.misc.tutorial

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TutorialIslandScript
@Inject
constructor(private val locRepo: LocRepository, private val eventBus: EventBus) : PluginScript() {
    private val Player.newAccount by boolVarBit("varbit.new_player_account")

    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.Login> { player.loginTutorial() }

        onOpNpc1("npc.newbie_basics_instructor") { startDialogue(it.npc) { gielinorGuide() } }
        onOpNpc1("npc.newbie_survival_instructor") { startDialogue(it.npc) { brynna() } }
        onOpNpc1("npc.newbie_cook_instructor") { startDialogue(it.npc) { lev() } }
        onOpNpc1("npc.newbie_quest_instructor") { startDialogue(it.npc) { questGuide() } }
        onOpNpc1("npc.newbie_mining_instructor") { startDialogue(it.npc) { dezzick() } }
        onOpNpc1("npc.newbie_combat_instructor") { startDialogue(it.npc) { vannaka() } }
        onOpNpc1("npc.newbie_account_instructor") { startDialogue(it.npc) { accountGuide() } }
        onOpNpc1("npc.brother_noob") { startDialogue(it.npc) { brotherBrace() } }
        onOpNpc1("npc.ironman_tutor_2") { startDialogue(it.npc) { paul() } }
        onOpNpc1("npc.newbie_magic_instructor") { startDialogue(it.npc) { terrova() } }
        onOpNpc1("npc.noobbanker") { openBank() }

        onOpNpc1("npc.0_48_48_newbiefishing") { fish() }
        onOpNpc2("npc.0_48_48_newbiefishing") { fish() }

        for (tree in TREES) {
            onOpLoc1(tree) { chop() }
        }
        onOpHeldU("obj.tinderbox", "obj.newbielogs") { lightFire() }
        onOpLocU("loc.newbiefire", "obj.newbieraw_shrimp") { cookShrimp() }
        onOpLocU("loc.newbierange", "obj.newbieraw_shrimp") { cookShrimp() }
        onOpLocU("loc.newbierange", "obj.bread_dough") { bakeBread() }
        onOpHeldU("obj.newbie_pot_flour", "obj.bucket_water") {
            makeDough("obj.bucket_water", "obj.bucket_empty")
        }
        onOpHeldU("obj.newbie_pot_flour", "obj.jug_water") {
            makeDough("obj.jug_water", "obj.jug_empty")
        }
        onOpLoc1("loc.newbiecopperrock") { mine("obj.copper_ore") }
        onOpLoc1("loc.newbietinrock") { mine("obj.tin_ore") }
        onOpLocU("loc.newbiefurnace", "obj.copper_ore") { smelt() }
        onOpLocU("loc.newbiefurnace", "obj.tin_ore") { smelt() }
        onOpLoc1("loc.newbiefurnace") { smelt() }
        onOpLoc1("loc.newbiebankbooth") { openBank() }

        onOpLoc1("loc.newbieladdertop1") { descend(it.loc) }
        onOpLoc1("loc.newbieladdertop2") { descend(it.loc) }
        onOpLoc1("loc.newbieladder1") { climbUp(it.loc) }
        onOpLoc1("loc.newbieladder2") { climbUp(it.loc) }

        for ((door, required) in TutorialIsland.DOOR_STAGES) {
            onOpLoc1(door) { tutorialDoor(it.loc, door, required) }
        }
    }

    private fun Player.loginTutorial() {
        val stage = tutorialStage
        when {
            newAccount && stage == TutorialIsland.FRESH -> {
                coords = TutorialIsland.START
                lastKnownNormalCoord = TutorialIsland.START
                mes("Welcome to Gielinor. Your journey begins on Tutorial Island.")
            }
            !newAccount && stage in 0 until TutorialIsland.DONE && !coords.onTutorialIsland() -> {
                tutorialStage = TutorialIsland.DONE
            }
        }
    }

    private suspend fun Dialogue.gielinorGuide() {

        if (player.tutorialStage < TutorialIsland.GUIDE_DONE) {
            chatNpc(
                happy,
                "Greetings! I see you are a new arrival to this land. My job is " +
                    "to welcome all new visitors. So welcome!",
            )
            chatNpc(
                happy,
                "You have already learned how to move around - clicking where you " +
                    "want to go on the ground moves you there.",
            )
            chatPlayer(quiz, "What should I do now?")
            chatNpc(happy, "First let me ask - have you played Old School RuneScape before?")
            val played =
                choice3(
                    "I'm brand new, show me everything!",
                    1,
                    "I've played before, just the basics.",
                    2,
                    "I'm a returning player, I know the ropes.",
                    3,
                )
            chatNpc(
                happy,
                when (played) {
                    1 ->
                        "Wonderful! The instructors on this island will teach you " +
                            "the skills you need. Follow the path to each one."
                    2 ->
                        "The instructors here will refresh your memory. Follow " +
                            "the path to the Survival Expert."
                    else ->
                        "A veteran returns! The instructors will get you up to " +
                            "speed quickly - or you can rush through if you like."
                },
            )
            chatNpc(
                happy,
                "Look at your settings by clicking the spanner icon - you can " +
                    "adjust volume, change display size and much more.",
            )
            chatNpc(
                happy,
                "When you are ready, go through the door and follow the path " +
                    "down the hill to the Survival Expert.",
            )
            player.tutorialStage = TutorialIsland.GUIDE_DONE
        } else {
            chatNpc(happy, "Head down the hill to the Survival Expert, ${player.displayName}.")
        }
    }

    private suspend fun Dialogue.brynna() {
        if (!lessonReady(TutorialIsland.GUIDE_DONE)) return
        when {
            player.tutorialStage < TutorialIsland.BRYNNA_NET -> {
                chatNpc(
                    happy,
                    "Hello there, newcomer. My name is Brynna. I will teach you " +
                        "how to survive in this land!",
                )
                chatNpc(
                    happy,
                    "First, you need to eat - and to eat, you need to catch some " +
                        "food. Take this fishing net.",
                )
                if (!supply(TutorialIsland.BRYNNA_NET, InvObj("obj.net"))) return
                objbox("obj.net", "Brynna gives you a small fishing net!")
                chatNpc(
                    happy,
                    "The fishing spot is just over there, at the water's edge. " +
                        "Click it and you will catch some shrimp.",
                )
            }
            player.tutorialStage == TutorialIsland.BRYNNA_NET -> {
                if (!supply(null, InvObj("obj.net"))) return
                chatNpc(happy, "Go and catch a shrimp at the fishing spot, then come back to me.")
            }
            player.tutorialStage == TutorialIsland.SHRIMP_CAUGHT -> {
                chatNpc(happy, "Well done! You caught a shrimp!")
                chatNpc(
                    happy,
                    "Now you need to cook it. For that you need a fire - take " +
                        "this axe and this tinderbox.",
                )
                if (!supply(TutorialIsland.AXE_TINDER,
                        InvObj("obj.bronze_axe"), InvObj("obj.tinderbox"))) return
                doubleobjbox(
                    "obj.bronze_axe",
                    "obj.tinderbox",
                    "Brynna gives you an axe and tinderbox!",
                )
                chatNpc(
                    happy,
                    "Chop down a tree with your axe, then use the tinderbox on " +
                        "the logs to light a fire.",
                )
            }
            player.tutorialStage < TutorialIsland.SHRIMP_COOKED -> {
                if (!supply(null, InvObj("obj.net"), InvObj("obj.bronze_axe"),
                        InvObj("obj.tinderbox"))) return
                chatNpc(happy, "Chop a tree, light a fire and cook your shrimp on it.")
            }
            player.tutorialStage == TutorialIsland.SHRIMP_COOKED -> {
                chatNpc(happy, "Excellent! You can now catch and cook your own food.")
                chatNpc(
                    happy,
                    "Whenever you have time, you can improve your skills the " +
                        "same way - the more you do something, the better you " +
                        "get at it.",
                )
                chatNpc(
                    happy,
                    "Now head through the gate to the kitchen. The Master Chef " +
                        "will teach you more about cooking.",
                )
                player.tutorialStage = TutorialIsland.LEV_FLOUR - 10
            }
            else -> chatNpc(happy, "Head to the kitchen and talk to the Master Chef.")
        }
    }

    private suspend fun Dialogue.lev() {
        if (!lessonReady(TutorialIsland.SHRIMP_COOKED)) return
        when {
            player.tutorialStage < TutorialIsland.LEV_FLOUR -> {
                chatNpc(happy, "Hello! Welcome to my kitchen. I am Lev, the head chef.")
                chatNpc(
                    happy,
                    "To make bread you need flour and water. Here is a pot of " +
                        "flour and a bucket of water.",
                )
                if (!supply(TutorialIsland.LEV_FLOUR,
                        InvObj("obj.newbie_pot_flour"), InvObj("obj.bucket_water"))) return
                doubleobjbox(
                    "obj.newbie_pot_flour",
                    "obj.bucket_water",
                    "Lev gives you a pot of flour and a bucket of water!",
                )
                chatNpc(
                    happy,
                    "Use one on the other to make bread dough, then cook the " +
                        "dough on my range.",
                )
            }
            player.tutorialStage == TutorialIsland.LEV_FLOUR -> {
                if (!supply(null, InvObj("obj.newbie_pot_flour"), InvObj("obj.bucket_water"))) return
                chatNpc(happy, "Mix the flour and water into dough, then bake it on the range.")
            }
            player.tutorialStage == TutorialIsland.DOUGH -> {
                if ("obj.bread_dough" in player.inv) {
                    chatNpc(happy, "Now cook the dough on the range to make bread.")
                } else {
                    if (!supply(null, InvObj("obj.newbie_pot_flour"),
                            InvObj("obj.bucket_water"))) return
                    chatNpc(happy, "Here are some more ingredients. Make some dough and bake it.")
                }
            }
            player.tutorialStage == TutorialIsland.BREAD -> {
                chatNpc(happy, "Smell that! A fresh loaf of bread. Delicious!")
                chatNpc(
                    happy,
                    "You can cook all sorts of food - some recipes give better " +
                        "healing than others.",
                )
                chatNpc(
                    happy,
                    "That's all I can teach you. Through the door to the north " +
                        "you'll find the Quest Guide.",
                )
                player.tutorialStage = TutorialIsland.QUEST_DONE - 10
            }
            else -> chatNpc(happy, "Head north to the Quest Guide's house.")
        }
    }

    private suspend fun Dialogue.questGuide() {
        if (!lessonReady(TutorialIsland.BREAD)) return
        if (player.tutorialStage < TutorialIsland.QUEST_DONE) {
            chatNpc(happy, "Ah, a new adventurer! Welcome to my house.")
            chatPlayer(quiz, "What do you do here?")
            chatNpc(
                happy,
                "I am the Quest Guide! Quests are missions you can do for the " +
                    "people of Gielinor - check your quest journal.",
            )
            chatNpc(
                happy,
                "Quests in <col=8b0000>red</col> haven't been started, those in " +
                    "<col=8b8000>yellow</col> are in progress, and " +
                    "<col=008000>green</col> ones are complete. Finishing them " +
                    "earns quest points and rewards.",
            )
            chatNpc(
                happy,
                "There's a ladder in this room leading down to the caves below. " +
                    "Head down and the Mining Instructor will show you the ropes.",
            )
            player.tutorialStage = TutorialIsland.QUEST_DONE
        } else {
            chatNpc(happy, "Down the ladder you go - the Mining Instructor awaits.")
        }
    }

    private suspend fun Dialogue.dezzick() {
        if (!lessonReady(TutorialIsland.QUEST_DONE)) return
        when {
            player.tutorialStage < TutorialIsland.DEZZICK_PICK -> {
                chatNpc(happy, "Hello there! I'm Dezzick, the mining instructor.")
                chatNpc(
                    happy,
                    "Mining is how you get ore - and ore makes metal. Take this " +
                        "bronze pickaxe.",
                )
                if (!supply(TutorialIsland.DEZZICK_PICK, InvObj("obj.bronze_pickaxe"))) return
                objbox("obj.bronze_pickaxe", "Dezzick gives you a bronze pickaxe!")
                chatNpc(
                    happy,
                    "Mine one copper ore and one tin ore from the rocks here - " +
                        "then we can smelt them into bronze.",
                )
            }
            player.tutorialStage == TutorialIsland.DEZZICK_PICK -> {
                if (!supply(null, InvObj("obj.bronze_pickaxe"))) return
                chatNpc(happy, "Mine a copper ore and a tin ore, then come see me.")
            }
            player.tutorialStage == TutorialIsland.ORES -> {
                chatNpc(
                    happy,
                    "You've got both ores! Now take them to the furnace over " +
                        "there and smelt them into a bronze bar.",
                )
            }
            player.tutorialStage == TutorialIsland.BAR -> {
                chatNpc(
                    happy,
                    "A bronze bar! Well done. Now you can smith it into " +
                        "something useful. Take this hammer.",
                )
                if (!supply(TutorialIsland.HAMMER, InvObj("obj.hammer"))) return
                objbox("obj.hammer", "Dezzick gives you a hammer!")
                chatNpc(
                    happy,
                    "Use the bar on an anvil while holding the hammer - smith " +
                        "yourself a bronze dagger.",
                )
            }
            player.tutorialStage == TutorialIsland.HAMMER -> {
                if (!supply(null, InvObj("obj.hammer"), InvObj("obj.bronze_pickaxe"))) return
                chatNpc(happy, "Smith a bronze dagger on the anvil - use your bar on it.")
            }
            else -> {
                chatNpc(
                    happy,
                    "A fine dagger! Now head east and talk to Vannaka, the " +
                        "combat instructor.",
                )
            }
        }
    }

    private suspend fun Dialogue.vannaka() {
        if (!lessonReady(TutorialIsland.DAGGER)) return
        when {
            player.tutorialStage == TutorialIsland.DAGGER -> {
                chatNpc(happy, "You're the new recruit. I'm Vannaka, the combat instructor.")
                chatNpc(
                    happy,
                    "Combat is dangerous, but someone's got to do it. Take this " +
                        "sword and shield.",
                )
                if (!supply(TutorialIsland.COMBAT_READY,
                        InvObj("obj.bronze_sword"), InvObj("obj.wooden_shield"))) return
                doubleobjbox(
                    "obj.bronze_sword",
                    "obj.wooden_shield",
                    "Vannaka gives you a sword and shield!",
                )
                chatNpc(
                    happy,
                    "Equip them - click the sword in your inventory to wield " +
                        "it. Then go and kill one of the giant rats in this cave.",
                )
                chatNpc(
                    happy,
                    "Click on a rat to attack it. Your combat style and stats " +
                        "are in the combat tab - the crossed swords.",
                )
            }
            player.tutorialStage == TutorialIsland.COMBAT_READY -> {
                if (!supply(null, InvObj("obj.bronze_sword"), InvObj("obj.wooden_shield"))) return
                chatNpc(happy, "Equip your sword and shield, open the gate and kill a giant rat.")
            }
            player.tutorialStage == TutorialIsland.MELEE_KILL -> {
                chatNpc(happy, "Nicely done! You handled that rat like a veteran.")
                chatNpc(
                    happy,
                    "Now for ranged combat - sometimes you want to strike from " +
                        "a distance. Take this bow and these arrows.",
                )
                if (!supply(TutorialIsland.BOW_ARROWS,
                        InvObj("obj.shortbow"), InvObj("obj.bronze_arrow", 50))) return
                doubleobjbox(
                    "obj.shortbow",
                    "obj.bronze_arrow",
                    "Vannaka gives you a shortbow and arrows!",
                )
                chatNpc(
                    happy,
                    "Wield the bow and arrows, then kill another rat from a " +
                        "distance.",
                )
            }
            player.tutorialStage == TutorialIsland.BOW_ARROWS -> {
                if (!supply(null, InvObj("obj.shortbow"))) return
                if ("obj.bronze_arrow" !in player.inv && "obj.bronze_arrow" !in player.worn) {
                    if (!supply(null, InvObj("obj.bronze_arrow", 50))) return
                }
                chatNpc(happy, "Wield the bow and arrows, then kill a rat with ranged combat.")
            }
            player.tutorialStage >= TutorialIsland.RANGED_KILL -> {
                chatNpc(happy, "A fine shot! You now know melee and ranged combat.")
                chatNpc(
                    happy,
                    "That's the last of my training. Head up the ladder to the " +
                        "surface - the bank and the chapel are next on your tour.",
                )
            }
            else -> chatNpc(happy, "Head up the ladder to the bank.")
        }
    }

    private suspend fun Dialogue.accountGuide() {
        if (!lessonReady(TutorialIsland.BANK_DONE)) return
        if (player.tutorialStage < TutorialIsland.ACCOUNT_DONE) {
            chatNpc(
                happy,
                "Greetings. I'm the Account Guide - I keep this bank's newest " +
                    "customers safe.",
            )
            chatNpc(
                happy,
                "Your bank stores your items securely - click a booth to open " +
                    "it. But remember: your account itself needs protecting too.",
            )
            chatNpc(
                happy,
                "Use a strong, unique password and set up the RuneScape " +
                    "Authenticator. Never tell anyone your password - not even " +
                    "Jagex staff!",
            )
            chatNpc(
                happy,
                "That's all from me. Through the next door you'll find Brother " +
                    "Brace in the chapel.",
            )
            player.tutorialStage = TutorialIsland.ACCOUNT_DONE
        } else {
            chatNpc(happy, "Keep your account safe, ${player.displayName}.")
        }
    }

    private suspend fun Dialogue.brotherBrace() {
        if (!lessonReady(TutorialIsland.ACCOUNT_DONE)) return
        if (player.tutorialStage < TutorialIsland.BRACE_DONE) {
            chatNpc(happy, "Welcome, child, to the chapel. I am Brother Brace.")
            chatPlayer(quiz, "What is this place?")
            chatNpc(
                happy,
                "This is where adventurers come to pray. Bones of slain foes " +
                    "can be buried for Prayer experience - and altars like this " +
                    "one restore your prayer points.",
            )
            chatNpc(
                happy,
                "Your friends and ignore lists are on the faces tab. Add " +
                    "trustworthy adventurers to your friends list - and anyone " +
                    "who bothers you to your ignore list.",
            )
            chatNpc(
                happy,
                "Now - the Ironman tutor waits in the building to the south. " +
                    "He can explain the Ironman game modes before you leave.",
            )
            player.tutorialStage = TutorialIsland.BRACE_DONE
        } else {
            chatNpc(happy, "Blessings upon you. The Ironman tutor is to the south.")
        }
    }

    private suspend fun Dialogue.paul() {
        if (player.coords.onTutorialIsland() && player.tutorialStage < TutorialIsland.DONE) {
            chatNpc(happy, "Hello! I'm Paul - the Ironman tutor here on the island.")
            chatNpc(
                happy,
                "Ironmen play the game alone - no trading, no taking items " +
                    "dropped by other players, no help from others.",
            )
            chatNpc(
                happy,
                "Hardcore Ironmen get a single life - one dangerous death and " +
                    "they become a normal Ironman. Ultimate Ironmen can't use " +
                    "the bank at all.",
            )
            when (
                choice4(
                    "Set me as a standard Ironman.",
                    1,
                    "Set me as a Hardcore Ironman.",
                    2,
                    "Set me as an Ultimate Ironman.",
                    3,
                    "I'll play normally, thanks.",
                    4,
                )
            ) {
                1 -> {
                    player.setGamemode(PlayerGamemode.IRONMAN)
                    chatNpc(
                        happy,
                        "You are now an Ironman. Adam in Lumbridge can give you " +
                            "your armour after you leave the island.",
                    )
                }
                2 -> {
                    player.setGamemode(PlayerGamemode.HARDCORE_IRONMAN)
                    chatNpc(
                        happy,
                        "A Hardcore Ironman! One life only. Adam in Lumbridge " +
                            "can give you your armour after you leave the island.",
                    )
                }
                3 -> {
                    player.setGamemode(PlayerGamemode.ULTIMATE_IRONMAN)
                    chatNpc(
                        happy,
                        "An Ultimate Ironman - no bank for you! Adam in Lumbridge " +
                            "can give you your armour after you leave the island.",
                    )
                }
                else -> {
                    player.setGamemode(PlayerGamemode.NORMAL)
                    chatNpc(
                        happy,
                        "Fair enough - you'll play as a normal adventurer. You " +
                            "can change your mind while you're on this island. " +
                            "Once you leave, you cannot become an Ironman.",
                    )
                }
            }
            chatNpc(
                happy,
                "Head through the door to the final building - the Magic " +
                    "Instructor is the last stop on the island.",
            )
            if (player.tutorialStage < TutorialIsland.PAUL_DONE) {
                player.tutorialStage = TutorialIsland.PAUL_DONE
            }
        } else {
            chatNpc(happy, "You are ${gamemodeName()}. Speak to Adam in Lumbridge about your status.")
        }
    }

    private suspend fun Dialogue.terrova() {
        if (!lessonReady(TutorialIsland.BRACE_DONE)) return
        when {
            player.tutorialStage < TutorialIsland.RUNES -> {
                chatNpc(happy, "Greetings! I am Terrova, the magic instructor.")
                chatNpc(
                    happy,
                    "Magic is powerful - with the right runes you can hurl " +
                        "fire, freeze foes, or teleport across the land.",
                )
                chatNpc(happy, "Take these runes - air runes and mind runes.")
                if (!supply(TutorialIsland.RUNES,
                        InvObj("obj.airrune", 5), InvObj("obj.mindrune", 5))) return
                objbox("obj.airrune", "Terrova gives you air and mind runes!")
                chatNpc(
                    happy,
                    "Open your spellbook - the magic tab - and cast Wind " +
                        "Strike on the chickens in the yard outside.",
                )
            }
            player.tutorialStage == TutorialIsland.RUNES -> {
                if ("obj.airrune" !in player.inv || "obj.mindrune" !in player.inv) {
                    if (!supply(null, InvObj("obj.airrune", 5), InvObj("obj.mindrune", 5))) return
                }
                chatNpc(happy, "Cast Wind Strike on a chicken outside to complete your training.")
            }
            player.tutorialStage >= TutorialIsland.CHICKEN_KILL -> {
                chatNpc(happy, "A natural! Your training is complete.")
                val leave =
                    choice2(
                        "Teleport me to the mainland!",
                        true,
                        "I'd like to stay a little longer.",
                        false,
                    )
                if (leave) {
                    chatNpc(
                        happy,
                        "May Guthix watch over you! Off you go - Lumbridge " +
                            "awaits. Speak to the Lumbridge Guide and the " +
                            "tutors there for more help.",
                    )
                    finish()
                } else {
                    chatNpc(
                        happy,
                        "Very well - the island is yours to explore. Talk to " +
                            "me when you're ready to leave.",
                    )
                }
            }
            else -> chatNpc(happy, "Welcome back! Talk to me when you're ready to " +
                "leave the island.")
        }
    }

    private fun Dialogue.gamemodeName(): String =
        when (player.gamemode) {
            PlayerGamemode.HARDCORE_IRONMAN -> "a Hardcore Ironman"
            PlayerGamemode.ULTIMATE_IRONMAN -> "an Ultimate Ironman"
            PlayerGamemode.IRONMAN -> "an Ironman"
            else -> "a normal adventurer"
        }

    private suspend fun Dialogue.lessonReady(required: Int): Boolean {
        if (player.tutorialStage >= required && player.tutorialStage < TutorialIsland.DONE) {
            return true
        }
        chatNpc(happy, "Please finish your current lesson first.")
        return false
    }

    private suspend fun Dialogue.supply(nextStage: Int?, vararg items: InvObj): Boolean {
        if (!TutorialInventory.supply(player, *items)) {
            chatNpc(sad, "You'll need some more room in your inventory first.")
            return false
        }
        if (nextStage != null) player.tutorialStage = nextStage
        return true
    }

    private suspend fun Dialogue.finish() {
        player.tutorialStage = TutorialIsland.DONE
        player.runClientScript(
            TutorialIsland.TUTORIAL_END_CLIENTSCRIPT.asRSCM(RSCMType.CLIENTSCRIPT)
        )
        access.teleport(TutorialIsland.MAINLAND)
        access.mes("You have completed the tutorial. Welcome to Gielinor!")
    }

    private fun ProtectedAccess.tutorialXp(stat: String, xp: Double) {
        if (player.statBase(stat) < TutorialIsland.XP_CAP_LEVEL) {
            statAdvance(stat, xp)
        }
    }

    private suspend fun ProtectedAccess.fish() {
        if (player.tutorialStage < TutorialIsland.BRYNNA_NET) {
            mes("You should talk to the Survival Expert first.")
            return
        }
        if ("obj.net" !in player.inv) {
            mes("You need a fishing net to catch shrimp here.")
            return
        }
        anim("seq.human_fishing_casting")
        mes("You cast out your net...")
        delay(4)
        if (!invAdd(player.inv, "obj.newbieraw_shrimp").success) {
            mes("You don't have enough inventory space.")
            return
        }
        tutorialXp("stat.fishing", 10.0)
        spam("You catch some shrimp.")
        if (player.tutorialStage == TutorialIsland.BRYNNA_NET) {
            player.tutorialStage = TutorialIsland.SHRIMP_CAUGHT
            mes("You caught a shrimp! Take it back to the Survival Expert.")
        }
    }

    private suspend fun ProtectedAccess.chop() {
        if (player.tutorialStage < TutorialIsland.AXE_TINDER) {
            mes("You should talk to the Survival Expert first.")
            return
        }
        if ("obj.bronze_axe" !in player.inv && "obj.bronze_axe" !in player.worn) {
            mes("You need an axe to chop this tree.")
            return
        }
        anim("seq.human_woodcutting_bronze_axe")
        mes("You swing your axe at the tree.")
        delay(4)
        if (!invAdd(player.inv, "obj.newbielogs").success) {
            mes("You don't have enough inventory space.")
            return
        }
        tutorialXp("stat.woodcutting", 25.0)
        spam("You get some logs.")
        if (player.tutorialStage == TutorialIsland.AXE_TINDER) {
            player.tutorialStage = TutorialIsland.LOGS
            mes("You got some logs. Use the tinderbox on them to light a fire.")
        }
    }

    private suspend fun ProtectedAccess.lightFire() {
        if (player.tutorialStage < TutorialIsland.LOGS) {
            mes("You should get some logs first.")
            return
        }
        anim("seq.human_createfire")
        delay(4)
        if ("obj.tinderbox" !in player.inv ||
            !invDel(player.inv, "obj.newbielogs", 1).success) return
        locRepo.add(
            player.coords,
            "loc.newbiefire",
            100,
            LocAngle.North,
            LocShape.CentrepieceStraight,
        )
        tutorialXp("stat.firemaking", 40.0)
        spam("The fire catches and the logs begin to burn.")
        if (player.tutorialStage == TutorialIsland.LOGS) {
            player.tutorialStage = TutorialIsland.FIRE_LIT
            mes("Your fire is burning! Use your raw shrimp on it to cook them.")
        }
    }

    private suspend fun ProtectedAccess.cookShrimp() {
        if ("obj.newbieraw_shrimp" !in player.inv) {
            mes("You have no raw shrimp to cook.")
            return
        }
        anim("seq.human_cooking")
        delay(4)
        if (!invReplace(player.inv, "obj.newbieraw_shrimp", 1, "obj.shrimp").success) return
        tutorialXp("stat.cooking", 30.0)
        spam("You cook the shrimp.")
        if (player.tutorialStage in TutorialIsland.LOGS..TutorialIsland.FIRE_LIT) {
            player.tutorialStage = TutorialIsland.SHRIMP_COOKED
            mes("Your shrimp is cooked! Take it back to the Survival Expert.")
        }
    }

    private suspend fun ProtectedAccess.makeDough(water: String, empty: String) {
        if ("obj.newbie_pot_flour" !in player.inv || water !in player.inv) {
            mes("You need both flour and water to make dough.")
            return
        }
        if (!TutorialInventory.exchange(
                player,
                listOf(InvObj("obj.newbie_pot_flour"), InvObj(water)),
                listOf(InvObj("obj.bread_dough"), InvObj("obj.pot_empty"), InvObj(empty)),
            )) {
            mes("You need some more inventory space to make dough.")
            return
        }
        spam("You mix the flour and water to make some bread dough.")
        if (player.tutorialStage == TutorialIsland.LEV_FLOUR) {
            player.tutorialStage = TutorialIsland.DOUGH
        }
    }

    private suspend fun ProtectedAccess.bakeBread() {
        if ("obj.bread_dough" !in player.inv) {
            mes("You have no dough to bake.")
            return
        }
        anim("seq.human_cooking")
        delay(4)
        if (!invReplace(player.inv, "obj.bread_dough", 1, "obj.bread").success) return
        tutorialXp("stat.cooking", 40.0)
        spam("You bake a loaf of bread.")
        if (player.tutorialStage == TutorialIsland.DOUGH) {
            player.tutorialStage = TutorialIsland.BREAD
            mes("Your bread is baked! Take it back to the Master Chef.")
        }
    }

    private suspend fun ProtectedAccess.mine(ore: String) {
        if (player.tutorialStage < TutorialIsland.DEZZICK_PICK) {
            mes("You should talk to the Mining Instructor first.")
            return
        }
        if ("obj.bronze_pickaxe" !in player.inv && "obj.bronze_pickaxe" !in player.worn) {
            mes("You need a pickaxe to mine here.")
            return
        }
        anim("seq.human_mining_bronze_pickaxe")
        delay(4)
        if (!invAdd(player.inv, ore).success) {
            mes("You don't have enough inventory space.")
            return
        }
        tutorialXp("stat.mining", 17.5)
        spam("You manage to mine some ore.")
        if (
            player.tutorialStage == TutorialIsland.DEZZICK_PICK &&
                "obj.copper_ore" in player.inv &&
                "obj.tin_ore" in player.inv
        ) {
            player.tutorialStage = TutorialIsland.ORES
            mes("You have both ores! Smelt them at the furnace.")
        }
    }

    private suspend fun ProtectedAccess.smelt() {
        if (player.tutorialStage < TutorialIsland.ORES) {
            mes("You should mine some copper and tin first.")
            return
        }
        if ("obj.copper_ore" !in player.inv || "obj.tin_ore" !in player.inv) {
            mes("You need one copper ore and one tin ore to make bronze.")
            return
        }
        anim("seq.human_furnace")
        delay(4)
        if (!TutorialInventory.exchange(
                player,
                listOf(InvObj("obj.copper_ore"), InvObj("obj.tin_ore")),
                listOf(InvObj("obj.bronze_bar")),
            )) return
        tutorialXp("stat.smithing", 6.25)
        spam("You smelt the ores into a bronze bar.")
        if (player.tutorialStage == TutorialIsland.ORES) {
            player.tutorialStage = TutorialIsland.BAR
            mes("You made a bronze bar! Show it to the Mining Instructor.")
        }
    }

    private suspend fun ProtectedAccess.openBank() {
        player.openBank(eventBus)
        if (player.tutorialStage in TutorialIsland.RANGED_KILL until TutorialIsland.BANK_DONE) {
            player.tutorialStage = TutorialIsland.BANK_DONE
        }
    }

    private suspend fun ProtectedAccess.descend(loc: BoundLocInfo) {
        if (player.tutorialStage < TutorialIsland.QUEST_DONE) {
            mes("You should talk to the Quest Guide first.")
            return
        }
        teleport(loc.coords.translateZ(TutorialIsland.UNDERGROUND_Z_OFFSET))
    }

    private suspend fun ProtectedAccess.climbUp(loc: BoundLocInfo) {
        teleport(loc.coords.translateZ(-TutorialIsland.UNDERGROUND_Z_OFFSET))
    }

    private suspend fun ProtectedAccess.tutorialDoor(
        door: BoundLocInfo,
        symbol: String,
        required: Int,
    ) {
        if (player.tutorialStage < required) {
            mes("You need to finish this part of the tutorial first.")
            return
        }
        soundSynth(TutorialIsland.doorSound(symbol))
        val pair = TutorialIsland.DOOR_PAIRS[symbol]
        openDoorHalf(door.coords, door.shape, door.angle, door.id, if (pair?.second == true) 3 else 1) {
            locRepo.del(door, TutorialIsland.DOOR_OPEN_TICKS)
        }
        if (pair != null) {
            val partner = doorPartner(door, pair.first)
            if (partner != null) {
                val rotations = if (pair.second) 1 else 3
                openDoorHalf(partner.coords, partner.shape, partner.angle, partner.id, rotations) {
                    locRepo.del(partner, TutorialIsland.DOOR_OPEN_TICKS)
                }
            }
        }
    }

    private fun ProtectedAccess.openDoorHalf(
        coords: CoordGrid,
        shape: LocShape,
        angle: LocAngle,
        locId: Int,
        rotations: Int,
        del: () -> Unit,
    ) {
        del()
        locRepo.add(
            DoorTranslations.translateOpen(coords, shape, angle),
            RSCM.getReverseMapping(RSCMType.LOC, locId),
            TutorialIsland.DOOR_OPEN_TICKS,
            angle.turn(rotations),
            shape,
        )
    }

    private fun doorPartner(door: BoundLocInfo, partnerSymbol: String): LocInfo? {
        val partnerId = partnerSymbol.asRSCM(RSCMType.LOC)
        for ((dx, dz) in DOOR_NEIGHBOURS) {
            val partner =
                locRepo.findAll(door.coords.translate(dx, dz)).firstOrNull { it.id == partnerId }
            if (partner != null) {
                return partner
            }
        }
        return null
    }

    private companion object {
        private val DOOR_NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        private val TREES =
            listOf(
                "loc.newbietree",
                "loc.newbietree2",
                "loc.newbiedeadtree",
                "loc.newbiedeadtree2",
                "loc.newbieoaktree",
            )
    }
}
