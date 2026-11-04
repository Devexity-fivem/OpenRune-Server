package org.rsmod.content.areas.wilderness.locs

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.areas.wilderness.checkWildernessBossFee
import org.rsmod.content.areas.wilderness.tryPayWildernessBossFee
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three Revenant Cave entrances are one-way drops into the Forinthry Dungeon; exits at the
 * level-17 and level-40 sides climb back out. Entry costs 100,000 coins, taken from inventory or
 * bank, and only needs repaying after dying inside the caves or to another player.
 */
class RevenantCaves : PluginScript() {

    private data class Route(val loc: String, val dest: CoordGrid)

    private val entrances =
        listOf(
            Route("loc.wild_cave_entrance_low", LOW_INSIDE),
            Route("loc.wild_cave_entrance_mid", MID_INSIDE),
            Route("loc.wild_cave_entrance_high", HIGH_INSIDE),
            Route("loc.wild_cave_exit01", MID_INSIDE),
        )

    private val exits =
        listOf(
            Route("loc.wild_cave_exit_low", LOW_OUTSIDE),
            Route("loc.wild_cave_exit_mid", MID_OUTSIDE),
            Route("loc.wild_cave_exit_high", HIGH_OUTSIDE),
            Route("loc.wild_cave_exit_surface", LOW_OUTSIDE),
        )

    override fun ScriptContext.startup() {
        for (route in entrances) {
            onOpLoc1(route.loc) { enterCave(route) }
            onOpLoc2(route.loc) { peek() }
            onOpLoc3(route.loc) { checkFee() }
        }
        for (route in exits) {
            onOpLoc1(route.loc) { leaveCave(route.dest) }
        }
    }

    private suspend fun ProtectedAccess.enterCave(route: Route) {
        arriveDelay()
        if (vars[FEE_VARP] == 1) {
            dropIn(route)
            return
        }
        tryPayWildernessBossFee(ENTRY_FEE) { source ->
            vars[FEE_VARP] = 1
            mes("<col=ef1020>You drop into the cave and $FEE_TEXT is taken from $source.")
            dropIn(route)
        }
    }

    private suspend fun ProtectedAccess.dropIn(route: Route) {
        anim(ENTER_SEQ)
        delay(1)
        telejump(route.dest, TeleportType.Movement)
        anim(LAND_SEQ)
    }

    private suspend fun ProtectedAccess.leaveCave(dest: CoordGrid) {
        arriveDelay()
        anim(EXIT_SEQ)
        delay(1)
        telejump(dest, TeleportType.Movement)
        mes("You climb back up to the surface.")
    }

    private fun ProtectedAccess.peek() {
        mes("You peek into the darkness of the caves below.")
    }

    private suspend fun ProtectedAccess.checkFee() {
        if (vars[FEE_VARP] == 1) {
            mes("You have already paid the entry fee.")
            return
        }
        checkWildernessBossFee(ENTRY_FEE)
    }

    private companion object {
        private const val ENTRY_FEE = 100_000
        private const val FEE_TEXT = "100,000 coins"
        private const val FEE_VARP = "varp.wild_cave_fee_paid"
        private const val ENTER_SEQ = "seq.godwars_human_crawling"
        private const val LAND_SEQ = "seq.human_falling_end"
        private const val EXIT_SEQ = "seq.human_reachforladder"

        private val LOW_INSIDE = CoordGrid(3170, 10145, 0)
        private val MID_INSIDE = CoordGrid(3235, 10085, 0)
        private val HIGH_INSIDE = CoordGrid(3242, 10200, 0)

        private val LOW_OUTSIDE = CoordGrid(3074, 3654, 0)
        private val MID_OUTSIDE = CoordGrid(3250, 3790, 0)
        private val HIGH_OUTSIDE = CoordGrid(3124, 3832, 0)
    }
}
