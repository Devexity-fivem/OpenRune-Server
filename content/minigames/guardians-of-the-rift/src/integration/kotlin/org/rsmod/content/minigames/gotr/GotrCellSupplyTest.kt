package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GotrCellSupplyTest {
    @Test
    fun GameTestState.`repeated weak-cell collection cannot bypass the charged-cell limit`() = runGameTest(Gotr::class) {
        val crate = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_weak_cells")
        player.placeAt(CoordGrid(3610, 9490))
        repeat(3) {
            player.opLoc1(crate)
            advance(2)
        }
        assertEquals(1, player.inv.count("obj.gotr_cell_tier1"))
    }

    @Test
    fun GameTestState.`a stronger held cell prevents collection of an additional weak cell`() = runGameTest(Gotr::class) {
        val crate = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_weak_cells")
        player.placeAt(CoordGrid(3610, 9490))
        player.inv[0] = InvObj("obj.gotr_cell_tier4", 1)
        player.opLoc1(crate)
        advance(2)
        assertEquals(0, player.inv.count("obj.gotr_cell_tier1"))
        assertEquals(1, player.inv.count("obj.gotr_cell_tier4"))
    }

    @Test
    fun GameTestState.`a full inventory cannot lose items while collecting a weak cell`() = runGameTest(Gotr::class) {
        val crate = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_weak_cells")
        player.placeAt(CoordGrid(3610, 9490))
        repeat(28) { player.inv[it] = InvObj("obj.bones", 1) }
        player.opLoc1(crate)
        advance(2)
        assertEquals(28, player.inv.count("obj.bones"))
        assertEquals(0, player.inv.count("obj.gotr_cell_tier1"))
    }
}
