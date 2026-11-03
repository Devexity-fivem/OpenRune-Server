package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        for (obstacle in COURSE.obstacles) {
            for (loc in obstacle.locs) {
                onOpLoc1(loc) { attemptRooftop(COURSE, obstacle, marks) }
            }
        }
    }

    private companion object {
        val COURSE =
            RooftopCourseSpec(
                requiredLevel = 50,
                markSpawn = CoordGrid(3046, 3364, 3),
                obstacles =
                    listOf(
                        RooftopObstacle(1, listOf("loc.rooftops_falador_wallclimb"), 11.0, CoordGrid(3036, 3342, 3), ROOFTOP_CLIMB),
                        RooftopObstacle(
                            2,
                            listOf("loc.rooftops_falador_tightrope_1"),
                            22.0,
                            CoordGrid(3047, 3343, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_EAST,
                        ),
                        RooftopObstacle(
                            3,
                            listOf("loc.rooftops_falador_handholds_start"),
                            61.0,
                            CoordGrid(3050, 3358, 3),
                            ROOFTOP_SIDESTEP,
                            walk = true,
                            dir = FACE_NORTH,
                        ),
                        RooftopObstacle(4, listOf("loc.rooftops_falador_gap_1"), 27.0, CoordGrid(3048, 3361, 3)),
                        RooftopObstacle(5, listOf("loc.rooftops_falador_gap_2"), 26.0, CoordGrid(3041, 3361, 3)),
                        RooftopObstacle(
                            6,
                            listOf("loc.rooftops_falador_tightrope_2"),
                            61.0,
                            CoordGrid(3028, 3354, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_SOUTHWEST,
                        ),
                        RooftopObstacle(
                            7,
                            listOf("loc.rooftops_falador_tightrope_3"),
                            53.0,
                            CoordGrid(3020, 3353, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_WEST,
                        ),
                        RooftopObstacle(8, listOf("loc.rooftops_falador_gap_3"), 30.0, CoordGrid(3017, 3349, 3)),
                        RooftopObstacle(9, listOf("loc.rooftops_falador_ledge_1"), 14.0, CoordGrid(3014, 3345, 3)),
                        RooftopObstacle(10, listOf("loc.rooftops_falador_ledge_2"), 13.0, CoordGrid(3012, 3342, 3)),
                        RooftopObstacle(
                            11,
                            listOf("loc.rooftops_falador_ledge_3a", "loc.rooftops_falador_ledge_3b"),
                            13.0,
                            CoordGrid(3014, 3333, 3),
                        ),
                        RooftopObstacle(12, listOf("loc.rooftops_falador_ledge_4"), 14.0, CoordGrid(3019, 3333, 3)),
                        RooftopObstacle(13, listOf("loc.rooftops_falador_edge"), 241.0, CoordGrid(3029, 3333, 0)),
                    ),
            )
    }
}
