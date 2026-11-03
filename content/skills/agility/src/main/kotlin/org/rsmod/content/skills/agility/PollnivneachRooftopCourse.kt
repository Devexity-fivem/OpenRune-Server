package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PollnivneachRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        for (obstacle in COURSE.obstacles) {
            if (obstacle.custom) continue
            for (loc in obstacle.locs) {
                onOpLoc1(loc) { attemptRooftop(COURSE, obstacle, marks) }
                if (obstacle.apRange > 0) {
                    onApLoc1(loc) {
                        if (isWithinApRange(it.loc, obstacle.apRange)) {
                            attemptRooftop(COURSE, obstacle, marks)
                        }
                    }
                }
            }
        }
        onOpLoc1("loc.rooftops_pollnivneach_monkeybars_start") { crossMonkeybars() }
        onOpLoc1("loc.rooftops_pollnivneach_line") { slideDryingLine() }
        onApLoc1("loc.rooftops_pollnivneach_line") {
            if (isWithinApRange(it.loc, LINE_AP_RANGE)) slideDryingLine()
        }
    }

    private suspend fun ProtectedAccess.crossMonkeybars() {
        if (!checkLevel()) return
        anim("seq.human_monkeybars_on")
        delay(MOUNT_TICKS)
        telejump(BARS_START)
        anim("seq.human_monkeybars_walk")
        exactMove(BARS_START, BARS_END, delay1 = 0, delay2 = BARS_TICKS * CYCLES_PER_TICK, dir = FACE_NORTH)
        delay(BARS_TICKS)
        anim("seq.human_monkeybars_off")
        delay(MOUNT_TICKS)
        resetAnim()
        statAdvance(AGILITY, BARS_XP)
        player.advanceCourse(OBSTACLE_BARS)
    }

    private suspend fun ProtectedAccess.slideDryingLine() {
        if (!checkLevel()) return
        telejump(LINE_TOP)
        anim("seq.zipline_bite")
        delay(GRAB_TICKS)
        anim("seq.zipline_slide")
        exactMove(LINE_TOP, LINE_GROUND, delay1 = 0, delay2 = SLIDE_TICKS * CYCLES_PER_TICK, dir = FACE_SOUTH)
        delay(SLIDE_TICKS)
        resetAnim()
        statAdvance(AGILITY, LINE_XP)
        if (player.completeLap(OBSTACLE_LINE)) {
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
        const val REQUIRED_LVL = 70
        const val CYCLES_PER_TICK = 30

        const val BARS_XP = 55.0
        const val LINE_XP = 540.0
        const val OBSTACLE_BARS = 7
        const val OBSTACLE_LINE = 9

        const val MOUNT_TICKS = 1
        const val BARS_TICKS = 7
        const val GRAB_TICKS = 1
        const val SLIDE_TICKS = 8
        const val LINE_AP_RANGE = 6

        val BARS_START = CoordGrid(3358, 2985, 2)
        val BARS_END = CoordGrid(3358, 2992, 2)
        val LINE_TOP = CoordGrid(3363, 3000, 2)
        val LINE_GROUND = CoordGrid(3357, 2989, 0)
        val MARK_SPAWN = CoordGrid(3360, 2993, 2)

        val COURSE =
            RooftopCourseSpec(
                requiredLevel = REQUIRED_LVL,
                markSpawn = MARK_SPAWN,
                obstacles =
                    listOf(
                        RooftopObstacle(1, listOf("loc.rooftops_pollnivneach_basket"), 10.0, CoordGrid(3351, 2964, 1), ROOFTOP_CLIMB),
                        RooftopObstacle(2, listOf("loc.rooftops_pollnivneach_marketstall"), 45.0, CoordGrid(3353, 2974, 1), apRange = 3),
                        RooftopObstacle(3, listOf("loc.rooftops_pollnivneach_hangingbanner"), 65.0, CoordGrid(3361, 2978, 1), seq = "seq.human_ropeswing", apRange = 3),
                        RooftopObstacle(4, listOf("loc.rooftops_pollnivneach_gap"), 35.0, CoordGrid(3367, 2975, 1), apRange = 3),
                        RooftopObstacle(5, listOf("loc.rooftops_pollnivneach_tree"), 75.0, CoordGrid(3364, 2982, 1), apRange = 3),
                        RooftopObstacle(6, listOf("loc.rooftops_pollnivneach_wallclimb"), 5.0, CoordGrid(3362, 2984, 2), ROOFTOP_CLIMB, apRange = 2),
                        RooftopObstacle(OBSTACLE_BARS, listOf("loc.rooftops_pollnivneach_monkeybars_start"), BARS_XP, BARS_END, custom = true),
                        RooftopObstacle(8, listOf("loc.rooftops_pollnivneach_treetop"), 60.0, CoordGrid(3363, 2999, 2), apRange = 4),
                        RooftopObstacle(OBSTACLE_LINE, listOf("loc.rooftops_pollnivneach_line"), LINE_XP, LINE_GROUND, apRange = LINE_AP_RANGE, custom = true),
                    ),
            )
    }
}
