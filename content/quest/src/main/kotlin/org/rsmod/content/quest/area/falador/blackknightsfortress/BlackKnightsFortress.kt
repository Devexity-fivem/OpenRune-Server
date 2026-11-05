package org.rsmod.content.quest.area.falador.blackknightsfortress

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressDialogue.amikDialogue
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressDialogue.banquetGuard
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressDialogue.eavesdropDialogue
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressDialogue.entranceGuard
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class BlackKnightsFortress
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) :
    QuestScript(
        KEY,
        "varp.spy",
        rewards { item("obj.coins", CoinReward, label = "$CoinReward Coins") },
        ItemRewardDisplay("obj.iron_chainbody", zoom = 250),
        questVarbit = "varbit.blackknightsfortress_progress",
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Black Knights' Fortress end state is ${quest.maxSteps} in the cache dbrow, " +
                "but completion writes $StageComplete."
        }

        onOpNpc1("npc.sir_amik_varze") { amikDialogue(it.npc) }

        onOpLoc1(FrontDoor) { frontDoor(it.loc) }
        onOpLoc1(BanquetDoor) { banquetDoor(it.loc) }
        onOpLoc1(LadderDoor) { ladderDoor(it.loc) }
        onOpLoc1(SecretWall) { secretWall(it.loc) }
        onOpLoc1(Grill) { eavesdrop() }

        onOpLoc1("loc.inaclefayeunopenabledoorl") { mes("You can't open this door.") }
        onOpLoc1("loc.inaclefayeunopenabledoorr") { mes("You can't open this door.") }
        onOpLoc1(InactiveDoor) { mes("It's locked.") }

        onOpLocU(Hole, Cabbage) { holeCabbage(correct = true) }
        onOpLocU(Hole, DraynorCabbage) { holeCabbage(correct = false) }
    }

    // -------------------------------------------------------------- The doors

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo) {
        arriveDelay()
        val outside = checkAxis(door)
        if (outside && !player.isUniformed()) {
            val guard = guardNear(FrontDoorGuardAnchor, FrontDoorGuardRange)
            if (guard != null) {
                startDialogue(guard) { entranceGuard() }
            }
            return
        }
        openDoor(door)
    }

    private suspend fun ProtectedAccess.banquetDoor(door: BoundLocInfo) {
        arriveDelay()
        if (checkAxis(door)) {
            openDoor(door)
            return
        }
        val guard = guardNear(player.coords, BanquetGuardRange) ?: return
        var bargedIn = false
        startDialogue(guard) { bargedIn = banquetGuard() }
        if (bargedIn) {
            openDoor(door)
            aggroKnights(BanquetAggroAnchor)
        }
    }

    private suspend fun ProtectedAccess.ladderDoor(door: BoundLocInfo) {
        arriveDelay()
        if (!checkAxis(door)) {
            aggroKnights(player.coords)
        }
        openDoor(door)
    }

    private suspend fun ProtectedAccess.secretWall(wall: BoundLocInfo) {
        arriveDelay()
        mes("You push against the wall. You find a secret passage.")
        soundSynth("synth.coffin_open")
        val through = wall.coords.translate(wall.angle)
        if (checkAxis(wall)) {
            if (player.coords != wall.coords) {
                teleport(wall.coords)
                delay(1)
            }
            teleport(through)
        } else {
            teleport(wall.coords)
        }
        locRepo.change(wall, InvisibleWall, DoorOpenTicks)
    }

    // Swaps the door for its "inactive" open variant for the usual duration; the
    // player walks through while it is gone.
    private fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        val openTile = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        soundSynth(DoorOpenSound)
        locRepo.del(door, DoorOpenDuration)
        locRepo.add(openTile, InactiveDoor, DoorOpenDuration, door.turnAngle(1), door.shape)
    }

    // check_axis: for north/south doors, standing on the door's z line means the
    // player is on the doorway axis; for west/east doors it's the x line.
    private fun ProtectedAccess.checkAxis(door: BoundLocInfo): Boolean =
        when (door.angle) {
            LocAngle.North, LocAngle.South -> player.coords.z == door.coords.z
            LocAngle.West, LocAngle.East -> player.coords.x == door.coords.x
        }

    private fun CoordGrid.translate(angle: LocAngle): CoordGrid =
        when (angle) {
            LocAngle.North -> translateZ(1)
            LocAngle.East -> translateX(1)
            LocAngle.South -> translateZ(-1)
            LocAngle.West -> translateX(-1)
        }

    private fun Player.isUniformed(): Boolean =
        worn[Wearpos.Hat.slot]?.isType(BronzeMedHelm) == true &&
            worn[Wearpos.Torso.slot]?.isType(IronChainbody) == true

    private fun guardNear(anchor: CoordGrid, range: Int): Npc? =
        npcRepo.findAll(ZoneKey.from(anchor), zoneRadius = 1).firstOrNull {
            FortressGuards.any(it::isType) &&
                it.isValidTarget() &&
                it.coords.chebyshevDistance(anchor) <= range
        }

    // LC black_knights_aggro: the nearest knight yells, then every knight in
    // range joins the attack.
    private fun ProtectedAccess.aggroKnights(anchor: CoordGrid) {
        val knights =
            npcRepo.findAll(ZoneKey.from(anchor), zoneRadius = 1)
                .filter { AggressiveKnights.any(it::isType) }
                .filter { it.isValidTarget() }
                .filter { it.coords.chebyshevDistance(anchor) <= AggroRange }
                .toList()
        knights.firstOrNull()?.say("Die intruder!!!")
        knights.forEach { it.opPlayer2(player, aiPlayerInteractions) }
    }

    // --------------------------------------------------------- The grill room

    private suspend fun ProtectedAccess.eavesdrop() {
        arriveDelay()
        if (player.spyStage == StageAccepted) {
            startDialogue { eavesdropDialogue() }
            player.spyStage = StageOverheard
        } else {
            mes("I can't hear much right now.")
        }
    }

    private suspend fun ProtectedAccess.holeCabbage(correct: Boolean) {
        if (player.spyStage != StageOverheard) {
            startDialogue { chatPlayer(confused, "Why would I want to do that?") }
            return
        }
        if (!correct) {
            mes("This is the wrong sort of cabbage!")
            delay(2)
            mes("You are meant to be hindering the witch.")
            delay(2)
            mes("Not helping her.")
            delay(2)
            return
        }
        invDel(inv, Cabbage, 1)
        mes("You drop a cabbage down the hole.")
        delay(2)
        mes("The cabbage lands in the cauldron below.")
        delay(2)
        mes("The mixture in the cauldron starts to froth and bubble.")
        delay(2)
        mes("You hear the witch groan in dismay.")
        delay(2)
        player.spyStage = StageRuined
        startDialogue {
            chatPlayer(quiz, "Right I think that's successfully sabotaged the secret weapon.")
        }
    }

    // ---------------------------------------------------------------- Journal

    override fun subTitle(): String =
        "by speaking to <col=800000>Sir Amik Varze<col=000080> on the " +
            "<col=800000>second floor<col=000080> of <col=800000>White Knights' " +
            "Castle<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            when {
                player.player.spyStage < StageOverheard -> {
                    line(
                        "<col=000080>I am spying on the <red>Black Knights<col=000080> " +
                            "for <red>Sir Amik Varze<col=000080>.|"
                    )
                    line(
                        "<col=000080>I need to find out what their <red>secret weapon" +
                            "<col=000080> is.|"
                    )
                }
                player.player.spyStage < StageRuined -> {
                    line(
                        "<str>I am spying on the Black Knights for Sir Amik Varze.|" +
                            "<str>I needed to find out what their secret weapon is.|"
                    )
                    line(
                        "<col=000080>The Black Knights have an <red>invincibility potion" +
                            "<col=000080>.|<col=000080>Only a specially grown cabbage from " +
                            "<red>Draynor Manor<col=000080> will ruin it.|"
                    )
                }
                else -> {
                    line(
                        "<str>I am spying on the Black Knights for Sir Amik Varze.|" +
                            "<str>Their secret weapon was an invincibility potion.|"
                    )
                    line(
                        "<col=000080>I ruined the potion with a <red>Draynor Manor " +
                            "cabbage<col=000080>.|<col=000080>I should return to " +
                            "<red>Sir Amik Varze<col=000080> to claim my reward.|"
                    )
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I am spying on the Black Knights for Sir Amik Varze.|" +
                    "I needed to find out what their secret weapon is.|"
            )
            line(
                "The Black Knights had an invincibility potion.|" +
                    "Only a specially grown cabbage from Draynor Manor would ruin it.|"
            )
            line("I dropped the cabbage down the hole and ruined the potion.|")
        }

    companion object {
        const val KEY = "quest_blackknightsfortress"

        const val StageAccepted = 1
        const val StageOverheard = 2
        const val StageRuined = 3
        const val StageComplete = 4

        const val CoinReward = 2500

        const val FrontDoor = "loc.bkfortressdoor1"
        const val BanquetDoor = "loc.bkfortressdoor2"
        const val LadderDoor = "loc.bkfortressdoor3"
        const val InactiveDoor = "loc.inacbkfortressdoor"
        const val SecretWall = "loc.bksecretdoor"
        const val Grill = "loc.witchgrill"
        const val Hole = "loc.blackknighthole"
        const val InvisibleWall = "loc.inviswall"

        const val BronzeMedHelm = "obj.bronze_med_helm"
        const val IronChainbody = "obj.iron_chainbody"
        const val Cabbage = "obj.cabbage"
        const val DraynorCabbage = "obj.magic_cabbage"

        const val DoorOpenSound = "synth.door_open"

        private const val DoorOpenDuration = 500
        private const val DoorOpenTicks = 3
        private const val FrontDoorGuardRange = 5
        private const val BanquetGuardRange = 10
        private const val AggroRange = 5

        private val FrontDoorGuardAnchor = CoordGrid(3016, 3512, 0)
        private val BanquetAggroAnchor = CoordGrid(3025, 3515, 0)

        internal val FortressGuards =
            listOf(
                "npc.fortressguard",
                "npc.fortressguard_01",
                "npc.fortressguard_02",
                "npc.fortressguard_03",
                "npc.fortressguard_04",
            )

        private val AggressiveKnights =
            listOf("npc.aggressive_black_knight", "npc.aggressive_black_knight_f")
    }
}
