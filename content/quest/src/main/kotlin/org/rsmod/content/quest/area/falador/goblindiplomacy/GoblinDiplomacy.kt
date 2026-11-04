package org.rsmod.content.quest.area.falador.goblindiplomacy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.falador.goblindiplomacy.GoblinDiplomacyDialogue.useMailDialogue
import org.rsmod.content.quest.area.falador.goblindiplomacy.GoblinDiplomacyDialogue.wrongColourDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext

class GoblinDiplomacy @Inject constructor(private val objRepo: ObjRepository) :
    QuestScript(
        KEY,
        "varp.goblinquest",
        rewards {
            xp("stat.crafting", CraftingXpReward)
            item("obj.gold_bar", GoldBarReward)
        },
        ItemRewardDisplay("obj.goblin_armour_orange", zoom = 480),
        questVarbit = "varbit.gobdip_main",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Goblin Diplomacy end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the hand-in writes $StageComplete."
        }

        val mailVis =
            listOf(
                "obj.goblin_armour_orange" to VisOrange,
                "obj.goblin_armour_darkblue" to VisBlue,
                "obj.goblin_armour" to VisBrown,
            )
        for (general in Generals) {
            for ((mail, vis) in mailVis) {
                onOpNpcU(npcType(general), objType(mail)) { useMailDialogue(it.npc, mail, vis) }
            }
            for (wrongMail in WrongColourMail) {
                onOpNpcU(npcType(general), objType(wrongMail)) { wrongColourDialogue(it.npc) }
            }
        }

        onOpLoc1("loc.goblin_outpost_large_crate_armour1") { searchCrate(Crate1Searched) }
        onOpLoc1("loc.goblin_outpost_large_crate_armour2") { searchCrate(Crate2Searched) }
        onOpLoc1("loc.goblin_outpost_large_crate_armour3") { searchCrate(Crate3Searched) }

        onOpHeldU("obj.orangedye", "obj.goblin_armour") {
            dyeMail("obj.orangedye", "obj.goblin_armour_orange", "orange")
        }
        onOpHeldU("obj.bluedye", "obj.goblin_armour") {
            dyeMail("obj.bluedye", "obj.goblin_armour_darkblue", "blue")
        }
        onOpHeldU("obj.reddye", "obj.goblin_armour") {
            dyeMail("obj.reddye", "obj.goblin_armour_red", "red")
        }
        onOpHeldU("obj.yellowdye", "obj.goblin_armour") {
            dyeMail("obj.yellowdye", "obj.goblin_armour_yellow", "yellow")
        }
        onOpHeldU("obj.greendye", "obj.goblin_armour") {
            dyeMail("obj.greendye", "obj.goblin_armour_green", "green")
        }
        onOpHeldU("obj.purpledye", "obj.goblin_armour") {
            dyeMail("obj.purpledye", "obj.goblin_armour_purple", "purple")
        }

        onOpHeldU("obj.reddye", "obj.yellowdye") {
            mixDyes(
                "You mix the red and yellow dyes to make an orange dye.",
                "obj.reddye",
                "obj.yellowdye",
                "obj.orangedye",
            )
        }
        onOpHeldU("obj.reddye", "obj.bluedye") {
            mixDyes(
                "You mix the red and blue dyes to make a purple dye.",
                "obj.reddye",
                "obj.bluedye",
                "obj.purpledye",
            )
        }
        onOpHeldU("obj.bluedye", "obj.yellowdye") {
            mixDyes(
                "You mix the blue and yellow dyes to make a green dye.",
                "obj.bluedye",
                "obj.yellowdye",
                "obj.greendye",
            )
        }
    }

    override fun subTitle(): String =
        "by talking to <col=800000>General Bentnoze<col=000080> or " +
            "<col=800000>General Wartface<col=000080> in <col=800000>Goblin Village<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            line(
                "<red>General Bentnoze</red> and <red>General Wartface</red> in " +
                    "<red>Goblin Village</red> are arguing about the colour of their armour. " +
                    "I have agreed to bring them armour in a new colour."
            )
            when (quest.getQuestStage(player.player)) {
                StageWillBringArmour ->
                    line("I need to bring them some <red>orange goblin mail</red>.")
                StageGaveOrange ->
                    line(
                        "They didn't like the orange armour. I need to bring them some " +
                            "<red>blue goblin mail</red> instead."
                    )
                StageGaveBlue ->
                    line(
                        "They didn't like the blue armour either. I need to bring them some " +
                            "<red>brown goblin mail</red>."
                    )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "<red>General Bentnoze</red> and <red>General Wartface</red> in " +
                    "<red>Goblin Village</red> were arguing about the colour of their armour."
            )
            addRawLine("")
            line(
                "Grubfoot modelled orange, then blue, then brown goblin mail for the " +
                    "generals. They finally agreed on brown and rewarded me for sorting " +
                    "their argument."
            )
        }

    private suspend fun ProtectedAccess.searchCrate(searchedVarbit: String) {
        arriveDelay()
        if (player.vars[searchedVarbit] != 0) {
            mesbox("You search the crate but find nothing of value.")
            return
        }
        player.anim("seq.human_reachforladder")
        VarPlayerIntMapSetter.set(player, searchedVarbit, 1)
        invAddOrDrop(objRepo, "obj.goblin_armour")
        mesbox("You search the crate and find some goblin armour.")
    }

    private suspend fun ProtectedAccess.dyeMail(dye: String, result: String, colour: String) {
        val success =
            player.invTransaction(inv) {
                val inventory = select(inv)
                delete {
                    from = inventory
                    obj = dye.asRSCM()
                    strictCount = 1
                }
                delete {
                    from = inventory
                    obj = "obj.goblin_armour".asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = result.asRSCM()
                    count = 1
                }
            }.success
        if (success) {
            mesbox("You dye the goblin mail $colour.")
        }
    }

    private suspend fun ProtectedAccess.mixDyes(
        message: String,
        first: String,
        second: String,
        result: String,
    ) {
        val success =
            player.invTransaction(inv) {
                val inventory = select(inv)
                delete {
                    from = inventory
                    obj = first.asRSCM()
                    strictCount = 1
                }
                delete {
                    from = inventory
                    obj = second.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = result.asRSCM()
                    count = 1
                }
            }.success
        if (success) {
            mesbox(message)
        }
    }

    companion object {
        const val KEY = "quest_goblindiplomacy"

        private const val StageWillBringArmour = 2
        private const val StageGaveOrange = 3
        private const val StageGaveBlue = 4
        private const val StageComplete = 6

        private const val VisBrown = 0
        private const val VisOrange = 1
        private const val VisBlue = 2

        private const val CraftingXpReward = 2000.0
        private const val GoldBarReward = 1

        private const val Crate1Searched = "varbit.gobdip_crate1_searched"
        private const val Crate2Searched = "varbit.gobdip_crate2_searched"
        private const val Crate3Searched = "varbit.gobdip_crate3_searched"

        private val Generals =
            listOf("npc.general_bentnoze_red", "npc.general_wartface_green")

        private val WrongColourMail =
            listOf(
                "obj.goblin_armour_red",
                "obj.goblin_armour_yellow",
                "obj.goblin_armour_green",
                "obj.goblin_armour_purple",
                "obj.goblin_armour_pink",
                "obj.goblin_armour_white",
                "obj.goblin_armour_black",
            )

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
