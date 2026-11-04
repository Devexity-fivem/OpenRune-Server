package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.INCANTATION_WORDS
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.KEYS
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.KeyDrain
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.KeyRovin
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.Silverlight
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.arisDialogue
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.caseOpen
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.drainKeyVar
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.dsStage
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.ensureIncantation
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.hasSilverlight
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.incantationSlot
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.prysinDialogue
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.rovinDialogue
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerDialogue.seenSummoning
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class DemonSlayer
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val playerList: PlayerList,
    private val protectedAccessLauncher: ProtectedAccessLauncher,
) :
    QuestScript(
        KEY,
        QUEST_VARBIT,
        rewards {},
        ItemRewardDisplay("obj.silverlight", zoom = 250),
        questVarbit = QUEST_VARBIT,
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Demon Slayer end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the finale writes $StageComplete."
        }

        onOpNpc1(npcType(ArisNpc)) { arisDialogue(it.npc) }
        onOpNpc1(npcType(SirPrysinNpc)) { prysinDialogue(it.npc) }
        onOpNpc1(npcType(SirPrysinSilverlightNpc)) { prysinDialogue(it.npc) }
        onOpNpc1(npcType(CaptainRovinNpc)) { rovinDialogue(it.npc) }

        onOpLocU(DrainLoc) { bucketOnDrain(it.objType) }
        onOpLoc1(DrainLoc) { searchDrain() }
        onOpLoc1(DrainKeyLoc) { searchDrain() }
        onOpLoc1(DrainNoKeyLoc) { searchDrain() }
        onOpLoc1(SewerKeyLoc) { takeSewerKey() }
        onOpLoc1(SewerKeyPipeLoc) { takeSewerKey() }
        onOpLoc1(SewerKeyMudLoc) { takeSewerKey() }
        onOpLoc1(SewerKeyMudOnlyLoc) { mudOnly() }
        onOpLoc1(SilverlightCaseLoc) { searchCase() }

        onPlayerCoordsChanged {
            val p = player
            if (p.dsStage() in StageHasSilverlight until StageComplete &&
                p.coords.level == 0 &&
                p.coords.x in StoneCircleX &&
                p.coords.z in StoneCircleZ
            ) {
                protectedAccessLauncher.launch(p) { stoneCircleEntry() }
            }
        }

        onNpcHit(delrithType) { onDelrithHit() }

        onOpNpc1(DelrithWeakenedNpc) { banish(it.npc) }
        onOpNpc2(DelrithWeakenedNpc) { banish(it.npc) }
    }

    private fun randomizeIncantation(player: Player) = player.ensureIncantation()

    // ------------------------------------------------------------- The drain

    private suspend fun ProtectedAccess.bucketOnDrain(usedType: ItemServerType) {
        arriveDelay()
        if (!usedType.isType(BucketOfWater)) {
            return
        }
        if (player.dsStage() < StageStarted) {
            mes("You see no reason to pour water down this drain.")
            return
        }
        if (player.drainKeyVar != DrainKeyInDrain) {
            mes("You pour the water down the drain.")
            invDel(inv, BucketOfWater, count = 1)
            invAdd(inv, Bucket)
            return
        }
        anim("seq.human_pour_pot")
        invDel(inv, BucketOfWater, count = 1)
        invAdd(inv, Bucket)
        mes("You pour the bucket of water down the drain.")
        player.drainKeyVar = DrainKeyInSewer
        mes("You hear something metallic being washed into the sewers below.")
    }

    private suspend fun ProtectedAccess.searchDrain() {
        arriveDelay()
        if (player.drainKeyVar == DrainKeyInDrain && player.dsStage() >= StageStarted) {
            mes("You can see a key lying just out of reach inside the drain.")
        } else {
            mes("You search the drain but find nothing of interest.")
        }
    }

    private suspend fun ProtectedAccess.takeSewerKey() {
        arriveDelay()
        if (player.drainKeyVar != DrainKeyInSewer || player.dsStage() < StageStarted) {
            mes("You find nothing but mud.")
            return
        }
        player.drainKeyVar = DrainKeyTaken
        mes("You reach into the sludge and pull out a rusty key.")
        invAdd(inv, KeyDrain)
    }

    private suspend fun ProtectedAccess.mudOnly() {
        arriveDelay()
        mes("You find nothing but mud.")
    }

    private suspend fun ProtectedAccess.searchCase() {
        arriveDelay()
        if (player.caseOpen) {
            mes("The Silverlight case stands open and empty.")
        } else {
            mes("The sword case is locked tight. It looks like it needs three keys.")
        }
    }

    // -------------------------------------------------------- The stone circle

    private suspend fun ProtectedAccess.stoneCircleEntry() {
        if (player.dsStage() < StageHasSilverlight || player.dsStage() >= StageComplete) {
            return
        }
        if (player.coords.level != 0 ||
            player.coords.x !in StoneCircleX ||
            player.coords.z !in StoneCircleZ
        ) {
            return
        }
        randomizeIncantation(player)
        if (findDelrith() != null) {
            return
        }
        if (player.seenSummoning) {
            spawnDelrith().queueCombatRetaliate(player)
            return
        }
        summonScene()
    }

    private suspend fun ProtectedAccess.summonScene() {
        player.seenSummoning = true
        midiJingle("jingle.delrith_summoning")
        val denath = spawnNpc(DenathNpc, DenathSpot)
        denath.faceSquare(StoneCircleCenter)
        mes("A group of dark wizards are chanting around the stone circle...")
        for (i in WizardSpots.indices) {
            val wizard =
                npcRepo.findAll(ZoneKey.from(WizardSpots[i]), zoneRadius = 1).firstOrNull {
                    it.isType(WizardNpcs[i])
                }
            wizard?.anim(DarkWizardChantAnim)
            wizard?.faceSquare(StoneCircleCenter)
        }
        delay(2)
        denath.anim(DarkWizardChantAnim)
        denath.say(SummoningChant)
        delay(4)
        denath.say("Bear witness as the powerful demon Delrith is summoned forth!")
        delay(3)
        explodeStoneTable()
        delay(2)
        val delrith = spawnDelrith()
        delrith.anim(DelrithSummonedAnim)
        delrith.say("Raaaarrrgh!")
        npcRepo.del(denath, Int.MAX_VALUE)
        delay(2)
        delrith.queueCombatRetaliate(player)
    }

    private fun ProtectedAccess.explodeStoneTable() {
        val zone = ZoneKey.from(StoneCircleCenter)
        val table =
            locRepo.findAll(zone).firstOrNull { loc -> StoneTableTypes.any(loc::isType) }
        if (table != null) {
            locAnim(worldRepo, table, TableExplosionAnim)
        }
    }

    private fun spawnDelrith(): Npc {
        val delrith = Npc(delrithType, DelrithSpot)
        npcRepo.add(delrith, DelrithLifespan)
        return delrith
    }

    private fun spawnNpc(internal: String, coords: CoordGrid): Npc {
        val npc = Npc(npcType(internal), coords)
        npcRepo.add(npc, DelrithLifespan)
        return npc
    }

    private fun findDelrith(): Npc? =
        npcRepo.findAll(ZoneKey.from(DelrithSpot), zoneRadius = 1).firstOrNull {
            it.isType(DelrithNpc) || it.isVisType(DelrithWeakenedNpc)
        }

    // ---------------------------------------------------------------- Delrith

    private fun org.rsmod.api.npc.events.NpcHitEvents.Impact.onDelrithHit() {
        if (!hit.isFromPlayer || npc.hitpoints > DelrithWeakenHp) {
            return
        }
        val attacker = hit.resolvePlayerSource(playerList) ?: return
        if (!hasSilverlightEquipped(attacker)) {
            return
        }
        npc.hitpoints = 1
        npc.transmog(weakenedDelrithType, Int.MAX_VALUE)
        npc.say("Grrraaahh!")
        npc.anim(DelrithStruckDownAnim)
        protectedAccessLauncher.launch(attacker) {
            mes("The Silverlight seems to be weakening Delrith!")
            mes("Finish him off by banishing him!")
        }
    }

    private fun hasSilverlightEquipped(player: Player): Boolean =
        player.worn.count(Silverlight) > 0

    // ---------------------------------------------------------------- Banish

    private suspend fun ProtectedAccess.banish(delrith: Npc) {
        if (player.dsStage() != StageHasSilverlight) {
            return
        }
        if (!hasSilverlight()) {
            mes("You need Silverlight in your hands to banish Delrith.")
            return
        }
        if (!delrith.isVisType(DelrithWeakenedNpc)) {
            return
        }
        randomizeIncantation(player)
        val remaining = INCANTATION_WORDS.toMutableList()
        for (slot in 0..4) {
            val picked = pickIncantationWord(remaining)
            if (picked == null || INCANTATION_WORDS.indexOf(picked) != player.incantationSlot(slot)) {
                wrongIncantation(delrith)
                return
            }
            remaining.remove(picked)
        }
        banishDelrith(delrith)
    }

    private suspend fun ProtectedAccess.pickIncantationWord(remaining: List<String>): String? {
        var picked: String? = null
        startDialogue {
            val index =
                when (remaining.size) {
                    5 ->
                        choice5(
                            remaining[0], 0, remaining[1], 1, remaining[2], 2,
                            remaining[3], 3, remaining[4], 4, title = INCANTATION_TITLE,
                        )
                    4 ->
                        choice4(
                            remaining[0], 0, remaining[1], 1, remaining[2], 2,
                            remaining[3], 3, title = INCANTATION_TITLE,
                        )
                    3 ->
                        choice3(
                            remaining[0], 0, remaining[1], 1, remaining[2], 2,
                            title = INCANTATION_TITLE,
                        )
                    2 ->
                        choice2(remaining[0], 0, remaining[1], 1, title = INCANTATION_TITLE)
                    else -> -1
                }
            picked = if (index == -1) remaining.firstOrNull() else remaining.getOrNull(index)
        }
        return picked
    }

    private suspend fun ProtectedAccess.wrongIncantation(delrith: Npc) {
        mes("The words begin to fade from your memory... Delrith regains his strength!")
        delrith.say("Ha! Pathetic mortal!")
        delrith.anim(DelrithPortalFailedAnim)
        delrith.transmog(delrithType, Int.MAX_VALUE)
        delrith.hitpoints = delrith.baseHitpointsLvl
        delrith.queueCombatRetaliate(player)
    }

    private suspend fun ProtectedAccess.banishDelrith(delrith: Npc) {
        val incantation =
            (0..4).joinToString("... ") { INCANTATION_WORDS[player.incantationSlot(it)] }
        mes("You recite the incantation: $incantation!")
        delrith.say("Noooooo!")
        delrith.anim(DelrithBanishedAnim)
        delay(3)
        delrith.anim(DelrithWeakenedPortalAnim)
        delay(2)
        npcRepo.del(delrith, Int.MAX_VALUE)
        quest.setQuestStage(this, StageComplete)
    }

    override fun subTitle(): String =
        "by talking to <col=800000>Aris<col=000080> in <col=800000>Varrock<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = player.player
            if (p.dsStage() >= StageStarted) {
                line(
                    "<str>Aris the fortune teller has foreseen that I am destined to" +
                        "<str>slay the great demon Delrith, using the sword Silverlight.|"
                )
            }
            if (p.dsStage() == StageStarted) {
                line(
                    "<col=000080><red>Sir Prysin<col=000080> in <red>Varrock Palace" +
                        "<col=000080> has <red>Silverlight<col=000080>, but its case needs " +
                        "three keys."
                )
                if (invTotalSilverlight(player) <= 0) {
                    val missing = KEYS.filter { player.invTotal(player.inv, it) <= 0 }
                    if (missing.isNotEmpty()) {
                        line(
                            "<col=000080>I still need the key${if (missing.size > 1) "s" else ""}" +
                                " from: <red>${missing.joinToString("<col=000080>, <red>") { keyOwner(it) }}" +
                                "<col=000080>."
                        )
                    }
                }
            }
            if (p.dsStage() >= StageHasSilverlight) {
                line("<str>I was given the sword Silverlight by Sir Prysin.|")
            }
            if (p.dsStage() == StageHasSilverlight) {
                line(
                    "<col=000080>I must wield <red>Silverlight<col=000080> and go to the " +
                        "<red>stone circle<col=000080> south of <red>Varrock<col=000080> to " +
                        "slay <red>Delrith<col=000080>."
                )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "<str>Aris the fortune teller foresaw that I would slay the great demon" +
                    "<str>Delrith with the sword Silverlight.|"
            )
            line("<str>I gathered the three keys and claimed Silverlight from Sir Prysin.|")
            line("<str>I weakened Delrith with Silverlight and banished him forever.|")
        }

    private fun invTotalSilverlight(player: ProtectedAccess): Int =
        player.invTotal(player.inv, Silverlight)

    private fun keyOwner(key: String): String =
        when (key) {
            KeyDrain -> "the drain"
            KeyRovin -> "Captain Rovin"
            else -> "Wizard Traiborn"
        }

    companion object {
        const val KEY = "quest_demonslayer"
        private const val QUEST_VARBIT = "varbit.demonslayer_main"

        const val StageStarted = 1
        const val StageHasSilverlight = 2
        const val StageComplete = 3

        const val DrainKeyInDrain = 0
        const val DrainKeyInSewer = 1
        const val DrainKeyTaken = 2

        private const val ArisNpc = "npc.aris"
        private const val SirPrysinNpc = "npc.sir_prysin"
        private const val SirPrysinSilverlightNpc = "npc.sir_prysin_silverlight"
        private const val CaptainRovinNpc = "npc.captain_rovin"
        private const val DenathNpc = "npc.qip_ds_dark_wizard_denath"
        private const val DelrithNpc = "npc.delrith"
        private const val DelrithWeakenedNpc = "npc.delrith_weakened"

        private val WizardNpcs =
            listOf(
                "npc.qip_ds_young_dark_wizard1",
                "npc.qip_ds_young_dark_wizard2",
                "npc.qip_ds_young_dark_wizard3",
                "npc.qip_ds_young_dark_wizard4",
            )

        private const val DrainLoc = "loc.questdrain"
        private const val DrainKeyLoc = "loc.qip_ds_questdrain_key"
        private const val DrainNoKeyLoc = "loc.qip_ds_questdrain_nokey"
        private const val SewerKeyLoc = "loc.qip_ds_sewer_key"
        private const val SewerKeyPipeLoc = "loc.qip_ds_keypipe"
        private const val SewerKeyMudLoc = "loc.qip_ds_rustykey_mud"
        private const val SewerKeyMudOnlyLoc = "loc.qip_ds_rustykey_mudonly"
        private const val SilverlightCaseLoc = "loc.qip_ds_silverlight_case"

        private const val BucketOfWater = "obj.bucket_water"
        private const val Bucket = "obj.bucket_empty"

        private const val DarkWizardChantAnim = "seq.qip_ds_dark_wizard_chanting"
        private const val TableExplosionAnim = "seq.qip_ds_table_explosion"
        private const val DelrithSummonedAnim = "seq.qip_ds_delrith_summoned"
        private const val DelrithStruckDownAnim = "seq.qip_ds_delrith_struck_down"
        private const val DelrithBanishedAnim = "seq.qip_ds_delrith_banished"
        private const val DelrithPortalFailedAnim = "seq.qip_ds_delrith_portal_failed"
        private const val DelrithWeakenedPortalAnim = "seq.qip_ds_delrith_weakened_portal"
        private const val SummoningChant = "Arise, O mighty Delrith!"
        private const val INCANTATION_TITLE = "Recite the incantation..."

        private val StoneCircleX = 3220..3237
        private val StoneCircleZ = 3358..3380
        private val StoneCircleCenter = CoordGrid(3228, 3368, 0)
        private val DelrithSpot = CoordGrid(3228, 3368, 0)
        private val DenathSpot = CoordGrid(3228, 3364, 0)
        private val WizardSpots =
            listOf(
                CoordGrid(3224, 3364, 0),
                CoordGrid(3232, 3364, 0),
                CoordGrid(3224, 3372, 0),
                CoordGrid(3232, 3372, 0),
            )

        private const val DelrithWeakenHp = 5
        private const val DelrithLifespan = 5000

        private fun npcType(internal: String) =
            requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }

        private val delrithType by lazy { npcType(DelrithNpc) }
        private val weakenedDelrithType by lazy { npcType(DelrithWeakenedNpc) }

        private val StoneTableTypes by lazy {
            listOf(
                    "loc.qip_ds_stone_table",
                    "loc.qip_ds_stone_table_intact",
                    "loc.qip_ds_stone_table_summoning",
                )
                .mapNotNull { ServerCacheManager.getObject(it.asRSCM(RSCMType.LOC)) }
        }
    }
}
