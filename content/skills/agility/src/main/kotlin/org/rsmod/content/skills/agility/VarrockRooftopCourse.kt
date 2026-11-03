package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.config.Constants.em_face_west
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_varrock_wallclimb") {
            climb(ROUGH_WALL_TOP, ROUGH_WALL_XP, OBSTACLE_ROUGH_WALL)
        }
        onOpLoc1("loc.rooftops_varrock_clothesline") { crossClothesLine() }
        onOpLoc1("loc.rooftops_varrock_leaptoruins") { leap(RUINS_LANDING, GAP_1_XP, OBSTACLE_GAP_1) }
        onApLoc1("loc.rooftops_varrock_wallswing") {
            if (isWithinApRange(it.loc, WALL_AP_RANGE)) balanceWall()
        }
        onOpLoc1("loc.rooftops_varrock_wallscramble") { leap(GAP_2_LANDING, GAP_2_XP, OBSTACLE_GAP_2) }
        onOpLoc1("loc.rooftops_varrock_leaptobalcony") { leap(GAP_3_LANDING, GAP_3_XP, OBSTACLE_GAP_3) }
        onOpLoc1("loc.rooftops_varrock_leapdown") { leap(GAP_4_LANDING, GAP_4_XP, OBSTACLE_GAP_4) }
        onOpLoc1("loc.rooftops_varrock_stepuproof") { leap(LEDGE_TOP, LEDGE_XP, OBSTACLE_LEDGE) }
        onOpLoc1("loc.rooftops_varrock_finish") { jumpOffEdge() }
    }

    private suspend fun ProtectedAccess.climb(dest: CoordGrid, xp: Double, index: Int) {
        if (!checkLevel()) return
        anim("seq.human_reachforladder")
        delay(CLIMB_TICKS)
        telejump(dest)
        statAdvance(AGILITY, xp)
        player.advanceCourse(index)
    }

    private suspend fun ProtectedAccess.crossClothesLine() {
        if (!checkLevel()) return
        val ticks = CLOTHES_LINE_START.chebyshevDistance(CLOTHES_LINE_END)
        telejump(CLOTHES_LINE_START)
        anim("seq.human_walk_logbalance")
        exactMove(
            CLOTHES_LINE_START,
            CLOTHES_LINE_END,
            delay1 = 0,
            delay2 = ticks * CYCLES_PER_TICK,
            dir = em_face_west,
        )
        delay(ticks)
        statAdvance(AGILITY, CLOTHES_LINE_XP)
        player.advanceCourse(OBSTACLE_CLOTHES_LINE)
    }

    private suspend fun ProtectedAccess.balanceWall() {
        if (!checkLevel()) return
        anim("seq.human_walk_sidestepl")
        delay(WALL_TICKS)
        telejump(WALL_LANDING)
        statAdvance(AGILITY, WALL_XP)
        player.advanceCourse(OBSTACLE_WALL)
    }

    private suspend fun ProtectedAccess.leap(dest: CoordGrid, xp: Double, index: Int) {
        if (!checkLevel()) return
        anim("seq.agility_shortcut_wall_jumpdown")
        delay(LEAP_TICKS)
        telejump(dest)
        statAdvance(AGILITY, xp)
        player.advanceCourse(index)
    }

    private suspend fun ProtectedAccess.jumpOffEdge() {
        if (!checkLevel()) return
        anim("seq.agility_shortcut_wall_jumpdown")
        delay(LEAP_TICKS)
        telejump(EDGE_GROUND)
        statAdvance(AGILITY, EDGE_XP)
        if (player.completeLap(OBSTACLE_EDGE)) {
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    private fun ProtectedAccess.checkLevel(): Boolean {
        if (player.agilityLvl >= REQUIRED_LVL) return true
        mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
        return false
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val REQUIRED_LVL = 30
        const val CYCLES_PER_TICK = 30

        const val ROUGH_WALL_XP = 13.5
        const val CLOTHES_LINE_XP = 23.0
        const val GAP_1_XP = 19.0
        const val WALL_XP = 28.0
        const val GAP_2_XP = 10.0
        const val GAP_3_XP = 24.5
        const val GAP_4_XP = 4.5
        const val LEDGE_XP = 3.5
        const val EDGE_XP = 143.7

        const val OBSTACLE_ROUGH_WALL = 1
        const val OBSTACLE_CLOTHES_LINE = 2
        const val OBSTACLE_GAP_1 = 3
        const val OBSTACLE_WALL = 4
        const val OBSTACLE_GAP_2 = 5
        const val OBSTACLE_GAP_3 = 6
        const val OBSTACLE_GAP_4 = 7
        const val OBSTACLE_LEDGE = 8
        const val OBSTACLE_EDGE = 9

        const val CLIMB_TICKS = 2
        const val WALL_TICKS = 4
        const val WALL_AP_RANGE = 2
        const val LEAP_TICKS = 2

        val ROUGH_WALL_TOP = CoordGrid(3219, 3414, 3)
        val CLOTHES_LINE_START = CoordGrid(3214, 3414, 3)
        val CLOTHES_LINE_END = CoordGrid(3208, 3414, 3)
        val RUINS_LANDING = CoordGrid(3197, 3416, 1)
        val WALL_LANDING = CoordGrid(3192, 3406, 3)
        val GAP_2_LANDING = CoordGrid(3195, 3398, 3)
        val GAP_3_LANDING = CoordGrid(3218, 3399, 3)
        val GAP_4_LANDING = CoordGrid(3236, 3403, 3)
        val LEDGE_TOP = CoordGrid(3238, 3410, 3)
        val EDGE_GROUND = CoordGrid(3236, 3417, 0)
        val MARK_SPAWN = CoordGrid(3195, 3404, 3)
    }
}
