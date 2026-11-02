package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GotrImbuingTest {
    @Test
    fun GameTestState.`same tier and higher cells cannot grant repeated free experience`() = runPortalTest { portal ->
        for (cell in listOf(MEDIUM, STRONG)) {
            player.clearAllInvs()
            player.inv[0] = InvObj(cell, 1)
            player.inv[1] = InvObj(UNCHARGED, 10)
            val xp = player.statMap.getFineXP(RUNECRAFTING)
            repeat(2) {
                player.opLoc1(portal)
                advance(3)
            }
            assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
            assertEquals(1, player.inv.count(cell))
            assertEquals(10, player.inv.count(UNCHARGED))
        }
    }

    @Test
    fun GameTestState.`upgrading one weak cell awards ten experience exactly once`() = runPortalTest { portal ->
        player.inv[0] = InvObj(WEAK, 1)
        player.inv[1] = InvObj(UNCHARGED, 10)
        val xp = player.statMap.getXP(RUNECRAFTING)
        player.opLoc1(portal)
        advanceUntil({ player.inv.count(MEDIUM) == 1 }, timeoutTicks = 10)
        assertEquals(0, player.inv.count(WEAK))
        assertEquals(10, player.inv.count(UNCHARGED))
        assertEquals(xp + 10, player.statMap.getXP(RUNECRAFTING))
        player.opLoc1(portal)
        advance(3)
        assertEquals(xp + 10, player.statMap.getXP(RUNECRAFTING))
        assertEquals(1, player.inv.count(MEDIUM))
    }

    @Test
    fun GameTestState.`charging consumes only one uncharged cell`() = runPortalTest { portal ->
        player.inv[0] = InvObj(UNCHARGED, 10)
        val xp = player.statMap.getXP(RUNECRAFTING)
        player.opLoc1(portal)
        advanceUntil({ player.inv.count(MEDIUM) == 1 }, timeoutTicks = 10)
        assertEquals(9, player.inv.count(UNCHARGED))
        assertEquals(xp + 10, player.statMap.getXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`a full inventory preserves an uncharged stack and gives no experience`() = runPortalTest { portal ->
        for (slot in 1 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        player.inv[0] = InvObj(UNCHARGED, 2)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(portal)
        advance(3)
        assertEquals(2, player.inv.count(UNCHARGED))
        assertEquals(0, player.inv.count(MEDIUM))
        assertEquals(27, player.inv.count("obj.bones"))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`essence conversion rolls back when both outputs cannot fit`() = runPortalTest { portal ->
        for (slot in 1 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        player.inv[0] = InvObj(ESSENCE, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(portal)
        advance(3)
        assertEquals(1, player.inv.count(ESSENCE))
        assertEquals(0, player.inv.count(STONE))
        assertEquals(0, player.inv.count(RUNE))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`rune stack overflow rolls back essence and the simultaneous cell upgrade`() = runPortalTest { portal ->
        player.inv[0] = InvObj(ESSENCE, 1)
        player.inv[1] = InvObj(RUNE, Int.MAX_VALUE)
        player.inv[2] = InvObj(WEAK, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(portal)
        advance(3)
        assertEquals(1, player.inv.count(ESSENCE))
        assertEquals(1, player.inv.count(WEAK))
        assertEquals(0, player.inv.count(MEDIUM))
        assertEquals(0, player.inv.count(STONE))
        assertEquals(Int.MAX_VALUE, player.inv.count(RUNE))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`one crafting action converts all essence and stops after its first talisman`() = runPortalTest { portal ->
        repeat(3) { player.inv[it] = InvObj(ESSENCE, 1) }
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        random.next = 0
        random.then = 0
        player.opLoc1(portal)
        advanceUntil({ player.inv.count(ESSENCE) == 0 }, timeoutTicks = 10)
        assertEquals(3, player.inv.count(STONE))
        assertEquals(6, player.inv.count(RUNE))
        assertEquals(xp + 180, player.statMap.getFineXP(RUNECRAFTING))
        assertEquals(1, player.inv.count("obj.gotr_portal_talisman_water"))
    }

    @Test
    fun GameTestState.`boosting across the water multiple threshold increases runes but not stones or experience`() = runPortalTest { portal ->
        player.setBaseLevel(RUNECRAFTING, 37)
        player.setCurrentLevel(RUNECRAFTING, 38)
        player.inv[0] = InvObj(ESSENCE, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        random.next = 249
        random.then = 249
        player.opLoc1(portal)
        advanceUntil({ player.inv.count(ESSENCE) == 0 }, timeoutTicks = 10)
        assertEquals(3, player.inv.count(RUNE))
        assertEquals(1, player.inv.count(STONE))
        assertEquals(xp + 60, player.statMap.getFineXP(RUNECRAFTING))
    }

    private fun GameTestState.runPortalTest(body: GameTestScope.(BoundLocInfo) -> Unit) =
        runGameTest(Gotr::class) {
            val gate = placeMapLoc(CoordGrid(3615, 9483), "loc.gotr_barrier")
            player.setBaseLevel(RUNECRAFTING, 27)
            player.setCurrentLevel(RUNECRAFTING, 27)
            player.placeAt(CoordGrid(3615, 9482))
            player.opLoc1(gate)
            advance(205)
            player.clearAllInvs()
            player.placeAt(CoordGrid(3610, 9490))
            val portal = placeMapLoc(CoordGrid(3611, 9490), "loc.gotr_portal_water")
            body(portal)
        }

    private companion object {
        const val RUNECRAFTING = "stat.runecrafting"
        const val UNCHARGED = "obj.gotr_cell_uncharged"
        const val WEAK = "obj.gotr_cell_tier1"
        const val MEDIUM = "obj.gotr_cell_tier2"
        const val STRONG = "obj.gotr_cell_tier3"
        const val ESSENCE = "obj.gotr_guardian_essence"
        const val STONE = "obj.gotr_guardian_stone_elemental"
        const val RUNE = "obj.waterrune"
    }
}
