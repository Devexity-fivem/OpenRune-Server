package org.rsmod.content.quest.area.varrock.shieldofarrav

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.baraekDialogue
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.cantEnter
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.charlieDialogue
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.curatorDialogue
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.hasAnywhere
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.katrineDialogue
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.missionReminder
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.roaldCertificate
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.roaldDialogue
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.stravenDialogue
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravDialogue.weaponsmasterDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.obj.Obj
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class ShieldOfArrav
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
) :
    QuestScript(
        KEY,
        "varp.scorpcatcher_secondary",
        rewards { item("obj.coins", CoinReward, label = "$CoinReward Coins") },
        ItemRewardDisplay("obj.arravshield1", zoom = 250),
        questVarbit = "varbit.shieldofarrav",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Shield of Arrav end state is ${quest.maxSteps} in the cache dbrow, " +
                "but completion writes $StageComplete."
        }

        onOpLoc1(QuestBookcase) { searchBookcase(it.loc) }
        onOpLoc1(PhoenixDoor) { phoenixDoor(it.loc) }
        onOpLoc1(PhoenixDoor2) { depotDoor(it.loc) }
        onOpLocU(phoenixDoor2Type, weaponKeyType) { unlockDepotDoor(it.loc) }
        onOpLoc1(PhoenixChest) { phoenixChest(it.loc) }
        onOpLoc1(BlackArmDoor) { blackArmDoor(it.loc) }
        onOpLoc1(BlackArmCupboard) { blackArmCupboard(it.loc) }

        onOpNpc1("npc.straven") { stravenDialogue(it.npc) }
        onOpNpcU(stravenType, reportType) { stravenDialogue(it.npc) }
        onOpNpc1("npc.katrine") { katrineDialogue(it.npc) }
        onOpNpc1("npc.baraek") { baraekDialogue(it.npc) }
        onOpNpc1("npc.tramppg") { charlieDialogue(it.npc) }
        onOpNpc1("npc.weaponsmaster") { weaponsmasterDialogue(it.npc) }
        onOpNpc1("npc.weaponsmaster_vis") { weaponsmasterDialogue(it.npc) }
        onOpNpc1("npc.curator") { curatorDialogue(it.npc) }
        onOpNpc1("npc.king_roald") { roaldDialogue(it.npc) }
        onOpNpcU(roaldType, certificateType) {
            startDialogue(it.npc) { roaldCertificate() }
        }
        onOpNpcU(roaldType, certLeftType) { startDialogue(it.npc) { roaldCertificate() } }
        onOpNpcU(roaldType, certRightType) { startDialogue(it.npc) { roaldCertificate() } }
        onOpNpc1("npc.jonny_the_beard") { jonnyTalk() }
        onOpNpc1("npc.jonny_the_beard_1op") { jonnyTalk() }

        onOpObj3(crossbowType) { takeCrossbow(it.obj) }

        onOpHeldU(CertLeft, CertRight) { combineCertificates() }
        onOpHeld1(CertLeft) { inspectCertificateHalf() }
        onOpHeld1(CertRight) { inspectCertificateHalf() }
        onOpHeld1(Certificate) { readCertificate() }
        onOpHeld1(ShieldLeft) { mes("Half of the Shield of Arrav.") }
        onOpHeld1(ShieldRight) { mes("Half of the Shield of Arrav.") }
        onOpHeld1(WeaponKey) { inspectWeaponKey() }
        onOpHeld3(Crossbow) { inspectCrossbow() }
        onOpHeld1(IntelReport) { readReport() }

        onOpPlayerU(weaponKeyType) { dropForPlayer(it.target, it.invSlot, it.objType) }
        onOpPlayerU(crossbowType) { dropForPlayer(it.target, it.invSlot, it.objType) }
        onOpPlayerU(certLeftType) { dropForPlayer(it.target, it.invSlot, it.objType) }
        onOpPlayerU(certRightType) { dropForPlayer(it.target, it.invSlot, it.objType) }
    }

    // ----------------------------------------------------------- The bookcase

    private suspend fun ProtectedAccess.searchBookcase(loc: BoundLocInfo) {
        arriveDelay()
        if (player.phoenixGang >= PhoenixSeekBook &&
            !player.hasAnywhere(ShieldOfArravBook) &&
            player.soaStage() < StageComplete
        ) {
            if (!inv.isFull()) {
                mes("You search the bookcase and find a book titled The Shield of Arrav.")
                startDialogue { chatPlayer(happy, "Aha! Exactly what I was looking for.") }
                invAddOrDrop(objRepo, ShieldOfArravBook)
                if (player.phoenixGang < PhoenixHasBook) {
                    player.phoenixGang = PhoenixHasBook
                }
            } else {
                mes("You don't have enough inventory space to hold that item.")
            }
            return
        }
        mes("A large collection of books.")
    }

    // --------------------------------------------------------- The gang doors

    private suspend fun ProtectedAccess.phoenixDoor(door: BoundLocInfo) {
        arriveDelay()
        val leaving = checkAxis(door)
        if (player.phoenixGang >= PhoenixJoined || leaving) {
            openHideoutDoor(door, leaving, "The door automatically opens for you.")
            return
        }
        val straven = stravenNear(door)
        if (straven != null) {
            when {
                player.blackArmGang >= BlackArmJoined ->
                    startDialogue(straven) {
                        chatNpc(angry, "Stay away from here! Black Arm dog!")
                    }
                player.phoenixGang == PhoenixStravenTask ->
                    startDialogue(straven) { missionReminder() }
                else -> startDialogue(straven) { cantEnter() }
            }
            return
        }
        mes("The door is securely locked.")
    }

    private fun stravenNear(door: BoundLocInfo): Npc? =
        npcRepo.findAll(ZoneKey.from(door.coords), zoneRadius = 1).firstOrNull {
            it.isType("npc.straven") &&
                it.coords.chebyshevDistance(door.coords) <= StravenDoorRange
        }

    private suspend fun ProtectedAccess.depotDoor(door: BoundLocInfo) {
        arriveDelay()
        if (checkAxis(door)) {
            openHideoutDoor(door, leaving = true, "You slip out through the door.")
            return
        }
        mes("The door is securely locked.")
    }

    private suspend fun ProtectedAccess.unlockDepotDoor(door: BoundLocInfo) {
        arriveDelay()
        if (checkAxis(door)) {
            openHideoutDoor(door, leaving = true, "You unlock the door.")
            return
        }
        openHideoutDoor(door, leaving = false, "You unlock the door.")
    }

    private suspend fun ProtectedAccess.blackArmDoor(door: BoundLocInfo) {
        arriveDelay()
        if (player.blackArmGang >= BlackArmJoined) {
            openHideoutDoor(
                door,
                checkAxis(door),
                "You hear heavy bolts being drawn back as the door is unlocked.",
            )
            return
        }
        mes("This door seems to be locked from the inside.")
    }

    // Mirrors LostCity's open_hideout_door: the wall loc swaps to inviswall for a
    // few ticks while a poordoor visual covers the swung-open doorway.
    private suspend fun ProtectedAccess.openHideoutDoor(
        door: BoundLocInfo,
        leaving: Boolean,
        message: String,
    ) {
        val openTile = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        soundSynth(DoorOpenSound)
        mes(message)
        if (leaving) {
            if (player.coords != door.coords) {
                teleport(door.coords)
                delay(1)
            }
            teleport(openTile)
        } else {
            teleport(door.coords)
        }
        locRepo.change(door, InvisibleWall, DoorOpenTicks)
        locRepo.add(openTile, PoorDoor, DoorOpenTicks, door.turnAngle(1), door.shape)
    }

    // check_axis: for north/south doors, standing on the door's z line means the
    // player is on the doorway axis (leaving); for west/east doors it's the x line.
    private fun ProtectedAccess.checkAxis(door: BoundLocInfo): Boolean =
        when (door.angle) {
            LocAngle.North, LocAngle.South -> player.coords.z == door.coords.z
            LocAngle.West, LocAngle.East -> player.coords.x == door.coords.x
        }

    // -------------------------------------------------- The shield half caches

    private suspend fun ProtectedAccess.phoenixChest(chest: BoundLocInfo) {
        arriveDelay()
        locRepo.change(chest, OpenPhoenixChest, ChestOpenTicks)
        mesbox("You search the chest.")
        if (player.soaStage() >= StageComplete ||
            player.hasAnywhere(ShieldLeft) ||
            player.hasAnywhere(Certificate)
        ) {
            mesbox("You open the chest, but don't find anything of interest inside.")
            return
        }
        if (!inv.isFull()) {
            mesbox("You open the chest and find half of a shield inside.")
            invAddOrDrop(objRepo, ShieldLeft)
        } else {
            mesbox(
                "You open the chest and find half of a shield inside, " +
                    "but you don't have enough room to take it."
            )
        }
    }

    private suspend fun ProtectedAccess.blackArmCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        locRepo.change(cupboard, OpenBlackArmCupboard, ChestOpenTicks)
        if (player.soaStage() >= StageComplete ||
            player.hasAnywhere(ShieldRight) ||
            player.hasAnywhere(Certificate)
        ) {
            mesbox("You open the cupboard, but don't find anything of interest inside.")
            return
        }
        if (!inv.isFull()) {
            mesbox("You open the cupboard and find half of a shield inside.")
            startDialogue {
                chatPlayer(
                    quiz,
                    "This must be the Shield of Arrav! Well... half of it at least. " +
                        "I wonder where the other half is...",
                )
            }
            invAddOrDrop(objRepo, ShieldRight)
        } else {
            mesbox(
                "You open the cupboard and find half of a shield inside, " +
                    "but you don't have enough room to take it."
            )
        }
    }

    // ---------------------------------------------------- The phoenix crossbow

    private suspend fun ProtectedAccess.takeCrossbow(obj: Obj) {
        val weaponsmaster =
            npcRepo.findAll(ZoneKey.from(player.coords), zoneRadius = 1).firstOrNull {
                it.isValidTarget() &&
                    it.coords.level == player.coords.level &&
                    it.coords.chebyshevDistance(player.coords) <= WeaponsmasterRange &&
                    (it.isType("npc.weaponsmaster") || it.isType("npc.weaponsmaster_vis"))
            }
        if (weaponsmaster != null) {
            arriveDelay()
            if (player.phoenixGang >= PhoenixJoined) {
                startDialogue(weaponsmaster) {
                    chatNpc(angry, "Hey! That's Straven's! He won't like you messing with that!")
                }
            } else {
                startDialogue(weaponsmaster) { chatNpc(angry, "Stop! Thief!") }
                weaponsmaster.queueCombatRetaliate(player)
            }
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        if (player.coords != obj.coords) {
            delay(1)
            anim("seq.human_pickuptable")
        }
        if (objRepo.del(obj)) {
            invAdd(inv, Crossbow, obj.count)
            soundSynth("synth.pick2")
        }
    }

    // -------------------------------------------------------- Item interactions

    private suspend fun ProtectedAccess.combineCertificates() {
        startDialogue {
            val combine =
                choice2(
                    "Yes.",
                    true,
                    "No.",
                    false,
                    title = "Combine the two half-certificates?",
                )
            if (combine) {
                access.invDel(access.inv, CertLeft, 1)
                access.invDel(access.inv, CertRight, 1)
                access.invAdd(access.inv, Certificate, 1)
                mesbox("You combine the two halves to make a complete certificate.")
            }
        }
    }

    private suspend fun ProtectedAccess.inspectCertificateHalf() {
        if (inv.contains(CertLeft) && inv.contains(CertRight)) {
            combineCertificates()
            return
        }
        mes(
            "It's half of a certificate for claiming a reward from King Roald. If you're " +
                "working with a player who's restricted from trading, you can still give " +
                "them this item by using it on them."
        )
    }

    private suspend fun ProtectedAccess.readCertificate() {
        mesbox(
            "The bearer of this certificate has brought both halves of the legendary " +
                "Shield of Arrav to me, Haig Halen, curator of the Varrock Museum. " +
                "I beseech ye to grant them the bounty that was advertised for its safe return."
        )
    }

    private suspend fun ProtectedAccess.inspectWeaponKey() {
        mes(
            "It's the key to the Phoenix Gang's weapons store. If you're working with a " +
                "player who's restricted from trading, you can still give them this item " +
                "by using it on them."
        )
    }

    private suspend fun ProtectedAccess.inspectCrossbow() {
        mes(
            "If you need to give this item to a player who's restricted from trading, " +
                "you can give them the item by using it on them."
        )
    }

    private suspend fun ProtectedAccess.readReport() {
        mesbox(
            "The intelligence report is too blurry to read. You need to take it to someone " +
                "who can decipher it."
        )
    }

    private suspend fun ProtectedAccess.dropForPlayer(
        target: Player,
        invSlot: Int,
        objType: ItemServerType,
    ) {
        val internal = RSCM.getReverseMapping(RSCMType.OBJ, objType.id)
        val name = ServerCacheManager.getItem(objType.id)?.name ?: return
        startDialogue {
            val drop =
                choice2(
                    "Yes.",
                    true,
                    "No.",
                    false,
                    title = "Drop your $name for ${target.displayName} to take?",
                )
            if (drop && access.invDel(access.inv, internal, 1, slot = invSlot).success) {
                objRepo.add(internal, player.coords, TradeableDropTicks, receiver = target)
                target.mes("${player.displayName} has dropped something for you to take.")
            }
        }
    }

    // ----------------------------------------------------------------- Jonny

    private suspend fun ProtectedAccess.jonnyTalk() {
        mes("Jonny the Beard is not interested in talking.")
    }

    // ----------------------------------------------------------------- Journal

    override fun subTitle(): String =
        "by speaking to <col=800000>Reldo<col=000080> in the " +
            "<col=800000>Varrock Palace Library<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val phoenix = player.player.phoenixGang
            val blackArm = player.player.blackArmGang
            when {
                phoenix < PhoenixHasBook -> {
                    line(
                        "<col=000080>I spoke to <red>Reldo<col=000080> in the " +
                            "<red>Varrock Palace Library<col=000080>. At his suggestion, I " +
                            "need to read a book called <red>The Shield of Arrav<col=000080> " +
                            "which is hidden somewhere in the library."
                    )
                }
                phoenix < PhoenixBookRead -> {
                    line(
                        "<str>I spoke to Reldo in the Varrock Palace Library. At his " +
                            "suggestion, I read a book called The Shield of Arrav. The book " +
                            "detailed the theft of the legendary Shield of Arrav and the " +
                            "reward on offer to anyone able to recover it.|"
                    )
                    line(
                        "<col=000080>I have found <red>The Shield of Arrav<col=000080> " +
                            "book. I should give it a read."
                    )
                }
                else -> {
                    bookReadLines()
                    if (phoenix < PhoenixLocationKnown && blackArm < BlackArmCharlieHint) {
                        line(
                            "<col=000080>I should speak to <red>Reldo<col=000080> to learn " +
                                "more about the gangs."
                        )
                    } else {
                        line(
                            "<str>I spoke to Reldo about the missing Shield of Arrav. He " +
                                "gave me details on the Phoenix Gang and the Black Arm Gang, " +
                                "the two groups suspected of being in possession of the Shield.|"
                        )
                        gangProgressLines(phoenix, blackArm)
                    }
                }
            }
        }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.gangProgressLines(
        phoenix: Int,
        blackArm: Int,
    ) {
        val joinedPhoenix = phoenix >= PhoenixJoined
        val joinedBlackArm = blackArm >= BlackArmJoined
        val shieldGiven = access.player.soaShieldGiven != 0
        when {
            shieldGiven -> {
                line(
                    "<str>I found half of the Shield of Arrav and took it to the Varrock " +
                        "Museum. Curator Haig Halen confirmed it to be genuine and gave me " +
                        "two Half-Certificates.|"
                )
                line(
                    "<col=000080>I need to swap one of my <red>Half-Certificates<col=000080> " +
                        "with a fellow adventurer who recovered the other Shield Half, then " +
                        "combine the halves and take the <red>Certificate<col=000080> to " +
                        "<red>King Roald<col=000080>."
                )
            }
            joinedPhoenix && !joinedBlackArm -> {
                phoenixMemberLines()
                if (access.inv.contains(ShieldLeft)) {
                    line(
                        "<col=000080>I should take this <red>Shield Half<col=000080> to the " +
                            "<red>Varrock Museum<col=000080> to see if it's genuine."
                    )
                } else {
                    line(
                        "<col=000080>I can now search the <red>Phoenix Gang Headquarters" +
                            "<col=000080> for the <red>Shield of Arrav<col=000080>."
                    )
                }
            }
            joinedBlackArm && !joinedPhoenix -> {
                blackArmMemberLines()
                if (access.inv.contains(ShieldRight)) {
                    line(
                        "<col=000080>I should take this <red>Shield Half<col=000080> to the " +
                            "<red>Varrock Museum<col=000080> to see if it's genuine."
                    )
                } else {
                    line(
                        "<col=000080>I can now search the <red>Black Arm Gang Headquarters" +
                            "<col=000080> for the <red>Shield of Arrav<col=000080>."
                    )
                }
            }
            else -> {
                if (phoenix in PhoenixLocationKnown until PhoenixJoined) {
                    line(
                        "<col=000080>I spoke to <red>Baraek<col=000080> in " +
                            "<red>Varrock Square<col=000080> about the <red>Phoenix Gang" +
                            "<col=000080>. He told me I'd find the Gang Headquarters in the " +
                            "<red>VTAM Building<col=000080> south east of the " +
                            "<red>Blue Moon Inn<col=000080>."
                    )
                }
                if (blackArm in BlackArmCharlieHint until BlackArmJoined) {
                    line(
                        "<col=000080>I spoke to <red>Katrine<col=000080> in the " +
                            "<red>Black Arm Gang Headquarters<col=000080> to the west of the " +
                            "<red>Varrock South Gate<col=000080>. She offered to let me join " +
                            "the Gang in return for stealing two <red>Phoenix Crossbows" +
                            "<col=000080> from the Phoenix Gang. She suggested I speak to " +
                            "<red>Charlie the Tramp<col=000080> about where to find them."
                    )
                } else if (blackArm == BlackArmCharlieHint) {
                    line(
                        "<col=000080>I spoke to <red>Charlie the Tramp<col=000080> near the " +
                            "<red>Varrock South Gate<col=000080> about the " +
                            "<red>Black Arm Gang<col=000080>. He directed me to speak to " +
                            "<red>Katrine<col=000080> in the Gang Headquarters to the west " +
                            "of the Varrock South Gate."
                    )
                }
            }
        }
    }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.phoenixMemberLines() {
        line(
            "<str>I spoke to Straven in the Phoenix Gang Headquarters south east of the " +
                "Blue Moon Inn. He offered to let me join the Gang in return for killing " +
                "Jonny the Beard and recovering an Intelligence Report from him.|"
        )
        line(
            "<str>I confronted and killed Jonny the Beard in the Blue Moon Inn and took an " +
                "Intelligence Report from him. I gave this Report to Straven and he allowed " +
                "me to join the Phoenix Gang.|"
        )
    }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.blackArmMemberLines() {
        line(
            "<str>I spoke to Katrine in the Black Arm Gang Headquarters to the west of the " +
                "Varrock South Gate. She offered to let me join the Gang in return for " +
                "stealing two Phoenix Crossbows from the Phoenix Gang.|"
        )
        line(
            "<str>With the help of a Fellow Adventurer, I was able to obtain two Phoenix " +
                "Crossbows. I gave them to Katrine and she allowed me to join the " +
                "Black Arm Gang.|"
        )
    }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.bookReadLines() {
        line(
            "<str>I spoke to Reldo in the Varrock Palace Library. At his suggestion, I " +
                "read a book called The Shield of Arrav. The book detailed the theft of the " +
                "legendary Shield of Arrav and the reward on offer to anyone able to " +
                "recover it.|"
        )
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            bookReadLines()
            line(
                "I spoke to Reldo about the missing Shield of Arrav. He gave me details " +
                    "on the Phoenix Gang and the Black Arm Gang, the two groups suspected " +
                    "of being in possession of the Shield.|"
            )
            if (player.player.phoenixGang >= PhoenixComplete) {
                phoenixMemberLines()
            } else {
                blackArmMemberLines()
            }
            line(
                "I found half of the Shield of Arrav and took it to the Varrock Museum " +
                    "where Curator Haig Halen confirmed it to be genuine. He gave me two " +
                    "Half-Certificates, which I combined into a Full Certificate with the " +
                    "help of a Fellow Adventurer.|"
            )
            line(
                "I took my Certificate to King Roald in Varrock Palace and he rewarded me " +
                    "for helping to return the Shield of Arrav.|"
            )
        }

    companion object {
        const val KEY = "quest_shieldofarrav"

        const val StageComplete = 2

        // varp.phoenixgang progression (matches LostCity values)
        const val PhoenixSeekBook = 1
        const val PhoenixHasBook = 2
        const val PhoenixBookRead = 3
        const val PhoenixLocationKnown = 4
        const val PhoenixStravenTask = 8
        const val PhoenixJoined = 9
        const val PhoenixComplete = 10

        // varp.blackarmgang progression (matches LostCity values)
        const val BlackArmCharlieHint = 1
        const val BlackArmKatrineTask = 2
        const val BlackArmJoined = 3
        const val BlackArmComplete = 4

        const val CoinReward = 600

        const val QuestBookcase = "loc.questbookcase"
        const val PhoenixDoor = "loc.phoenixdoor"
        const val PhoenixDoor2 = "loc.phoenixdoor2"
        const val BlackArmDoor = "loc.blackarmdoor"
        const val PhoenixChest = "loc.phoenixshutchest"
        const val OpenPhoenixChest = "loc.phoenixopenchest"
        const val BlackArmCupboard = "loc.blackarmcupboardshut"
        const val OpenBlackArmCupboard = "loc.blackarmcupboardopen"

        const val InvisibleWall = "loc.inviswall"
        const val PoorDoor = "loc.poordoor"
        const val DoorOpenSound = "synth.door_open"

        const val ShieldOfArravBook = "obj.the_shield_of_arrav"
        const val WeaponKey = "obj.phoenixkey2"
        const val IntelReport = "obj.intelligence_report"
        const val Crossbow = "obj.phoenix_crossbow"
        const val ShieldLeft = "obj.arravshield1"
        const val ShieldRight = "obj.arravshield2"
        const val CertLeft = "obj.arravcertificate_lft"
        const val CertRight = "obj.arravcertificate_rht"
        const val Certificate = "obj.arravcertificate"

        private const val DoorOpenTicks = 3
        private const val ChestOpenTicks = 100
        private const val StravenDoorRange = 8
        private const val WeaponsmasterRange = 10
        private const val TradeableDropTicks = 300

        private val phoenixDoor2Type =
            checkNotNull(ServerCacheManager.getObject(PhoenixDoor2.asRSCM(RSCMType.LOC)))
        private val weaponKeyType =
            checkNotNull(ServerCacheManager.getItem(WeaponKey.asRSCM(RSCMType.OBJ)))
        private val stravenType =
            checkNotNull(ServerCacheManager.getNpc("npc.straven".asRSCM(RSCMType.NPC)))
        private val reportType =
            checkNotNull(ServerCacheManager.getItem(IntelReport.asRSCM(RSCMType.OBJ)))
        private val roaldType =
            checkNotNull(ServerCacheManager.getNpc("npc.king_roald".asRSCM(RSCMType.NPC)))
        private val certificateType =
            checkNotNull(ServerCacheManager.getItem(Certificate.asRSCM(RSCMType.OBJ)))
        private val certLeftType =
            checkNotNull(ServerCacheManager.getItem(CertLeft.asRSCM(RSCMType.OBJ)))
        private val certRightType =
            checkNotNull(ServerCacheManager.getItem(CertRight.asRSCM(RSCMType.OBJ)))
        private val crossbowType =
            checkNotNull(ServerCacheManager.getItem(Crossbow.asRSCM(RSCMType.OBJ)))
    }
}
