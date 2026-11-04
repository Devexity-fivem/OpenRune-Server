package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ArdougneRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

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
                requiredLevel = 90,
                markSpawn = CoordGrid(2666, 3297, 0),
                markDenominator = 3,
                obstacles =
                    listOf(
                        RooftopObstacle(
                            1,
                            listOf("loc.rooftops_ardy_wallclimb"),
                            43.0,
                            CoordGrid(2671, 3299, 3),
                            ROOFTOP_CLIMB,
                            apRange = 3,
                        ),
                        RooftopObstacle(
                            2,
                            listOf("loc.rooftops_ardy_jump"),
                            65.0,
                            CoordGrid(2665, 3318, 3),
                            apRange = 3,
                        ),
                        RooftopObstacle(
                            3,
                            listOf("loc.rooftops_ardy_plank"),
                            50.0,
                            CoordGrid(2656, 3318, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_WEST,
                            requiredLevel = 1,
                        ),
                        RooftopObstacle(
                            4,
                            listOf("loc.rooftops_ardy_jump_2"),
                            21.0,
                            CoordGrid(2653, 3314, 3),
                            apRange = 3,
                        ),
                        RooftopObstacle(
                            5,
                            listOf("loc.rooftops_ardy_jump_3"),
                            28.0,
                            CoordGrid(2651, 3309, 3),
                            requiredLevel = 1,
                        ),
                        RooftopObstacle(
                            6,
                            listOf("loc.rooftops_ardy_wallcrossing"),
                            57.0,
                            CoordGrid(2656, 3297, 3),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_SOUTHEAST,
                            apRange = 2,
                            requiredLevel = 1,
                        ),
                        RooftopObstacle(
                            7,
                            listOf("loc.rooftops_ardy_jump_4"),
                            625.0,
                            CoordGrid(2666, 3297, 0),
                            apRange = 2,
                            requiredLevel = 1,
                        ),
                    ),
            )
    }
}
