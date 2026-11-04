package org.rsmod.content.quest.area.falador.piratestreasure

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Pirate's Treasure dialogue, transcribed from LostCity's `quest_hunt` scripts
 * (`redbeard_frank.rs2`, `luthas.rs2`, `zambo.rs2`, `wyson_the_gardener.rs2`).
 * The quest script owns these npc bindings outright.
 */
object PiratesTreasureDialogue {

    private val quest
        get() = checkNotNull(Quest.get(PiratesTreasure.KEY)) { "quest not registered" }

    var Player.crateBananas: Int
        get() = vars[VAR_BANANAS]
        set(value) = VarPlayerIntMapSetter.set(this, VAR_BANANAS, value)

    var Player.crateRum: Int
        get() = vars[VAR_RUM]
        set(value) = VarPlayerIntMapSetter.set(this, VAR_RUM, value)

    var Player.employed: Int
        get() = vars[VAR_EMPLOYED]
        set(value) = VarPlayerIntMapSetter.set(this, VAR_EMPLOYED, value)

    var Player.gardenerProvoked: Int
        get() = vars[VAR_GARDENER_PROVOKED]
        set(value) = VarPlayerIntMapSetter.set(this, VAR_GARDENER_PROVOKED, value)

    val Player.employedPlantation: Boolean
        get() = employed and EMPLOY_PLANTATION != 0

    val Player.employedFoodStore: Boolean
        get() = employed and EMPLOY_FOOD_STORE != 0

    fun Player.huntStage(): Int = quest.getQuestStage(this)

    suspend fun ProtectedAccess.frankDialogue(npc: Npc) = startDialogue(npc) { frank() }

    suspend fun Dialogue.frank() {
        chatNpc(laugh, "Arrrh Matey!")

        if (player.huntStage() == 1) {
            frankProgress()
        } else if (player.huntStage() == 2 && invTotal(access.inv, ChestKey) == 0) {
            chatPlayer(sad, "I seem to have lost my chest key...")
            chatNpc(neutral, "Arr, silly you. Fortunately I took the precaution to have " +
                "another one made.")
            objbox(ChestKey, zoom = 250, text = "Frank hands you a chest key")
            access.invAdd(access.inv, ChestKey)
            frankOptions()
        } else {
            frankOptions()
        }
    }

    private suspend fun Dialogue.frankProgress() {
        chatNpc(quiz, "Have ye brought some rum for yer ol' mate Frank?")
        if (invTotal(access.inv, KaramjaRum) >= 1) {
            chatPlayer(happy, "Yes, I've got some.")
            frankHandRum()
            return
        }
        chatPlayer(sad, "No, not yet.")
    }

    suspend fun Dialogue.frankHandRum() {
        chatNpc(
            shifty,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve " +
                "under a pirate captain called One-Eyed Hector.",
        )
        chatNpc(
            shifty,
            "Hector were very successful and became very rich. But about a year ago " +
                "we were boarded by the Customs and Excise Agents.",
        )
        chatNpc(
            sad,
            "Hector were killed along with many of the crew, I were one of the few to " +
                "escape and I escaped with this.",
        )

        quest.setQuestStage(access, 2)
        access.invDel(access.inv, KaramjaRum, count = 1)
        access.invAdd(access.inv, ChestKey)

        objbox(
            ChestKey,
            zoom = 250,
            text = "Frank happily takes the rum...\n... and hands you a key.",
        )
        chatNpc(
            shifty,
            "This be Hector's key. I believe it opens his chest in his old room in " +
                "the Blue Moon Inn in Varrock.",
        )
        chatNpc(neutral, "With any luck his treasure will be in there.")

        val option =
            choice2("Ok thanks, I'll go and get it.", 1, "So why didn't you ever get it?", 2)
        if (option == 1) {
            chatPlayer(happy, "Ok thanks, I'll go and get it.")
        } else {
            chatPlayer(quiz, "So why didn't you ever get it?")
            chatNpc(
                sad,
                "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken " +
                    "trouble maker.",
            )
        }
    }

