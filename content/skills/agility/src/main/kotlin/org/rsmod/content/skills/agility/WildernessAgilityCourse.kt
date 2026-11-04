package org.rsmod.content.skills.agility

import jakarta.inject.Inject
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
        onApLoc1("loc.balancegate52a") { apOpenEntrance(it.loc) }
        onApLoc1("loc.balancegate52b_left") { apCrossRidge(it.loc) }
        onApLoc1("loc.balancegate52b_right") { apCrossRidge(it.loc) }
        onApLoc1("loc.obstical_pipe2") { apSqueezePipe(it.loc) }
        onApLoc1("loc.obstical_ropeswing2") { apSwing(it.loc) }
        onApLoc1("loc.steppingstone1") { apCrossStones(it.loc) }
        onApLoc1("loc.wilderness_log_balance1") { apCrossLog(it.loc) }
        onApLoc1("loc.wildclimbingrock") { apClimbRocks(it.loc) }
        onApLoc1("loc.wildy_agility_pillar") { apTagDispenser() }
        onApLoc2("loc.wildy_agility_pillar") { apPayDispenser() }
        onApLoc3("loc.wildy_agility_pillar") { apRedeemDispenser() }
    }

    private suspend fun ProtectedAccess.apOpenEntrance(door: BoundLocInfo) {
        if (!isWithinApRange(door, AP_RANGE)) return
        locRepo.del(door, DOOR_OPEN_TICKS)
        spam("You open the door.")
    }

    private suspend fun ProtectedAccess.apCrossRidge(gate: BoundLocInfo) {
        if (!isWithinApRange(gate, AP_RANGE)) return
        if (coords.z > gate.coords.z) {
            crossRidge(em_face_south, RIDGE_EXIT_Z)
            return
        }
        if (!checkLevel(GATE_LVL, "to enter the course")) return
        crossRidge(em_face_north, RIDGE_ENTRY_Z)
    }

    private suspend fun ProtectedAccess.crossRidge(facing: Int, destZ: Int) {
        spam("You go through the gate and try to edge over the ridge...")
        anim("seq.human_walk_logbalance")
        if (facing == em_face_north && failRoll(GATE_LOW, GATE_HIGH)) {
            exactMove(coords, CoordGrid(coords.x, coords.z + 2, 0), delay1 = 0, delay2 = RIDGE_FAIL_CYCLES, dir = facing, teleportType = TeleportType.Exempt)
            delay(RIDGE_FAIL_TICKS)
            telejump(CoordGrid(coords.x + 2, coords.z, 0), TeleportType.Exempt)
            spam("You lose your footing and fall into the wolf pit.")
            delay(1)
            resetAnim()
            return
        }
        exactMove(coords, CoordGrid(coords.x, destZ, 0), delay1 = 0, delay2 = RIDGE_CYCLES, dir = facing, teleportType = TeleportType.Exempt)
        delay(RIDGE_TICKS)
        resetAnim()
        spam("You skillfully balance across the ridge...")
        statAdvance(AGILITY, RIDGE_XP)
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
        squeezePipe()
    }

    private suspend fun ProtectedAccess.squeezePipe() {
        pipeUsedUntil = player.currentMapClock + PIPE_BUSY_TICKS
        anim("seq.human_doublepipesqueeze")
        exactMove(coords, PIPE_MID, delay1 = 0, delay2 = PIPE_CYCLES, dir = em_face_north, teleportType = TeleportType.Exempt)
        delay(PIPE_TICKS)
        telejump(PIPE_SECOND, TeleportType.Exempt)
        anim("seq.human_doublepipesqueeze")
        exactMove(coords, PIPE_END, delay1 = 0, delay2 = PIPE_CYCLES, dir = em_face_north, teleportType = TeleportType.Exempt)
        delay(PIPE_TICKS)
        resetAnim()
        spam("You pull yourself through the pipes.")
        statAdvance(AGILITY, PIPE_XP)
        player.advanceWilderness(OBSTACLE_PIPE)
    }

    private suspend fun ProtectedAccess.apSwing(swing: BoundLocInfo) {
        if (swing.coords.x != SWING.x || !isWithinApRange(swing, AP_RANGE)) return
        if (coords.z > SWING_START.z) {
            mes("You cannot do that from here.")
            return
        }
        telejump(SWING_START, TeleportType.Exempt)
        if (!checkLevel(OBSTACLE_LVL, "to use this obstacle")) return
        if (failRoll(SWING_LOW, SWING_HIGH)) {
            telejump(SWING_PIT, TeleportType.Exempt)
            anim("seq.human_climbing_fall")
            applyFailDamage(FAIL_PERCENT_LOW)
            spam("You slip and fall to the pit below.")
            delay(1)
            resetAnim()
            return
        }
        if (player.currentMapClock < swingUsedUntil) {
            spam("The rope swing is being used.")
            return
        }
        swingUsedUntil = player.currentMapClock + SWING_BUSY_TICKS
        anim("seq.human_ropeswing")
        worldRepo.locAnim(swing, "seq.ropeswing_long")
        exactMove(coords, SWING_LANDING, delay1 = 0, delay2 = SWING_CYCLES, dir = em_face_north, teleportType = TeleportType.Exempt)
        delay(SWING_TICKS)
        spam("You skillfully swing across.")
        statAdvance(AGILITY, SWING_XP)
        player.advanceWilderness(OBSTACLE_SWING)
    }

    private suspend fun ProtectedAccess.apCrossStones(stone: BoundLocInfo) {
        if (stone.coords != STONE || !isWithinApRange(stone, AP_RANGE)) return
        if (coords.x <= STONE.x) {
            mes("You cannot do that from here.")
            return
        }
        if (!checkLevel(OBSTACLE_LVL, "to use this obstacle")) return
        crossStones()
    }

    private suspend fun ProtectedAccess.crossStones() {
        anim("seq.human_spot_jump")
        for (hop in 1..STONE_FAIL_HOP) {
            hopStone(hop)
        }
        if (failRoll(STONE_LOW, STONE_HIGH)) {
            exactMove(coords, CoordGrid(coords.x, coords.z + 2, 0), delay1 = 0, delay2 = HOP_CYCLES, dir = em_face_north, teleportType = TeleportType.Exempt)
            delay(HOP_TICKS)
            telejump(STONE_FAIL, TeleportType.Exempt)
            applyFailDamage(FAIL_PERCENT_STONE)
            spam("...You lose your footing and fall into the lava.")
            delay(1)
            resetAnim()
            return
        }
        for (hop in STONE_FAIL_HOP + 1..STONE_HOPS) {
            hopStone(hop)
        }
        resetAnim()
        spam("...You safely cross to the other side.")
        statAdvance(AGILITY, STONE_XP)
        player.advanceWilderness(OBSTACLE_STONES)
    }

    private suspend fun ProtectedAccess.hopStone(hop: Int) {
        val dest = CoordGrid(STONE.x + 1 - hop, STONE.z, 0)
        exactMove(coords, dest, delay1 = 0, delay2 = HOP_CYCLES, dir = em_face_west, teleportType = TeleportType.Exempt)
        delay(HOP_TICKS)
    }

    private suspend fun ProtectedAccess.apCrossLog(log: BoundLocInfo) {
        if (log.coords != LOG || !isWithinApRange(log, AP_RANGE)) return
        if (!checkLevel(OBSTACLE_LVL, "to use this obstacle")) return
        crossLog()
    }

    private suspend fun ProtectedAccess.crossLog() {
        telejump(LOG_START, TeleportType.Exempt)
        spam("You walk carefully across the slippery log...")
        anim("seq.human_walk_logbalance")
        exactMove(coords, LOG_MID, delay1 = 0, delay2 = LOG_CYCLES, dir = em_face_west, teleportType = TeleportType.Exempt)
        delay(LOG_TICKS)
        if (failRoll(LOG_LOW, LOG_HIGH)) {
            exactMove(coords, CoordGrid(coords.x, coords.z + 2, 0), delay1 = 0, delay2 = HOP_CYCLES, dir = em_face_north, teleportType = TeleportType.Exempt)
            delay(HOP_TICKS)
            telejump(LOG_PIT, TeleportType.Exempt)
            anim("seq.human_climbing_fall")
            applyFailDamage(FAIL_PERCENT_LOW)
            spam("You slip and fall onto the spikes below.")
            delay(1)
            resetAnim()
            return
        }
        exactMove(coords, LOG_NEAR, delay1 = 0, delay2 = LOG_CYCLES, dir = em_face_west, teleportType = TeleportType.Exempt)
        delay(LOG_TICKS)
        spam("You skillfully edge across the gap.")
        exactMove(coords, LOG_END, delay1 = 0, delay2 = HOP_CYCLES, dir = em_face_west, teleportType = TeleportType.Exempt)
        delay(HOP_TICKS)
        resetAnim()
        statAdvance(AGILITY, LOG_XP)
        player.advanceWilderness(OBSTACLE_LOG)
    }

    private suspend fun ProtectedAccess.apClimbRocks(rock: BoundLocInfo) {
        if (!isWithinApRange(rock, AP_RANGE)) return
        if (!checkLevel(OBSTACLE_LVL, "to use this obstacle")) return
        anim("seq.human_climbing")
        exactMove(coords, rock.coords, delay1 = 0, delay2 = ROCK_CYCLES, dir = em_face_south, teleportType = TeleportType.Exempt)
        delay(ROCK_TICKS)
        exactMove(coords, CoordGrid(rock.coords.x, rock.coords.z - ROCK_DESCENT, 0), delay1 = 0, delay2 = ROCK_CYCLES, dir = em_face_south, teleportType = TeleportType.Exempt)
        delay(ROCK_TICKS)
        resetAnim()
        spam("You reach the top.")
        if (player.advanceWilderness(OBSTACLE_ROCKS)) {
            statAdvance(AGILITY, ROCKS_XP)
            player.wildernessTagPending = 1
        }
    }

    private suspend fun ProtectedAccess.apTagDispenser() {
        player.runEnergy = RUN_FULL
        spam("You feel rejuvenated.")
        if (player.wildernessTagPending != 0) {
            player.wildernessTagPending = 0
            invAdd(player.inv, TICKET, 1)
            spam("The dispenser spits out a ticket.")
        }
    }

    private suspend fun ProtectedAccess.apPayDispenser() {
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

    private suspend fun ProtectedAccess.apRedeemDispenser() {
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

    private fun ProtectedAccess.checkLevel(level: Int, action: String): Boolean {
        if (player.agilityLvl >= level) return true
        mes("You need an Agility level of $level $action.")
        return false
    }

    private fun ProtectedAccess.failRoll(low: Int, high: Int): Boolean {
        val success = low + (high - low) * (player.agilityLvl - 1) / 98
        return random.of(RATE_SCALE) >= success
    }

    private fun ProtectedAccess.applyFailDamage(percent: Int) {
        val damage = (player.hitpoints * percent) / 100 + 1
        player.takeInstantHit(HitType.Typeless, damage, hitProcessor)
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val TICKET = "obj.wildy_agility_token"

        const val GATE_LVL = 52
        const val OBSTACLE_LVL = 49
        const val AP_RANGE = 3
        const val RATE_SCALE = 256

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

        const val GATE_LOW = 180
        const val GATE_HIGH = 245
        const val SWING_LOW = 175
        const val SWING_HIGH = 245
        const val STONE_LOW = 185
        const val STONE_HIGH = 240
        const val LOG_LOW = 170
        const val LOG_HIGH = 250

        const val FAIL_PERCENT_LOW = 15
        const val FAIL_PERCENT_STONE = 20

        const val RIDGE_TICKS = 5
        const val RIDGE_CYCLES = RIDGE_TICKS * 30
        const val RIDGE_FAIL_TICKS = 2
        const val RIDGE_FAIL_CYCLES = RIDGE_FAIL_TICKS * 30
        const val PIPE_TICKS = 3
        const val PIPE_CYCLES = PIPE_TICKS * 30
        const val PIPE_BUSY_TICKS = 4
        const val SWING_TICKS = 4
        const val SWING_CYCLES = SWING_TICKS * 30
        const val SWING_BUSY_TICKS = 2
        const val HOP_TICKS = 1
        const val HOP_CYCLES = HOP_TICKS * 30
        const val STONE_HOPS = 6
        const val STONE_FAIL_HOP = 3
        const val LOG_TICKS = 3
        const val LOG_CYCLES = LOG_TICKS * 30
        const val ROCK_TICKS = 2
        const val ROCK_CYCLES = ROCK_TICKS * 30
        const val ROCK_DESCENT = 3
        const val DOOR_OPEN_TICKS = 500

        const val PIPE_MIN_Z = 3939
        const val RIDGE_ENTRY_Z = 3936
        const val RIDGE_EXIT_Z = 3926

        const val RUN_FULL = 1000
        const val PAY_FEE = 150_000
        const val TICKET_XP = 200.0
        const val REDEEM_TIER_1 = 11
        const val REDEEM_TIER_2 = 51
        const val REDEEM_TIER_3 = 101
        const val REDEEM_BONUS_1 = 1.05
        const val REDEEM_BONUS_2 = 1.10
        const val REDEEM_BONUS_3 = 1.15

        val PIPE_MID = CoordGrid(3004, 3940, 0)
        val PIPE_SECOND = CoordGrid(3004, 3947, 0)
        val PIPE_END = CoordGrid(3004, 3950, 0)
        val SWING = CoordGrid(3005, 3954, 0)
        val SWING_START = CoordGrid(3005, 3955, 0)
        val SWING_LANDING = CoordGrid(3005, 3960, 0)
        val SWING_PIT = CoordGrid(3004, 10357, 0)
        val STONE = CoordGrid(3001, 3960, 0)
        val STONE_FAIL = CoordGrid(3002, 3963, 0)
        val LOG = CoordGrid(3001, 3945, 0)
        val LOG_START = CoordGrid(3002, 3945, 0)
        val LOG_MID = CoordGrid(2998, 3945, 0)
        val LOG_NEAR = CoordGrid(2995, 3945, 0)
        val LOG_END = CoordGrid(2994, 3945, 0)
        val LOG_PIT = CoordGrid(2998, 10345, 0)
    }
}
