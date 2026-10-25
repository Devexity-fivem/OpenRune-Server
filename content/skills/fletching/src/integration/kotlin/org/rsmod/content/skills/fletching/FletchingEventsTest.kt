package org.rsmod.content.skills.fletching

import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.ui.Component

/* Obj transactions and suspended coroutines are not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class FletchingEventsTest {
    @Test
    fun GameTestState.`carving logs produces all arrow shafts`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.inv[0] = InvObj("obj.knife")
            player.inv[1] = InvObj("obj.logs")
            player.inv[2] = InvObj("obj.logs")

            selectFromMenu(probe, "obj.logs", "obj.knife", "component.skillmulti:a")
            advance(15)

            assertEquals(0, player.inv.count("obj.logs"))
            assertEquals(30, player.inv.count("obj.arrow_shaft"))
            assertEquals(100, player.statMap.getFineXP("stat.fletching"))
        }

    @Test
    fun GameTestState.`carving without a knife refuses with a message`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.inv[0] = InvObj("obj.logs")

            selectFromMenu(probe, "obj.logs", "obj.knife", "component.skillmulti:a")

            // The refusal message is captured during resume; `advance` clears captures.
            assertMessageSent("You need a knife to do that.")
            advance(5)
            assertEquals(1, player.inv.count("obj.logs"))
            assertEquals(0, player.inv.count("obj.arrow_shaft"))
        }

    @Test
    fun GameTestState.`carving is gated by fletching level`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.inv[0] = InvObj("obj.knife")
            player.inv[1] = InvObj("obj.logs")

            // Button "d" is the level 10 longbow (u); the test player is level 1.
            selectFromMenu(probe, "obj.logs", "obj.knife", "component.skillmulti:d")

            // The refusal message is captured during resume; `advance` clears captures.
            assertMessageSent("You need a Fletching level of 10 to make this.")
            advance(5)
            assertEquals(1, player.inv.count("obj.logs"))
            assertEquals(0, player.inv.count("obj.unstrung_longbow"))
        }

    @Test
    fun GameTestState.`feathers attach to shafts in sets of fifteen`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.setCurrentLevel("stat.fletching", 99)
            player.inv[0] = InvObj("obj.arrow_shaft", 30)
            player.inv[1] = InvObj("obj.feather", 20)

            selectFromMenu(probe, "obj.arrow_shaft", "obj.feather", "component.skillmulti:a")
            advance(10)

            // Only one full batch is possible with 20 feathers; leftovers are kept.
            assertEquals(15, player.inv.count("obj.headless_arrow"))
            assertEquals(15, player.inv.count("obj.arrow_shaft"))
            assertEquals(5, player.inv.count("obj.feather"))
            assertEquals(150, player.statMap.getFineXP("stat.fletching"))
        }

    @Test
    fun GameTestState.`feather variants count towards the feather input`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.setCurrentLevel("stat.fletching", 99)
            player.inv[0] = InvObj("obj.arrow_shaft", 15)
            player.inv[1] = InvObj("obj.hunting_jungle_feather", 15)

            selectFromMenu(
                probe,
                "obj.arrow_shaft",
                "obj.hunting_jungle_feather",
                "component.skillmulti:a",
            )
            advance(10)

            assertEquals(15, player.inv.count("obj.headless_arrow"))
            assertEquals(0, player.inv.count("obj.arrow_shaft"))
            assertEquals(0, player.inv.count("obj.hunting_jungle_feather"))
        }

    @Test
    fun GameTestState.`arrowheads attach to headless arrows`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.setCurrentLevel("stat.fletching", 99)
            player.inv[0] = InvObj("obj.headless_arrow", 30)
            player.inv[1] = InvObj("obj.bronze_arrowheads", 30)

            selectFromMenu(
                probe,
                "obj.headless_arrow",
                "obj.bronze_arrowheads",
                "component.skillmulti:a",
            )
            advance(10)

            assertEquals(30, player.inv.count("obj.bronze_arrow"))
            assertEquals(0, player.inv.count("obj.headless_arrow"))
            assertEquals(0, player.inv.count("obj.bronze_arrowheads"))
            // 1.3 xp per arrow x 30 = 39 xp = 390 tenths.
            assertEquals(390, player.statMap.getFineXP("stat.fletching"))
        }

    @Test
    fun GameTestState.`bow stringing produces a strung bow`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.setCurrentLevel("stat.fletching", 99)
            player.inv[0] = InvObj("obj.unstrung_shortbow")
            player.inv[1] = InvObj("obj.bow_string")

            selectFromMenu(
                probe,
                "obj.unstrung_shortbow",
                "obj.bow_string",
                "component.skillmulti:a",
            )
            advance(10)

            assertEquals(0, player.inv.count("obj.unstrung_shortbow"))
            assertEquals(0, player.inv.count("obj.bow_string"))
            assertTrue(player.inv.any { it.isType("obj.shortbow") })
            assertEquals(50, player.statMap.getFineXP("stat.fletching"))
        }

    @Test
    fun GameTestState.`menu omits recipes without enough materials`() =
        runInjectedGameTest(Probe::class, null, FletchingEvents::class) { probe ->
            player.setCurrentLevel("stat.fletching", 99)
            player.inv[0] = InvObj("obj.knife")
            player.inv[1] = InvObj("obj.achey_tree_logs")

            player.withProtectedAccess {
                with(probe.events) { useItemOnItem("obj.achey_tree_logs", "obj.knife") }
            }

            // The comp ogre bow needs wolf bones; only ogre shafts should be offered.
            val labels =
                client.filterIsInstance<RunClientScript>().first { it.id == 2046 }.values[1] as String
            assertEquals(1, labels.count { it == '|' })
            assertTrue("Ogre arrow shaft" in labels)

            player.ui.modals.entries().forEach { (key, _) -> player.ui.queueClose(Component(key)) }
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.skillmulti:a", 0))
            advance(10)

            assertEquals(0, player.inv.count("obj.achey_tree_logs"))
            assertEquals(4, player.inv.count("obj.ogre_arrow_shaft"))
        }

    /** Mirrors ResumePauseButtonHandler: the modal close is queued before resuming. */
    private fun GameTestScope.selectFromMenu(
        probe: Probe,
        first: String,
        second: String,
        button: String,
    ) {
        player.withProtectedAccess {
            with(probe.events) { useItemOnItem(first, second) }
        }
        player.ui.modals.entries().forEach { (key, _) -> player.ui.queueClose(Component(key)) }
        player.resumeActiveCoroutine(ResumePauseButtonInput(button, 0))
    }

    class Probe
    @Inject
    constructor(val events: FletchingEvents)
}
