package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.config.Constants.em_face_east
import org.rsmod.api.config.Constants.em_face_south
import org.rsmod.api.config.Constants.em_face_west
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AlKharidRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_kharid_wallclimb") { climbRoughWall() }
        onOpLoc1("loc.rooftops_kharid_tightrope_1") { crossFirstTightrope() }
        onOpLoc1("loc.rooftops_kharid_rope_swing") { swingCable() }
        onOpLoc1("loc.rooftops_kharid_slide_side") { rideZipLine() }
        onOpLoc1("loc.rooftops_kharid_bamboo_tree_top") { swingTropicalTree() }
        onOpLoc1("loc.rooftops_kharid_wallclimb_2") { climbBeams() }
        onOpLoc1("loc.rooftops_kharid_tightrope_4") { crossSecondTightrope() }
        onOpLoc1("loc.rooftops_kharid_leapdown") { jumpGap() }
    }

    private suspend fun ProtectedAccess.climbRoughWall() {
        if (!checkLevel()) return
        anim("seq.human_reachforladder")
        delay(CLIMB_TICKS)
        telejump(ROUGH_WALL_TOP)
        statAdvance(AGILITY, ROUGH_WALL_XP)
        player.advanceCourse(OBSTACLE_ROUGH_WALL)
    }

    private suspend fun ProtectedAccess.crossFirstTightrope() {
        if (!checkLevel()) return
        walkRope(TIGHTROPE_1_START, TIGHTROPE_1_END, em_face_south)
        statAdvance(AGILITY, TIGHTROPE_1_XP)
        player.advanceCourse(OBSTACLE_TIGHTROPE_1)
    }

    private suspend fun ProtectedAccess.swingCable() {
        if (!checkLevel()) return
        anim("seq.human_ropeswing_long")
        exactMove(coords, CABLE_LANDING, delay1 = SWING_DELAY_CYCLES, delay2 = CABLE_CYCLES, dir = em_face_east)
        delay(CABLE_TICKS)
        statAdvance(AGILITY, CABLE_XP)
        player.advanceCourse(OBSTACLE_CABLE)
    }

    private suspend fun ProtectedAccess.rideZipLine() {
        if (!checkLevel()) return
        telejump(ZIP_LINE_START)
        anim("seq.zipline_bite")
        delay(ZIP_GRAB_TICKS)
        anim("seq.zipline_slide")
        exactMove(coords, ZIP_LINE_END, delay1 = 0, delay2 = ZIP_LINE_CYCLES, dir = em_face_east)
        delay(ZIP_LINE_TICKS)
        statAdvance(AGILITY, ZIP_LINE_XP)
        player.advanceCourse(OBSTACLE_ZIP_LINE)
    }

    private suspend fun ProtectedAccess.swingTropicalTree() {
        if (!checkLevel()) return
        anim("seq.human_ropeswing")
        delay(TREE_TICKS)
        telejump(TREE_LANDING)
        statAdvance(AGILITY, TREE_XP)
        player.advanceCourse(OBSTACLE_TROPICAL_TREE)
    }

    private suspend fun ProtectedAccess.climbBeams() {
        if (!checkLevel()) return
        anim("seq.human_reachforladder")
        delay(CLIMB_TICKS)
        telejump(BEAMS_TOP)
        statAdvance(AGILITY, BEAMS_XP)
        player.advanceCourse(OBSTACLE_BEAMS)
    }

    private suspend fun ProtectedAccess.crossSecondTightrope() {
        if (!checkLevel()) return
        walkRope(TIGHTROPE_2_START, TIGHTROPE_2_END, em_face_west)
        statAdvance(AGILITY, TIGHTROPE_2_XP)
        player.advanceCourse(OBSTACLE_TIGHTROPE_2)
    }

    private suspend fun ProtectedAccess.jumpGap() {
        if (!checkLevel()) return
        anim("seq.agility_shortcut_wall_jumpdown")
        delay(GAP_TICKS)
        telejump(GAP_GROUND)
        statAdvance(AGILITY, GAP_XP)
        if (player.completeLap(OBSTACLE_GAP)) {
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    private suspend fun ProtectedAccess.walkRope(start: CoordGrid, end: CoordGrid, dir: Int) {
        val ticks = start.chebyshevDistance(end)
        telejump(start)
        anim("seq.human_walk_logbalance")
        exactMove(start, end, delay1 = 0, delay2 = ticks * CYCLES_PER_TICK, dir = dir)
        delay(ticks)
    }

    private fun ProtectedAccess.checkLevel(): Boolean {
        if (player.agilityLvl >= REQUIRED_LVL) return true
        mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
        return false
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val REQUIRED_LVL = 20
        const val CYCLES_PER_TICK = 30

        const val ROUGH_WALL_XP = 12.0
        const val TIGHTROPE_1_XP = 36.0
        const val CABLE_XP = 48.0
        const val ZIP_LINE_XP = 48.0
        const val TREE_XP = 12.0
        const val BEAMS_XP = 6.0
        const val TIGHTROPE_2_XP = 18.0
        const val GAP_XP = 36.0

        const val OBSTACLE_ROUGH_WALL = 1
        const val OBSTACLE_TIGHTROPE_1 = 2
        const val OBSTACLE_CABLE = 3
        const val OBSTACLE_ZIP_LINE = 4
        const val OBSTACLE_TROPICAL_TREE = 5
        const val OBSTACLE_BEAMS = 6
        const val OBSTACLE_TIGHTROPE_2 = 7
        const val OBSTACLE_GAP = 8

        const val CLIMB_TICKS = 2
        const val SWING_DELAY_CYCLES = 30
        const val CABLE_TICKS = 6
        const val CABLE_CYCLES = CABLE_TICKS * CYCLES_PER_TICK
        const val ZIP_GRAB_TICKS = 1
        const val ZIP_LINE_TICKS = 6
        const val ZIP_LINE_CYCLES = ZIP_LINE_TICKS * CYCLES_PER_TICK
        const val TREE_TICKS = 3
        const val GAP_TICKS = 2

        val ROUGH_WALL_TOP = CoordGrid(3273, 3192, 3)
        val TIGHTROPE_1_START = CoordGrid(3272, 3182, 3)
        val TIGHTROPE_1_END = CoordGrid(3272, 3172, 3)
        val CABLE_LANDING = CoordGrid(3284, 3166, 3)
        val ZIP_LINE_START = CoordGrid(3301, 3163, 3)
        val ZIP_LINE_END = CoordGrid(3315, 3163, 1)
        val TREE_LANDING = CoordGrid(3317, 3174, 2)
        val BEAMS_TOP = CoordGrid(3316, 3180, 3)
        val TIGHTROPE_2_START = CoordGrid(3314, 3186, 3)
        val TIGHTROPE_2_END = CoordGrid(3302, 3186, 3)
        val GAP_GROUND = CoordGrid(3299, 3194, 0)
        val MARK_SPAWN = CoordGrid(3290, 3164, 3)
    }
}
