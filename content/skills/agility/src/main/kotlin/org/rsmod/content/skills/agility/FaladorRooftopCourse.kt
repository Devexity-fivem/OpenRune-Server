package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        for (obstacle in OBSTACLES) {
            for (loc in obstacle.locs) {
                onOpLoc1(loc) { attempt(obstacle) }
            }
        }
    }

    private suspend fun ProtectedAccess.attempt(obstacle: Obstacle) {
        if (player.agilityLvl < REQUIRED_LVL) {
            mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
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
            delay(MOVE_TICKS)
            telejump(obstacle.dest)
        }
        statAdvance(AGILITY, obstacle.xp)
        if (obstacle.index < LAST_OBSTACLE) {
            player.advanceCourse(obstacle.index)
        } else if (player.completeLap(LAST_OBSTACLE)) {
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    private class Obstacle(
        val index: Int,
        val locs: List<String>,
        val xp: Double,
        val dest: CoordGrid,
        val seq: String = JUMP,
        val walk: Boolean = false,
        val dir: Int = 0,
    )

    private companion object {
        const val AGILITY = "stat.agility"
        const val REQUIRED_LVL = 50
        const val CYCLES_PER_TICK = 30
        const val MOVE_TICKS = 2
        const val LAST_OBSTACLE = 13

        const val JUMP = "seq.agility_shortcut_wall_jumpdown"
        const val ROPE = "seq.human_walk_logbalance"
        const val CLIMB = "seq.human_reachforladder"
        const val SIDESTEP = "seq.human_walk_sidestepl"

        const val FACE_NORTH = 1024
        const val FACE_EAST = 1536
        const val FACE_WEST = 512
        const val FACE_SOUTHWEST = 256

        val OBSTACLES =
            listOf(
                Obstacle(1, listOf("loc.rooftops_falador_wallclimb"), 11.0, CoordGrid(3036, 3342, 3), CLIMB),
                Obstacle(
                    2,
                    listOf("loc.rooftops_falador_tightrope_1"),
                    22.0,
                    CoordGrid(3047, 3343, 3),
                    ROPE,
                    walk = true,
                    dir = FACE_EAST,
                ),
                Obstacle(
                    3,
                    listOf("loc.rooftops_falador_handholds_start"),
                    61.0,
                    CoordGrid(3050, 3358, 3),
                    SIDESTEP,
                    walk = true,
                    dir = FACE_NORTH,
                ),
                Obstacle(4, listOf("loc.rooftops_falador_gap_1"), 27.0, CoordGrid(3048, 3361, 3)),
                Obstacle(5, listOf("loc.rooftops_falador_gap_2"), 26.0, CoordGrid(3041, 3361, 3)),
                Obstacle(
                    6,
                    listOf("loc.rooftops_falador_tightrope_2"),
                    61.0,
                    CoordGrid(3028, 3354, 3),
                    ROPE,
                    walk = true,
                    dir = FACE_SOUTHWEST,
                ),
                Obstacle(
                    7,
                    listOf("loc.rooftops_falador_tightrope_3"),
                    53.0,
                    CoordGrid(3020, 3353, 3),
                    ROPE,
                    walk = true,
                    dir = FACE_WEST,
                ),
                Obstacle(8, listOf("loc.rooftops_falador_gap_3"), 30.0, CoordGrid(3017, 3349, 3)),
                Obstacle(9, listOf("loc.rooftops_falador_ledge_1"), 14.0, CoordGrid(3014, 3345, 3)),
                Obstacle(10, listOf("loc.rooftops_falador_ledge_2"), 13.0, CoordGrid(3012, 3342, 3)),
                Obstacle(
                    11,
                    listOf("loc.rooftops_falador_ledge_3a", "loc.rooftops_falador_ledge_3b"),
                    13.0,
                    CoordGrid(3014, 3333, 3),
                ),
                Obstacle(12, listOf("loc.rooftops_falador_ledge_4"), 14.0, CoordGrid(3019, 3333, 3)),
                Obstacle(13, listOf("loc.rooftops_falador_edge"), 241.0, CoordGrid(3029, 3333, 0)),
            )

        val MARK_SPAWN = CoordGrid(3046, 3364, 3)
    }
}
