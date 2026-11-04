package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.config.Constants.em_face_east
import org.rsmod.api.config.Constants.em_face_north
import org.rsmod.api.config.Constants.em_face_south
import org.rsmod.api.config.Constants.em_face_west
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BarbarianOutpostCourse @Inject constructor(
    private val marks: MarkOfGrace,
    private val random: GameRandom,
    private val hitProcessor: DamageOnlyPlayerHitProcessor,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onApLoc1("loc.agility_obstical_pipe_barbarian") { apSqueezePipe(it.loc) }
        onApLoc1("loc.obstical_ropeswing1") { apRopeswing(it.loc) }
        onApLoc1("loc.barbarian_log_balance1") { apCrossLog(it.loc) }
        onApLoc1("loc.agility_obstical_net_barbarian") { apClimbNet(it.loc) }
        onApLoc1("loc.balancing_ledge1") { apCrossLedge(it.loc) }
        onApLoc1("loc.barbarian_laddertop_norim") { apClimbLadder(it.loc) }
        onApLoc1("loc.castlecrumbly1") { apClimbWall(it.loc) }
    }

    private suspend fun ProtectedAccess.apSqueezePipe(pipe: BoundLocInfo) {
        if (pipe.coords.x != PIPE.x || !isWithinApRange(pipe, AP_RANGE)) return
        if (!QuestRequirements.hasCompleted(player, BARCRAWL)) {
            mes("You need to have completed the Barcrawl miniquest to enter here.")
            return
        }
        if (!checkLevel()) return
        squeezePipe()
    }

    private suspend fun ProtectedAccess.squeezePipe() {
        val entering = coords.z < PIPE.z
        val dest = CoordGrid(coords.x, if (entering) PIPE.z + 1 else PIPE.z - 1, 0)
        val facing = if (entering) em_face_north else em_face_south
        spam("You pull yourself through the pipes.")
        anim("seq.human_pipesqueeze")
        exactMove(coords, dest, delay1 = 0, delay2 = PIPE_CYCLES, dir = facing)
        delay(PIPE_TICKS)
        statAdvance(AGILITY, PIPE_XP)
    }

    private suspend fun ProtectedAccess.apRopeswing(swing: BoundLocInfo) {
        if (swing.coords.x != SWING.x || !isWithinApRange(swing, AP_RANGE)) return
        if (!checkLevel()) return
        if (coords.z < SWING_PLATFORM_Z) {
            mes("You cannot do that from here.")
            return
        }
        ropeswing(swing)
    }

    private suspend fun ProtectedAccess.ropeswing(swing: BoundLocInfo) {
        if (failedChance(SWING_LOW, SWING_MAX)) {
            anim("seq.human_ropeswing_long_miss")
            delay(FALL_TICKS)
            telejump(SPIKE_PIT)
            anim("seq.human_climbing_fall")
            applyFailDamage()
            spam("You slip and fall to the pit bellow.")
            delay(1)
            resetAnim()
            return
        }
        anim("seq.human_ropeswing_long")
        worldRepo.locAnim(swing, "seq.ropeswing_long")
        exactMove(coords, SWING_LANDING, delay1 = 0, delay2 = SWING_CYCLES, dir = em_face_south)
        delay(SWING_TICKS)
        statAdvance(AGILITY, SWING_XP)
        player.advanceCourse(OBSTACLE_SWING)
    }

    private suspend fun ProtectedAccess.apCrossLog(log: BoundLocInfo) {
        if (!isWithinApRange(log, AP_RANGE)) return
        if (!checkLevel()) return
        crossLog()
    }

    private suspend fun ProtectedAccess.crossLog() {
        val westbound = coords.x > LOG_MIDDLE.x
        val dest = if (westbound) LOG_WEST_END else LOG_EAST_END
        val facing = if (westbound) em_face_west else em_face_east

        spam("You walk carefully across the slippery log...")
        anim("seq.human_walk_logbalance")
        if (failedChance(LOG_LOW, LOG_MAX)) {
            exactMove(coords, LOG_FAIL_POINT, delay1 = 0, delay2 = LOG_FAIL_CYCLES, dir = facing)
            delay(LOG_FAIL_TICKS)
            anim("seq.human_falling")
            telejump(LOG_WATER)
            applyFailDamage()
            spam("...You lose your footing and fall into the water.")
            spam("Something in the water bites you.")
            anim("seq.human_swim")
            exactMove(coords, LOG_BANK, delay1 = 0, delay2 = SWIM_CYCLES, dir = em_face_south)
            delay(SWIM_TICKS)
            resetAnim()
            return
        }
        exactMove(coords, dest, delay1 = 0, delay2 = LOG_CYCLES, dir = facing)
        delay(LOG_TICKS)
        resetAnim()
        statAdvance(AGILITY, LOG_XP)
        if (westbound) {
            player.advanceCourse(OBSTACLE_LOG)
        }
    }

    private suspend fun ProtectedAccess.apClimbNet(net: BoundLocInfo) {
        if (net.coords.x != NET.x || !isWithinApRange(net, AP_RANGE)) return
        if (!checkLevel()) return
        if (coords.x < net.coords.x) {
            mes("You cannot do that from here.")
            return
        }
        climbNet()
    }

    private suspend fun ProtectedAccess.climbNet() {
        spam("You climb the netting.")
        anim("seq.human_climbing")
        delay(CLIMB_TICKS)
        telejump(NET_TOP)
        resetAnim()
        statAdvance(AGILITY, NET_XP)
        player.advanceCourse(OBSTACLE_NET)
    }

    private suspend fun ProtectedAccess.apCrossLedge(ledge: BoundLocInfo) {
        if (ledge.coords.x != LEDGE.x || !isWithinApRange(ledge, AP_RANGE)) return
        if (!checkLevel()) return
        crossLedge()
    }

    private suspend fun ProtectedAccess.crossLedge() {
        val westbound = coords.x >= LEDGE.x
        val dest = if (westbound) LEDGE_WEST_END else LEDGE_EAST_END
        val facing = if (westbound) em_face_west else em_face_east

        spam("You carefully tread across the ledge.")
        anim("seq.human_walk_sidestepl")
        if (failedChance(LEDGE_LOW, LEDGE_MAX)) {
            exactMove(coords, LEDGE_FAIL_POINT, delay1 = 0, delay2 = LEDGE_FAIL_CYCLES, dir = facing)
            delay(LEDGE_FAIL_TICKS)
            anim("seq.human_climbing_fall")
            telejump(LEDGE_PIT)
            applyFailDamage()
            spam("You slip and fall to the pit bellow.")
            delay(1)
            resetAnim()
            return
        }
        exactMove(coords, dest, delay1 = 0, delay2 = LEDGE_CYCLES, dir = facing)
        delay(LEDGE_TICKS)
        resetAnim()
        statAdvance(AGILITY, LEDGE_XP)
        if (westbound) {
            player.advanceCourse(OBSTACLE_LEDGE)
        }
    }

    private suspend fun ProtectedAccess.apClimbLadder(ladder: BoundLocInfo) {
        if (ladder.coords != LADDER_TOP || !isWithinApRange(ladder, AP_RANGE)) return
        if (!checkLevel()) return
        anim("seq.human_climbing_down")
        delay(CLIMB_TICKS)
        telejump(LADDER_BOTTOM)
        resetAnim()
        player.advanceCourse(OBSTACLE_LADDER)
    }

    private suspend fun ProtectedAccess.apClimbWall(wall: BoundLocInfo) {
        val index = wallIndex(wall.coords) ?: return
        if (!isWithinApRange(wall, AP_RANGE)) return
        if (!checkLevel()) return
        if (coords.x > wall.coords.x) {
            mes("You cannot climb from this side.")
            return
        }
        climbWall(wall.coords, index)
    }

    private suspend fun ProtectedAccess.climbWall(wall: CoordGrid, index: Int) {
        val dest = CoordGrid(wall.x + 1, wall.z, wall.level)
        anim("seq.agility_shortcut_wall_jump")
        exactMove(coords, dest, delay1 = 0, delay2 = WALL_CYCLES, dir = em_face_east)
        delay(WALL_TICKS)
        statAdvance(AGILITY, WALL_XP)
        if (index < OBSTACLE_WALL_3) {
            player.advanceCourse(index)
        } else if (player.completeLap(OBSTACLE_WALL_3)) {
            statAdvance(AGILITY, LAP_BONUS_AGILITY)
            statAdvance(STRENGTH, LAP_BONUS_STRENGTH)
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    private fun ProtectedAccess.checkLevel(): Boolean {
        if (player.agilityLvl >= REQUIRED_LVL) return true
        mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
        return false
    }

    private fun ProtectedAccess.failedChance(low: Int, maxLevel: Int): Boolean {
        if (player.agilityLvl >= maxLevel) return false
        val range = maxLevel - REQUIRED_LVL
        val success = low + (RATE_SCALE - low) * (player.agilityLvl - REQUIRED_LVL) / range
        return random.of(RATE_SCALE) >= success
    }

    private fun ProtectedAccess.applyFailDamage() {
        val damage = (player.hitpoints / FAIL_DAMAGE_DIVISOR).coerceAtLeast(FAIL_DAMAGE_MIN)
        player.takeInstantHit(HitType.Typeless, damage, hitProcessor)
    }

    private fun wallIndex(wall: CoordGrid): Int? =
        when (wall) {
            WALL_1 -> OBSTACLE_WALL_1
            WALL_2 -> OBSTACLE_WALL_2
            WALL_3 -> OBSTACLE_WALL_3
            else -> null
        }

    private companion object {
        const val AGILITY = "stat.agility"
        const val STRENGTH = "stat.strength"
        const val BARCRAWL = "miniquest_barcrawl"

        const val REQUIRED_LVL = 35
        const val AP_RANGE = 3
        const val RATE_SCALE = 256

        const val PIPE_XP = 10.0
        const val SWING_XP = 22.0
        const val LOG_XP = 13.7
        const val NET_XP = 8.2
        const val LEDGE_XP = 22.0
        const val WALL_XP = 13.7
        const val LAP_BONUS_AGILITY = 46.3
        const val LAP_BONUS_STRENGTH = 41.3

        const val OBSTACLE_SWING = 1
        const val OBSTACLE_LOG = 2
        const val OBSTACLE_NET = 3
        const val OBSTACLE_LEDGE = 4
        const val OBSTACLE_LADDER = 5
        const val OBSTACLE_WALL_1 = 6
        const val OBSTACLE_WALL_2 = 7
        const val OBSTACLE_WALL_3 = 8

        const val SWING_PLATFORM_Z = 3554
        const val PIPE_TICKS = 3
        const val PIPE_CYCLES = PIPE_TICKS * 30
        const val SWING_TICKS = 4
        const val SWING_CYCLES = SWING_TICKS * 30
        const val LOG_TICKS = 10
        const val LOG_CYCLES = LOG_TICKS * 30
        const val LOG_FAIL_TICKS = 4
        const val LOG_FAIL_CYCLES = LOG_FAIL_TICKS * 30
        const val SWIM_TICKS = 2
        const val SWIM_CYCLES = SWIM_TICKS * 30
        const val CLIMB_TICKS = 2
        const val LEDGE_TICKS = 5
        const val LEDGE_CYCLES = LEDGE_TICKS * 30
        const val LEDGE_FAIL_TICKS = 2
        const val LEDGE_FAIL_CYCLES = LEDGE_FAIL_TICKS * 30
        const val FALL_TICKS = 3
        const val WALL_TICKS = 2
        const val WALL_CYCLES = WALL_TICKS * 30

        const val SWING_LOW = 229
        const val SWING_MAX = 68
        const val LOG_LOW = 209
        const val LOG_MAX = 93
        const val LEDGE_LOW = 229
        const val LEDGE_MAX = 68

        const val FAIL_DAMAGE_DIVISOR = 12
        const val FAIL_DAMAGE_MIN = 2

        val PIPE = CoordGrid(2552, 3559, 0)
        val SWING = CoordGrid(2551, 3552, 0)
        val SWING_LANDING = CoordGrid(2551, 3549, 0)
        val SPIKE_PIT = CoordGrid(2549, 9951, 0)
        val LOG_MIDDLE = CoordGrid(2545, 3546, 0)
        val LOG_EAST_END = CoordGrid(2551, 3546, 0)
        val LOG_WEST_END = CoordGrid(2541, 3546, 0)
        val LOG_FAIL_POINT = CoordGrid(2545, 3546, 0)
        val LOG_WATER = CoordGrid(2545, 3545, 0)
        val LOG_BANK = CoordGrid(2545, 3542, 0)
        val NET = CoordGrid(2538, 3545, 0)
        val NET_TOP = CoordGrid(2537, 3546, 1)
        val LEDGE = CoordGrid(2535, 3547, 1)
        val LEDGE_EAST_END = CoordGrid(2536, 3547, 1)
        val LEDGE_WEST_END = CoordGrid(2532, 3547, 1)
        val LEDGE_FAIL_POINT = CoordGrid(2534, 3547, 1)
        val LEDGE_PIT = CoordGrid(2534, 3545, 0)
        val LADDER_TOP = CoordGrid(2532, 3545, 1)
        val LADDER_BOTTOM = CoordGrid(2532, 3546, 0)
        val WALL_1 = CoordGrid(2536, 3553, 0)
        val WALL_2 = CoordGrid(2539, 3553, 0)
        val WALL_3 = CoordGrid(2542, 3553, 0)
        val MARK_SPAWN = CoordGrid(2532, 3546, 1)
    }
}
