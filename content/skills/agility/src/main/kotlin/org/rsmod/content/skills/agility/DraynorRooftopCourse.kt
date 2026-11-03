package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.config.Constants.em_face_east
import org.rsmod.api.config.Constants.em_face_south
import org.rsmod.api.config.Constants.em_face_west
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_draynor_wallclimb") { climbRoughWall() }
        onOpLoc1("loc.rooftops_draynor_tightrope_1") { crossFirstTightrope() }
        onOpLoc1("loc.rooftops_draynor_tightrope_2") { crossSecondTightrope() }
        onOpLoc1("loc.rooftops_draynor_wallcrossing") { balanceNarrowWall() }
        onOpLoc1("loc.rooftops_draynor_wallscramble") { jumpUpWall() }
        onOpLoc1("loc.rooftops_draynor_leapdown") { jumpGap() }
        onOpLoc1("loc.rooftops_draynor_crate") { climbDownCrate() }
    }

    private suspend fun ProtectedAccess.climbRoughWall() {
        anim("seq.human_reachforladder")
        delay(CLIMB_TICKS)
        telejump(ROUGH_WALL_TOP)
        statAdvance(AGILITY, ROUGH_WALL_XP)
        completeObstacle(OBSTACLE_ROUGH_WALL)
    }

    private suspend fun ProtectedAccess.crossFirstTightrope() {
        walkRope(TIGHTROPE_1_START, TIGHTROPE_1_END, em_face_west)
        statAdvance(AGILITY, TIGHTROPE_1_XP)
        completeObstacle(OBSTACLE_TIGHTROPE_1)
    }

    private suspend fun ProtectedAccess.crossSecondTightrope() {
        walkRope(TIGHTROPE_2_START, TIGHTROPE_2_END, em_face_south)
        statAdvance(AGILITY, TIGHTROPE_2_XP)
        completeObstacle(OBSTACLE_TIGHTROPE_2)
    }

    private suspend fun ProtectedAccess.walkRope(start: CoordGrid, end: CoordGrid, dir: Int) {
        val ticks = start.chebyshevDistance(end)
        telejump(start)
        anim("seq.human_walk_logbalance")
        exactMove(start, end, delay1 = 0, delay2 = ticks * CYCLES_PER_TICK, dir = dir)
        delay(ticks)
    }

    private suspend fun ProtectedAccess.balanceNarrowWall() {
        anim("seq.human_walk_sidestepl")
        exactMove(coords, NARROW_WALL_END, delay1 = 0, delay2 = NARROW_WALL_CYCLES, dir = em_face_south)
        delay(NARROW_WALL_TICKS)
        statAdvance(AGILITY, NARROW_WALL_XP)
        completeObstacle(OBSTACLE_NARROW_WALL)
    }

    private suspend fun ProtectedAccess.jumpUpWall() {
        anim("seq.agility_shortcut_wall_jump2")
        delay(WALL_TICKS)
        telejump(WALL_TOP)
        statAdvance(AGILITY, WALL_XP)
        completeObstacle(OBSTACLE_WALL)
    }

    private suspend fun ProtectedAccess.jumpGap() {
        anim("seq.agility_shortcut_wall_jumpdown")
        exactMove(coords, GAP_LANDING, delay1 = 0, delay2 = GAP_CYCLES, dir = em_face_east)
        delay(GAP_TICKS)
        statAdvance(AGILITY, GAP_XP)
        completeObstacle(OBSTACLE_GAP)
    }

    private suspend fun ProtectedAccess.climbDownCrate() {
        anim("seq.agility_shortcut_wall_jumpdown")
        delay(CRATE_TICKS)
        telejump(CRATE_GROUND)
        statAdvance(AGILITY, CRATE_XP)
        completeObstacle(OBSTACLE_CRATE)
        if (player.courseProgress == OBSTACLE_CRATE) {
            player.courseProgress = 0
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    private fun ProtectedAccess.completeObstacle(index: Int) {
        player.courseProgress =
            when {
                index == OBSTACLE_ROUGH_WALL -> OBSTACLE_ROUGH_WALL
                player.courseProgress == index - 1 -> index
                else -> 0
            }
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val REQUIRED_LVL = 1
        const val CYCLES_PER_TICK = 30

        const val ROUGH_WALL_XP = 5.0
        const val TIGHTROPE_1_XP = 8.0
        const val TIGHTROPE_2_XP = 7.0
        const val NARROW_WALL_XP = 7.0
        const val WALL_XP = 10.0
        const val GAP_XP = 4.0
        const val CRATE_XP = 79.0

        const val OBSTACLE_ROUGH_WALL = 1
        const val OBSTACLE_TIGHTROPE_1 = 2
        const val OBSTACLE_TIGHTROPE_2 = 3
        const val OBSTACLE_NARROW_WALL = 4
        const val OBSTACLE_WALL = 5
        const val OBSTACLE_GAP = 6
        const val OBSTACLE_CRATE = 7

        const val CLIMB_TICKS = 2
        const val NARROW_WALL_TICKS = 4
        const val NARROW_WALL_CYCLES = NARROW_WALL_TICKS * CYCLES_PER_TICK
        const val WALL_TICKS = 2
        const val GAP_TICKS = 2
        const val GAP_CYCLES = GAP_TICKS * CYCLES_PER_TICK
        const val CRATE_TICKS = 2

        val ROUGH_WALL_TOP = CoordGrid(3102, 3279, 3)
        val TIGHTROPE_1_START = CoordGrid(3099, 3277, 3)
        val TIGHTROPE_1_END = CoordGrid(3090, 3277, 3)
        val TIGHTROPE_2_START = CoordGrid(3092, 3276, 3)
        val TIGHTROPE_2_END = CoordGrid(3092, 3267, 3)
        val NARROW_WALL_END = CoordGrid(3088, 3261, 3)
        val WALL_TOP = CoordGrid(3088, 3255, 3)
        val GAP_LANDING = CoordGrid(3096, 3256, 3)
        val CRATE_GROUND = CoordGrid(3103, 3261, 0)
        val MARK_SPAWN = CoordGrid(3098, 3259, 3)
    }
}

private var Player.courseProgress by intVarp("varp.agility_course_progress")
