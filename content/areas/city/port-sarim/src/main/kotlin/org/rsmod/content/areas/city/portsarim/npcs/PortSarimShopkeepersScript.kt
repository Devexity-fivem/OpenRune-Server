package org.rsmod.content.areas.city.portsarim.npcs

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.EMPLOY_FOOD_STORE
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.employed
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.employedFoodStore
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.huntStage
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Prices mirror each shop's inv config, in tenths of a percent. */
private enum class PortSarimShop(
    val npc: String,
    val title: String,
    val inv: String,
    val sell: Double,
    val buy: Double,
    val change: Double,
) {
    Fishing("npc.gerrant", "Gerrant's Fishy Business.", "inv.fishingshop", 100.0, 70.0, 1.0),
    Food("npc.wydin", "Food Store", "inv.wydinstore", 100.0, 70.0, 1.0),
    Jewellery("npc.grum", "Grum's Gold Exchange.", "inv.goldshop", 100.0, 70.0, 2.0),
    Magic("npc.betty", "Betty's Magic Emporium.", "inv.magicshop", 100.0, 60.0, 0.1),
    Battleaxes("npc.brian", "Brian's Battleaxe Bazaar.", "inv.battleaxeshop", 100.0, 55.0, 1.0),
}

class PortSarimShopkeepersScript @Inject constructor(private val shops: Shops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (shop in PortSarimShop.entries - PortSarimShop.Jewellery) {
            onOpNpc3(shop.npc) { player.openShop(shop) }
        }
        onTradeAcross(PortSarimShop.Jewellery.npc) { player.openShop(PortSarimShop.Jewellery) }
        onTalkAcross(PortSarimShop.Jewellery.npc) { startDialogue(it) { grum() } }
        onOpNpc1(PortSarimShop.Fishing.npc) { startDialogue(it.npc) { gerrant() } }
        onOpNpc1(PortSarimShop.Food.npc) { startDialogue(it.npc) { wydin() } }
        onOpNpc1(PortSarimShop.Magic.npc) { startDialogue(it.npc) { betty() } }
        onOpNpc1(PortSarimShop.Battleaxes.npc) { startDialogue(it.npc) { brian() } }
    }

    private suspend fun Dialogue.gerrant() {
        chatNpc(
            neutral,
            "Welcome! You can buy fishing equipment at my store. We'll also buy anything you " +
                "catch off you.",
        )
        if (choice2("Let's see what you've got then.", true, "Sorry, I'm not interested.", false)) {
            chatPlayer(neutral, "Let's see what you've got then.")
            player.openShop(PortSarimShop.Fishing)
        } else {
            chatPlayer(neutral, "Sorry, I'm not interested.")
        }
    }

    private suspend fun Dialogue.wydin() {
        if (player.employedFoodStore) {
            return wydinEmployed()
        }
        chatNpc(happy, "Welcome to my food store! Would you like to buy anything?")
        if (player.huntStage() == 1) {
            val topic =
                choice4(
                    "Yes please.",
                    WydinTopic.Yes,
                    "No, thank you.",
                    WydinTopic.No,
                    "What can you recommend?",
                    WydinTopic.Recommend,
                    "Can I get a job here?",
                    WydinTopic.Job,
                )
            when (topic) {
                WydinTopic.Yes -> {
                    chatPlayer(happy, "Yes please.")
                    player.openShop(PortSarimShop.Food)
                }
                WydinTopic.No -> chatPlayer(neutral, "No, thank you.")
                WydinTopic.Recommend -> wydinRecommend()
                WydinTopic.Job -> wydinJob()
            }
            return
        }
        when (choice3("Yes please.", 1, "No, thank you.", 2, "What can you recommend?", 3)) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                player.openShop(PortSarimShop.Food)
            }
            2 -> chatPlayer(neutral, "No, thank you.")
            else -> wydinRecommend()
        }
    }

    private suspend fun Dialogue.wydinRecommend() {
        chatPlayer(quiz, "What can you recommend?")
        chatNpc(
            happy,
            "We have this really exotic fruit all the way from Karamja. It's called a " +
                "banana.",
        )
        val tryOne =
            choice2(
                "Hmm, I think I'll try one.",
                true,
                "I don't like the sound of that.",
                false,
            )
        if (tryOne) {
            chatPlayer(happy, "Hmm, I think I'll try one.")
            chatNpc(
                happy,
                "Great. You might as well take a look at the rest of my wares as well.",
            )
            player.openShop(PortSarimShop.Food)
        } else {
            chatPlayer(neutral, "I don't like the sound of that.")
            chatNpc(neutral, "Well, it's your choice, but I do recommend them.")
        }
    }

    private suspend fun Dialogue.wydinEmployed() {
        chatNpc(quiz, "Is it nice and tidy round the back now?")
        when (
            choice4(
                "Yes, can I work out front now?",
                1,
                "Yes, are you going to pay me yet?",
                2,
                "No, it's a complete mess",
                3,
                "Can I buy something please?",
                4,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, can I work out front now?")
                chatNpc(neutral, "I'm the one who works here.")
            }
            2 -> {
                chatPlayer(happy, "Yes, are you going to pay me yet?")
                chatNpc(shifty, "Umm... No, not yet.")
            }
            3 -> {
                chatPlayer(sad, "No, it's a complete mess.")
                chatNpc(neutral, "Ah well, it'll give you something to do, won't it.")
            }
            else -> {
                chatPlayer(quiz, "Can I buy something please?")
                chatNpc(happy, "Yes, of course.")
                player.openShop(PortSarimShop.Food)
            }
        }
    }

    private suspend fun Dialogue.wydinJob() {
        chatPlayer(quiz, "Can I get a job here?")
        chatNpc(
            neutral,
            "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have " +
                "you got your own white apron?",
        )
        val hasApron =
            invTotal(access.inv, WhiteApron) > 0 ||
                player.worn[Wearpos.Torso.slot]?.isType(WhiteApron) == true
        if (!hasApron) {
            chatPlayer(sad, "No, I haven't.")
            chatNpc(
                neutral,
                "Well, you can't work here unless you have a white apron. Health " +
                    "and safety regulations, you understand.",
            )
            return
        }
        chatPlayer(happy, "Yes, I have one right here.")
        player.employed = player.employed or EMPLOY_FOOD_STORE
        chatNpc(
            happy,
            "Wow - you are well prepared! You're hired. Go through to the back and " +
                "tidy up for me, please.",
        )
    }

    private suspend fun Dialogue.grum() {
        chatNpc(happy, "Would you like to buy or sell some gold jewellery?")
        if (choice2("Yes please.", true, "No, I'm not that rich.", false)) {
            player.openShop(PortSarimShop.Jewellery)
            return
        }
        chatPlayer(sad, "No, I'm not that rich.")
        chatNpc(angry, "Get out then! We don't want any riff-raff in here.")
    }

    private suspend fun Dialogue.betty() {
        chatNpc(quiz, "Hello again. What can I do for you?")
        val sellsDye = QuestRequirements.hasCompleted(player, HAND_IN_THE_SAND)
        val topic =
            if (sellsDye) {
                choice3(
                    "Can I see your wares?",
                    BettyTopic.Wares,
                    "Could I buy some more pink dye?",
                    BettyTopic.Dye,
                    "I'm good, thanks.",
                    BettyTopic.Leave,
                )
            } else {
                choice2(
                    "Can I see your wares?",
                    BettyTopic.Wares,
                    "I'm good, thanks.",
                    BettyTopic.Leave,
                )
            }
        when (topic) {
            BettyTopic.Wares -> {
                chatPlayer(quiz, "Can I see your wares?")
                chatNpc(happy, "Of course.")
                player.openShop(PortSarimShop.Magic)
            }
            BettyTopic.Dye -> buyPinkDye()
            BettyTopic.Leave -> chatPlayer(neutral, "I'm good, thanks.")
        }
    }

    private suspend fun Dialogue.buyPinkDye() {
        chatPlayer(quiz, "Could I buy some more pink dye?")
        chatNpc(happy, "Of course. It's $PINK_DYE_PRICE coins per bottle.")
        val inv = access.inv
        if (inv.count("obj.coins") < PINK_DYE_PRICE) {
            chatPlayer(sad, "Oh, I don't have enough money with me.")
            return
        }
        if (inv.freeSpace() == 0 && inv.count("obj.coins") > PINK_DYE_PRICE) {
            chatPlayer(sad, "Oh, I don't have enough room for any.")
            return
        }
        if (!choice2("Sounds good.", true, "Actually, I'll pass.", false)) {
            chatPlayer(neutral, "Actually, I'll pass.")
            chatNpc(neutral, "Fair enough.")
            return
        }
        chatPlayer(happy, "Sounds good.")
        if (access.invDel(inv, "obj.coins", PINK_DYE_PRICE).failure) {
            return
        }
        access.invAdd(inv, PINK_DYE)
        objbox(
            PINK_DYE,
            zoom = 400,
            "Betty sells you some pink dye in exchange for $PINK_DYE_PRICE coins.",
        )
    }

    private suspend fun Dialogue.brian() {
        if (choice2("So, are you selling something?", true, "'Ello.", false)) {
            chatPlayer(quiz, "So, are you selling something?")
            chatNpc(happy, "Yep, take a look at these great axes!")
            player.openShop(PortSarimShop.Battleaxes)
        } else {
            chatPlayer(happy, "'Ello.")
            chatNpc(happy, "'Ello!")
        }
    }

    private fun Player.openShop(shop: PortSarimShop) {
        shops.open(
            player = this,
            title = shop.title,
            shopInv = shop.inv,
            buyPercentage = shop.buy,
            sellPercentage = shop.sell,
            changePercentage = shop.change,
        )
    }

    private enum class BettyTopic {
        Wares,
        Dye,
        Leave,
    }

    private enum class WydinTopic {
        Yes,
        No,
        Recommend,
        Job,
    }

    private companion object {
        const val HAND_IN_THE_SAND = "quest_handinthesand"
        const val PINK_DYE = "obj.handsand_pink_dye"
        const val PINK_DYE_PRICE = 20
        const val WhiteApron = "obj.white_apron"
    }
}
