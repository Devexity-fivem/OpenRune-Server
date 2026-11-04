package org.rsmod.content.areas.wilderness.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.areas.wilderness.tryPayWildernessBossFee
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Resource Area gate charges an entry fee scaled by Wilderness Diary completion (7,500 coins,
 * 6,000 after medium, 3,750 after hard, free after elite), taken from inventory or bank. The gate
 * stays open briefly once paid for; peeking lets players scout inside first.
 */
class WildernessResourceArea @Inject constructor(private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.wilderness_resource_gate") { openGate(it.loc) }
        onOpLoc2("loc.wilderness_resource_gate") { peek() }
    }

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo) {
        arriveDelay()
        val fee = entryFee()
        if (fee == 0) {
            locRepo.del(gate, OPEN_TICKS)
            return
        }
        tryPayWildernessBossFee(fee) { source ->
            mes("<col=ef1020>${fee.format()} coins is taken from $source to pay the entry fee.")
            locRepo.del(gate, OPEN_TICKS)
        }
    }

    private fun ProtectedAccess.entryFee(): Int =
        when {
            vars[ELITE_VARBIT] != 0 -> 0
            vars[HARD_VARBIT] != 0 -> 3_750
            vars[MEDIUM_VARBIT] != 0 -> 6_000
            else -> 7_500
        }

    private fun ProtectedAccess.peek() {
        mes("You peer through the gate at the resources beyond.")
    }

    private fun Int.format(): String = "%,d".format(this)

    private companion object {
        private const val MEDIUM_VARBIT = "varbit.wilderness_diary_medium_complete"
        private const val HARD_VARBIT = "varbit.wilderness_diary_hard_complete"
        private const val ELITE_VARBIT = "varbit.wilderness_diary_elite_complete"
        private const val OPEN_TICKS = 40
    }
}
