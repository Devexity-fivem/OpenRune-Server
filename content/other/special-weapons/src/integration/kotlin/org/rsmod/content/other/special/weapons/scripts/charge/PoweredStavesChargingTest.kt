package org.rsmod.content.other.special.weapons.scripts.charge

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.AYAK_MAX
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.AYAK_RUNE
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.AYAK_TEAR
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.ETHER_MAX
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.SANG
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.SANG_MAX
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.SEAS
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.SWAMP
import org.rsmod.content.other.special.weapons.scripts.charge.PoweredStavesCharging.Companion.TRIDENT_MAX
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType

/* Obj transactions and suspended coroutines are not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
class PoweredStavesChargingTest {
    @Test
    fun GameTestState.`charging consumes the exact recipe materials`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.toxic_tots_charged")
            player.inv[1] = InvObj("obj.snakeboss_scale", 500)
            player.inv[2] = InvObj("obj.deathrune", 100)
            player.inv[3] = InvObj("obj.chaosrune", 100)
            player.inv[4] = InvObj("obj.firerune", 500)

            player.withProtectedAccess {
                with(probe.charging) { chargeStaff(player.inv, 0, SWAMP, TRIDENT_MAX) }
            }
            player.resumeActiveCoroutine(ResumePCountDialogInput(40))
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.objectbox:universe", 1))
            advance()

            assertEquals(40, probe.charges.getCharges(player.inv[0], "varobj.trident_charges"))
            assertEquals(460, player.inv.count("obj.snakeboss_scale"))
            assertEquals(60, player.inv.count("obj.deathrune"))
            assertEquals(60, player.inv.count("obj.chaosrune"))
            assertEquals(300, player.inv.count("obj.firerune"))
        }

    @Test
    fun GameTestState.`charging is capped by the staff's remaining space`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.sanguinesti_staff")
            probe.charges.addCharges(
                player.inv,
                0,
                SANG_MAX - 2,
                "varobj.sanguinesti_charges",
                SANG_MAX,
            )
            player.inv[1] = InvObj("obj.bloodrune", 30)

            // Only 2 charges of space remain, so requesting 10 applies just 2 (6 blood runes).
            player.withProtectedAccess {
                with(probe.charging) { chargeStaff(player.inv, 0, SANG, SANG_MAX) }
            }
            player.resumeActiveCoroutine(ResumePCountDialogInput(10))
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.objectbox:universe", 1))
            advance()

            assertEquals(SANG_MAX, probe.charges.getCharges(player.inv[0], "varobj.sanguinesti_charges"))
            assertEquals(24, player.inv.count("obj.bloodrune"))
        }

    @Test
    fun GameTestState.`charging a seas trident to full converts to the tradeable variant`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.tots_charged")
            probe.charges.addCharges(
                player.inv,
                0,
                TRIDENT_MAX - 1,
                "varobj.trident_charges",
                TRIDENT_MAX,
            )
            player.inv[1] = InvObj("obj.deathrune", 10)
            player.inv[2] = InvObj("obj.chaosrune", 10)
            player.inv[3] = InvObj("obj.firerune", 50)
            player.inv[4] = InvObj("obj.coins", 100)

            player.withProtectedAccess {
                with(probe.charging) { chargeStaff(player.inv, 0, SEAS, TRIDENT_MAX) }
            }
            player.resumeActiveCoroutine(ResumePCountDialogInput(5))
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.objectbox:universe", 1))
            advance()

            // Only one charge fit; the trident is now the implicit-full `tots` variant.
            assertTrue(player.inv[0].isType("obj.tots"))
            assertEquals(9, player.inv.count("obj.deathrune"))
            assertEquals(9, player.inv.count("obj.chaosrune"))
            assertEquals(45, player.inv.count("obj.firerune"))
            assertEquals(90, player.inv.count("obj.coins"))
        }

    @Test
    fun GameTestState.`full seas trident refuses further charges`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.tots")
            player.inv[1] = InvObj("obj.deathrune", 100)
            player.inv[2] = InvObj("obj.chaosrune", 100)
            player.inv[3] = InvObj("obj.firerune", 500)
            player.inv[4] = InvObj("obj.coins", 1_000)

            player.withProtectedAccess {
                with(probe.charging) { chargeStaff(player.inv, 0, SEAS, TRIDENT_MAX) }
            }

            assertMessageSent("Your trident is already fully charged.")
            assertEquals(100, player.inv.count("obj.deathrune"))
        }

    @Test
    fun GameTestState.`charging without materials prints the failure message`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.sanguinesti_staff")

            player.withProtectedAccess {
                with(probe.charging) { chargeStaff(player.inv, 0, SANG, SANG_MAX) }
            }

            assertMessageSent("You don't have the required runes to charge your Sanguinesti staff.")
            assertEquals(0, probe.charges.getCharges(player.inv[0], "varobj.sanguinesti_charges"))
        }

    @Test
    fun GameTestState.`uncharge converts to the uncharged form and refunds materials`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.tots_charged")
            probe.charges.addCharges(player.inv, 0, 10, "varobj.trident_charges", TRIDENT_MAX)

            player.withProtectedAccess {
                with(probe.charging) { uncharge(player.inv, 0, SEAS) }
            }
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
            advance()

            assertTrue(player.inv[0].isType("obj.tots_uncharged"))
            assertEquals(10, player.inv.count("obj.deathrune"))
            assertEquals(10, player.inv.count("obj.chaosrune"))
            assertEquals(50, player.inv.count("obj.firerune"))
            assertEquals(100, player.inv.count("obj.coins"))
        }

    @Test
    fun GameTestState.`cancelling the uncharge prompt keeps the staff intact`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.tots_charged")
            probe.charges.addCharges(player.inv, 0, 10, "varobj.trident_charges", TRIDENT_MAX)

            player.withProtectedAccess {
                with(probe.charging) { uncharge(player.inv, 0, SEAS) }
            }
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 2))
            advance()

            assertTrue(player.inv[0].isType("obj.tots_charged"))
            assertEquals(10, probe.charges.getCharges(player.inv[0], "varobj.trident_charges"))
            assertEquals(0, player.inv.count("obj.deathrune"))
        }

    @Test
    fun GameTestState.`swapping a wilderness sceptre preserves its ether charges`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.wild_cave_sceptre_charged")
            probe.charges.addCharges(
                player.inv,
                0,
                1_500,
                "varobj.wilderness_ether_charges",
                ETHER_MAX,
            )

            player.withProtectedAccess {
                with(probe.charging) { swap(player.inv, 0) }
            }

            assertTrue(player.inv[0].isType("obj.wild_cave_sceptre_charged_recol"))
            assertEquals(
                1_500,
                probe.charges.getCharges(player.inv[0], "varobj.wilderness_ether_charges"),
            )
        }

    @Test
    fun GameTestState.`eye of ayak rejects tears while it holds rune charges`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.eye_of_ayak")
            probe.charges.addCharges(player.inv, 0, 5, "varobj.eye_of_ayak_rune_charges", AYAK_MAX)
            player.inv[1] = InvObj("obj.demon_tear", 100)

            player.withProtectedAccess {
                with(probe.charging) { chargeAyakPool(player.inv, 0, AYAK_TEAR) }
            }

            assertMessageSent(
                "The Eye of Ayak is charged with runes, and thus can only be charged " +
                    "with more runes. You must fully uncharge it to use demon tears."
            )
            assertEquals(0, probe.charges.getCharges(player.inv[0], "varobj.eye_of_ayak_tear_charges"))
            assertEquals(5, probe.charges.getCharges(player.inv[0], "varobj.eye_of_ayak_rune_charges"))
            assertEquals(100, player.inv.count("obj.demon_tear"))
        }

    @Test
    fun GameTestState.`eye of ayak rejects runes while it holds tear charges`() =
        runInjectedGameTest(Probe::class, null, PoweredStavesCharging::class) { probe ->
            player.inv[0] = InvObj("obj.eye_of_ayak")
            probe.charges.addCharges(player.inv, 0, 5, "varobj.eye_of_ayak_tear_charges", AYAK_MAX)
            player.inv[1] = InvObj("obj.deathrune", 100)
            player.inv[2] = InvObj("obj.chaosrune", 100)

            player.withProtectedAccess {
                with(probe.charging) { chargeAyakPool(player.inv, 0, AYAK_RUNE) }
            }
            advance()

            assertEquals(5, probe.charges.getCharges(player.inv[0], "varobj.eye_of_ayak_tear_charges"))
            assertEquals(0, probe.charges.getCharges(player.inv[0], "varobj.eye_of_ayak_rune_charges"))
            assertEquals(100, player.inv.count("obj.deathrune"))
        }

    class Probe
    @Inject
    constructor(val charging: PoweredStavesCharging, val charges: ObjChargeManager)
}
