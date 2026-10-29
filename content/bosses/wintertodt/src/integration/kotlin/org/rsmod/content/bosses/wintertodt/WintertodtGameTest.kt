package org.rsmod.content.bosses.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.map.CoordGrid

class WintertodtGameTest {
    private fun GameTestScope.placeArena() {
        placeMapLoc(CoordGrid(1620, 3996, 0), "loc.wint_brazier")
        placeMapLoc(CoordGrid(1620, 4018, 0), "loc.wint_brazier")
        placeMapLoc(CoordGrid(1640, 3996, 0), "loc.wint_brazier")
        placeMapLoc(CoordGrid(1640, 4018, 0), "loc.wint_brazier")
        val wizard = ServerCacheManager.getNpc("npc.wint_wizard".asRSCM(RSCMType.NPC))!!
        spawnNpc(CoordGrid(1619, 3996, 0), wizard)
        spawnNpc(CoordGrid(1619, 4018, 0), wizard)
        spawnNpc(CoordGrid(1641, 3996, 0), wizard)
        spawnNpc(CoordGrid(1641, 4018, 0), wizard)
        advance(2)
    }

    @Test
    fun GameTestState.`braziers pyromancers and boss are discovered`() =
        runInjectedGameTest(WintertodtManager::class, null, WintertodtScript::class) { manager ->
            placeArena()
            assertNotNull(manager.brazierAt(CoordGrid(1620, 3996, 0)))
            assertNotNull(manager.brazierAt(CoordGrid(1640, 4018, 0)))
            assertEquals(0, manager.litCount())
        }

    @Test
    fun GameTestState.`lit braziers drain the boss energy`() =
        runInjectedGameTest(WintertodtManager::class, null, WintertodtScript::class) { manager ->
            placeArena()
            val brazier = manager.brazierAt(CoordGrid(1620, 3996, 0))!!
            manager.lightBrazier(brazier)
            assertEquals(1, manager.litCount())
            val before = manager.energy
            advance(10)
            assertEquals(before - 10, manager.energy)
        }

    @Test
    fun GameTestState.`warmth drains inside the prison`() =
        runInjectedGameTest(WintertodtManager::class, null, WintertodtScript::class) { manager ->
            placeArena()
            player.placeAt(CoordGrid(1630, 4007, 0))
            player.warmth = WintertodtManager.MAX_WARMTH
            advance(WintertodtManager.COLD_PULSE_INTERVAL + 2)
            assertTrue(player.warmth < WintertodtManager.MAX_WARMTH)
        }

    @Test
    fun GameTestState.`round end awards crates and resets`() =
        runInjectedGameTest(WintertodtManager::class, null, WintertodtScript::class) { manager ->
            placeArena()
            player.placeAt(CoordGrid(1630, 4007, 0))
            advance(1)
            player.warmth = 500
            player.wintertodtPoints = 600
            repeat(200) { manager.feedBrazier(true) }
            advance(2)
            assertTrue(manager.resting)
            assertEquals(WintertodtManager.MAX_WARMTH, player.warmth)
            val crate =
                ServerCacheManager.getItem("obj.wint_reward_box".asRSCM(RSCMType.OBJ))!!
            assertTrue(player.inv.any { it != null && it.id == crate.id })
        }

    @Test
    fun GameTestState.`injured pyromancer locks its brazier and can be healed`() =
        runInjectedGameTest(WintertodtManager::class, null, WintertodtScript::class) { manager ->
            placeArena()
            manager.injurePyromancer(0)
            assertTrue(manager.pyroDown(0))
            assertEquals(0, manager.brazierState(0))
            manager.healPyromancer(0)
            assertTrue(!manager.pyroDown(0))
        }
}
