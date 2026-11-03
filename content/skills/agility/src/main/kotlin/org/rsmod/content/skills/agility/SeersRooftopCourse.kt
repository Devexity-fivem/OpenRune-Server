package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SeersRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

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
                requiredLevel = 60,
                markSpawn = CoordGrid(2709, 3491, 2),
                obstacles =
                    listOf(
                        RooftopObstacle(1, listOf("loc.rooftops_seers_wallclimb"), 45.0, CoordGrid(2729, 3491, 3), ROOFTOP_CLIMB),
                        RooftopObstacle(2, listOf("loc.rooftops_seers_jump"), 20.0, CoordGrid(2713, 3493, 2)),
                        RooftopObstacle(
                            3,
                            listOf("loc.rooftops_seers_tightrope"),
                            20.0,
                            CoordGrid(2710, 3480, 2),
                            ROOFTOP_ROPE,
                            walk = true,
                            dir = FACE_SOUTH,
                        ),
                        RooftopObstacle(4, listOf("loc.rooftops_seers_jump_1"), 35.0, CoordGrid(2712, 3472, 3)),
                        RooftopObstacle(5, listOf("loc.rooftops_seers_jump_2"), 15.0, CoordGrid(2702, 3464, 2)),
                        RooftopObstacle(6, listOf("loc.rooftops_seers_leapdown"), 435.0, CoordGrid(2704, 3464, 0)),
                    ),
            )
    }
}
