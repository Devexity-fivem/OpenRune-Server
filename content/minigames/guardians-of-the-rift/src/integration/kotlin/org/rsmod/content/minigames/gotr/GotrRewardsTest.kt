package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GotrRewardsTest {
    @Test
    fun GameTestState.`a full inventory keeps both points and all items`() = runRewardsTest {
        for (slot in 0 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(3)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(1, player.vars[CATALYTIC])
        assertEquals(0, player.vars[SEARCHES])
        assertEquals(28, player.inv.count("obj.bones"))
    }

    @Test
    fun GameTestState.`overflowing a pearl stack keeps the points and existing stack`() = runRewardsTest {
        player.inv[0] = InvObj(PEARLS, Int.MAX_VALUE)
        for (slot in 1 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(3)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(1, player.vars[CATALYTIC])
        assertEquals(Int.MAX_VALUE, player.inv.count(PEARLS))
    }

    @Test
    fun GameTestState.`every search grants at least one reward`() = runRewardsTest {
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advanceUntil({ player.vars[ELEMENTAL] == 0 }, timeoutTicks = 10)
        assertTrue(totalItems(player) > 0)
        assertEquals(0, player.vars[CATALYTIC])
        assertEquals(1, player.vars[SEARCHES])
    }

    @Test
    fun GameTestState.`repeated searches spend one pair each`() = runRewardsTest {
        player.setVarp(ELEMENTAL, 2)
        player.setVarp(CATALYTIC, 2)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(4)
        assertEquals(1, player.vars[ELEMENTAL])
        advance(4)
        player.opLoc1(rift)
        advance(4)
        assertEquals(0, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
        assertEquals(2, player.vars[SEARCHES])
    }

    @Test
    fun GameTestState.`another player cannot spend the owners reward points`() = runRewardsTest {
        val rift = placeMapLoc(TARGET, RIFT)
        val friend = registerPlayer(CoordGrid(3201, 3199))
        friend.opLoc1(rift)
        advance(3)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(1, player.vars[CATALYTIC])
        assertEquals(0, totalItems(friend))
        assertEquals(0, friend.vars[ELEMENTAL])
        assertEquals(0, friend.vars[CATALYTIC])
    }

    @Test
    fun GameTestState.`a pearl sacrifice consumes twenty five pearls without consuming unmatched points`() = runRewardsTest {
        player.setVarp(CATALYTIC, 0)
        player.inv[0] = InvObj(PEARLS, 25)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(3)
        assertTrue(player.inv.count(PEARLS) <= 16)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
        assertEquals(1, player.vars[SEARCHES])
    }

    @Test
    fun GameTestState.`a paired reward uses points before touching pearls`() = runRewardsTest {
        player.inv[0] = InvObj(PEARLS, 25)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(3)
        assertTrue(player.inv.count(PEARLS) >= 25)
        assertEquals(0, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
    }

    @Test
    fun GameTestState.`a big-search consumes five searches`() = runRewardsTest {
        player.setVarp(ELEMENTAL, 5)
        player.setVarp(CATALYTIC, 5)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc3(rift)
        advance(10)
        assertEquals(0, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
        assertEquals(5, player.vars[SEARCHES])
        assertTrue(totalItems(player) >= 5)
    }

    @Test
    fun GameTestState.`a big-search stops early when payment runs out`() = runRewardsTest {
        player.setVarp(ELEMENTAL, 2)
        player.setVarp(CATALYTIC, 2)
        player.inv[0] = InvObj(PEARLS, 25)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc3(rift)
        advance(10)
        assertEquals(0, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
        assertEquals(3, player.vars[SEARCHES])
        assertTrue(player.inv.count(PEARLS) <= 16)
    }

    @Test
    fun GameTestState.`checking the rift does not spend points`() = runRewardsTest {
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc2(rift)
        advance(2)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(1, player.vars[CATALYTIC])
        assertEquals(0, player.vars[SEARCHES])
    }

    @Test
    fun GameTestState.`an intricate pouch opens into loot`() = runRewardsTest {
        player.inv[0] = InvObj(POUCH, 1)
        player.withProtectedAccess { opHeld1(0) }
        advance(3)
        assertEquals(0, player.inv.count(POUCH))
        assertTrue(totalItems(player) >= 1)
    }

    private fun totalItems(player: Player): Int {
        var total = 0
        for (slot in 0 until 28) {
            total += player.inv[slot]?.count ?: 0
        }
        return total
    }

    private fun GameTestState.runRewardsTest(body: GameTestScope.() -> Unit) =
        runGameTest(Gotr::class) {
            player.clearAllInvs()
            player.placeAt(CoordGrid(3200, 3200))
            player.setVarp(ELEMENTAL, 1)
            player.setVarp(CATALYTIC, 1)
            body()
        }

    private companion object {
        val TARGET = CoordGrid(3201, 3200)
        const val RIFT = "loc.gotr_reward_rift"
        const val PEARLS = "obj.abyssal_pearl"
        const val POUCH = "obj.gotr_intricate_pouch"
        const val ELEMENTAL = "varp.gotr_elemental_reward_points"
        const val CATALYTIC = "varp.gotr_catalytic_reward_points"
        const val SEARCHES = "varp.gotr_searches"
    }
}
