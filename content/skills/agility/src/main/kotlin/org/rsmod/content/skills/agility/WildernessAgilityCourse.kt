package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.config.Constants.em_face_east
import org.rsmod.api.config.Constants.em_face_north
import org.rsmod.api.config.Constants.em_face_south
import org.rsmod.api.config.Constants.em_face_west
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onApLoc2
import org.rsmod.api.script.onApLoc3
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WildernessAgilityCourse @Inject constructor(
    private val random: GameRandom,
    private val hitProcessor: DamageOnlyPlayerHitProcessor,
    private val worldRepo: WorldRepository,
    private val locRepo: LocRepository,
) : PluginScript() {

    private var pipeUsedUntil = 0
    private var swingUsedUntil = 0

    override fun ScriptContext.startup() {
        onApLoc1("loc.balancegate52a") { apEnterRidge(it.loc) }
        onApLoc1("loc.balancegate52b_left") { apExitRidge(it.loc) }
        onApLoc1("loc.balancegate52b_right") { apExitRidge(it.loc) }
        onApLoc1("loc.obstical_pipe2") { apSqueezePipe(it.loc) }
        onApLoc1("loc.obstical_ropeswing2") { apSwing(it.loc) }
        onApLoc1("loc.steppingstone1") { apCrossStones(it.loc) }
        onApLoc1("loc.wilderness_log_balance1") { apCrossLog(it.loc) }
        onApLoc1("loc.wildclimbingrock") { apClimbRocks(it.loc) }
        onApLoc1("loc.wildy_agility_pillar") { apTagDispenser(it.loc) }
        onApLoc2("loc.wildy_agility_pillar") { apPayDispenser(it.loc) }
        onApLoc3("loc.wildy_agility_pillar") { apRedeemDispenser(it.loc) }
    }

    private suspend fun ProtectedAccess.apEnterRidge(door: BoundLocInfo) {
        if (!isWithinApRange(door, AP_RANGE)) return
        if (player.agilityLvl < RIDGE_LVL) {
            mes("You need an Agility level of $RIDGE_LVL to attempt balancing on the ridge.")
            return
        }
        spam("You go through the gate and try to edge over the ridge...")
        swingOpen(door, GATE_OPEN_TICKS)
        anim("seq.human_walk_logbalance")
        forceWalk(RIDGE_MID, em_face_north)
        if (failRoll(RIDGE_LOW, RIDGE_HIGH)) {
            failRidge()
            return
        }
        forceWalk(RIDGE_TOP, em_face_north)
        resetAnim()
        spam("You skillfully balance across the ridge...")
        statAdvance(AGILITY, RIDGE_XP)
        openCourseGates()
        telejump(RIDGE_INSIDE, TeleportType.Movement)
    }

    private suspend fun ProtectedAccess.apExitRidge(gate: BoundLocInfo) {
        if (!isWithinApRange(gate, AP_RANGE)) return
        spam("You go through the gate and try to edge over the ridge...")
        openCourseGates()
        anim("seq.human_walk_logbalance")
        forceWalk(RIDGE_TOP, em_face_south)
        forceWalk(RIDGE_MID, em_face_south)
        if (failRoll(RIDGE_LOW, RIDGE_HIGH)) {
            failRidge()
            return
        }
        forceWalk(RIDGE_BOTTOM, em_face_south)
        resetAnim()
        spam("You skillfully balance across the ridge...")
        statAdvance(AGILITY, RIDGE_XP)
        telejump(RIDGE_OUTSIDE, TeleportType.Movement)
    }

    private suspend fun ProtectedAccess.failRidge() {
        anim("seq.human_walk_logbalance_stumble")
        spam("You lose your footing and fall into the wolf pit.")
        faceSquare(CoordGrid(coords.x - 2, coords.z, 0))
        delay(1)
        resetAnim()
        exactMove(coords, CoordGrid(coords.x + RIDGE_FALL_X, coords.z - 1, 0), delay1 = 0, delay2 = RIDGE_FALL_CYCLES, dir = em_face_east, teleportType = TeleportType.Movement)
        delay(RIDGE_FALL_TICKS)
    }

    private fun ProtectedAccess.swingOpen(door: BoundLocInfo, ticks: Int) {
        val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.del(door, ticks)
        locRepo.add(openCoords, OPEN_DOOR, ticks, door.turnAngle(rotations = 1), door.shape)
        soundSynth("synth.door_open")
    }

    private fun ProtectedAccess.openCourseGates() {
        for (loc in
            locRepo.findAll(CoordGrid(GATE_SOUTH_X, GATE_Z, 0)) +
                locRepo.findAll(CoordGrid(GATE_NORTH_X, GATE_Z, 0))) {
            locRepo.del(loc, GATE_OPEN_TICKS)
        }
    }

    private suspend fun ProtectedAccess.apSqueezePipe(pipe: BoundLocInfo) {
        if (!isWithinApRange(pipe, AP_RANGE)) return
        if (coords.z >= PIPE_MIN_Z) {
            mes("You can't enter the pipe from this side.")
            return
        }
        if (!checkLevel(OBSTACLE_LVL, "to squeeze through the pipe")) return
        if (player.currentMapClock < pipeUsedUntil) {
            spam("The pipe is being used.")
            return
        }
        squeezePipe(pipe)
    }

    private suspend fun ProtectedAccess.squeezePipe(pipe: BoundLocInfo) {
        val px = pipe.coords.x
        val pz = pipe.coords.z
        pipeUsedUntil = player.currentMapClock + PIPE_BUSY_TICKS
        anim("seq.human_doublepipesqueeze", delay = PIPE_ANIM_DELAY)
        exactMove(CoordGrid(px, pz - 1, 0), CoordGrid(px, pz + 2, 0), delay1 = PIPE_MOVE_START, delay2 = PIPE_MOVE_END, dir = em_face_north, teleportType = TeleportType.Movement)
        delay(PIPE_SEGMENT_TICKS)
        delay(1)
        telejump(CoordGrid(px, pz + PIPE_TELE_OFFSET, 0), TeleportType.Movement)
        delay(1)
        anim("seq.human_doublepipesqueeze", delay = PIPE_ANIM_DELAY)
        exactMove(CoordGrid(px, pz + PIPE_TELE_OFFSET, 0), CoordGrid(px, pz + PIPE_END_OFFSET, 0), delay1 = PIPE_MOVE_START, delay2 = PIPE_MOVE_END, dir = em_face_north, teleportType = TeleportType.Movement)
        delay(PIPE_SEGMENT_TICKS)
        resetAnim()
        spam("You pull yourself through the pipes.")
        statAdvance(AGILITY, PIPE_XP)
        player.advanceWilderness(OBSTACLE_PIPE)
    }

    private suspend fun ProtectedAccess.apSwing(swing: BoundLocInfo) {
        if (!isWithinApRange(swing, AP_RANGE)) return
        val start = CoordGrid(swing.coords.x, swing.coords.z + 1, swing.coords.level)
        if (coords.z > start.z) {
            mes("You cannot do that from here.")
            return
        }
        val steps = distanceTo(start)
        delay(1)
        telejump(start, TeleportType.Movement)
        if (steps > 0) {
            delay(steps)
        }
        if (failRoll(SWING_LOW, SWING_HIGH)) {
            telejump(SWING_PIT, TeleportType.Movement)
            applyFailDamage(FAIL_PER_MILLE_LOW)
            spam("You slip and fall to the pit below.")
            return
        }
        if (player.currentMapClock < swingUsedUntil) {
            spam("The rope swing is being used.")
            return
        }
        swingUsedUntil = player.currentMapClock + SWING_BUSY_TICKS
        anim("seq.human_ropeswing_long", delay = SWING_ANIM_DELAY)
        worldRepo.locAnim(swing, "seq.ropeswing_long")
        exactMove(start, CoordGrid(start.x, start.z + SWING_TILES, start.level), delay1 = SWING_MOVE_START, delay2 = SWING_MOVE_END, dir = em_face_north, teleportType = TeleportType.Movement)
        delay(SWING_TICKS)
        spam("You skillfully swing across.")
        statAdvance(AGILITY, SWING_XP)
        player.advanceWilderness(OBSTACLE_SWING)
    }

    private suspend fun ProtectedAccess.apCrossStones(stone: BoundLocInfo) {
        if (!isWithinApRange(stone, AP_RANGE)) return
        spam("You carefully start crossing the stepping stones...")
        for (hop in 1..STONE_FAIL_HOP) {
            hopStone(stone, hop)
        }
        if (failRoll(STONE_LOW, STONE_HIGH)) {
            anim("seq.human_walk_logbalance_stumble")
            faceSquare(CoordGrid(coords.x, coords.z + 2, 0))
            delay(1)
            telejump(CoordGrid(stone.coords.x + 1, stone.coords.z + 3, 0), TeleportType.Movement)
            applyFailDamage(FAIL_PER_MILLE_STONE)
            spam("...You lose your footing and fall into the lava.")
            delay(1)
            resetAnim()
            return
        }
        for (hop in STONE_FAIL_HOP + 1..STONE_HOPS) {
            hopStone(stone, hop)
        }
        resetAnim()
        spam("...You safely cross to the other side.")
        statAdvance(AGILITY, STONE_XP)
        player.advanceWilderness(OBSTACLE_STONES)
    }

    private suspend fun ProtectedAccess.hopStone(stone: BoundLocInfo, hop: Int) {
        anim("seq.human_spot_jump")
        exactMove(
            CoordGrid(stone.coords.x + 2 - hop, stone.coords.z, 0),
            CoordGrid(stone.coords.x + 1 - hop, stone.coords.z, 0),
            delay1 = HOP_MOVE_START,
            delay2 = HOP_MOVE_END,
            dir = em_face_west,
            teleportType = TeleportType.Movement,
        )
        delay(HOP_TICKS)
    }

    private suspend fun ProtectedAccess.apCrossLog(log: BoundLocInfo) {
        if (!isWithinApRange(log, AP_RANGE)) return
        telejump(CoordGrid(log.coords.x + 1, log.coords.z, 0), TeleportType.Movement)
        delay(1)
        spam("You walk carefully across the slippery log...")
        anim("seq.human_walk_logbalance")
        forceWalk(CoordGrid(log.coords.x - 3, log.coords.z, 0), em_face_west)
        if (failRoll(LOG_LOW, LOG_HIGH)) {
            anim("seq.human_walk_logbalance_stumble")
            faceSquare(CoordGrid(coords.x, coords.z + 2, 0))
            delay(1)
            telejump(LOG_PIT, TeleportType.Movement)
            applyFailDamage(FAIL_PER_MILLE_LOW)
            spam("You slip and fall onto the spikes below.")
            delay(1)
            resetAnim()
            return
        }
        forceWalk(CoordGrid(log.coords.x - 6, log.coords.z, 0), em_face_west)
        spam("You skillfully edge across the gap.")
        forceWalk(CoordGrid(log.coords.x - 7, log.coords.z, 0), em_face_west)
        resetAnim()
        statAdvance(AGILITY, LOG_XP)
        player.advanceWilderness(OBSTACLE_LOG)
    }

    private suspend fun ProtectedAccess.apClimbRocks(rock: BoundLocInfo) {
        if (!isWithinApRange(rock, AP_RANGE)) return
        delay(1)
        anim("seq.human_climbing")
        forceWalk(rock.coords, em_face_south)
        forceWalk(CoordGrid(rock.coords.x, rock.coords.z - ROCK_DESCENT, 0), em_face_south)
        resetAnim()
        if (player.advanceWilderness(OBSTACLE_ROCKS)) {
            spam("You reach the top.")
            statAdvance(AGILITY, ROCKS_XP)
            player.wildernessTagPending = 1
        }
    }

    private suspend fun ProtectedAccess.apTagDispenser(dispenser: BoundLocInfo) {
        if (!isWithinApRange(dispenser, AP_RANGE)) return
        player.runEnergy = RUN_FULL
        spam("You feel rejuvenated.")
        if (player.wildernessTagPending != 0) {
            player.wildernessTagPending = 0
            invAdd(player.inv, TICKET, 1)
            spam("The dispenser spits out a ticket.")
        }
    }

    private suspend fun ProtectedAccess.apPayDispenser(dispenser: BoundLocInfo) {
        if (!isWithinApRange(dispenser, AP_RANGE)) return
        if (player.wildernessLootPaid != 0) {
            mes("You have already paid the fee.")
            return
        }
        if (!invTakeFee(PAY_FEE)) {
            mes("You need ${PAY_FEE} coins to pay the fee.")
            return
        }
        player.wildernessLootPaid = 1
        spam("You pay the dispenser fee.")
    }

    private suspend fun ProtectedAccess.apRedeemDispenser(dispenser: BoundLocInfo) {
        if (!isWithinApRange(dispenser, AP_RANGE)) return
        val count = player.inv.count(TICKET)
        if (count == 0) {
            mes("You don't have any tickets to redeem.")
            return
        }
        val bonus =
            when {
                count >= REDEEM_TIER_3 -> REDEEM_BONUS_3
                count >= REDEEM_TIER_2 -> REDEEM_BONUS_2
                count >= REDEEM_TIER_1 -> REDEEM_BONUS_1
                else -> 1.0
            }
        invDel(player.inv, TICKET, count)
        statAdvance(AGILITY, (count * TICKET_XP * bonus).toInt().toDouble())
        spam("The dispenser accepts your tickets.")
    }

    private suspend fun ProtectedAccess.forceWalk(dest: CoordGrid, dir: Int) {
        val tiles = distanceTo(dest)
        if (tiles == 0) return
        exactMove(coords, dest, delay1 = 0, delay2 = tiles * TICK_CYCLES, dir = dir, teleportType = TeleportType.Movement)
        delay(tiles)
    }

    private fun ProtectedAccess.checkLevel(level: Int, action: String): Boolean {
        if (player.agilityLvl >= level) return true
        mes("You need an Agility level of $level $action.")
        return false
    }

    private fun ProtectedAccess.failRoll(low: Int, high: Int): Boolean {
        val success = low + (high - low) * (player.agilityLvl - 1) / 98
        return random.of(RATE_SCALE) >= success
    }

    private fun ProtectedAccess.applyFailDamage(perMille: Int) {
        val damage = (player.hitpoints * perMille) / 1000 + 1
        player.takeInstantHit(HitType.Typeless, damage, hitProcessor)
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val TICKET = "obj.wildy_agility_token"

        const val RIDGE_LVL = 52
        const val OBSTACLE_LVL = 49
        const val AP_RANGE = 1
        const val RATE_SCALE = 256
        const val OPEN_DOOR = "loc.poshdooropen"

        const val RIDGE_XP = 15.0
        const val PIPE_XP = 12.5
        const val SWING_XP = 20.0
        const val STONE_XP = 20.0
        const val LOG_XP = 20.0
        const val ROCKS_XP = 498.9

        const val OBSTACLE_PIPE = 1
        const val OBSTACLE_SWING = 2
        const val OBSTACLE_STONES = 3
        const val OBSTACLE_LOG = 4
        const val OBSTACLE_ROCKS = 5

        const val RIDGE_LOW = 200
        const val RIDGE_HIGH = 250
        const val SWING_LOW = 200
        const val SWING_HIGH = 250
        const val STONE_LOW = 180
        const val STONE_HIGH = 250
        const val LOG_LOW = 200
        const val LOG_HIGH = 250

        const val FAIL_PER_MILLE_LOW = 150
        const val FAIL_PER_MILLE_STONE = 216

        const val TICK_CYCLES = 30
        const val RIDGE_FALL_TICKS = 1
        const val RIDGE_FALL_CYCLES = 25
        const val RIDGE_FALL_X = 3
        const val PIPE_ANIM_DELAY = 30
        const val PIPE_MOVE_START = 30
        const val PIPE_MOVE_END = 126
        const val PIPE_SEGMENT_TICKS = 3
        const val PIPE_TELE_OFFSET = 9
        const val PIPE_END_OFFSET = 12
        const val PIPE_BUSY_TICKS = 11
        const val SWING_ANIM_DELAY = 20
        const val SWING_MOVE_START = 45
        const val SWING_MOVE_END = 70
        const val SWING_TICKS = 1
        const val SWING_TILES = 5
        const val SWING_BUSY_TICKS = 2
        const val HOP_MOVE_START = 15
        const val HOP_MOVE_END = 30
        const val HOP_TICKS = 1
        const val STONE_HOPS = 6
        const val STONE_FAIL_HOP = 3
        const val ROCK_DESCENT = 3
        const val GATE_OPEN_TICKS = 5

        const val PIPE_MIN_Z = 3939
        const val GATE_Z = 3931
        const val GATE_SOUTH_X = 2997
        const val GATE_NORTH_X = 2998

        const val RUN_FULL = 1000
        const val PAY_FEE = 150_000
        const val TICKET_XP = 200.0
        const val REDEEM_TIER_1 = 11
        const val REDEEM_TIER_2 = 51
        const val REDEEM_TIER_3 = 101
        const val REDEEM_BONUS_1 = 1.05
        const val REDEEM_BONUS_2 = 1.10
        const val REDEEM_BONUS_3 = 1.15

        val SWING_PIT = CoordGrid(3004, 10357, 0)
        val LOG_PIT = CoordGrid(2998, 10345, 0)
        val RIDGE_MID = CoordGrid(2998, 3924, 0)
        val RIDGE_TOP = CoordGrid(2998, 3930, 0)
        val RIDGE_BOTTOM = CoordGrid(2998, 3918, 0)
        val RIDGE_INSIDE = CoordGrid(2998, 3932, 0)
        val RIDGE_OUTSIDE = CoordGrid(2998, 3916, 0)
    }
}
