package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
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
        assertEquals(0, player.inv.count(PEARLS))
        assertEquals(28, player.inv.count("obj.bones"))
    }

    @Test
    fun GameTestState.`overflowing a pearl stack keeps the points and existing stack`() = runRewardsTest {
        player.inv[0] = InvObj(PEARLS, Int.MAX_VALUE)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(3)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(1, player.vars[CATALYTIC])
        assertEquals(Int.MAX_VALUE, player.inv.count(PEARLS))
    }

    @Test
    fun GameTestState.`repeated searches spend one pair and cannot duplicate a reward`() = runRewardsTest {
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advanceUntil({ player.vars[ELEMENTAL] == 0 }, timeoutTicks = 10)
        val received = player.inv.count(PEARLS)
        assertTrue(received in 14..16)
        assertEquals(0, player.vars[CATALYTIC])
        player.opLoc1(rift)
        advance(3)
        assertEquals(received, player.inv.count(PEARLS))
        assertEquals(0, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
    }

    @Test
    fun GameTestState.`another player cannot spend the owners reward points`() = runRewardsTest {
        val rift = placeMapLoc(TARGET, RIFT)
        val friend = registerPlayer(CoordGrid(3201, 3199))
        friend.opLoc1(rift)
        advance(3)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(1, player.vars[CATALYTIC])
        assertEquals(0, friend.inv.count(PEARLS))
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
        assertTrue(player.inv.count(PEARLS) in 14..16)
        assertEquals(1, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
    }

    @Test
    fun GameTestState.`a paired reward uses points before touching pearls`() = runRewardsTest {
        player.inv[0] = InvObj(PEARLS, 25)
        val rift = placeMapLoc(TARGET, RIFT)
        player.opLoc1(rift)
        advance(3)
        assertTrue(player.inv.count(PEARLS) in 39..41)
        assertEquals(0, player.vars[ELEMENTAL])
        assertEquals(0, player.vars[CATALYTIC])
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
        const val ELEMENTAL = "varp.gotr_elemental_reward_points"
        const val CATALYTIC = "varp.gotr_catalytic_reward_points"
    }
}
