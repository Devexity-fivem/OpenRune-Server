package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GotrWorkbenchTest {
    @Test
    fun GameTestState.`a large fragment stack is preserved when the inventory fills`() = runGameTest(Gotr::class) {
        val bench = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_workbench")
        player.placeAt(CoordGrid(3610, 9490))
        player.inv[0] = InvObj(FRAGMENT, 1000)
        for (slot in 1 until 26) player.inv[slot] = InvObj("obj.bones", 1)
        player.opLoc1(bench)
        advance(5)
        assertEquals(1000, player.inv.count(FRAGMENT) + player.inv.count(ESSENCE))
        assertEquals(2, player.inv.count(ESSENCE))
        assertEquals(25, player.inv.count("obj.bones"))
    }

    @Test
    fun GameTestState.`existing essence does not allow a full inventory to destroy fragments`() = runGameTest(Gotr::class) {
        val bench = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_workbench")
        player.placeAt(CoordGrid(3610, 9490))
        player.inv[0] = InvObj(FRAGMENT, 100)
        player.inv[1] = InvObj(ESSENCE, 1)
        for (slot in 2 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        val xp = player.statMap.getFineXP("stat.crafting")
        player.opLoc1(bench)
        advance(3)
        assertEquals(100, player.inv.count(FRAGMENT))
        assertEquals(1, player.inv.count(ESSENCE))
        assertEquals(xp, player.statMap.getFineXP("stat.crafting"))
    }

    @Test
    fun GameTestState.`the last fragment can free the output slot in a full inventory`() = runGameTest(Gotr::class) {
        val bench = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_workbench")
        player.placeAt(CoordGrid(3610, 9490))
        player.inv[0] = InvObj(FRAGMENT, 1)
        for (slot in 1 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        val xp = player.statMap.getFineXP("stat.crafting")
        repeat(2) { player.opLoc1(bench) }
        advance(3)
        assertEquals(0, player.inv.count(FRAGMENT))
        assertEquals(1, player.inv.count(ESSENCE))
        assertEquals(27, player.inv.count("obj.bones"))
        assertEquals(xp + 10, player.statMap.getFineXP("stat.crafting"))
    }

    @Test
    fun GameTestState.`crafting experience scales and clamps without granting experience for unconverted fragments`() {
        for (level in listOf(1, 25, 50, 99)) {
            runGameTest(Gotr::class) {
                val bench = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_workbench")
                player.placeAt(CoordGrid(3610, 9490))
                player.setBaseLevel("stat.crafting", level)
                player.setCurrentLevel("stat.crafting", level)
                player.inv[0] = InvObj(FRAGMENT, 100)
                val xp = player.statMap.getFineXP("stat.crafting")
                player.opLoc1(bench)
                advance(3)
                val produced = player.inv.count(ESSENCE)
                assertTrue(produced in 1..3)
                assertEquals(100, produced + player.inv.count(FRAGMENT))
                val fineXpPerEssence = level.coerceIn(10, 50)
                assertEquals(xp + produced * fineXpPerEssence, player.statMap.getFineXP("stat.crafting"))
            }
        }
    }

    @Test
    fun GameTestState.`a vanished fragment cannot create essence or experience`() = runGameTest(Gotr::class) {
        val bench = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_workbench")
        player.placeAt(CoordGrid(3610, 9490))
        player.inv[0] = InvObj(FRAGMENT, 1)
        val xp = player.statMap.getFineXP("stat.crafting")
        player.opLoc1(bench)
        player.inv[0] = null
        advance(3)
        assertEquals(0, player.inv.count(ESSENCE))
        assertEquals(xp, player.statMap.getFineXP("stat.crafting"))
    }

    @Test
    fun GameTestState.`walking away cancels continuous workbench crafting`() = runGameTest(Gotr::class) {
        val bench = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_workbench")
        player.placeAt(CoordGrid(3610, 9490))
        player.inv[0] = InvObj(FRAGMENT, 100)
        player.opLoc1(bench)
        advance(2)
        assertTrue(player.inv.count(ESSENCE) > 0)
        val destination = CoordGrid(3608, 9490)
        player.moveGameClick(destination)
        advanceUntil({ player.coords == destination }, timeoutTicks = 10)
        val fragments = player.inv.count(FRAGMENT)
        val essence = player.inv.count(ESSENCE)
        val xp = player.statMap.getFineXP("stat.crafting")
        advance(4)
        assertEquals(fragments, player.inv.count(FRAGMENT))
        assertEquals(essence, player.inv.count(ESSENCE))
        assertEquals(xp, player.statMap.getFineXP("stat.crafting"))
        assertEquals(100, fragments + essence)
    }

    private companion object {
        const val FRAGMENT = "obj.gotr_guardian_fragment"
        const val ESSENCE = "obj.gotr_guardian_essence"
    }
}