    private suspend fun Dialogue.frankOptions() {
        if (player.huntStage() == 0) {
            when (
                choice3(
                    "I'm in search of treasure.",
                    FrankTopic.Treasure,
                    "Arr!",
                    FrankTopic.Arr,
                    "Do you have anything for trade?",
                    FrankTopic.Trade,
                )
            ) {
                FrankTopic.Treasure -> frankTreasure()
                FrankTopic.Arr -> frankArr()
                FrankTopic.Trade -> frankTrade()
            }
        } else {
            when (
                choice2("Arr!", FrankTopic.Arr, "Do you have anything for trade?", FrankTopic.Trade)
            ) {
                FrankTopic.Arr -> frankArr()
                FrankTopic.Trade -> frankTrade()
                else -> frankTrade()
            }
        }
    }

    private suspend fun Dialogue.frankTreasure() {
        chatPlayer(shifty, "I'm in search of treasure.")
        chatNpc(
            shifty,
            "Arr, treasure you be after eh? Well I might be able to tell you where " +
                "to find some... For a price...",
        )
        chatPlayer(quiz, "What sort of price?")
        chatNpc(neutral, "Well for example if you can get me a bottle of rum... " +
            "Not just any rum mind...")
        chatNpc(
            happy,
            "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!",
        )
        quest.setQuestStage(access, 1)
    }

    private suspend fun Dialogue.frankArr() {
        chatPlayer(laugh, "Arr!")
        chatNpc(laugh, "Arr!")
        frankOptions()
    }

    private suspend fun Dialogue.frankTrade() {
        chatPlayer(quiz, "Do you have anything for trade?")
        chatNpc(neutral, CustomsReply)
    }

    suspend fun ProtectedAccess.luthasDialogue(npc: Npc) = startDialogue(npc) { luthas() }

    private suspend fun Dialogue.luthas() {
        if (!player.employedPlantation) {
            chatNpc(happy, "Hello I'm Luthas, I run the banana plantation here.")
            when (
                choice2(
                    "Could you offer me employment on your plantation?",
                    LuthasTopic.Employment,
                    "That customs officer is annoying isn't she?",
                    LuthasTopic.Customs,
                )
            ) {
                LuthasTopic.Employment -> luthasEmployment()
                LuthasTopic.Customs -> luthasCustoms()
                else -> luthasCustoms()
            }
            return
        }

        if (player.crateBananas == CrateFull) {
            chatPlayer(happy, "I've filled a crate with bananas.")
            chatNpc(happy, "Well done, here's your payment.")
            access.mes("Luthas hands you 30 coins.")
            access.invAdd(access.inv, "obj.coins", count = 30)
            player.employed = player.employed and EMPLOY_PLANTATION.inv()
            player.crateBananas = 0
            if (player.crateRum == RumPlantation) {
                player.crateRum = RumFoodStore
            }
            delay(3)
            when (
                choice4(
                    "Will you pay me for another crate full?",
                    LuthasTopic.Another,
                    "Thank you, I'll be on my way",
                    LuthasTopic.Thanks,
                    "So where are these bananas going to be delivered to?",
                    LuthasTopic.Delivered,
                    "That customs officer is annoying isn't she?",
                    LuthasTopic.Customs,
                )
            ) {
                LuthasTopic.Another -> luthasAnother()
                LuthasTopic.Thanks -> chatPlayer(happy, "Thank you, I'll be on my way.")
                LuthasTopic.Delivered -> luthasDelivered()
                LuthasTopic.Customs -> luthasCustoms()
                else -> luthasCustoms()
            }
            return
        }

        chatNpc(quiz, "Have you completed your task yet?")
        when (
            choice4(
                "What did I have to do again?",
                LuthasTopic.WhatToDo,
                "No, the crate isn't full yet.",
                LuthasTopic.NotFull,
                "So where are these bananas going to be delivered to?",
                LuthasTopic.Delivered,
                "That customs officer is annoying isn't she?",
                LuthasTopic.Customs,
            )
        ) {
            LuthasTopic.WhatToDo -> luthasWhatToDo()
            LuthasTopic.NotFull -> luthasNotFull()
            LuthasTopic.Delivered -> luthasDelivered()
            LuthasTopic.Customs -> luthasCustoms()
            else -> luthasCustoms()
        }
    }

