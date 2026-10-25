package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

class GnomeAgilityCourseGameTest {

    private class CourseLocs(
        val log: BoundLocInfo,
        val netUp: BoundLocInfo,
        val branchUp: BoundLocInfo,
        val rope: BoundLocInfo,
        val branchDown: BoundLocInfo,
        val netOver: BoundLocInfo,
        val pipe: BoundLocInfo,
    )

    private fun GameTestScope.placeCourse(): CourseLocs =
        CourseLocs(
            log = placeMapLoc(CoordGrid(2474, 3434, 0), "loc.gnome_log_balance1"),
            netUp = placeMapLoc(CoordGrid(2473, 3425, 0), "loc.obstical_net2"),
            branchUp = placeMapLoc(CoordGrid(2473, 3422, 1), "loc.climbing_branch"),
            rope = placeMapLoc(CoordGrid(2478, 3420, 2), "loc.balancing_rope"),
            branchDown = placeMapLoc(CoordGrid(2486, 3419, 2), "loc.climbing_tree"),
            netOver = placeMapLoc(CoordGrid(2485, 3426, 0), "loc.obstical_net3"),
            pipe = placeMapLoc(CoordGrid(2484, 3431, 0), "loc.obstical_pipe3_1"),
        )

    private fun GameTestScope.runLap(locs: CourseLocs) {
        player.observerUUID = 1
        player.teleport(CoordGrid(2474, 3436, 0))
        player.opLoc1(locs.log)
        advance(12)
        player.teleport(CoordGrid(2473, 3427, 0))
        player.opLoc1(locs.netUp)
        advance(4)
        player.opLoc1(locs.branchUp)
        advance(4)
        player.teleport(CoordGrid(2477, 3420, 2))
        player.opLoc1(locs.rope)
        advance(10)
        player.teleport(CoordGrid(2485, 3419, 2))
        player.opLoc1(locs.branchDown)
        advance(4)
        player.teleport(CoordGrid(2485, 3424, 0))
        player.opLoc1(locs.netOver)
        advance(4)
        player.teleport(CoordGrid(2484, 3430, 0))
        player.opLoc1(locs.pipe)
        advance(8)
    }

    @Test
    fun GameTestState.`full lap awards obstacle xp plus single completion bonus`() =
        runGameTest(GnomeAgilityCourse::class) {
            val locs = placeCourse()
            player.teleport(CoordGrid(2474, 3436, 0))
            player.opLoc1(locs.log)
            advance(12)
            assertEquals(CoordGrid(2474, 3429, 0), player.coords)
            assertEquals(1, player.courseProgress)

            player.teleport(CoordGrid(2473, 3427, 0))
            player.opLoc1(locs.netUp)
            advance(4)
            assertEquals(CoordGrid(2473, 3424, 1), player.coords)
            assertEquals(2, player.courseProgress)

            player.opLoc1(locs.branchUp)
            advance(4)
            assertEquals(CoordGrid(2473, 3420, 2), player.coords)
            assertEquals(3, player.courseProgress)

            player.teleport(CoordGrid(2477, 3420, 2))
            player.opLoc1(locs.rope)
            advance(10)
            assertEquals(CoordGrid(2483, 3420, 2), player.coords)
            assertEquals(4, player.courseProgress)

            player.teleport(CoordGrid(2485, 3419, 2))
            player.opLoc1(locs.branchDown)
            advance(4)
            assertEquals(CoordGrid(2486, 3419, 0), player.coords)
            assertEquals(5, player.courseProgress)

            player.teleport(CoordGrid(2485, 3424, 0))
            player.opLoc1(locs.netOver)
            advance(4)
            assertEquals(CoordGrid(2485, 3427, 0), player.coords)
            assertEquals(6, player.courseProgress)

            player.teleport(CoordGrid(2484, 3430, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            assertEquals(CoordGrid(2484, 3437, 0), player.coords)
            assertEquals(0, player.courseProgress)
            assertEquals(1105, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`pipe alone gives obstacle xp but no completion bonus`() =
        runGameTest(GnomeAgilityCourse::class) {
            val locs = placeCourse()
            player.teleport(CoordGrid(2484, 3430, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            assertEquals(CoordGrid(2484, 3437, 0), player.coords)
            assertEquals(75, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`repeated pipe runs cannot farm the bonus`() =
        runGameTest(GnomeAgilityCourse::class) {
            val locs = placeCourse()
            runLap(locs)
            assertEquals(1105, player.statMap.getFineXP("stat.agility"))

            player.teleport(CoordGrid(2484, 3430, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            player.teleport(CoordGrid(2484, 3430, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            assertEquals(1105 + 75 + 75, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`out of order obstacle resets lap progress`() =
        runGameTest(GnomeAgilityCourse::class) {
            val locs = placeCourse()
            player.teleport(CoordGrid(2474, 3436, 0))
            player.opLoc1(locs.log)
            advance(12)
            assertEquals(1, player.courseProgress)

            player.teleport(CoordGrid(2473, 3424, 1))
            player.opLoc1(locs.branchUp)
            advance(4)
            assertEquals(0, player.courseProgress)

            player.teleport(CoordGrid(2484, 3430, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            assertEquals(100 + 65 + 75, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`second lap awards a fresh completion bonus`() =
        runGameTest(GnomeAgilityCourse::class) {
            val locs = placeCourse()
            runLap(locs)
            runLap(locs)
            assertEquals(2210, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`mark of grace spawns at branch platform on completed lap`() =
        runInjectedGameTest(ObjRegistry::class, null, GnomeAgilityCourse::class) { objs ->
            val locs = placeCourse()
            random.next = 0
            runLap(locs)
            val marks = objs.findAll(MARK_TILE).filter { it.isVisibleTo(player) }.toList()
            assertEquals(1, marks.size)
        }

    @Test
    fun GameTestState.`mark roll failure spawns nothing`() =
        runInjectedGameTest(ObjRegistry::class, null, GnomeAgilityCourse::class) { objs ->
            val locs = placeCourse()
            random.next = 5
            runLap(locs)
            assertEquals(0, objs.findAll(MARK_TILE).count())
        }

    @Test
    fun GameTestState.`mark cooldown blocks respawn within three game minutes`() =
        runInjectedGameTest(ObjRegistry::class, null, GnomeAgilityCourse::class) { objs ->
            val locs = placeCourse()
            random.next = 0
            runLap(locs)
            assertEquals(1, objs.findAll(MARK_TILE).count())

            random.next = 0
            runLap(locs)
            assertEquals(1, objs.findAll(MARK_TILE).count())
        }

    @Test
    fun GameTestState.`level penalty shrinks mark roll denominator`() =
        runInjectedGameTest(ObjRegistry::class, null, GnomeAgilityCourse::class) { objs ->
            val locs = placeCourse()
            player.setBaseLevel("stat.agility", 21)
            random.next = 2
            runLap(locs)
            assertEquals(0, objs.findAll(MARK_TILE).count())
        }

    private companion object {
        val MARK_TILE = CoordGrid(2484, 3418, 2)
    }
}

private var Player.courseProgress by intVarp("varp.agility_course_progress")
