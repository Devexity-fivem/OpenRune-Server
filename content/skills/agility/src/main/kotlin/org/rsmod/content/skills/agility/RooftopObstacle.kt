package org.rsmod.content.skills.agility

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.map.CoordGrid

internal class RooftopObstacle(
    val index: Int,
    val locs: List<String>,
    val xp: Double,
    val dest: CoordGrid,
    val seq: String = ROOFTOP_JUMP,
    val walk: Boolean = false,
    val dir: Int = 0,
    val apRange: Int = 0,
    val custom: Boolean = false,
)

internal class RooftopCourseSpec(
    val requiredLevel: Int,
    val markSpawn: CoordGrid,
    val obstacles: List<RooftopObstacle>,
) {
    val lastIndex: Int = obstacles.maxOf { it.index }
}

internal suspend fun ProtectedAccess.attemptRooftop(
    course: RooftopCourseSpec,
    obstacle: RooftopObstacle,
    marks: MarkOfGrace,
) {
    if (player.agilityLvl < course.requiredLevel) {
        mes("You need an Agility level of ${course.requiredLevel} to attempt this.")
        return
    }
    if (obstacle.walk) {
        val start = coords
        val ticks = start.chebyshevDistance(obstacle.dest)
        anim(obstacle.seq)
        exactMove(start, obstacle.dest, delay1 = 0, delay2 = ticks * CYCLES_PER_TICK, dir = obstacle.dir)
        delay(ticks)
        resetAnim()
    } else {
        anim(obstacle.seq)
        delay(ROOFTOP_MOVE_TICKS)
        telejump(obstacle.dest)
    }
    statAdvance("stat.agility", obstacle.xp)
    if (obstacle.index < course.lastIndex) {
        player.advanceCourse(obstacle.index)
    } else if (player.completeLap(course.lastIndex)) {
        marks.onLapCompleted(player, course.markSpawn, course.requiredLevel)
    }
}

internal const val ROOFTOP_JUMP = "seq.agility_shortcut_wall_jumpdown"
internal const val ROOFTOP_ROPE = "seq.human_walk_logbalance"
internal const val ROOFTOP_CLIMB = "seq.human_reachforladder"
internal const val ROOFTOP_SIDESTEP = "seq.human_walk_sidestepl"

internal const val FACE_SOUTH = 0
internal const val FACE_SOUTHWEST = 256
internal const val FACE_WEST = 512
internal const val FACE_NORTH = 1024
internal const val FACE_EAST = 1536

private const val CYCLES_PER_TICK = 30
private const val ROOFTOP_MOVE_TICKS = 2
