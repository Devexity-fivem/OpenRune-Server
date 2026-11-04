package org.rsmod.content.quest.area.rimmington.corsaircurse

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.arsenDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.ccStage
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.colinBits
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.colinDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.colinFerryDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.cookBits
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.gnocciDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.ithoiDead
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.ithoiDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.killOrdered
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.proveReady
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.setProgress
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.tessDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.tockDialogue
import org.rsmod.content.quest.area.rimmington.corsaircurse.CorsairCurseDialogue.yusufDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class CorsairCurse
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val protectedAccessLauncher: ProtectedAccessLauncher,
    private val eventBus: EventBus,
) :
    QuestScript(
        KEY,
        QUEST_VARP,
        rewards { xp("stat.firemaking", FiremakingXpReward) },
        ItemRewardDisplay("obj.corscurs_relic", zoom = 250),
        questVarbit = QUEST_VARBIT,
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == ProgressComplete) {
            "The Corsair Curse end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the finale writes $ProgressComplete."
        }

        onOpNpc1(npcType(Tock1opNpc)) { tockDialogue(it.npc) }
        onOpNpc1(npcType(Tock2opsNpc)) { tockDialogue(it.npc) }
        onOpNpc1(npcType(TockThroneNpc)) { tockDialogue(it.npc) }
        onOpNpc1(npcType(Colin2opsNpc)) { colinFerryDialogue(it.npc) }
        onOpNpc1(npcType(NavigatorNpc)) { ithoiDialogue(it.npc) }
        onOpNpc1(npcType(ColinNpc)) { colinDialogue(it.npc) }
        onOpNpc1(npcType(CookNpc)) { gnocciDialogue(it.npc) }
        onOpNpc1(npcType(ThiefNpc)) { arsenDialogue(it.npc) }
        onOpNpc1(npcType(TessNpc)) { tessDialogue(it.npc) }
        onOpNpc1(npcType(YusufTalkNpc)) { yusufDialogue(it.npc) }
        onOpNpc1(npcType(YusufBankNpc)) { yusufDialogue(it.npc) }

        onOpNpc3(Tock2opsNpc) { ferryTravel(it.npc) }
        onOpNpc3(Colin2opsNpc) { ferryTravel(it.npc) }
        onOpNpc3(YusufBankNpc) { player.openBank(eventBus) }
        onOpNpc4(YusufBankNpc) { player.openBank(eventBus) }

        onOpLoc1(TelescopeLoc) { observeTelescope() }
        for (sand in SandLocs) {
            onOpLocU(sand, Spade) { digSand() }
        }
        for (driftwood in DriftwoodLocs) {
            onOpLocU(driftwood, Tinderbox) { lightDriftwood() }
        }
        onOpLoc1(DungeonEntranceLoc) { enterDungeon() }
        onOpLoc1(DungeonExitLoc) { exitDungeon() }

        onPlayerCoordsChanged { trySpawnCombatIthoi(player) }

        onNpcQueue(ithoiCombatType, "queue.death") { onIthoiDeath() }
    }

    // ------------------------------------------------------------- The ferries

    private suspend fun ProtectedAccess.ferryTravel(npc: Npc) {
        val toCove = npc.isType(FerryRimmingtonParent)
        if (toCove) {
            mes("The ship sails to Corsair Cove...")
            teleport(CoveArrival)
            if (player.ccStage() in StageAccepted until StageAtCove) {
                player.setProgress(StageAtCove)
            }
        } else {
            mes("The ship sails to Rimmington...")
            teleport(RimmingtonArrival)
        }
    }

    // ------------------------------------------------------------ The telescope

    private suspend fun ProtectedAccess.observeTelescope() {
        arriveDelay()
        if (player.ccStage() >= StageMetIthoi && player.colinBits == COLIN_HEARD) {
            startDialogue {
                chatPlayer(quiz, "Let's see if I can find that mermaid he saw...")
            }
            mes("An ogre approaches the telescope's view.")
            mes("That doesn't look anything like a mermaid. That's an ogre!")
            player.colinBits = COLIN_SAW_OGRE
        } else if (player.colinBits >= COLIN_SAW_OGRE) {
            mes("I've seen enough of that ogre already.")
        } else {
            mes(
                "You don't feel you need to see the sea any closer up. " +
                    "It's right in front of you already."
            )
        }
    }

    // ------------------------------------------------------------------ The sand

    private suspend fun ProtectedAccess.digSand() {
        arriveDelay()
        if (player.cookBits != COOK_HEARD || player.ccStage() < StageMetIthoi) {
            mes("You dig in the sand but find nothing.")
            return
        }
        var search = false
        startDialogue {
            chatPlayer(quiz, "Okay, he said he buried the possessed doll in the sand...")
            search =
                choice2(
                    "Search for the possessed doll and face the consequences.",
                    true,
                    "Leave the thing alone.",
                    false,
                    title = "What will you do?",
                )
        }
        if (!search) {
            mes("Actually, it sounds scary.")
            return
        }
        anim(DigAnim)
        delay(2)
        player.cookBits = COOK_DUG
        mes("That's not possessed. That sounds more like clockwork.")
    }

    // -------------------------------------------------------------- The driftwood

    private suspend fun ProtectedAccess.lightDriftwood() {
        arriveDelay()
        if (!player.proveReady || player.ccStage() >= StageBurning) {
            startDialogue {
                chatPlayer(
                    quiz,
                    "Maybe I should try to get more information out of him before " +
                        "trying to make his hut catch fire.",
                )
            }
            return
        }
        player.setProgress(StageBurning)
        anim(LightFireAnim)
        delay(2)
        mes("You light the driftwood...")
        val ithoi = spawnNpc(NavigatorRunningNpc, IthoiRunSpot)
        ithoi.say("Arrr... uh-oh!")
        delay(2)
        mes("Ithoi rushes out of the hut.")
        ithoi.say("Arrr!")
        delay(2)
        mes("Ithoi casts Water Strike, extinguishing the fire, and returns to his hut.")
        npcRepo.del(ithoi, Int.MAX_VALUE)
        player.setProgress(StageExposed)
        delay(1)
        startDialogue {
            chatPlayer(
                quiz,
                "Well, I guess that proves he's well enough to get up! And " +
                    "apparently he knows some magic too.",
            )
        }
    }

    // ---------------------------------------------------------------- The dungeon

    private suspend fun ProtectedAccess.enterDungeon() {
        arriveDelay()
        teleport(DungeonInside)
    }

    private suspend fun ProtectedAccess.exitDungeon() {
        arriveDelay()
        teleport(DungeonOutside)
    }

    // ------------------------------------------------------------- Combat Ithoi

    private fun trySpawnCombatIthoi(player: Player) {
        if (player.ccStage() != StageKillOrder || !player.killOrdered || player.ithoiDead) {
            return
        }
        val c = player.coords
        if (c.level != 1 || c.x !in IthoiHutX || c.z !in IthoiHutZ) {
            return
        }
        val existing =
            npcRepo.findAll(ZoneKey.from(IthoiFightSpot), zoneRadius = 1).firstOrNull {
                it.isType(NavigatorCombatNpc)
            }
        if (existing != null) {
            return
        }
        protectedAccessLauncher.launch(player) {
            val ithoi = Npc(ithoiCombatType, IthoiFightSpot)
            npcRepo.add(ithoi, IthoiLifespan)
            ithoi.say("Ye'll pay!")
            ithoi.queueCombatRetaliate(player)
        }
    }

    @OptIn(InternalApi::class)
    private suspend fun StandardNpcAccess.onIthoiDeath() {
        val player = npc.findHero(playerList) ?: return
        protectedAccessLauncher.launchLenient(player) {
            npc.say("Awww!")
            player.ithoiDead = true
            mes("Ithoi the Navigator is dead.")
        }
    }

    private fun spawnNpc(internal: String, coords: CoordGrid): Npc {
        val npc = Npc(npcType(internal), coords)
        npcRepo.add(npc, IthoiLifespan)
        return npc
    }

    // ------------------------------------------------------------------ Journal

    override fun subTitle(): String =
        "by talking to <col=800000>Captain Tock<col=000080> at the crossroads " +
            "north of <col=800000>Port Sarim<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = player.player
            if (p.ccStage() >= StageAccepted) {
                line(
                    "<str>Captain Tock asked me to help lift the curse that has " +
                        "<str>afflicted his crew in Corsair Cove.|"
                )
            }
            when {
                p.ccStage() < StageAtCove ->
                    line(
                        "<col=000080>I should meet <red>Captain Tock<col=000080> on " +
                            "board his ship moored west of <red>Rimmington<col=000080>."
                    )
                p.ccStage() < StageMetIthoi ->
                    line(
                        "<col=000080>I should talk to <red>Ithoi the Navigator" +
                            "<col=000080> and the other sick Corsairs in " +
                            "<red>Corsair Cove<col=000080>."
                    )
                p.ccStage() < StageRuledOut ->
                    line(
                        "<col=000080>I should investigate each Corsair's theory " +
                            "about the curse and disprove it."
                    )
                p.ccStage() < StageConfessed ->
                    line(
                        "<col=000080>I ruled out the crew's theories. I should keep " +
                            "questioning them about the night they fell ill."
                    )
                p.ccStage() < StageKillOrder ->
                    line(
                        "<col=000080><red>Ithoi<col=000080> confessed to poisoning " +
                            "the crew and faking the curse. I must prove he isn't " +
                            "really sick."
                    )
                !p.ithoiDead ->
                    line(
                        "<col=000080><red>Captain Tock<col=000080> ordered me to " +
                            "kill <red>Ithoi<col=000080> for poisoning the crew."
                    )
                else ->
                    line(
                        "<col=000080>I killed <red>Ithoi<col=000080>. I should " +
                            "report back to <red>Captain Tock<col=000080>."
                    )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "<str>Captain Tock asked me to lift the curse afflicting his crew " +
                    "<str>in Corsair Cove.|"
            )
            line(
                "<str>I disproved the crew's theories and discovered Ithoi the " +
                    "<str>Navigator had poisoned them all and faked the curse.|"
            )
            line("<str>I exposed his lie and slew him. The crew has recovered.|")
        }

    companion object {
        const val KEY = "quest_corsaircurse"
        private const val QUEST_VARP = "varp.corscurs"
        private const val QUEST_VARBIT = "varbit.corscurs_progress"

        const val StageAccepted = 10
        const val StageAtCove = 15
        const val StageMetIthoi = 20
        const val StageRuledOut = 45
        const val StageEvidence = 30
        const val StageConfessed = 48
        const val StageExposed = 51
        const val StageKillOrder = 57
        const val StageBurning = 50
        const val ProgressComplete = 60

        private const val COLIN_HEARD = 1
        private const val COLIN_SAW_OGRE = 2
        private const val COOK_HEARD = 1
        private const val COOK_DUG = 2

        private const val Tock1opNpc = "npc.corsair_captain_1op"
        private const val Tock2opsNpc = "npc.corsair_captain_2ops"
        private const val TockThroneNpc = "npc.corsair_captain_throne_child"
        private const val NavigatorNpc = "npc.corscurs_navigator"
        private const val NavigatorRunningNpc = "npc.corscurs_navigator_running"
        private const val NavigatorCombatNpc = "npc.corscurs_navigator_combat"
        private const val ColinNpc = "npc.corscurs_cabinboy"
        private const val Colin2opsNpc = "npc.corscurs_cabinboy_2ops"
        private const val CookNpc = "npc.corscurs_cook"
        private const val ThiefNpc = "npc.corscurs_thief"
        private const val TessNpc = "npc.ogress_tess"
        private const val YusufTalkNpc = "npc.corscurs_banker_1op"
        private const val YusufBankNpc = "npc.corscurs_banker_allops"
        private const val FerryRimmingtonParent = "npc.corsair_ferry_rimmington"

        private const val TelescopeLoc = "loc.corscurs_telescope"
        private const val DungeonEntranceLoc = "loc.ds2_ogre_corsair_vine_ladder_entrance"
        private const val DungeonExitLoc = "loc.ds2_ogre_corsair_vine_ladder"

        private const val Spade = "obj.spade"
        private const val Tinderbox = "obj.tinderbox"

        private const val DigAnim = "seq.human_dig"
        private const val LightFireAnim = "seq.human_createfire"

        private val SandLocs =
            listOf(
                "loc.corscurs_sand_medium",
                "loc.corscurs_sand_small",
                "loc.corscurs_sand_multi",
            )

        private val DriftwoodLocs =
            listOf(
                "loc.corscurs_driftwood_dry",
                "loc.corscurs_driftwood_burning",
                "loc.corscurs_driftwood_damp",
                "loc.corscurs_driftwood_multi",
            )

        val CoveArrival = CoordGrid(2575, 2836, 1)
        val RimmingtonArrival = CoordGrid(2910, 3225, 0)

        private val IthoiRunSpot = CoordGrid(2529, 2835, 0)
        private val IthoiFightSpot = CoordGrid(2529, 2838, 1)
        private val IthoiHutX = 2525..2535
        private val IthoiHutZ = 2832..2845
        private const val IthoiLifespan = 2000

        private val DungeonInside = CoordGrid(2008, 9007, 1)
        private val DungeonOutside = CoordGrid(2482, 2851, 0)

        private const val FiremakingXpReward = 1200.0

        private fun npcType(internal: String) =
            requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }

        private val ithoiCombatType by lazy { npcType(NavigatorCombatNpc) }
    }
}
