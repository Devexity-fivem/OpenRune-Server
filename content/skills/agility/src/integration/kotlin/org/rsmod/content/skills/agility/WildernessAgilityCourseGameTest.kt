package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

class WildernessAgilityCourseGameTest {

    private class CourseLocs(
        val gate: BoundLocInfo,
        val pipe: BoundLocInfo,
        val swing: BoundLocInfo,
        val stone: BoundLocInfo,
        val log: BoundLocInfo,
        val rocks: BoundLocInfo,
        val dispenser: BoundLocInfo,
    )

    private fun GameTestScope.placeCourse(): CourseLocs =
        CourseLocs(
            gate = placeMapLoc(CoordGrid(2998, 3931, 0), "loc.balancegate52b_left"),
            pipe = placeMapLoc(CoordGrid(3004, 3938, 0), "loc.obstical_pipe2"),
            swing = placeMapLoc(CoordGrid(3005, 3954, 0), "loc.obstical_ropeswing2"),
            stone = placeMapLoc(CoordGrid(3001, 3960, 0), "loc.steppingstone1"),
            log = placeMapLoc(CoordGrid(3001, 3945, 0), "loc.wilderness_log_balance1"),
            rocks = placeMapLoc(CoordGrid(2994, 3936, 0), "loc.wildclimbingrock"),
            dispenser = placeMapLoc(CoordGrid(3005, 3936, 0), "loc.wildy_agility_pillar"),
        )

    private fun GameTestScope.setAgility(level: Int) {
        player.setBaseLevel("stat.agility", level)
        player.statMap.setCurrentLevel("stat.agility", level.toByte())
    }

    private fun GameTestScope.runPipeToStones(locs: CourseLocs) {
        random.next = 0
        player.teleport(CoordGrid(3004, 3937, 0))
        player.opLoc1(locs.pipe)
        advance(8)
        assertEquals(1, player.wildernessProgress)
        player.teleport(CoordGrid(3005, 3953, 0))
        player.opLoc1(locs.swing)
        advance(6)
        assertEquals(2, player.wildernessProgress)
        player.teleport(CoordGrid(3002, 3960, 0))
        player.opLoc1(locs.stone)
        advance(8)
        assertEquals(3, player.wildernessProgress)
    }

    @Test
    fun GameTestState.`full lap awards completion xp`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            runPipeToStones(locs)

            random.next = 0
            player.teleport(CoordGrid(3002, 3945, 0))
            player.opLoc1(locs.log)
            advance(8)
            assertEquals(4, player.wildernessProgress)

            player.teleport(CoordGrid(2994, 3938, 0))
            player.opLoc1(locs.rocks)
            advance(6)
            assertEquals(0, player.wildernessProgress)
            assertEquals(5714, player.statMap.getFineXP("stat.agility"))
            assertEquals(1, player.tagPending)
        }

    @Test
    fun GameTestState.`swing fail drops into dungeon pit`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            random.next = 255
            player.teleport(CoordGrid(3005, 3953, 0))
            player.opLoc1(locs.swing)
            advance(6)
            assertEquals(0, player.wildernessProgress)
            assertEquals(CoordGrid(3004, 10357, 0), player.coords)
        }

    @Test
    fun GameTestState.`stone fail lands on north bank and keeps progress`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            random.next = 0
            player.teleport(CoordGrid(3004, 3937, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            player.teleport(CoordGrid(3005, 3953, 0))
            player.opLoc1(locs.swing)
            advance(6)
            assertEquals(2, player.wildernessProgress)

            random.next = 255
            player.teleport(CoordGrid(3002, 3960, 0))
            player.opLoc1(locs.stone)
            advance(8)
            assertEquals(2, player.wildernessProgress)
            assertEquals(CoordGrid(3002, 3963, 0), player.coords)

            random.next = 0
            player.teleport(CoordGrid(3002, 3960, 0))
            player.opLoc1(locs.stone)
            advance(8)
            assertEquals(3, player.wildernessProgress)
        }

    @Test
    fun GameTestState.`log fail drops into dungeon and retry completes lap`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            runPipeToStones(locs)

            random.next = 255
            player.teleport(CoordGrid(3002, 3945, 0))
            player.opLoc1(locs.log)
            advance(8)
            assertEquals(3, player.wildernessProgress)
            assertEquals(CoordGrid(2998, 10345, 0), player.coords)

            random.next = 0
            player.teleport(CoordGrid(3002, 3945, 0))
            player.opLoc1(locs.log)
            advance(8)
            assertEquals(4, player.wildernessProgress)

            player.teleport(CoordGrid(2994, 3938, 0))
            player.opLoc1(locs.rocks)
            advance(6)
            assertEquals(0, player.wildernessProgress)
            assertEquals(5714, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`out of order obstacle drags progress back`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            runPipeToStones(locs)

            random.next = 0
            player.teleport(CoordGrid(3004, 3937, 0))
            player.opLoc1(locs.pipe)
            advance(8)
            assertEquals(1, player.wildernessProgress)
        }

    @Test
    fun GameTestState.`gate rejects below level 52`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(45)
            player.teleport(CoordGrid(2998, 3929, 0))
            player.opLoc1(locs.gate)
            advance(8)
            assertEquals(0, player.wildernessProgress)
            assertEquals(0, player.statMap.getFineXP("stat.agility"))
            assertTrue(player.coords.z < 3931)
        }

    @Test
    fun GameTestState.`gate crossing grants entry xp`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            random.next = 0
            player.teleport(CoordGrid(2998, 3929, 0))
            player.opLoc1(locs.gate)
            advance(8)
            assertEquals(150, player.statMap.getFineXP("stat.agility"))
            assertTrue(player.coords.z > 3931)
        }

    @Test
    fun GameTestState.`dispenser tag restores energy and grants ticket after lap`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            runPipeToStones(locs)
            random.next = 0
            player.teleport(CoordGrid(3002, 3945, 0))
            player.opLoc1(locs.log)
            advance(8)
            player.teleport(CoordGrid(2994, 3938, 0))
            player.opLoc1(locs.rocks)
            advance(6)

            player.runEnergy = 400
            player.teleport(CoordGrid(3005, 3937, 0))
            player.opLoc1(locs.dispenser)
            advance(2)
            assertEquals(0, player.tagPending)
            assertTrue(player.runEnergy >= 1000)
            assertEquals(1, player.inv.count("obj.wildy_agility_token"))
        }

    @Test
    fun GameTestState.`redeem converts tickets at bulk rate`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(52)

            val ticket =
                requireNotNull(
                    ServerCacheManager.getItem("obj.wildy_agility_token".asRSCM(RSCMType.OBJ))
                )
            player.inv[0] = InvObj(ticket, 11)
            player.teleport(CoordGrid(3005, 3937, 0))
            player.opLoc3(locs.dispenser)
            advance(2)
            assertEquals(0, player.inv.count("obj.wildy_agility_token"))
            assertEquals(23100, player.statMap.getFineXP("stat.agility"))
        }

    @Test
    fun GameTestState.`below level 49 pipe entry is rejected`() =
        runGameTest(WildernessAgilityCourse::class) {
            val locs = placeCourse()
            setAgility(45)
            player.teleport(CoordGrid(3004, 3937, 0))
            player.opLoc1(locs.pipe)
            advance(6)
            assertEquals(0, player.wildernessProgress)
            assertEquals(0, player.statMap.getFineXP("stat.agility"))
        }
}

private var Player.wildernessProgress by intVarp("varp.wilderness_course_progress")
private var Player.tagPending by intVarp("varp.wilderness_agility_tag_pending")
