package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.hunter.HunterTrapManager.State
import org.rsmod.content.skills.hunter.HunterTrapManager.TrapKind
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/* Obj transactions and npc/loc registry updates are not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class HunterEventsTest {
    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    @Test
    fun GameTestState.`laying a snare consumes the item and spawns an armed trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }

            assertEquals(0, player.inv.count(SNARE))
            assertNotNull(probe.traps.trapAt(player.coords))
            assertEquals(1, probe.traps.activeTraps(player))
        }

    @Test
    fun GameTestState.`laying a box trap consumes the item and spawns an armed trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(BOX_TRAP)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }

            assertEquals(0, player.inv.count(BOX_TRAP))
            val trap = probe.traps.trapAt(player.coords)
            assertNotNull(trap)
            assertEquals(locId("loc.hunting_boxtrap_empty"), trap.loc.id)
        }

    @Test
    fun GameTestState.`trap limit is enforced by hunter level`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE, 5)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            // Second snare must go on a different tile: walk one tile over.
            player.teleport(player.coords.translateX(1))
            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            player.teleport(player.coords.translateX(1))
            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }

            // Level 1 allows two traps.
            assertEquals(2, probe.traps.activeTraps(player))
            assertEquals(3, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`snares and box traps share the same trap limit`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)
            player.inv[1] = InvObj(BOX_TRAP, 3)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            player.teleport(player.coords.translateX(1))
            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            player.teleport(player.coords.translateX(1))
            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }

            assertEquals(2, probe.traps.activeTraps(player))
            assertEquals(2, player.inv.count(BOX_TRAP))
        }

    @Test
    fun GameTestState.`the wilderness allows one extra box trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.insideWilderness = true
            player.inv[0] = InvObj(BOX_TRAP, 5)

            repeat(3) {
                player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
                player.teleport(player.coords.translateX(1))
            }
            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }

            // Two snare limit + one wilderness box trap = three laid, fourth refused.
            assertEquals(3, probe.traps.activeTraps(player))
            assertEquals(2, player.inv.count(BOX_TRAP))
        }

    @Test
    fun GameTestState.`a nearby bird is caught and the trap yields loot`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(SNARE)
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_bird_jungle"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            val trapCoords = player.coords
            // Birds ignore a trap while the owner stands on it.
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)

            // The bird is consumed and the trap is now in its caught state.
            assertEquals(State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)

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
    fun GameTestState.`a nearby chinchompa is caught and yields the captured animal`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 63)
            player.inv[0] = InvObj(BOX_TRAP)
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_chinchompa"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)

            assertEquals(State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)

            player.withProtectedAccess { with(probe.events) { checkTrap(trapCoords) } }

            assertEquals(1, player.inv.count(BOX_TRAP))
            assertEquals(1, player.inv.count("obj.chinchompa_captured"))
            assertEquals(1984, player.statMap.getFineXP("stat.hunter"))
            assertNull(probe.traps.trapAt(trapCoords))
        }

    @Test
    fun GameTestState.`a caught embertailed jerboa leaves only its tail`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 39)
            player.inv[0] = InvObj(BOX_TRAP)
            spawnNpc(player.coords.translateX(2), npcType("npc.varlamore_hunterjerboa01"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)

            assertEquals(State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)

            player.withProtectedAccess { with(probe.events) { checkTrap(trapCoords) } }

            assertEquals(1, player.inv.count(BOX_TRAP))
            assertEquals(1, player.inv.count("obj.hunting_jerboa_tail"))
            assertEquals(1370, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`the trapping loc shows the side the prey entered from`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 63)
            player.inv[0] = InvObj(BOX_TRAP)
            // East of the trap: it walks in from the east side.
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_chinchompa"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))

            var trappingLocId: Int? = null
            repeat(40) {
                probe.traps.tick()
                val trap = probe.traps.trapAt(trapCoords)
                if (trap?.state == State.TRAPPING && trappingLocId == null) {
                    trappingLocId = trap.loc.id
                }
            }

            assertEquals(locId("loc.hunting_boxtrap_trapping_chinchompa_e"), trappingLocId)
            assertEquals(State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)
        }

    @Test
    fun GameTestState.`a trap cannot trigger while the owner stands on it`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(SNARE)
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_bird_jungle"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            tickTraps(probe, 60)

            val trap = probe.traps.trapAt(player.coords)
            assertEquals(State.ARMED, trap?.state)

            // Stepping off lets the trap trigger.
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)
            assertEquals(State.CAUGHT, trap?.state)

            // Resetting a caught trap forfeits the catch and re-arms it.
            probe.traps.reset(checkNotNull(trap))
            assertEquals(State.ARMED, trap.state)
            assertEquals(0, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`a failed catch leaves a broken trap that can be reset`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 0.0 }
            player.inv[0] = InvObj(BOX_TRAP)
            player.setCurrentLevel("stat.hunter", 63)
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_chinchompa"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 40)

            val trap = probe.traps.trapAt(trapCoords)
            assertEquals(State.FAILED, trap?.state)
            assertEquals(locId("loc.hunting_boxtrap_failed"), trap?.loc?.id)

            // Reset re-arms the same trap without another box trap.
            probe.traps.reset(checkNotNull(trap))
            assertEquals(State.ARMED, trap.state)
            assertEquals(locId("loc.hunting_boxtrap_empty"), trap.loc.id)
            assertEquals(0, player.inv.count(BOX_TRAP))
        }

    @Test
    fun GameTestState.`dismantling an armed trap returns the snare`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            player.withProtectedAccess { with(probe.events) { dismantleTrap(player.coords) } }

            assertNull(probe.traps.trapAt(player.coords))
            assertEquals(1, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`dismantling an armed box trap returns the box trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            player.inv[0] = InvObj(BOX_TRAP)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            player.withProtectedAccess { with(probe.events) { dismantleTrap(player.coords) } }

            assertNull(probe.traps.trapAt(player.coords))
            assertEquals(1, player.inv.count(BOX_TRAP))
        }

    @Test
    fun GameTestState.`birds above the hunter level never trigger the trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(SNARE)
            // Tropical wagtail requires level 19; the test player is level 1.
            spawnNpc(player.coords.translateX(2), npcType("npc.multicoloured_bird"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 60)

            assertEquals(State.ARMED, probe.traps.trapAt(trapCoords)?.state)
        }

    @Test
    fun GameTestState.`box prey above the hunter level never trigger the trap`() =
        runInjectedGameTest(Probe::class, null, HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(BOX_TRAP)
            // Grey chinchompas require level 53; the test player is level 1.
            spawnNpc(player.coords.translateX(2), npcType("npc.hunting_chinchompa"))

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            val trapCoords = player.coords
            player.teleport(player.coords.translateX(1))
            tickTraps(probe, 60)

            assertEquals(State.ARMED, probe.traps.trapAt(trapCoords)?.state)
        }

    private fun npcType(symbol: String) =
        checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)))

    private fun locId(symbol: String) =
        checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC))).id

    private fun tickTraps(probe: Probe, ticks: Int) = repeat(ticks) { probe.traps.tick() }

    private companion object {
        const val SNARE = "obj.hunting_ojibway_bird_snare"
        const val BOX_TRAP = "obj.hunting_box_trap"
    }

    class Probe
    @Inject
    constructor(
        val events: HunterEvents,
        val traps: HunterTrapManager,
    )
}