    private suspend fun Dialogue.luthasEmployment() {
        chatPlayer(quiz, "Could you offer me employment on your plantation?")
        chatNpc(
            happy,
            "Yes, I can sort something out. There's a crate ready to be loaded " +
                "onto the ship.",
        )
        chatNpc(happy, "If you could fill it up with bananas, I'll pay you 30 gold.")
        player.employed = player.employed or EMPLOY_PLANTATION
    }

    private suspend fun Dialogue.luthasCustoms() {
        chatPlayer(shifty, "That customs officer is annoying isn't she?")
        chatNpc(
            neutral,
            "Well I know her pretty well. She doesn't cause me any trouble any more.",
        )
        chatNpc(
            neutral,
            "She doesn't even search my export crates any more. She knows they " +
                "only contain bananas.",
        )
    }

    private suspend fun Dialogue.luthasWhatToDo() {
        chatPlayer(quiz, "What did I have to do again?")
        chatNpc(
            happy,
            "There's a crate ready to be loaded onto the ship. If you could fill " +
                "it up with bananas, I'll pay you 30 gold.",
        )
    }

    private suspend fun Dialogue.luthasNotFull() {
        chatPlayer(sad, "No, the crate isn't full yet...")
        chatNpc(angry, "Well come back when it is.")
    }

    private suspend fun Dialogue.luthasDelivered() {
        chatPlayer(quiz, "So where are these bananas going to be delivered to?")
        chatNpc(neutral, "I sell them to Wydin who runs a grocery store in Port Sarim.")
    }

    private suspend fun Dialogue.luthasAnother() {
        chatPlayer(quiz, "Will you pay me for another crate full?")
        chatNpc(happy, "Yes certainly.")
        chatNpc(
            neutral,
            "If you go outside you should see the old crate has been loaded on to " +
                "the ship, and there is another empty crate in its place.",
        )
        player.employed = player.employed or EMPLOY_PLANTATION
    }

    suspend fun ProtectedAccess.zemboDialogue(npc: Npc, shops: Shops) =
        startDialogue(npc) { zembo(shops) }

    private suspend fun Dialogue.zembo(shops: Shops) {
        chatNpc(
            quiz,
            "Hey, are you wanting to try some of my fine wines and spirits? All " +
                "brewed locally on Karamja island.",
        )
        if (choice2("Yes please.", true, "No, thank you.", false)) {
            shops.open(
                player = player,
                title = BoozeshopTitle,
                shopInv = BoozeshopInv,
                buyPercentage = BoozeshopBuy,
                sellPercentage = BoozeshopSell,
                changePercentage = BoozeshopChange,
            )
        } else {
            chatPlayer(neutral, "No, thank you.")
        }
    }

    private enum class FrankTopic {
        Treasure,
        Arr,
        Trade,
    }

    private enum class LuthasTopic {
        Employment,
        Customs,
        WhatToDo,
        NotFull,
        Delivered,
        Another,
        Thanks,
    }

    const val VAR_BANANAS = "varbit.pt_crate_bananas"
    const val VAR_RUM = "varbit.pt_crate_rum"
    const val VAR_EMPLOYED = "varbit.pt_employed"
    const val VAR_GARDENER_PROVOKED = "varbit.pt_gardener_provoked"

    const val EMPLOY_PLANTATION = 1
    const val EMPLOY_FOOD_STORE = 2

    const val CrateFull = 10
    const val RumPlantation = 1
    const val RumFoodStore = 2

    const val KaramjaRum = "obj.karamja_rum"
    const val ChestKey = "obj.chest_key"
    const val Coins = "obj.coins"

    const val CustomsReply =
        "Nothin' at the moment, but then again the Customs Agents are on the warpath right now."

    private const val BoozeshopInv = "inv.boozeshop"
    private const val BoozeshopTitle = "Karamja Wines, Spirits, and Beers."
    private const val BoozeshopBuy = 70.0
    private const val BoozeshopSell = 100.0
    private const val BoozeshopChange = 2.0
}
