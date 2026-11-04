package org.rsmod.content.areas.city.portsarim.travel

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.employedPlantation
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.huntStage
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KaramjaFerryScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (sailor in SARIM_CREW) {
            onOpNpc1(sailor) { startDialogue(it.npc) { offerTrip() } }
            onOpNpc3(sailor) { payAndSail(MUSA_POINT, MUSA_POINT_NAME) }
        }
        onOpNpc1(CUSTOMS_OFFICER_MUSA) { startDialogue(it.npc) { customsOfficer() } }
        onOpNpc3(CUSTOMS_OFFICER_MUSA) { startDialogue(it.npc) { customsJourney() } }
        onOpNpc1(CUSTOMS_OFFICER_PANDEMONIUM) {
            startDialogue(it.npc) { customsOfficer() }
        }
        onOpNpc3(CUSTOMS_OFFICER_PANDEMONIUM) { payAndSail(PORT_SARIM, PORT_SARIM_NAME) }
        onOpNpc4(CUSTOMS_OFFICER_PANDEMONIUM) {
            payAndSail(PANDEMONIUM, PANDEMONIUM_NAME)
        }
    }

    private suspend fun Dialogue.offerTrip() {
        chatNpc(
            quiz,
            "Hello there. Do you want to go on a trip to Karamja? We can take you to Musa Point " +
                "for only $FARE coins.",
        )
        if (!choice2("Yes please.", true, "No thank you.", false)) {
            chatPlayer(neutral, "No thank you.")
            chatNpc(neutral, "Fair enough.")
            return
        }
        chatPlayer(happy, "Yes please.")
        board(MUSA_POINT, MUSA_POINT_NAME)
    }

    private suspend fun Dialogue.customsOfficer() {
        chatNpc(neutral, "Can I help you?")
        val topic =
            choice2(
                "Can I journey on this ship?",
                CustomsTopic.Journey,
                "Does Karamja have unusual customs then?",
                CustomsTopic.Customs,
            )
        when (topic) {
            CustomsTopic.Journey -> customsJourney()
            CustomsTopic.Customs -> {
                chatPlayer(quiz, "Does Karamja have any unusual customs then?")
                chatNpc(neutral, "I'm not that sort of customs officer.")
            }
        }
    }

    private suspend fun Dialogue.customsJourney() {
        chatPlayer(quiz, "Can I journey on this ship?")
        if (player.huntStage() >= 2 || player.employedPlantation) {
            if (invTotal(access.inv, KaramjaRum) > 0) {
                customsSpotInspection()
            }
            chatNpc(neutral, "Hey, I know you, you work at the plantation.")
            chatNpc(
                neutral,
                "I don't think you'll try smuggling anything, you just need to pay a " +
                    "boarding charge of $FARE coins.",
            )
            customsPassSearch()
            return
        }
        chatNpc(quiz, "You need to be searched before you can board.")
        when (
            choice3(
                "Why?",
                CustomsSearch.Why,
                "Search away, I have nothing to hide.",
                CustomsSearch.Search,
                "You're not putting your hands on my things!",
                CustomsSearch.DontTouch,
            )
        ) {
            CustomsSearch.Why -> customsWhy()
            CustomsSearch.Search -> customsSearch()
            CustomsSearch.DontTouch -> customsDontTouch()
        }
    }

    private suspend fun Dialogue.customsSpotInspection() {
        chatNpc(neutral, "Spot inspection. You don't mind do you?")
        chatNpc(neutral, "Aha, trying to smuggle rum eh?")
        chatPlayer(shifty, "Umm... it's for personal use?")
        access.invDel(access.inv, KaramjaRum, count = RumConfiscateCount)
        access.mes("The customs officer confiscates your rum.")
    }

    private suspend fun Dialogue.customsWhy() {
        chatPlayer(quiz, "Why?")
        chatNpc(
            neutral,
            "Because Asgarnia has banned the import of intoxicating\nspirits.",
        )
        when (
            choice2(
                "Search away, I have nothing to hide.",
                CustomsSearch.Search,
                "You're not putting your hands on my things!",
                CustomsSearch.DontTouch,
            )
        ) {
            CustomsSearch.Search -> customsSearch()
            else -> customsDontTouch()
        }
    }

    private suspend fun Dialogue.customsDontTouch() {
        chatPlayer(shocked, "You're not putting your hands on my things!")
        chatNpc(bored, "You're not getting on this ship then.")
    }

    private suspend fun Dialogue.customsSearch() {
        chatPlayer(happy, "Search away, I have nothing to hide.")
        if (invTotal(access.inv, KaramjaRum) > 0) {
            chatNpc(neutral, "Aha, trying to smuggle rum are we?")
            chatPlayer(shifty, "Umm... it's for personal use?")
            access.invDel(access.inv, KaramjaRum, count = RumConfiscateCount)
            access.mes("The customs officer confiscates your rum.")
            access.mes("You will need to find some way to smuggle it off the island...")
            return
        }
        chatNpc(
            neutral,
            "Well you've got some odd stuff, but it's all legal. Now\nyou need to " +
                "pay a boarding charge of $FARE coins.",
        )
        customsPassSearch()
    }

    private suspend fun Dialogue.customsPassSearch() {
        if (choice2("Ok.", true, "Oh, I'll not bother then.", false)) {
            customsPay()
        } else {
            chatPlayer(neutral, "Oh, I'll not bother then.")
        }
    }

    private suspend fun Dialogue.customsPay() {
        chatPlayer(neutral, "Ok.")
        if (invTotal(access.inv, "obj.coins") < FARE) {
            chatPlayer(sad, "Oh dear, I don't actually seem to have enough money.")
            return
        }
        access.invDel(access.inv, "obj.coins", count = FARE)
        access.sailFerry(PORT_SARIM, PORT_SARIM_NAME, FARE)
    }

    private suspend fun Dialogue.board(dest: CoordGrid, destName: String) {
        if (!access.payFare(FARE)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        access.sailFerry(dest, destName, FARE)
    }

    private suspend fun ProtectedAccess.payAndSail(dest: CoordGrid, destName: String) {
        if (!payFare(FARE)) {
            mes("You do not have enough coins to pay passage, you need $FARE.")
            return
        }
        sailFerry(dest, destName, FARE)
    }

    private enum class CustomsTopic {
        Journey,
        Customs,
    }

    private enum class CustomsSearch {
        Why,
        Search,
        DontTouch,
    }

    private companion object {
        const val FARE = 30
        const val CUSTOMS_OFFICER_MUSA = "npc.customs_officer_1op"
        const val CUSTOMS_OFFICER_PANDEMONIUM = "npc.customs_officer_2op"
        const val MUSA_POINT_NAME = "Musa Point"
        const val PORT_SARIM_NAME = "Port Sarim"
        const val PANDEMONIUM_NAME = "The Pandemonium"
        const val KaramjaRum = "obj.karamja_rum"
        const val RumConfiscateCount = 28

        val SARIM_CREW = listOf("npc.seaman_lorris", "npc.seaman_thresnor", "npc.captain_tobias")
        val MUSA_POINT = CoordGrid(2956, 3146, 0)
        val PORT_SARIM = CoordGrid(3029, 3217, 0)
        val PANDEMONIUM = CoordGrid(3043, 2965, 0)
    }
}
