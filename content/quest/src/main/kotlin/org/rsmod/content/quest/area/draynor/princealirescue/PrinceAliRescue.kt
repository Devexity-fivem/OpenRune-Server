package org.rsmod.content.quest.area.draynor.princealirescue

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.hassanDialogue
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.joeBeerHandIn
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.joeDialogue
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.keliDialogue
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.osmanDialogue
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.osmanItemUse
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.princeAliDialogue
import org.rsmod.content.quest.area.draynor.princealirescue.PrinceAliRescueDialogue.princeAliItems
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class PrinceAliRescue
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
) :
    QuestScript(
        KEY,
        "varp.princequest",
        rewards {
            item("obj.coins", 700, label = "700 Coins")
            extra("Free use of the Al Kharid toll gate")
        },
        ItemRewardDisplay("obj.coins", zoom = 250),
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Prince Ali Rescue end state is ${quest.maxSteps} in the cache dbrow, " +
                "but completion writes $StageComplete."
        }

        onOpNpc1("npc.hassan") { hassanDialogue(it.npc) }
        onOpNpc1("npc.osman") { osmanDialogue(it.npc) }
        onOpNpcU(osmanType, objType(KeyPrint)) { osmanItemUse(it.npc) }
        onOpNpcU(osmanType, objType(BronzeBar)) { osmanItemUse(it.npc) }

        onOpNpc1("npc.joe") { joeDialogue(it.npc) }
        onOpNpc1("npc.joe_vis") { joeDialogue(it.npc) }
        onOpNpcU(joeType, objType(Beer)) { joeBeerHandIn(it.npc) }
        onOpNpcU(joeVisType, objType(Beer)) { joeBeerHandIn(it.npc) }

        onOpNpc1("npc.lady_keli") { keliOp1(it.npc) }
        onOpNpc1("npc.lady_keli_vis") { keliOp1(it.npc) }
        onOpNpcU(keliType, objType(Rope)) { tieUpKeli(it.npc) }
        onOpNpcU(keliVisType, objType(Rope)) { tieUpKeli(it.npc) }

        onOpNpc1("npc.prince_ali_prison") { princeAliTalk(it.npc) }
        onOpNpc1("npc.prince_ali_vis_blackeye") { princeAliTalk(it.npc) }
        onOpNpc1("npc.prince_ali_palace") { princeAliTalk(it.npc) }
        onOpNpc1("npc.prince_ali_vis") { princeAliTalk(it.npc) }
        onOpNpcU(aliPrisonType, objType(PinkSkirt)) { princeAliItem(it.npc) }
        onOpNpcU(aliPrisonType, objType(BlondWig)) { princeAliItem(it.npc) }
        onOpNpcU(aliPrisonType, objType(SkinPaste)) { princeAliItem(it.npc) }
        onOpNpcU(aliBlackeyeType, objType(PinkSkirt)) { princeAliItem(it.npc) }
        onOpNpcU(aliBlackeyeType, objType(BlondWig)) { princeAliItem(it.npc) }
        onOpNpcU(aliBlackeyeType, objType(SkinPaste)) { princeAliItem(it.npc) }

        onOpHeldU(PlainWig, YellowDye) { dyeWig() }

        onOpLoc1(AliDoor) { openCellDoor(it.loc) }
        onOpLocU(AliDoor, PrincesKey) { unlockCellDoor(it.loc) }
    }

    // ------------------------------------------------------------- The cell door

    private suspend fun ProtectedAccess.openCellDoor(door: BoundLocInfo) {
        arriveDelay()
        if (player.coords.z > door.coords.z) {
            mes("The door is locked.")
            soundSynth(LockedSound)
            return
        }
        openPrisonGate(door, entering = true)
    }

    private suspend fun ProtectedAccess.unlockCellDoor(door: BoundLocInfo) {
        arriveDelay()
        if (player.coords.z <= door.coords.z) {
            mes(constants.dm_default)
            return
        }
        if (player.paStage() <= StageGuardDrunk || keliNearby(door)) {
            mes("You'd better get rid of Lady Keli before trying to go through there.")
            return
        }
        mes("You unlock the door.")
        openPrisonGate(door, entering = false)
    }

    private fun keliNearby(door: BoundLocInfo): Boolean =
        npcRepo.findAll(ZoneKey.from(door.coords), zoneRadius = 1).any {
            it.coords.chebyshevDistance(door.coords) <= KeliRange &&
                it.isVisType("npc.lady_keli_vis")
        }

    private suspend fun ProtectedAccess.openPrisonGate(
        door: BoundLocInfo,
        entering: Boolean,
    ) {
        soundSynth(GateOpenSound)
        val openTile = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        if (entering && player.coords != door.coords) {
            teleport(door.coords)
            delay(1)
        }
        teleport(if (entering) openTile else door.coords)
        locRepo.change(door, InvisibleWall, GateOpenTicks)
        locRepo.add(openTile, OpenPrisonGate, GateOpenTicks, door.turnAngle(1), door.shape)
    }

    // ------------------------------------------------------------ The blonde wig

    private suspend fun ProtectedAccess.dyeWig() {
        invDel(inv, YellowDye, 1)
        invDel(inv, PlainWig, 1)
        invAdd(inv, BlondWig, 1)
        mes("You dye the wig blonde.")
    }

    // ------------------------------------------------------------- Tying Keli

    private suspend fun ProtectedAccess.tieUpKeli(npc: Npc) {
        arriveDelay()
        val stage = player.paStage()
        if (stage < StageStarted) {
            mes(constants.dm_default)
            return
        }
        if (stage >= StageSaved) {
            mes("You have rescued the prince already, you cannot use the same plan again!")
            return
        }
        if (stage < StageGuardDrunk) {
            mes("You cannot tie Keli up until you have all equipment and disabled the guard!")
            return
        }
        mesbox("You overpower Keli, tie her up, and put her in a cupboard.")
        if (invDel(inv, Rope, 1).success) {
            removeKeli(npc.coords)
            quest.setQuestStage(this, StageTiedKeli)
        }
    }

    private fun removeKeli(coords: CoordGrid) {
        npcRepo.findAll(ZoneKey.from(coords), zoneRadius = 1)
            .filter { it.isType("npc.lady_keli") || it.isType("npc.lady_keli_vis") }
            .forEach { npcRepo.del(it, Int.MAX_VALUE) }
    }

    private suspend fun ProtectedAccess.keliOp1(npc: Npc) {
        if (player.paStage() >= StageTiedKeli) {
            keliTiedResponse(npc)
        } else {
            keliDialogue(npc)
        }
    }

    private suspend fun ProtectedAccess.keliTiedResponse(npc: Npc) {
        arriveDelay()
        npc.say("You tricked me, and tied me up, Guards kill this stranger!!")
        delay(1)
        val guard =
            npcRepo.findAll(ZoneKey.from(player.coords), zoneRadius = 1).firstOrNull {
                it.coords.chebyshevDistance(player.coords) <= JailguardRange &&
                    JailguardTypes.any(it::isType)
            }
        if (guard != null) {
            guard.say("Yes M'lady")
            guard.queueCombatRetaliate(player, delay = 0)
        }
    }

    // ------------------------------------------------------------------ Rescue

    private suspend fun ProtectedAccess.princeAliTalk(npc: Npc) {
        if (princeAliDialogue(npc)) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    private suspend fun ProtectedAccess.princeAliItem(npc: Npc) {
        if (princeAliItems(npc)) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    // ------------------------------------------------------------------ Journal

    override fun subTitle(): String =
        "by speaking to <col=800000>Hassan<col=000080> in " +
            "<col=800000>Al-Kharid Palace<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val stage = player.player.paStage()
            when {
                stage < StageSpokenOsman -> {
                    line(
                        "<str>I started this quest by speaking to Hassan in Al-Kharid|" +
                            "<str>Palace. He told me I should speak to Osman the spy master.|"
                    )
                    line(
                        "<col=000080>I should go and speak to <red>Osman<col=000080> " +
                            "for details on the quest."
                    )
                }
                stage < StagePrepFinished -> {
                    introLines()
                    line(
                        "<col=000080>Talk to <red>Leela<col=000080> near " +
                            "<red>Draynor Village<col=000080> for advice|"
                    )
                    itemLine(
                        PrincesKey,
                        "<str>I have a duplicate of the key with me|",
                        "<col=000080>Get a duplicate of the key that is imprisoning the prince|",
                    )
                    itemLine(
                        Rope,
                        "<str>I have some rope with me|",
                        "<col=000080>Get some rope to tie up the Princes' kidnapper|",
                    )
                    itemLine(
                        SkinPaste,
                        "<str>I have some skin paste with me|",
                        "<col=000080>Get something to colour the Princes' skin as disguise|",
                    )
                    itemLine(
                        PinkSkirt,
                        "<str>I have a skirt suitable for a disguise with me|",
                        "<col=000080>Get a skirt similar to his kidnapper as disguise|",
                    )
                    itemLine(
                        BlondWig,
                        "<str>I have a wig suitable for a disguise with me",
                        "<col=000080>Get a Wig to help disguise the prince",
                    )
                }
                stage < StageGuardDrunk -> {
                    introLines()
                    line(
                        "<col=000080>Before I can free <red>Prince Ali<col=000080>, I " +
                            "need to deal with the <red>Guard<col=000080>.|<red>Leela" +
                            "<col=000080> suggested I speak with the <red>Guard" +
                            "<col=000080> to try and determine any weaknesses he might have.|"
                    )
                }
                stage < StageTiedKeli -> {
                    introLines()
                    line(
                        "<str>I also had to prevent the Guard from seeing what I was up|" +
                            "<str>to, by getting him drunk.|"
                    )
                    line(
                        "<col=000080>The last thing I need to do is deal with " +
                            "<red>Lady Keli<col=000080>.|<red>Leela<col=000080> " +
                            "suggested I use some <red>Rope<col=000080> to tie her up.|"
                    )
                }
                stage < StageSaved -> {
                    introLines()
                    guardDoneLines()
                    line(
                        "<col=000080>I can now free <red>Prince Ali<col=000080>. I'll " +
                            "need to make sure I give him his disguise when I do.|"
                    )
                }
                else -> {
                    introLines()
                    guardDoneLines()
                    line(
                        "<str>I then used a wig, a skirt and some skin paste to make the|" +
                            "<str>prince look like Lady Keli so he could escape to his|" +
                            "<str>freedom with Leela after unlocking his cell door.|"
                    )
                    line(
                        "<col=000080>I should return to <red>Hassan<col=000080> to " +
                            "claim my reward.|"
                    )
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I started this quest by speaking to Hassan in Al-Kharid|" +
                    "Palace. He told me I should speak to Osman the spy master.|"
            )
            line("I should go and speak to Osman for details on the quest.|")
            line(
                "Prince Ali has been kidnapped but luckily the spy Leela|" +
                    "has found he is being held near draynor village. I will|" +
                    "need to disguise the Prince and tie up his captor to free|" +
                    "him from their clutches.|"
            )
            line(
                "I also had to prevent the Guard from seeing what I was up|" +
                    "to, by getting him drunk.|"
            )
            line(
                "With the guard disposed of, I used my rope to tie up Lady|" +
                    "Keli in a cupboard, so I could disguise the Prince.|"
            )
            line(
                "I then used a wig, a skirt and some skin paste to make the|" +
                    "prince look like Lady Keli so he could escape to his|" +
                    "freedom with Leela after unlocking his cell door.|"
            )
            line("I returned to Al-Kharid where Hassan rewarded me for|my work.|")
        }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.introLines() {
        line(
            "<str>Prince Ali has been kidnapped but luckily the spy Leela|" +
                "<str>has found he is being held near draynor village. I will|" +
                "<str>need to disguise the Prince and tie up his captor to free|" +
                "<str>him from their clutches. To do this I should:||"
        )
    }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.guardDoneLines() {
        line(
            "<str>I also had to prevent the Guard from seeing what I was up|" +
                "<str>to, by getting him drunk.|"
        )
        line(
            "<str>With the guard disposed of, I used my rope to tie up Lady|" +
                "<str>Keli in a cupboard, so I could disguise the Prince.|"
        )
    }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.itemLine(
        obj: String,
        have: String,
        missing: String,
    ) {
        if (access.inv.contains(obj)) {
            line(have)
        } else {
            line(missing)
        }
    }

    companion object {
        const val KEY = "quest_princealirescue"

        const val StageStarted = 10
        const val StageSpokenOsman = 20
        const val StagePrepFinished = 30
        const val StageGuardDrunk = 40
        const val StageTiedKeli = 50
        const val StageSaved = 100
        const val StageComplete = 110

        const val AliDoor = "loc.alidoor"
        const val OpenPrisonGate = "loc.poshdooropen"
        const val InvisibleWall = "loc.inviswall"

        const val PrincesKey = "obj.princeskey"
        const val PlainWig = "obj.plainwig"
        const val BlondWig = "obj.blondwig"
        const val YellowDye = "obj.yellowdye"
        const val PinkSkirt = "obj.pink_skirt"
        const val SkinPaste = "obj.skinpaste"
        const val Rope = "obj.rope"
        const val Beer = "obj.beer"
        const val KeyPrint = "obj.keyprint"
        const val BronzeBar = "obj.bronze_bar"

        const val LockedSound = "synth.locked"
        const val GateOpenSound = "synth.iron_door_open"

        private const val GateOpenTicks = 3
        private const val KeliRange = 10
        private const val JailguardRange = 7

        private val JailguardTypes =
            listOf(
                "npc.jailguard",
                "npc.jailguard_two_patrol",
                "npc.jailguard_three_patrol",
                "npc.jailguard_four_patrol",
            )

        private fun npcType(internal: String) =
            requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }

        private fun objType(internal: String) =
            requireNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
                "Missing obj type: $internal"
            }

        private val osmanType by lazy { npcType("npc.osman") }
        private val joeType by lazy { npcType("npc.joe") }
        private val joeVisType by lazy { npcType("npc.joe_vis") }
        private val keliType by lazy { npcType("npc.lady_keli") }
        private val keliVisType by lazy { npcType("npc.lady_keli_vis") }
        private val aliPrisonType by lazy { npcType("npc.prince_ali_prison") }
        private val aliBlackeyeType by lazy { npcType("npc.prince_ali_vis_blackeye") }
    }
}
