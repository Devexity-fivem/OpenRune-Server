package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

class BarbarianOutpostCourseGameTest {

    private class CourseLocs(
        val swing: BoundLocInfo,
        val log: BoundLocInfo,
        val net: BoundLocInfo,
        val ledge: BoundLocInfo,
        val ladder: BoundLocInfo,
        val wall1: BoundLocInfo,
        val wall2: BoundLocInfo,
        val wall3: BoundLocInfo,
    )

    private fun GameTestScope.placeCourse(): CourseLocs =
        CourseLocs(
            swing = placeMapLoc(CoordGrid(2551, 3552, 0), "loc.obstical_ropeswing1"),
            log = placeMapLoc(CoordGrid(2550, 3546, 0), "loc.barbarian_log_balance1"),
            net = placeMapLoc(CoordGrid(2538, 3545, 0), "loc.agility_obstical_net_barbarian"),
            ledge = placeMapLoc(CoordGrid(2535, 3547, 1), "loc.balancing_ledge1"),
            ladder = placeMapLoc(CoordGrid(2532, 3545, 1), "loc.barbarian_laddertop_norim"),
            wall1 = placeMapLoc(CoordGrid(2536, 3553, 0), "loc.castlecrumbly1"),
            wall2 = placeMapLoc(CoordGrid(2539, 3553, 0), "loc.castlecrumbly1"),
            wall3 = placeMapLoc(CoordGrid(2542, 3553, 0), "loc.castlecrumbly1"),
        )

    private fun GameTestScope.setAgility(level: Int) {
        player.setBaseLevel("stat.agility", level)
        player.statMap.setCurrentLevel("stat.agility", level.toByte())
    }

    private fun GameTestScope.runTail(locs: CourseLocs) {
        // net
        player.teleport(CoordGrid(2540, 3545, 0))
        player.opLoc1(locs.net)
        advance(4)
        // ledge east -> west
        player.teleport(CoordGrid(2536, 3547, 1))
        player.opLoc1(locs.ledge)
        advance(6)
        // ladder down
        player.teleport(CoordGrid(2532, 3546, 1))
        player.opLoc1(locs.ladder)
        advance(4)
        // walls
        player.teleport(CoordGrid(2535, 3553, 0))
        player.opLoc1(locs.wall1)
        advance(4)
        player.opLoc1(locs.wall2)
        advance(4)
        player.opLoc1(locs.wall3)
        advance(4)
    }

    @Test
    fun GameTestState.`full lap awards obstacle xp plus completion bonuses`() =
        runGameTest(BarbarianOutpostCourse::class) {
            val locs = placeCourse()
            setAgility(35)

            random.next = 0
            player.teleport(CoordGrid(2551, 3554, 0))
            player.opLoc1(locs.swing)
            advance(6)
            assertEquals(1, player.courseProgress)

            player.teleport(CoordGrid(2550, 3546, 0))
            player.opLoc1(locs.log)
            advance(12)
            assertEquals(2, player.courseProgress)

            runTail(locs)
            assertEquals(0, player.courseProgress)
            assertEquals(1533, player.statMap.getFineXP("stat.agility"))
            assertEquals(413, player.statMap.getFineXP("stat.strength"))
        }

    @Test
    fun GameTestState.`log fail keeps progress and retry completes lap`() =
        runGameTest(BarbarianOutpostCourse::class) {
            val locs = placeCourse()
            setAgility(35)

            random.next = 0
            player.teleport(CoordGrid(2551, 3554, 0))
            player.opLoc1(locs.swing)
            advance(6)
            assertEquals(1, player.courseProgress)

            random.next = 255
            player.teleport(CoordGrid(2550, 3546, 0))
            player.opLoc1(locs.log)
            advance(12)
            assertEquals(1, player.courseProgress)

            random.next = 0
            player.teleport(CoordGrid(2550, 3546, 0))
            player.opLoc1(locs.log)
            advance(12)
            assertEquals(2, player.courseProgress)

            runTail(locs)
            assertEquals(0, player.courseProgress)
            assertEquals(1533, player.statMap.getFineXP("stat.agility"))
            assertEquals(413, player.statMap.getFineXP("stat.strength"))
        }

    @Test
    fun GameTestState.`ledge fail keeps progress and retry completes lap`() =
        runGameTest(BarbarianOutpostCourse::class) {
            val locs = placeCourse()
            setAgility(35)

            random.next = 0
            player.teleport(CoordGrid(2551, 3554, 0))
            player.opLoc1(locs.swing)
            advance(6)
            player.teleport(CoordGrid(2550, 3546, 0))
            player.opLoc1(locs.log)
            advance(12)
            player.teleport(CoordGrid(2540, 3545, 0))
            player.opLoc1(locs.net)
            advance(4)
            assertEquals(3, player.courseProgress)

            random.next = 255
            player.teleport(CoordGrid(2536, 3547, 1))
            player.opLoc1(locs.ledge)
            advance(6)
            assertEquals(3, player.courseProgress)

            // Climbing back lands on the west walkway; re-crossing west grants progress again.
            random.next = 0
            player.teleport(CoordGrid(2536, 3547, 1))
            player.opLoc1(locs.ledge)
            advance(6)
            assertEquals(4, player.courseProgress)

            player.teleport(CoordGrid(2532, 3546, 1))
            player.opLoc1(locs.ladder)
            advance(4)
            assertEquals(5, player.courseProgress)

            player.teleport(CoordGrid(2535, 3553, 0))
            player.opLoc1(locs.wall1)
            advance(4)
            player.opLoc1(locs.wall2)
            advance(4)
            player.opLoc1(locs.wall3)
            advance(4)
            assertEquals(0, player.courseProgress)
            assertEquals(1533, player.statMap.getFineXP("stat.agility"))
            assertEquals(413, player.statMap.getFineXP("stat.strength"))
        }

    @Test
    fun GameTestState.`swing fail grants no progress`() =
        runGameTest(BarbarianOutpostCourse::class) {
            val locs = placeCourse()
            setAgility(35)

            random.next = 255
            player.teleport(CoordGrid(2551, 3554, 0))
            player.opLoc1(locs.swing)
            advance(6)
            assertEquals(0, player.courseProgress)
        }

    @Test
    fun GameTestState.`below level 35 is rejected`() =
        runGameTest(BarbarianOutpostCourse::class) {
            val locs = placeCourse()
            setAgility(30)
            player.teleport(CoordGrid(2551, 3554, 0))
            player.opLoc1(locs.swing)
            advance(4)
            assertEquals(0, player.courseProgress)
            assertEquals(0, player.statMap.getFineXP("stat.agility"))
        }
}

private var Player.courseProgress by intVarp("varp.agility_course_progress")
