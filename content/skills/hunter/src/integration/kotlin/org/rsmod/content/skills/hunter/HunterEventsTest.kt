package org.rsmod.content.skills.hunter

import com.google.inject.AbstractModule
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.droptable.toml.DropTableTomlResolver
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.drops.toml.ContentDropTableTomlResolver
import org.rsmod.content.skills.hunter.HunterTrapManager.State
import org.rsmod.content.skills.hunter.HunterTrapManager.TrapKind
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/* Obj transactions and npc/loc registry updates are not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class HunterEventsTest {
    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    @Test
    fun GameTestState.`laying a snare consumes the item and spawns an armed trap`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }

            assertEquals(0, player.inv.count(SNARE))
            assertNotNull(probe.traps.trapAt(player.coords))
            assertEquals(1, probe.traps.activeTraps(player))
        }

    @Test
    fun GameTestState.`laying a box trap consumes the item and spawns an armed trap`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj(BOX_TRAP)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }

            assertEquals(0, player.inv.count(BOX_TRAP))
            val trap = probe.traps.trapAt(player.coords)
            assertNotNull(trap)
            assertEquals(locId("loc.hunting_boxtrap_empty"), trap.loc.id)
        }

    @Test
    fun GameTestState.`trap limit is enforced by hunter level`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj(SNARE)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.SNARE) } }
            player.withProtectedAccess { with(probe.events) { dismantleTrap(player.coords) } }

            assertNull(probe.traps.trapAt(player.coords))
            assertEquals(1, player.inv.count(SNARE))
        }

    @Test
    fun GameTestState.`dismantling an armed box trap returns the box trap`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj(BOX_TRAP)

            player.withProtectedAccess { with(probe.events) { layTrap(TrapKind.BOX) } }
            player.withProtectedAccess { with(probe.events) { dismantleTrap(player.coords) } }

            assertNull(probe.traps.trapAt(player.coords))
            assertEquals(1, player.inv.count(BOX_TRAP))
        }

    @Test
    fun GameTestState.`birds above the hunter level never trigger the trap`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
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

    @Test
    fun GameTestState.`a deadfall needs a knife and a log in the inventory`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 33)
            val boulder = deadfallBoulder(probe, player.coords)

            player.inv[0] = InvObj(LOGS)
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(boulder) } }
            assertNull(probe.traps.trapAt(player.coords))

            player.inv[0] = InvObj(KNIFE)
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(boulder) } }
            assertNull(probe.traps.trapAt(player.coords))

            player.inv[1] = InvObj(LOGS)
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(boulder) } }
            val trap = probe.traps.trapAt(player.coords)
            assertNotNull(trap)
            assertEquals(TrapKind.DEADFALL, trap.kind)
            assertEquals(locId("loc.hunting_deadfall_trap"), trap.loc.id)
        }

    @Test
    fun GameTestState.`setting a deadfall preserves the log most of the time`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 33)
            player.inv[0] = InvObj(KNIFE)
            player.inv[1] = InvObj(LOGS)
            player.inv[2] = InvObj(LOGS)

            probe.events.logPreserveChance = 1.0
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(deadfallBoulder(probe, player.coords)) } }
            assertEquals(2, player.inv.count(LOGS))

            probe.events.logPreserveChance = 0.0
            val boulder = deadfallBoulder(probe, player.coords.translateX(2))
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(boulder) } }
            assertEquals(1, player.inv.count(LOGS))
        }

    @Test
    fun GameTestState.`at most two deadfalls can be set at once`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 80)
            player.inv[0] = InvObj(KNIFE)
            player.inv[1] = InvObj(LOGS, 5)

            val base = player.coords
            probe.events.logPreserveChance = 1.0
            repeat(3) { i ->
                val boulder = deadfallBoulder(probe, base.translateX(i * 2))
                player.withProtectedAccess { with(probe.events) { setDeadfallTrap(boulder) } }
            }

            assertEquals(2, probe.traps.activeTraps(player, TrapKind.DEADFALL))
            assertEquals(2, probe.traps.activeTraps(player))
        }

    @Test
    fun GameTestState.`a deadfall catch drops bones the part and the meat`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 33)
            player.inv[0] = InvObj(KNIFE)
            player.inv[1] = InvObj(LOGS)
            spawnNpc(player.coords.translateZ(3), npcType("npc.huntingbeast_barbedtail"))

            val trapCoords = player.coords
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(deadfallBoulder(probe, trapCoords)) } }
            tickTraps(probe, 60)

            assertEquals(State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)

            player.withProtectedAccess { with(probe.events) { checkTrap(trapCoords) } }

            assertEquals(1, player.inv.count("obj.bones"))
            assertEquals(1, player.inv.count("obj.hunting_barbed_harpoon"))
            assertEquals(1, player.inv.count("obj.huntingbeast_barbed_meat"))
            assertEquals(1344, player.statMap.getFineXP("stat.hunter"))
            assertNull(probe.traps.trapAt(trapCoords))
        }

    @Test
    fun GameTestState.`a sprung deadfall disappears without a resettable state`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 0.0 }
            player.setCurrentLevel("stat.hunter", 33)
            player.inv[0] = InvObj(KNIFE)
            player.inv[1] = InvObj(LOGS)
            spawnNpc(player.coords.translateZ(3), npcType("npc.huntingbeast_barbedtail"))

            val trapCoords = player.coords
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(deadfallBoulder(probe, trapCoords)) } }
            tickTraps(probe, 60)

            assertNull(probe.traps.trapAt(trapCoords))
        }

    @Test
    fun GameTestState.`prey entering a deadfall from the back uses the mirrored loc`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 33)
            player.inv[0] = InvObj(KNIFE)
            player.inv[1] = InvObj(LOGS)
            // The boulder faces south, so a kebbit arriving from the north enters
            // from its back side and triggers the mirrored trapping loc.
            spawnNpc(player.coords.translateZ(3), npcType("npc.huntingbeast_barbedtail"))

            val trapCoords = player.coords
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(deadfallBoulder(probe, trapCoords)) } }

            var trappingLocId: Int? = null
            repeat(60) {
                probe.traps.tick()
                val trap = probe.traps.trapAt(trapCoords)
                if (trap?.state == State.TRAPPING && trappingLocId == null) {
                    trappingLocId = trap.loc.id
                }
            }

            assertEquals(locId("loc.hunting_deadfall_trapping_barbed_m"), trappingLocId)
            assertEquals(State.CAUGHT, probe.traps.trapAt(trapCoords)?.state)
        }

    @Test
    fun GameTestState.`deadfall prey above the hunter level never trigger the trap`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(KNIFE)
            player.inv[1] = InvObj(LOGS)
            // Sabre-toothed kebbits require level 51; the test player is level 1.
            spawnNpc(player.coords.translateZ(3), npcType("npc.huntingbeast_sabreteeth"))

            val trapCoords = player.coords
            player.withProtectedAccess { with(probe.events) { setDeadfallTrap(deadfallBoulder(probe, trapCoords)) } }
            tickTraps(probe, 60)

            assertEquals(State.ARMED, probe.traps.trapAt(trapCoords)?.state)
        }

    @Test
    fun GameTestState.`a net trap needs a rope a net and the hunter level`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 29)
            val tree = netTree(probe, player.coords)

            player.inv[0] = InvObj(ROPE)
            player.withProtectedAccess { with(probe.events) { setNetTrap(tree, probe.events.swampPrey()) } }
            assertNull(probe.traps.trapAt(player.coords))

            player.inv[1] = InvObj(SMALL_NET)
            player.withProtectedAccess { with(probe.events) { setNetTrap(tree, probe.events.swampPrey()) } }
            val trap = probe.traps.trapAt(player.coords)
            assertNotNull(trap)
            assertEquals(TrapKind.NET, trap.kind)
            assertEquals(locId("loc.hunting_sapling_net_set_swamp"), trap.loc.id)
            assertEquals("npc.salamander_green", trap.prey?.npcSymbol)
            assertEquals(0, player.inv.count(ROPE))
            assertEquals(0, player.inv.count(SMALL_NET))
        }

    @Test
    fun GameTestState.`a net trap catches only the salamander of its tree`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 80)
            player.inv[0] = InvObj(ROPE)
            player.inv[1] = InvObj(SMALL_NET)
            spawnNpc(player.coords.translateZ(3), npcType("npc.salamander_red"))

            val trapCoords = player.coords
            player.withProtectedAccess {
                with(probe.events) { setNetTrap(netTree(probe, trapCoords), swampPrey()) }
            }
            tickTraps(probe, 60)

            assertEquals(State.ARMED, probe.traps.trapAt(trapCoords)?.state)
        }

    @Test
    fun GameTestState.`checking a caught net trap returns the salamander and materials`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 29)
            player.inv[0] = InvObj(ROPE)
            player.inv[1] = InvObj(SMALL_NET)
            spawnNpc(player.coords.translateZ(3), npcType("npc.salamander_green"))

            val trapCoords = player.coords
            player.withProtectedAccess {
                with(probe.events) { setNetTrap(netTree(probe, trapCoords), swampPrey()) }
            }
            tickTraps(probe, 60)

            val trap = probe.traps.trapAt(trapCoords)
            assertEquals(State.CAUGHT, trap?.state)
            assertEquals(locId("loc.hunting_sapling_full_green"), trap?.loc?.id)

            player.withProtectedAccess { with(probe.events) { checkTrap(trapCoords) } }

            assertEquals(1, player.inv.count("obj.green_salamander"))
            assertEquals(1, player.inv.count(ROPE))
            assertEquals(1, player.inv.count(SMALL_NET))
            assertEquals(1520, player.statMap.getFineXP("stat.hunter"))
            assertNull(probe.traps.trapAt(trapCoords))
        }

    @Test
    fun GameTestState.`a failed net trap can be reset or dismantled for materials`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 0.0 }
            player.setCurrentLevel("stat.hunter", 29)
            player.inv[0] = InvObj(ROPE, 2)
            player.inv[1] = InvObj(SMALL_NET, 2)
            spawnNpc(player.coords.translateZ(3), npcType("npc.salamander_green"))

            val trapCoords = player.coords
            player.withProtectedAccess {
                with(probe.events) { setNetTrap(netTree(probe, trapCoords), swampPrey()) }
            }
            tickTraps(probe, 60)

            val trap = probe.traps.trapAt(trapCoords)
            assertEquals(State.FAILED, trap?.state)
            assertEquals(locId("loc.hunting_sapling_failed_swamp"), trap?.loc?.id)

            probe.traps.reset(checkNotNull(trap))
            assertEquals(State.ARMED, trap.state)
            assertEquals(locId("loc.hunting_sapling_net_set_swamp"), trap.loc.id)

            player.withProtectedAccess { with(probe.events) { dismantleTrap(trapCoords) } }
            assertNull(probe.traps.trapAt(trapCoords))
            assertEquals(2, player.inv.count(ROPE))
            assertEquals(2, player.inv.count(SMALL_NET))
        }

    @Test
    fun GameTestState.`baiting an armed net trap consumes the tar`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 29)
            player.inv[0] = InvObj(ROPE)
            player.inv[1] = InvObj(SMALL_NET)
            player.inv[2] = InvObj(GUAM_TAR)

            val trapCoords = player.coords
            player.withProtectedAccess {
                with(probe.events) { setNetTrap(netTree(probe, trapCoords), swampPrey()) }
            }
            player.withProtectedAccess {
                with(probe.events) { baitTrap(trapCoords, GUAM_TAR) }
            }
            assertEquals(0, player.inv.count(GUAM_TAR))
            assertEquals(true, probe.traps.trapAt(trapCoords)?.baited)
        }

    @Test
    fun GameTestState.`catching a butterfly needs the level a net and a jar`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 14)
            val ruby = probe.events.butterflies.first { it.npcSymbol == "npc.butterfly_ruby" }
            val npc = spawnNpc(player.coords.translateZ(2), npcType(ruby.npcSymbol))
            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = InvObj(BUTTERFLY_JAR)

            player.withProtectedAccess { with(probe.events) { catchButterfly(npc, ruby) } }
            assertEquals(1, player.inv.count(BUTTERFLY_JAR))

            player.setCurrentLevel("stat.hunter", 15)
            player.worn[0] = null
            player.withProtectedAccess { with(probe.events) { catchButterfly(npc, ruby) } }
            assertEquals(1, player.inv.count(BUTTERFLY_JAR))

            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = null
            player.withProtectedAccess { with(probe.events) { catchButterfly(npc, ruby) } }
            assertTrue(npc.isVisible)
        }

    @Test
    fun GameTestState.`a caught butterfly fills a jar and grants xp`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.setCurrentLevel("stat.hunter", 45)
            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = InvObj(BUTTERFLY_JAR)
            val warlock = probe.events.butterflies.first { it.npcSymbol == "npc.butterfly_warlock" }
            val npc = spawnNpc(player.coords.translateZ(2), npcType(warlock.npcSymbol))

            player.withProtectedAccess { with(probe.events) { catchButterfly(npc, warlock) } }

            assertEquals(0, player.inv.count(BUTTERFLY_JAR))
            assertEquals(1, player.inv.count("obj.butterfly_jar_warlock"))
            assertEquals(540, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`a missed catch keeps the jar and the butterfly flees`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            probe.traps.catchChance = { _, _ -> 0.0 }
            player.setCurrentLevel("stat.hunter", 45)
            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = InvObj(BUTTERFLY_JAR)
            val warlock = probe.events.butterflies.first { it.npcSymbol == "npc.butterfly_warlock" }
            val spawn = player.coords.translateZ(2)
            val npc = spawnNpc(spawn, npcType(warlock.npcSymbol))

            player.withProtectedAccess { with(probe.events) { catchButterfly(npc, warlock) } }

            assertEquals(1, player.inv.count(BUTTERFLY_JAR))
            assertEquals(0, player.inv.count("obj.butterfly_jar_warlock"))
            assertEquals(0, player.statMap.getFineXP("stat.hunter"))
            assertNotEquals(spawn, npc.coords)
        }

    @Test
    fun GameTestState.`releasing a jarred butterfly returns the empty jar`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj("obj.butterfly_jar_warlock")

            player.withProtectedAccess { opHeld4(0) }

            assertEquals(0, player.inv.count("obj.butterfly_jar_warlock"))
            assertEquals(1, player.inv.count(BUTTERFLY_JAR))
        }

    @Test
    fun GameTestState.`catching an impling with a net fills a jar and grants gielinor xp`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = InvObj(IMPLING_JAR)
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_1"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings.first()) }
            }

            assertEquals(0, player.inv.count(IMPLING_JAR))
            assertEquals(1, player.inv.count("obj.ii_captured_impling_1"))
            assertEquals(200, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`catching a maze impling grants puro-puro xp`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = InvObj(IMPLING_JAR)
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_1_maze"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings.first()) }
            }

            assertEquals(1, player.inv.count("obj.ii_captured_impling_1"))
            assertEquals(180, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`barehanded catching needs ten extra levels`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 20)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(IMPLING_JAR)
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_1"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings.first()) }
            }

            assertEquals(1, player.inv.count(IMPLING_JAR))
            assertEquals(0, player.inv.count("obj.ii_captured_impling_1"))
            assertEquals(0, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`barehanded catch outside puro-puro needs no jar and pays loot directly`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_2"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings[1]) }
            }

            assertEquals(0, player.inv.count("obj.ii_captured_impling_2"))
            assertEquals(220, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`maze implings refuse barehanded catching`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(IMPLING_JAR)
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_1_maze"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings.first()) }
            }

            assertEquals(1, player.inv.count(IMPLING_JAR))
            assertEquals(0, player.inv.count("obj.ii_captured_impling_1"))
            assertEquals(0, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`maze catch works with the net in inventory`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(IMPLING_JAR)
            player.inv[1] = InvObj(BUTTERFLY_NET)
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_1_maze"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings.first()) }
            }

            assertEquals(0, player.inv.count(IMPLING_JAR))
            assertEquals(1, player.inv.count("obj.ii_captured_impling_1"))
            assertEquals(180, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`barehanded catch pays loot directly even with a jar on hand`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.inv[0] = InvObj(IMPLING_JAR)
            val npc = spawnNpc(player.coords.translateZ(2), npcType("npc.ii_impling_type_1"))

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, probe.events.implings.first()) }
            }

            assertTrue(player.inv.count(IMPLING_JAR) >= 1)
            assertEquals(0, player.inv.count("obj.ii_captured_impling_1"))
            assertEquals(200, player.statMap.getFineXP("stat.hunter"))
        }

    @Test
    fun GameTestState.`looting an impling jar removes the jarred item and rolls loot`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            var jarReturns = 0
            var lootRolls = 0
            repeat(40) {
                for (slot in 0 until player.inv.size) {
                    player.inv[slot] = null
                }
                player.inv[0] = InvObj("obj.ii_captured_impling_1")
                player.withProtectedAccess { opHeld3(0) }
                assertEquals(0, player.inv.count("obj.ii_captured_impling_1"))
                if (player.inv.count(IMPLING_JAR) == 1) jarReturns++
                val extra = player.inv.count { it != null } - player.inv.count(IMPLING_JAR)
                if (extra > 0) lootRolls++
            }
            assertTrue(lootRolls >= 25)
            assertTrue(jarReturns >= 25)
        }

    @Test
    fun GameTestState.`destroying an impling jar removes it after confirmation`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj("obj.ii_captured_impling_1")

            player.withProtectedAccess { opHeld5(0) }

            val coroutine = player.activeCoroutine
            assertNotNull(coroutine)
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                coroutine.resumeWith(
                    ResumePauseButtonInput("component.confirmdestroy:universe", 1)
                )
            }

            assertEquals(0, player.inv.count("obj.ii_captured_impling_1"))
        }

    @Test
    fun GameTestState.`cancelling the destroy dialog keeps the impling jar`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj("obj.ii_captured_impling_1")

            player.withProtectedAccess { opHeld5(0) }

            val coroutine = player.activeCoroutine
            assertNotNull(coroutine)
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                coroutine.resumeWith(
                    ResumePauseButtonInput("component.confirmdestroy:universe", 0)
                )
            }

            assertEquals(1, player.inv.count("obj.ii_captured_impling_1"))
        }

    @Test
    fun GameTestState.`lucky impling jar has no loot table yet and stays put`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj("obj.ii_captured_impling_11")

            player.withProtectedAccess { opHeld3(0) }

            assertEquals(1, player.inv.count("obj.ii_captured_impling_11"))
        }

    @Test
    fun GameTestState.`impling precursor manifests as an impling after the invisible wait`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            val npc =
                spawnNpc(
                    player.coords.translateZ(2),
                    npcType("npc.ii_common_impling_precursor"),
                )

            probe.mapClock.cycle += 300
            probe.implingSpawns.tick()

            assertNotNull(npc.transmog)
            assertTrue(npc.visType.internalName.startsWith("npc.ii_impling_type_"))
        }

    @Test
    fun GameTestState.`manifested impling cycles back to visible after the window closes`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            val npc =
                spawnNpc(
                    player.coords.translateZ(2),
                    npcType("npc.ii_common_impling_precursor"),
                )

            probe.mapClock.cycle += 300
            probe.implingSpawns.tick()
            assertNotNull(npc.transmog)

            // The engine expires the transmog at the end of the visible window.
            npc.resetTransmog()
            probe.mapClock.cycle += 3000
            probe.implingSpawns.tick()
            probe.mapClock.cycle += 300
            probe.implingSpawns.tick()

            assertNotNull(npc.transmog)
        }

    @Test
    fun GameTestState.`catching a manifested impling consumes a jar and produces the jarred type`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.events.implingCatchChance = { _, _ -> 1.0 }
            player.worn[0] = InvObj(BUTTERFLY_NET)
            player.inv[0] = InvObj(IMPLING_JAR)
            val npc =
                spawnNpc(
                    player.coords.translateZ(2),
                    npcType("npc.ii_common_impling_precursor"),
                )

            probe.mapClock.cycle += 300
            probe.implingSpawns.tick()
            val manifested = npc.visType
            val prey =
                checkNotNull(
                    probe.events.implings.firstOrNull { it.npcSymbol == manifested.internalName }
                )

            player.withProtectedAccess {
                with(probe.events) { catchImpling(npc, prey) }
            }

            assertEquals(0, player.inv.count(IMPLING_JAR))
            assertEquals(1, player.inv.count(prey.jarred))
        }

    @Test
    fun GameTestState.`a caught kebbit swaps the glove and lands a retrievable falcon`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_WITH)
            val kebbit = spawnNpc(player.coords.translateZ(2), npcType("npc.huntingbeast_speedy"))
            val prey = probe.events.falconryPrey.first { it.npcSymbol == "npc.huntingbeast_speedy" }

            player.withProtectedAccess { with(probe.events) { sendFalcon(kebbit, prey) } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_EMPTY))
            val falcon =
                probe.npcRepo.findAll(kebbit.coords).firstOrNull {
                    it.type.internalName == "npc.hunting_falcon_onspeedy"
                }
            assertNotNull(falcon)
            assertEquals(player, probe.falconry.ownerOf(falcon))
        }

    @Test
    fun GameTestState.`retrieving the falcon pays drops and xp and returns the bird`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_WITH)
            val kebbit = spawnNpc(player.coords.translateZ(2), npcType("npc.huntingbeast_speedy"))
            val prey = probe.events.falconryPrey.first { it.npcSymbol == "npc.huntingbeast_speedy" }

            player.withProtectedAccess { with(probe.events) { sendFalcon(kebbit, prey) } }
            val falcon =
                probe.npcRepo.findAll(kebbit.coords).firstOrNull {
                    it.type.internalName == "npc.hunting_falcon_onspeedy"
                }
            assertNotNull(falcon)

            player.withProtectedAccess { with(probe.events) { retrieveFalcon(falcon, prey) } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_WITH))
            assertEquals(1, player.inv.count("obj.bones"))
            assertEquals(1, player.inv.count("obj.huntingbeast_speedy_fur"))
            assertEquals(1040, player.statMap.getFineXP("stat.hunter"))
            assertNull(probe.falconry.ownerOf(falcon))
        }

    @Test
    fun GameTestState.`a missed falcon strike returns the bird to the glove`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.traps.catchChance = { _, _ -> 0.0 }
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_WITH)
            val kebbit = spawnNpc(player.coords.translateZ(2), npcType("npc.huntingbeast_speedy"))
            val prey = probe.events.falconryPrey.first { it.npcSymbol == "npc.huntingbeast_speedy" }

            player.withProtectedAccess { with(probe.events) { sendFalcon(kebbit, prey) } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_WITH))
            assertTrue(
                probe.npcRepo.findAll(kebbit.coords).none {
                    it.type.internalName == "npc.hunting_falcon_onspeedy"
                }
            )
        }

    @Test
    fun GameTestState.`falcon strikes require the hunter level`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 20)
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_WITH)
            val kebbit = spawnNpc(player.coords.translateZ(2), npcType("npc.huntingbeast_speedy"))
            val prey = probe.events.falconryPrey.first { it.npcSymbol == "npc.huntingbeast_speedy" }

            player.withProtectedAccess { with(probe.events) { sendFalcon(kebbit, prey) } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_WITH))
            assertNull(probe.falconry.ownerOf(kebbit))
        }

    @Test
    fun GameTestState.`an empty glove cannot send a falcon`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_EMPTY)
            val kebbit = spawnNpc(player.coords.translateZ(2), npcType("npc.huntingbeast_speedy"))
            val prey = probe.events.falconryPrey.first { it.npcSymbol == "npc.huntingbeast_speedy" }

            player.withProtectedAccess { with(probe.events) { sendFalcon(kebbit, prey) } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_EMPTY))
        }

    @Test
    fun GameTestState.`renting a falcon costs 500 coins and equips the glove`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj("obj.coins", 1000)

            player.withProtectedAccess { with(probe.events) { rentFalcon() } }

            assertEquals(500, player.inv.count("obj.coins"))
            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_WITH))
        }

    @Test
    fun GameTestState.`renting auto-unequips weapon and shield into the inventory`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.inv[0] = InvObj("obj.coins", 1000)
            player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_scimitar")
            player.worn[Wearpos.LeftHand.slot] = InvObj("obj.wooden_shield")
            player.worn[Wearpos.Hands.slot] = InvObj("obj.leather_gloves")

            player.withProtectedAccess { with(probe.events) { rentFalcon() } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_WITH))
            assertEquals(1, player.inv.count("obj.bronze_scimitar"))
            assertEquals(1, player.inv.count("obj.wooden_shield"))
            assertEquals(1, player.inv.count("obj.leather_gloves"))
        }

    @Test
    fun GameTestState.`renting is refused when the inventory cannot hold unequipped gear`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            for (slot in 0 until 28) {
                player.inv[slot] = InvObj("obj.logs")
            }
            player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_scimitar")

            player.withProtectedAccess { with(probe.events) { rentFalcon() } }

            assertTrue(player.worn[Wearpos.RightHand.slot].isType("obj.bronze_scimitar"))
        }

    @Test
    fun GameTestState.`leaving the falconry sends the falcon home`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_WITH)
            player.teleport(CoordGrid(2380, 3600))

            player.teleport(CoordGrid(3200, 3200))
            eventBus.publish(
                PlayerMovementEvent.CoordsMovedEvent(player, CoordGrid(2380, 3600))
            )

            assertTrue(player.worn[Wearpos.RightHand.slot].isType(FALCON_GLOVES_EMPTY))
        }

    @Test
    fun GameTestState.`a landed falcon leaves its prey after the timeout`() =
        runInjectedGameTest(Probe::class, HunterTestModule(), HunterEvents::class) { probe ->
            player.setCurrentLevel("stat.hunter", 99)
            probe.traps.catchChance = { _, _ -> 1.0 }
            player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVES_WITH)
            val kebbit = spawnNpc(player.coords.translateZ(2), npcType("npc.huntingbeast_speedy"))
            val prey = probe.events.falconryPrey.first { it.npcSymbol == "npc.huntingbeast_speedy" }

            player.withProtectedAccess { with(probe.events) { sendFalcon(kebbit, prey) } }
            val falcon =
                probe.npcRepo.findAll(kebbit.coords).firstOrNull {
                    it.type.internalName == "npc.hunting_falcon_onspeedy"
                }
            assertNotNull(falcon)

            probe.mapClock.cycle += FalconryManager.STAY_TICKS
            probe.falconry.tick()

            assertNull(probe.falconry.ownerOf(falcon))
            assertTrue(probe.npcRepo.findAll(kebbit.coords).none { it === falcon })
        }

    private fun HunterEvents.swampPrey() =
        checkNotNull(netPrey.firstOrNull { it.npcSymbol == "npc.salamander_green" })

    private fun netTree(probe: Probe, coords: CoordGrid): BoundLocInfo {
        val loc =
            probe.locRepo.add(
                coords,
                "loc.hunting_sapling_up_swamp",
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        return BoundLocInfo(loc, checkNotNull(ServerCacheManager.getObject(loc.id)))
    }

    private fun deadfallBoulder(probe: Probe, coords: CoordGrid): BoundLocInfo {
        val loc =
            probe.locRepo.add(
                coords,
                "loc.hunting_deadfall_boulder",
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        return BoundLocInfo(loc, checkNotNull(ServerCacheManager.getObject(loc.id)))
    }

    private fun npcType(symbol: String) =
        checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)))

    private fun locId(symbol: String) =
        checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC))).id

    private fun tickTraps(probe: Probe, ticks: Int) = repeat(ticks) { probe.traps.tick() }

    private companion object {
        const val SNARE = "obj.hunting_ojibway_bird_snare"
        const val BOX_TRAP = "obj.hunting_box_trap"
        const val KNIFE = "obj.knife"
        const val LOGS = "obj.logs"
        const val ROPE = "obj.rope"
        const val SMALL_NET = "obj.net"
        const val GUAM_TAR = "obj.salamander_tar_green"
        const val BUTTERFLY_NET = "obj.hunting_butterfly_net"
        const val BUTTERFLY_JAR = "obj.butterfly_jar"
        const val IMPLING_JAR = "obj.ii_impling_jar"
        const val FALCON_GLOVES_WITH = "obj.falcon_on_gloves"
        const val FALCON_GLOVES_EMPTY = "obj.falcon_gloves"
    }

    private class HunterTestModule : AbstractModule() {
        override fun configure() {
            bind(DropTableTomlResolver::class.java).to(ContentDropTableTomlResolver::class.java)
        }
    }

    class Probe
    @Inject
    constructor(
        val events: HunterEvents,
        val traps: HunterTrapManager,
        val locRepo: LocRepository,
        val npcRepo: NpcRepository,
        val implingSpawns: ImplingSpawnManager,
        val falconry: FalconryManager,
        val mapClock: MapClock,
    )
}
