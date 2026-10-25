package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj

/* Obj transactions and npc/loc registry updates are not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class HunterEventsTest {
    @Test
    fun GameTestState.`laying a snare consumes the item and spawns an armed trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)

            player.withProtectedAccess { with(probe.events) { laySnare() } }

            assertEquals(0, player.inv.count(SNARE))
            assertNotNull(probe.traps.trapAt(player.coords))
            assertEquals(1, probe.traps.activeTraps(player))
        }

    @Test
    fun GameTestState.`trap limit is enforced by hunter level`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE, 5)

            player.withProtectedAccess { with(probe.events) { laySnare() } }
            // Second snare must go on a different tile: walk one tile over.
            player.teleport(player.coords.translateX(1))
            player.withProtectedAccess { with(probe.events) { laySnare() } }
            player.teleport(player.coords.translateX(1))
            player.withProtectedAccess { with(probe.events) { laySnare() } }

            // Level 1 allows two traps.
            assertEquals(2, probe.traps.activeTraps(player))
            assertEquals(3, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`a nearby bird is caught and the trap yields loot`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(SNARE)
            spawnNpc(
                player.coords.translateX(2),
                npcType("npc.hunting_bird_jungle"),
            )

            player.withProtectedAccess { with(probe.events) { laySnare() } }
            val trapCoords = player.coords
            // Birds ignore a trap while the owner stands on it.
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)

            // The bird is consumed and the trap is now in its caught state.
            assertEquals(HunterTrapManager.State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)

            player.withProtectedAccess { with(probe.events) { checkTrap(trapCoords) } }

            assertEquals(1, player.inv.count(SNARE))
            assertEquals(1, player.inv.count("obj.bones"))
            assertEquals(1, player.inv.count("obj.spit_raw_bird_meat"))
            val feathers = player.inv.count("obj.hunting_jungle_feather")
            assertTrue(feathers in 5..10) { "expected 5-10 red feathers, got $feathers" }
            assertEquals(340, player.statMap.getFineXP("stat.hunter"))
            assertNull(probe.traps.trapAt(trapCoords))
        }

    @Test
    fun GameTestState.`a trap cannot trigger while the owner stands on it`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(SNARE)
            spawnNpc(
                player.coords.translateX(2),
                npcType("npc.hunting_bird_jungle"),
            )

            player.withProtectedAccess { with(probe.events) { laySnare() } }
            tickTraps(probe, 60)

            val trap = probe.traps.trapAt(player.coords)
            assertEquals(HunterTrapManager.State.ARMED, trap?.state)

            // Stepping off lets the trap trigger.
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)
            assertEquals(HunterTrapManager.State.CAUGHT, trap?.state)

            // Resetting a caught trap forfeits the catch and re-arms it.
            probe.traps.reset(checkNotNull(trap))
            assertEquals(HunterTrapManager.State.ARMED, trap.state)
            assertEquals(0, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`a failed catch leaves a broken trap that can be reset`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 0.0 }
            player.inv[0] = InvObj(SNARE)
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_bird_jungle"))

            player.withProtectedAccess { with(probe.events) { laySnare() } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)

            val trap = probe.traps.trapAt(trapCoords)
            assertEquals(HunterTrapManager.State.FAILED, trap?.state)

            // Reset re-arms the same trap without another snare.
            probe.traps.reset(checkNotNull(trap))
            assertEquals(HunterTrapManager.State.ARMED, trap.state)
            assertEquals(0, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`dismantling an armed trap returns the snare`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)

            player.withProtectedAccess { with(probe.events) { laySnare() } }
            player.withProtectedAccess { with(probe.events) { dismantleSnare(player.coords) } }

            assertNull(probe.traps.trapAt(player.coords))
            assertEquals(1, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`birds above the hunter level never trigger the trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(SNARE)
            // Tropical wagtail requires level 19; the test player is level 1.
            spawnNpc(player.coords.translateX(2), npcType("npc.multicoloured_bird"))

            player.withProtectedAccess { with(probe.events) { laySnare() } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 60)

            assertEquals(HunterTrapManager.State.ARMED, probe.traps.trapAt(trapCoords)?.state)
        }

    private fun npcType(symbol: String) =
        checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)))

    private fun tickTraps(probe: Probe, ticks: Int) = repeat(ticks) { probe.traps.tick() }

    private companion object {
        const val SNARE = "obj.hunting_ojibway_bird_snare"
    }

    class Probe
    @Inject
    constructor(
        val events: HunterEvents,
        val traps: HunterTrapManager,
    )
}
