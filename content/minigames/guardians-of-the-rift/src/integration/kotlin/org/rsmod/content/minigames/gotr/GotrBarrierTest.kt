package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GotrBarrierTest {
    @Test
    fun GameTestState.`an occupied tile consumes one medium cell to strengthen its barrier`() =
        runBarrierTest {
            val tile = placeMapLoc(TILE, "loc.gotr_cell_tile_inactive", shape = LocShape.GroundDecor)
            player.inv[0] = InvObj(WEAK_CELL, 1)
            player.opLoc1(tile)
            advanceUntil({ player.inv.count(WEAK_CELL) == 0 }, timeoutTicks = 10)
            assertExists(tile)

            player.inv[0] = InvObj(MEDIUM_CELL, 1)
            player.opLoc1(tile)
            advanceUntil({ player.inv.count(MEDIUM_CELL) == 0 }, timeoutTicks = 10)

            assertMessageSent("You strengthen the barrier.")
            assertExists(tile)
            player.inv[0] = InvObj(WEAK_CELL, 1)
            player.opLoc1(tile)
            advanceUntil({ player.inv.count(WEAK_CELL) == 0 }, timeoutTicks = 10)
            assertMessageSent("You recharge the barrier.")
            player.opLoc1(tile)
            advance(2)
            assertEquals(0, player.inv.count(MEDIUM_CELL))
            assertEquals(0, player.inv.count(WEAK_CELL))
        }

    @Test
    fun GameTestState.`the clicked tile is used instead of a nearby players first tile`() =
        runBarrierTest {
            val first = placeMapLoc(TILE, "loc.gotr_cell_tile_inactive", shape = LocShape.GroundDecor)
            val secondCoords = CoordGrid(3609, 9507, 0)
            val second = placeMapLoc(secondCoords, "loc.gotr_cell_tile_inactive", shape = LocShape.GroundDecor)
            player.inv[0] = InvObj(WEAK_CELL, 1)
            player.opLoc1(first)
            advanceUntil({ player.inv.count(WEAK_CELL) == 0 }, timeoutTicks = 10)

            player.placeAt(CoordGrid(3608, 9505, 0))
            player.inv[0] = InvObj(WEAK_CELL, 1)
            player.opLoc1(second)
            advanceUntil({ player.inv.count(WEAK_CELL) == 0 }, timeoutTicks = 10)

            assertMessageSent("You erect a barrier on the cell tile.")
        }

    @Test
    fun GameTestState.`one players recharge cap cannot block another players cell`() =
        runBarrierTest {
            val tile = placeMapLoc(TILE, "loc.gotr_cell_tile_inactive", shape = LocShape.GroundDecor)
            repeat(5) {
                player.inv[0] = InvObj(WEAK_CELL, 1)
                player.opLoc1(tile)
                advanceUntil({ player.inv.count(WEAK_CELL) == 0 }, timeoutTicks = 10)
            }
            player.setVarp("varp.gotr_recharge_energy", 450)
            player.inv[0] = InvObj(WEAK_CELL, 1)
            player.opLoc1(tile)
            advance(3)
            assertEquals(1, player.inv.count(WEAK_CELL))

            val friend = registerPlayer(CoordGrid(3609, 9502))
            friend.inv[0] = InvObj(WEAK_CELL, 1)
            friend.opLoc1(tile)
            advanceUntil({ friend.inv.count(WEAK_CELL) == 0 }, timeoutTicks = 10)
            assertEquals(450, player.vars["varp.gotr_recharge_energy"])
            assertEquals(2, friend.vars["varp.gotr_recharge_energy"])
            assertEquals(1, player.inv.count(WEAK_CELL))
        }

    private fun GameTestState.runBarrierTest(body: GameTestScope.() -> Unit) =
        runGameTest(Gotr::class) {
            player.clearAllInvs()
            player.placeAt(CoordGrid(3608, 9502, 0))
            body()
        }

    private companion object {
        val TILE = CoordGrid(3608, 9503, 0)
        const val WEAK_CELL = "obj.gotr_cell_tier1"
        const val MEDIUM_CELL = "obj.gotr_cell_tier2"
    }
}
