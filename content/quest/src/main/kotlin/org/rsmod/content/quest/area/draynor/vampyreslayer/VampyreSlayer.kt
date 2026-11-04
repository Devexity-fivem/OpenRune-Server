package org.rsmod.content.quest.area.draynor.vampyreslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.cancelDying
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerDialogue.harlowBeerHandIn
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerDialogue.harlowDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class VampyreSlayer
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val protectedAccessLauncher: ProtectedAccessLauncher,
) :
    QuestScript(
        KEY,
        "varp.vampire",
        rewards { xp("stat.attack", AttackXpReward) },
        ItemRewardDisplay("obj.stake"),
    ) {

    private val countDraynorType: NpcServerType by lazy {
        checkNotNull(ServerCacheManager.getNpc(CountDraynorNpc.asRSCM(RSCMType.NPC))) {
            "No npc type '$CountDraynorNpc' resolved."
        }
    }

    override fun ScriptContext.init() {
        onOpNpc1("npc.dr_harlow") { harlowDialogue(it.npc) }
        onOpNpc1("npc.dr_harlow_vis") { harlowDialogue(it.npc) }
        onOpNpcU(npcType("npc.dr_harlow"), objType(Beer)) { harlowBeerHandIn(it.npc) }
        onOpNpcU(npcType("npc.dr_harlow_vis"), objType(Beer)) { harlowBeerHandIn(it.npc) }

        onOpLoc1(CoffinClosed) { openCoffin(it.loc) }
        onOpLoc1(CupboardShut) { openCupboard(it.loc) }
        onOpLoc1(CupboardOpen) { searchCupboard(it.loc) }
        onOpLoc2(CupboardOpen) { closeCupboard(it.loc) }

        onNpcQueue(countDraynorType, "queue.death") { onCountDraynorDeath() }
    }

    private suspend fun ProtectedAccess.openCoffin(loc: BoundLocInfo) {
        arriveDelay()
        locRepo.change(loc, CoffinOpen, CoffinOpenTicks)
        mes("You open the coffin.")

        if (!quest.isQuestInProgress(player) || findCountDraynor() != null) {
            return
        }

        // Count Draynor rises from his coffin a moment after it is opened.
        delay(CoffinSpawnDelay)
        if (findCountDraynor() != null) {
            return
        }

        val count = Npc(countDraynorType, DraynorSpawn)
        npcRepo.add(count, CountDraynorLifespan)
        if (invTotal(inv, Garlic) > 0) {
            count.hitpoints = (count.hitpoints - GarlicHpReduction).coerceAtLeast(1)
            count.baseHitpointsLvl -= GarlicHpReduction
            mes("The vampyre seems weaker.")
        }
        count.queueCombatRetaliate(player)
    }

    private fun findCountDraynor(): Npc? =
        npcRepo.findAll(ZoneKey.from(DraynorSpawn), zoneRadius = 1).firstOrNull {
            it.isType(CountDraynorNpc)
        }

    private suspend fun ProtectedAccess.openCupboard(loc: BoundLocInfo) {
        arriveDelay()
        locRepo.change(loc, CupboardOpen, Int.MAX_VALUE)
        mes("You open the cupboard.")
    }

    private suspend fun ProtectedAccess.searchCupboard(loc: BoundLocInfo) {
        arriveDelay()
        if (invAdd(inv, Garlic).success) {
            mes("You find some garlic in the cupboard.")
        }
    }

    private suspend fun ProtectedAccess.closeCupboard(loc: BoundLocInfo) {
        arriveDelay()
        locRepo.change(loc, CupboardShut, Int.MAX_VALUE)
        mes("You close the cupboard.")
    }

    @OptIn(InternalApi::class)
    private suspend fun StandardNpcAccess.onCountDraynorDeath() {
        val player = npc.findHero(playerList) ?: return
        protectedAccessLauncher.launchLenient(player) {
            val canFinish = invTotal(inv, Stake) > 0 && invTotal(inv, Hammer) > 0
            if (!canFinish) {
                npc.cancelDying()
                npc.hitpoints = npc.baseHitpointsLvl
                mes("The vampire seems to regenerate.")
                return@launchLenient
            }
            mes("You hammer the stake in to the vampyre's chest!")
            quest.completeQuest(this)
        }
    }

    override fun subTitle(): String =
        "by talking to <col=800000>Morgan<col=000080> in <col=800000>Draynor Village<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            line(
                "<red>Morgan</red> in <red>Draynor Village</red> told me that a vampyre " +
                    "living in the basement of <red>Draynor Manor</red> is terrorising the " +
                    "village. I have agreed to slay it."
            )
            when (quest.getQuestStage(player.player)) {
                StageSpokeToHarlow ->
                    line(
                        "<red>Dr Harlow</red> told me to drive a stake through the " +
                            "vampyre's heart with a hammer, and to bring <red>garlic</red> " +
                            "to weaken it. The vampyre sleeps in a coffin in the manor " +
                            "basement."
                    )
                else ->
                    line(
                        "Morgan said a retired vampyre hunter named <red>Dr Harlow</red> " +
                            "lives in the <red>Blue Moon Inn</red> in Varrock. He can teach " +
                            "me how to kill the fiend. There is garlic in the cupboard " +
                            "upstairs in Morgan's house."
                    )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "<red>Morgan</red> in <red>Draynor Village</red> told me that a vampyre " +
                    "living in <red>Draynor Manor</red> was terrorising the village."
            )
            addRawLine("")
            line(
                "With <red>Dr Harlow's</red> help I entered the vampyre's crypt, weakened " +
                    "it with <red>garlic</red>, and drove a <red>stake</red> through " +
                    "<red>Count Draynor's</red> heart. The village is safe."
            )
        }

    companion object {
        const val KEY = "quest_vampyreslayer"

        private const val CoffinClosed = "loc.vampcoffin"
        private const val CoffinOpen = "loc.vampcoffinopen"
        private const val CupboardShut = "loc.garliccupboardshut"
        private const val CupboardOpen = "loc.garliccupboardopen"

        private const val CountDraynorNpc = "npc.count_draynor"
        private const val Garlic = "obj.garlic"
        private const val Stake = "obj.stake"
        private const val Hammer = "obj.hammer"
        private const val Beer = "obj.beer"

        private const val AttackXpReward = 4825.0
        private const val GarlicHpReduction = 10
        private const val CoffinSpawnDelay = 4
        private const val CoffinOpenTicks = 500
        private const val CountDraynorLifespan = 500

        private const val StageSpokeToHarlow = 2

        private val DraynorSpawn = CoordGrid(3077, 9774, 0)

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
