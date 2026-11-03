package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RellekkaRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        for (obstacle in COURSE.obstacles) {
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
    }

    private companion object {
        val COURSE =
            RooftopCourseSpec(
                requiredLevel = 80,
                markSpawn = CoordGrid(2646, 3660, 3),
                obstacles =
                    listOf(
                        RooftopObstacle(
                            1,
                            listOf("loc.rooftops_rellekka_wallclimb"),
                            20.0,
                            CoordGrid(2625, 3676, 3),
                            ROOFTOP_CLIMB,
                            apRange = 2,
                        ),
                        RooftopObstacle(
                            2,
                            listOf("loc.rooftops_rellekka_gap_1"),
                            30.0,
                            CoordGrid(2622, 3668, 3),
                        ),
                        RooftopObstacle(
                            3,
                            listOf("loc.rooftops_rellekka_tightrope_1"),
                            40.0,
                            CoordGrid(2628, 3654, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_SOUTHEAST,
                        ),
                        RooftopObstacle(
                            4,
                            listOf("loc.rooftops_rellekka_gap_2"),
                            85.0,
                            CoordGrid(2641, 3653, 3),
                            apRange = 3,
                        ),
                        RooftopObstacle(
                            5,
                            listOf("loc.rooftops_rellekka_gap_3"),
                            25.0,
                            CoordGrid(2644, 3657, 3),
                        ),
                        RooftopObstacle(
                            6,
                            listOf("loc.rooftops_rellekka_tightrope_3"),
                            105.0,
                            CoordGrid(2655, 3670, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_NORTHEAST,
                        ),
                        RooftopObstacle(
                            7,
                            listOf("loc.rooftops_rellekka_dropoff"),
                            475.0,
                            CoordGrid(2655, 3676, 0),
                        ),
                    ),
            )
    }
}
