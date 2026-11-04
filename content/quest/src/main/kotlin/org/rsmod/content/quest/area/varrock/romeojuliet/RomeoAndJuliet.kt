package org.rsmod.content.quest.area.varrock.romeojuliet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer1
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.apothecaryDialogue
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.berriesOnApothecary
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.draulDialogue
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.fatherLawrenceDialogue
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.julietDialogue
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.messageOnRomeo
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.phillipaDialogue
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.rjStage
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoAndJulietDialogue.romeoDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class RomeoAndJuliet
@Inject
constructor(
    private val npcRepo: NpcRepository,
) :
    QuestScript(
        KEY,
        "varp.rjquest",
        rewards {},
        ItemRewardDisplay("obj.cadava", zoom = 250),
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Romeo & Juliet end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the crypt rescue writes $StageComplete."
        }

        onOpNpc1(RomeoNpc) { romeoDialogue(it.npc, npcRepo) }
        onOpNpcU(npcType(RomeoNpc), objType(JulietMessage)) { messageOnRomeo(it.npc) }
        onAiApPlayer1(npcType(RomeoNpc)) {
            it.target.queue("queue.romeo_approach", 1, npc.uid)
            resetMode()
        }
        onPlayerQueueWithArgs<NpcUid>("queue.romeo_approach") {
            val romeo = findUid(it.args)
            if (romeo != null) {
                romeoDialogue(romeo, npcRepo)
            }
        }

        onOpNpc1(JulietNpc) { julietDialogue(it.npc, npcRepo) }
        onOpNpcU(npcType(JulietNpc), objType(CadavaPotion)) {
            julietDialogue(it.npc, npcRepo)
        }

        onOpNpc1(PhillipaNpc) { phillipaDialogue(it.npc) }
        onOpNpc1(DraulNpc) { draulDialogue(it.npc) }
        onOpNpc1(FatherLawrenceNpc) { fatherLawrenceDialogue(it.npc, npcRepo) }

        onOpNpc1(ApothecaryNpc) { apothecaryDialogue(it.npc) }
        onOpNpc3(ApothecaryNpc) { openApothecaryPotions() }
        onOpNpcU(npcType(ApothecaryNpc), objType(CadavaBerries)) {
            berriesOnApothecary(it.npc)
        }

        onOpHeld1(CadavaPotion) { lookAtCadava() }
        onOpHeld2(CadavaPotion) { drinkCadava() }
    }

    private suspend fun ProtectedAccess.openApothecaryPotions() {
        ifOpenMainModal("interface.apothecary_potions")
    }

    private suspend fun ProtectedAccess.lookAtCadava() {
        mes(
            "This looks very colourful, but you remember what Father Lawrence told " +
                "you about it, probably best not to drink it."
        )
    }

    private suspend fun ProtectedAccess.drinkCadava() {
        if (!insideApothecaryShop(player.coords)) {
            mes(
                "You dare not drink this outside of the apothecary's establishment. " +
                    "At least there the apothecary may be able to help you if you need " +
                    "assistance."
            )
            return
        }
        startDialogue {
            val drink =
                choice2(
                    "Yes, I'll drink it all down.",
                    true,
                    "No, I'm having second thoughts now.",
                    false,
                    title = "Are you sure you wish to drink this Cadava Potion?",
                )
            if (!drink) {
                access.mes("You decide against drinking the potion.")
                return@startDialogue
            }
            access.invDel(access.inv, CadavaPotion, count = 1)
            access.mes(
                "In defiance of Father Lawrence's warnings, you drink the entire " +
                    "contents of the potion."
            )
            access.anim("seq.human_drink_from_vial_cadava")
            access.mes("The screen starts swaying.")
            Camera.camShake(player, CamShakeAxis.PAN_UP_DOWN, 4, 4, 10)
            chatPlayer(drunk, "Urk!")
            access.statSub("stat.hitpoints", player.hitpoints - 1, 0)
            access.mes("Oh dear...you are nearly dead.")
            access.mes("Some time later.....")
            Camera.camShakeResetAll(player)
            chatNpcSpecific(
                "Apothecary",
                ApothecaryNpc,
                shocked,
                "Blimey... you've been out for hours! Are you ok? People were " +
                    "tripping over you as they walked into the shop.",
            )
        }
    }

    override fun subTitle(): String =
        "by talking to <col=800000>Romeo<col=000080> in <col=800000>Varrock Square" +
            "<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = player.player
            if (p.rjStage() >= StageSpokenRomeo) {
                line(
                    "<str>I have agreed to find Juliet for Romeo and tell her how he" +
                        "<str>feels. For some reason he can't just do this himself.|"
                )
            }
            if (p.rjStage() == StageSpokenRomeo) {
                line(
                    "<col=000080>I should go and speak to <red>Juliet<col=000080>. I can find " +
                        "her <red>west<col=000080> of <red>Varrock<col=000080>."
                )
            }
            if (p.rjStage() >= StageSpokenJuliet) {
                line(
                    "<str>I found Juliet on the Western edge of Varrock, and told " +
                        "<str>her about Romeo. She gave me a message to take back.|"
                )
            }
            when (p.rjStage()) {
                StageSpokenJuliet ->
                    line(
                        "<col=000080>I should take the <red>message<col=000080> from <red>Juliet" +
                            "<col=000080> to <red>Romeo<col=000080>."
                    )
            }
            if (p.rjStage() >= StagePassedMessage) {
                line(
                    "<str>I delivered the message to Romeo, and he was sad to hear" +
                        "<str>that Juliet's father opposed their marriage. However, he|"
                )
                line("<str>said that Father Lawrence might be able to overcome this.|")
            }
            when (p.rjStage()) {
                StagePassedMessage ->
                    line(
                        "<col=000080>I should find <red>Father Lawrence<col=000080> and see how we " +
                            "can help"
                    )
            }
            if (p.rjStage() >= StageSpokenFather) {
                line(
                    "<str>I found Father Lawrence and he suggested the use of a" +
                        "<str>potion to fool Juliet's father that she is dead so that|"
                )
                line("<str>Romeo and Juliet can be together in peace.|")
            }
            when (p.rjStage()) {
                StageSpokenFather ->
                    line(
                        "<col=000080>I need to find the <red>Apothecary<col=000080> to make a " +
                            "<red>cadava potion<col=000080>."
                    )
            }
            if (p.rjStage() >= StageSpokenApothecary) {
                line(
                    "<str>I went to the Apothecary regarding making this cadava" +
                        "<str>potion, and he told me to bring him some cadava berries.|"
                )
            }
            if (p.rjStage() == StageSpokenApothecary) {
                when {
                    player.invTotal(player.inv, CadavaPotion) > 0 ->
                        line(
                            "<col=000080>I should take this <red>cadava potion<col=000080> to " +
                                "<red>Juliet<col=000080>."
                        )
                    player.invTotal(player.inv, CadavaBerries) > 0 ->
                        line(
                            "<col=000080>I should take these <red>cadava berries<col=000080> to " +
                                "the <red>Apothecary<col=000080>."
                        )
                    else -> line("<col=000080>I will have to find some <red>cadava berries<col=000080>.")
                }
            }
            if (p.rjStage() >= StageJulietCrypt) {
                line(
                    "<str>After the Apothecary made me the potion, I delivered it to" +
                        "<str>Juliet. She asked me to tell Romeo the plan.|"
                )
            }
            if (p.rjStage() == StageJulietCrypt) {
                line(
                    "<col=000080>I have to find <red>Romeo<col=000080> and tell him what's happened."
                )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "<str>I have agreed to find Juliet for Romeo and tell her how he" +
                    "<str>feels. For some reason he can't just do this himself.|"
            )
            line(
                "<str>I found Juliet on the Western edge of Varrock, and told " +
                    "<str>her about Romeo. She gave me a message to take back.|"
            )
            line(
                "<str>I delivered the message to Romeo, and he was sad to hear" +
                    "<str>that Juliet's father opposed their marriage. However, he|"
            )
            line("<str>said that Father Lawrence might be able to overcome this.|")
            line(
                "<str>I found Father Lawrence and he suggested the use of a" +
                    "<str>potion to fool Juliet's father that she is dead so that|"
            )
            line("<str>Romeo and Juliet can be together in peace.|")
            line(
                "<str>I went to the Apothecary regarding making this cadava" +
                    "<str>potion, and he told me to bring him some cadava berries.|"
            )
            line(
                "<str>After the Apothecary made me the potion, I delivered it to" +
                    "<str>Juliet. She asked me to tell Romeo the plan.|"
            )
            line(
                "<str>I told Romeo what was going to happen, but I'm not exactly" +
                    "<str>sure he understood what was happening. Ah well, I was|"
            )
            line("<str>rewarded for all of my help regardless.|")
        }

    companion object {
        const val KEY = "quest_romeoandjuliet"

        private const val StageComplete = RomeoAndJulietDialogue.StageComplete

        private const val StageSpokenRomeo = RomeoAndJulietDialogue.StageSpokenRomeo
        private const val StageSpokenJuliet = RomeoAndJulietDialogue.StageSpokenJuliet
        private const val StagePassedMessage = RomeoAndJulietDialogue.StagePassedMessage
        private const val StageSpokenFather = RomeoAndJulietDialogue.StageSpokenFather
        private const val StageSpokenApothecary =
            RomeoAndJulietDialogue.StageSpokenApothecary
        private const val StageJulietCrypt = RomeoAndJulietDialogue.StageJulietCrypt

        private const val RomeoNpc = RomeoAndJulietDialogue.RomeoNpc
        private const val JulietNpc = RomeoAndJulietDialogue.JulietNpc
        private const val PhillipaNpc = RomeoAndJulietDialogue.PhillipaNpc
        private const val DraulNpc = RomeoAndJulietDialogue.DraulNpc
        private const val FatherLawrenceNpc = "npc.father_lawrence"
        private const val ApothecaryNpc = "npc.apothecary"

        private const val JulietMessage = RomeoAndJulietDialogue.JulietMessage
        private const val CadavaPotion = RomeoAndJulietDialogue.CadavaPotion
        private const val CadavaBerries = RomeoAndJulietDialogue.CadavaBerries

        private fun insideApothecaryShop(coords: CoordGrid): Boolean =
            coords.level == 0 &&
                coords.x in 3191..3201 &&
                coords.z in 3399..3409

        private fun npcType(internal: String) =
            requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }

        private fun objType(internal: String) =
            requireNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
                "Missing obj type: $internal"
            }
    }
}
