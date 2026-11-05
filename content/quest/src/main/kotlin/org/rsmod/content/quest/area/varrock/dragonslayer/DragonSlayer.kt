package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerDialogue.guildmasterDialogue
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerDialogue.jenkinsDialogue
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerDialogue.nedOnCrandor
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerDialogue.nedOnShip
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerDialogue.wormbrainDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class DragonSlayer
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
) :
    QuestScript(
        KEY,
        "varp.dragonquest",
        rewards {
            xp("stat.strength", StrengthXpReward)
            xp("stat.defence", DefenceXpReward)
            extra("Ability to wear Rune platebody.")
            extra("Ability to wear Green dragonhide body.")
        },
        ItemRewardDisplay("obj.dragon_slayer_qip_elvargs_head", zoom = 250),
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Dragon Slayer end state is ${quest.maxSteps} in the cache dbrow, " +
                "but completion writes $StageComplete."
        }

        // Melzar's Maze
        onOpLoc1("loc.melzardoor") { melzarDoor(it.loc) }
        onOpLocU("loc.melzardoor", "obj.melzarkey") { melzarDoorKey(it.loc) }
        for ((door, key) in MazeDoors) {
            onOpLoc1(door) { mes("This door is securely locked.") }
            onOpLocU(door, key) { mazeDoorKey(it.loc, key) }
        }
        onOpLoc1("loc.funexit") { funExit(it.loc) }
        onOpLoc1("loc.funladdertop") { funLadderTop(it.loc) }
        onOpLoc1("loc.funchestshut") { melzarChest(it.loc) }
        onOpLoc1("loc.funchestopen") { melzarChestOpen(it.loc) }
        onOpLoc2("loc.funchestopen") { closeChest(it.loc, "loc.funchestshut") }
        // The magic door under Ice Mountain (open/pass handled by DwarvenMineLocScript).
        onOpLocU("loc.dragon_slayer_qip_magic_door", "obj.silk") { magicDoorItem(it.loc, MagicDoorItem.Silk) }
        onOpLocU("loc.dragon_slayer_qip_magic_door", "obj.bowl_unfired") { magicDoorItem(it.loc, MagicDoorItem.Bowl) }
        onOpLocU("loc.dragon_slayer_qip_magic_door", "obj.lobster_pot") { magicDoorItem(it.loc, MagicDoorItem.LobsterPot) }
        onOpLocU("loc.dragon_slayer_qip_magic_door", "obj.wizards_mind_bomb") { magicDoorItem(it.loc, MagicDoorItem.MindBomb) }

        // The Lady Lumbridge.
        onOpLoc1("loc.dragonshipgangplank_on") { boardShip(it.loc) }
        onOpLoc1("loc.dragonshipgangplank_off") { disembark(it.loc) }
        onOpLoc1("loc.dragonshipladdertop") { shipLadderDown(it.loc) }
        onOpLoc1("loc.dragonshipladder") { shipLadderUp(it.loc) }
        onOpLoc1("loc.dragonslayer_shiphole") { repairShip(it.loc) }
        onOpLocU("loc.dragonslayer_shiphole", "obj.woodplank") { repairShip(it.loc) }

        // Crandor and Elvarg's lair.
        onOpLoc1("loc.dragonsecretdoor") { secretDoor(it.loc) }
        onOpLoc1("loc.dragon_slayer_qip_climbing_rope") { climbingRope(it.loc) }

        // The map pieces.
        onOpHeld1("obj.mappart1") { studyMapPart(MapPart.Melzar) }
        onOpHeld1("obj.mappart2") { studyMapPart(MapPart.Wormbrain) }
        onOpHeld1("obj.mappart3") { studyMapPart(MapPart.Thalzar) }
        onOpHeldU("obj.mappart1", "obj.mappart2") { combineMapParts() }
        onOpHeldU("obj.mappart1", "obj.mappart3") { combineMapParts() }
        onOpHeldU("obj.mappart2", "obj.mappart3") { combineMapParts() }
        onOpHeld1("obj.dragonmap") {
            mesbox(
                "This is a map that shows the route through the reefs to Crandor. " +
                    "You will need a ship to follow this route."
            )
        }

        onOpNpc1("npc.guildmaster") { guildmasterDialogue(it.npc) }
        onOpNpc1("npc.wormbrain") { wormbrainDialogue(it.npc) }
        onOpNpc1("npc.dragonslayer_ned_on_ship") { nedTalk(it.npc) }
        onOpNpc1("npc.dragonslayer_ned_on_crandor") { nedTalk(it.npc) }
        onOpNpc1("npc.cabin_boy_jenkins") { jenkinsDialogue(it.npc) }
    }

    // ---------------------------------------------------------- Melzar's Maze

    private suspend fun ProtectedAccess.melzarDoor(door: BoundLocInfo) {
        arriveDelay()
        soundSynth("synth.locked")
        mes("The door is securely locked.")
    }

    private suspend fun ProtectedAccess.melzarDoorKey(door: BoundLocInfo) {
        arriveDelay()
        openQuestDoor(door, "You unlock the door.")
    }

    private suspend fun ProtectedAccess.mazeDoorKey(door: BoundLocInfo, key: String) {
        arriveDelay()
        mes("The key disintegrates as it unlocks the door.")
        invDel(inv, key, 1)
        openQuestDoor(door)
    }

    private suspend fun ProtectedAccess.funExit(door: BoundLocInfo) {
        arriveDelay()
        if (!checkAxis(door)) {
            mes("The door is locked.")
            return
        }
        openQuestDoor(door)
    }

    private suspend fun ProtectedAccess.funLadderTop(ladder: BoundLocInfo) {
        arriveDelay()
        teleport(ladder.coords.translateLevel(-1))
    }

    // The chest at the top of the maze holding Melzar's map piece.
    private suspend fun ProtectedAccess.melzarChest(chest: BoundLocInfo) {
        arriveDelay()
        openChest(chest, "loc.funchestopen")
    }

    private suspend fun ProtectedAccess.melzarChestOpen(chest: BoundLocInfo) {
        arriveDelay()
        if (!player.gettingMapParts || player.hasAnywhere("obj.mappart1") || player.hasAnywhere("obj.dragonmap")) {
            mes("You find nothing in the chest.")
        } else {
            mesbox("You find a map piece in the chest.")
            invAddOrDrop(objRepo, "obj.mappart1")
        }
        locRepo.change(chest, "loc.funchestshut", ChestShutDuration)
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo, openLoc: String) {
        mes("You open the chest.")
        anim("seq.human_openchest")
        soundSynth("synth.chest_open")
        locRepo.change(chest, openLoc, ChestShutDuration)
    }

    private suspend fun ProtectedAccess.closeChest(chest: BoundLocInfo, shutLoc: String) {
        arriveDelay()
        mes("You close the chest.")
        anim("seq.human_closechest")
        soundSynth("synth.chest_close")
        locRepo.change(chest, shutLoc, ChestShutDuration)
    }

    // --------------------------------------------------------- The magic door

    private suspend fun ProtectedAccess.magicDoorItem(door: BoundLocInfo, item: MagicDoorItem) {
        arriveDelay()
        if (player.magicDoorOpen || item.used(player)) {
            mes("Nothing interesting happens.")
            return
        }
        item.markUsed(player)
        invDel(inv, item.obj, 1)
        mes(item.message)
        if (player.magicDoorOpen) {
            mes("You have unlocked the magic door.")
        }
    }

    // ------------------------------------------------------- The Lady Lumbridge

    private suspend fun ProtectedAccess.boardShip(plank: BoundLocInfo) {
        arriveDelay()
        if (player.dragonQuest < StageBoughtShip) {
            val klarense = npcNear(plank.coords, "npc.klarense", KlarenseRange)
            if (klarense != null) {
                startDialogue(klarense) {
                    chatNpc(neutral, "Hey, stay off my ship! That's private property!")
                }
            } else {
                mes("The gangplank leads onto a private ship.")
            }
            return
        }
        mes("You board the ship.")
        when (plank.angle) {
            LocAngle.West, LocAngle.East -> {
                teleport(plank.coords.translateX(-1))
                delay(1)
                telejump(plank.coords.translate(-2, 0, 1))
            }
            else -> {
                teleport(plank.coords.translateZ(1))
                delay(1)
                telejump(plank.coords.translate(0, 1, 1))
            }
        }
    }

    private suspend fun ProtectedAccess.disembark(plank: BoundLocInfo) {
        arriveDelay()
        mes("You disembark the ship.")
        when (plank.angle) {
            LocAngle.West, LocAngle.East -> {
                teleport(plank.coords.translateX(1))
                delay(1)
                telejump(plank.coords.translate(2, 0, -1))
            }
            else -> {
                teleport(plank.coords.translateZ(-1))
                delay(1)
                telejump(plank.coords.translate(0, -1, -1))
            }
        }
    }

    private suspend fun ProtectedAccess.shipLadderDown(ladder: BoundLocInfo) {
        arriveDelay()
        teleport(ShipHold)
    }

    private suspend fun ProtectedAccess.shipLadderUp(ladder: BoundLocInfo) {
        arriveDelay()
        teleport(ShipDeck)
    }

    private suspend fun ProtectedAccess.repairShip(hole: BoundLocInfo) {
        arriveDelay()
        if (player.dragonQuest < StageBoughtShip) {
            mesbox("You need to buy the ship first.")
            return
        }
        if (!player.inv.contains("obj.woodplank")) {
            mesbox("You'll need to use wooden planks on this hole to patch it up.")
            return
        }
        if (invTotal(inv, Nails) < NailsPerPlank) {
            mesbox("You need 30 steel nails to attach the plank with.")
            return
        }
        if (!player.inv.contains("obj.hammer")) {
            mesbox("You need a hammer to force the nails in with.")
            return
        }
        invDel(inv, "obj.woodplank", 1)
        invDel(inv, Nails, NailsPerPlank)
        when {
            !player.dsShipFixed1 -> {
                player.dsShipFixed1 = true
                mesbox(
                    "You nail a plank over the hole, but you still need more planks to " +
                        "close the hole completely."
                )
            }
            !player.dsShipFixed2 -> {
                player.dsShipFixed2 = true
                mesbox(
                    "You nail a plank over the hole, but you still need one more plank to " +
                        "close the hole completely."
                )
            }
            else -> {
                player.dsShipFixed3 = true
                if (player.dragonQuest < StageRepairedShip) {
                    player.dragonQuest = StageRepairedShip
                }
                mesbox(
                    "You nail a final plank over the hole. You have successfully patched " +
                        "the hole in the ship."
                )
            }
        }
    }

    // ---------------------------------------------------------- Crandor & lair

    private suspend fun ProtectedAccess.secretDoor(door: BoundLocInfo) {
        arriveDelay()
        val entering = checkAxis(door)
        if (!player.dsFoundSecretDoor) {
            if (entering || player.dragonQuest >= StageComplete) {
                mes("You found a secret door!")
                mes("You remember where the secret door is for future reference.")
                player.dsFoundSecretDoor = true
            } else {
                mes("Nothing interesting happens.")
                return
            }
        }
        openQuestDoor(door)
    }

    private suspend fun ProtectedAccess.climbingRope(rope: BoundLocInfo) {
        arriveDelay()
        teleport(rope.coords.translate(-3, 1))
    }

    // -------------------------------------------------------------- Map parts

    private suspend fun ProtectedAccess.studyMapPart(part: MapPart) {
        mesbox(part.studyText)
    }

    private suspend fun ProtectedAccess.combineMapParts() {
        if (player.inv.contains("obj.mappart1") &&
            player.inv.contains("obj.mappart2") &&
            player.inv.contains("obj.mappart3")
        ) {
            invDel(inv, "obj.mappart1", 1)
            invDel(inv, "obj.mappart2", 1)
            invDel(inv, "obj.mappart3", 1)
            invAddOrDrop(objRepo, "obj.dragonmap")
            mesbox(
                "You join the three pieces together and assemble a map that shows the route " +
                    "through the reefs to Crandor."
            )
        } else {
            mes("You still need one more piece of map.")
        }
    }

    // ------------------------------------------------------------------ Ned

    private suspend fun ProtectedAccess.nedTalk(npc: Npc) {
        if (npc.isType("npc.dragonslayer_ned_on_crandor")) {
            startDialogue(npc) { nedOnCrandor() }
        } else {
            startDialogue(npc) { nedOnShip() }
        }
    }

    // ---------------------------------------------------------------- Helpers

    private suspend fun ProtectedAccess.openQuestDoor(
        door: BoundLocInfo,
        message: String? = null,
    ) {
        soundSynth(DoorOpenSound)
        if (message != null) {
            mes(message)
        }
        if (checkAxis(door)) {
            val openTile = openTileFor(door)
            if (player.coords != door.coords) {
                teleport(door.coords)
                delay(1)
            }
            teleport(openTile)
            locRepo.change(door, "loc.inviswall", DoorOpenTicks)
        } else {
            teleport(door.coords)
            locRepo.change(door, "loc.inviswall", DoorOpenTicks)
        }
    }

    private fun openTileFor(door: BoundLocInfo): CoordGrid =
        when (door.angle) {
            LocAngle.West -> door.coords.translateX(-1)
            LocAngle.East -> door.coords.translateX(1)
            LocAngle.North -> door.coords.translateZ(1)
            LocAngle.South -> door.coords.translateZ(-1)
        }

    private fun ProtectedAccess.checkAxis(door: BoundLocInfo): Boolean =
        when (door.angle) {
            LocAngle.North, LocAngle.South -> player.coords.z == door.coords.z
            LocAngle.West, LocAngle.East -> player.coords.x == door.coords.x
        }

    private fun npcNear(coords: CoordGrid, type: String, range: Int): Npc? =
        npcRepo.findAll(ZoneKey.from(coords), zoneRadius = 1).firstOrNull {
            it.isType(type) && it.coords.chebyshevDistance(coords) <= range
        }

    // ----------------------------------------------------------------- Journal

    override fun subTitle(): String =
        "by speaking to the <col=800000>Guildmaster<col=000080> in the " +
            "<col=800000>Champions' Guild<col=000080>, south-west of Varrock."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val stage = player.player.dragonQuest
            when {
                stage < StageSpokenToOziach ->
                    line(
                        "<col=000080>The Guildmaster of the Champions' Guild said I should " +
                            "speak to <red>Oziach<col=000080>, who lives in a hut by the cliffs " +
                            "to the west of <red>Edgeville<col=000080>."
                    )
                stage < StageBoughtShip -> {
                    line(
                        "<str>The Guildmaster of the Champions' Guild said I should speak to " +
                            "Oziach, who lives by the cliffs west of Edgeville.|"
                    )
                    line(
                        "<col=000080>Oziach has asked me to slay <red>Elvarg<col=000080>, the " +
                            "dragon of <red>Crandor<col=000080>, to prove myself worthy of " +
                            "wearing <red>rune platemail<col=000080>."
                    )
                    if (player.player.dsChampionExplained) {
                        gatheringLines()
                    } else {
                        line(
                            "<col=000080>I should speak to the <red>Guildmaster<col=000080> " +
                                "for more information."
                        )
                    }
                }
                stage < StageSailed -> {
                    line(
                        "<str>The Guildmaster of the Champions' Guild said I should speak to " +
                            "Oziach, who lives by the cliffs west of Edgeville.|"
                    )
                    line(
                        "<str>Oziach has asked me to slay Elvarg, the dragon of Crandor, to " +
                            "prove myself worthy of wearing rune platemail.|"
                    )
                    gatheringLines()
                }
                stage < StageComplete -> {
                    line(
                        "<str>Oziach asked me to slay Elvarg, the dragon of Crandor.|"
                    )
                    line(
                        "<col=000080>I have slain <red>Elvarg<col=000080>! I should return to " +
                            "<red>Oziach<col=000080> to claim my reward."
                    )
                }
            }
        }

    private fun org.rsmod.content.quest.manager.QuestJournalBuilder.gatheringLines() {
        line(
            "<col=000080>To get to Crandor I will need a <red>map<col=000080> to navigate the " +
                "reefs, a <red>ship<col=000080>, and a <red>captain<col=000080>. I will also need " +
                "a <red>shield<col=000080> against the dragon's breath."
        )
    }

    override fun completedLog(player: ProtectedAccess): String =
        questJournal(player) {
            line(
                "<str>I spoke to the Guildmaster who directed me to Oziach. Oziach sent me " +
                    "to slay Elvarg, the dragon of Crandor.|"
            )
            line(
                "<str>I found the three pieces of the Crandor map, bought the Lady Lumbridge, " +
                    "and sailed with Captain Ned to Crandor where I slew Elvarg.|"
            )
            line(
                "<str>Oziach proclaimed me a true hero and now sells me rune platemail.|"
            )
            line("<col=000080>QUEST COMPLETE!")
        }

    companion object {
        const val KEY = "quest_dragonslayer1"

        const val StageNotStarted = 0
        const val StageSpokenToGuildmaster = 1
        const val StageSpokenToOziach = 2
        const val StageBoughtShip = 3
        const val StageRepairedShip = 6
        const val StageNedGivenMap = 7
        const val StageSailed = 8
        const val StageElvargSlain = 9
        const val StageComplete = 10

        const val StrengthXpReward = 186500.0
        const val DefenceXpReward = 186500.0
        const val ShipPrice = 2000
        const val WormbrainPrice = 10000
        const val NailsPerPlank = 30
        const val Nails = "obj.nails"

        const val DoorOpenSound = "synth.door_open"
        const val KlarenseRange = 5
        private const val DoorOpenTicks = 3
        private const val ChestShutDuration = 300

        val ShipDeck = CoordGrid(3047, 3207, 1)
        val ShipHold = CoordGrid(3047, 9640, 1)
        val CrandorBeach = CoordGrid(2851, 3235, 0)

        private val MazeDoors =
            mapOf(
                "loc.reddoor" to "obj.redkey",
                "loc.orangedoor" to "obj.orangekey",
                "loc.yellowdoor" to "obj.yellowkey",
                "loc.bluedoor" to "obj.bluekey",
                "loc.magentadoor" to "obj.magentakey",
                "loc.greendoor" to "obj.greenkey",
            )
    }

    internal enum class MagicDoorItem(
        val obj: String,
        val message: String,
        val used: (Player) -> Boolean,
        val markUsed: (Player) -> Unit,
    ) {
        Silk(
            "obj.silk",
            "You put the silk into the opening in the door.",
            { it.dsUsedSilk },
            { it.dsUsedSilk = true },
        ),
        Bowl(
            "obj.bowl_unfired",
            "You put the unfired bowl into the opening in the door.",
            { it.dsUsedBowl },
            { it.dsUsedBowl = true },
        ),
        LobsterPot(
            "obj.lobster_pot",
            "You put the Lobster pot into the opening in the door.",
            { it.dsUsedLobsterPot },
            { it.dsUsedLobsterPot = true },
        ),
        MindBomb(
            "obj.wizards_mind_bomb",
            "You pour the Wizard's Mind Bomb into the opening in the door.",
            { it.dsUsedMindBomb },
            { it.dsUsedMindBomb = true },
        ),
    }

    internal enum class MapPart(val studyText: String) {
        Melzar(
            "This is a piece of map that you found in Melzar's Maze. You will need to join " +
                "it to the other two map pieces before you can see the route to Crandor."
        ),
        Wormbrain(
            "This is a piece of map that you got from Wormbrain, the goblin thief. You will " +
                "need to join it to the other two map pieces before you can see the route " +
                "to Crandor."
        ),
        Thalzar(
            "This is a piece of map that you found in a secret chest in the Dwarven Mine. " +
                "You will need to join it to the other two map pieces before you can see " +
                "the route to Crandor."
        ),
    }
}
