package org.rsmod.content.minigames.mixology

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.interfaces.omnishop.OmnishopCurrencyAdapter
import org.rsmod.content.interfaces.omnishop.OmnishopCurrencyAdapters
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Wires the Mixology Rewards shop's varp-backed resin currencies. */
class MixologyShop : PluginScript() {
    override fun ScriptContext.startup() {
        for ((row, varp) in CURRENCIES) {
            OmnishopCurrencyAdapters.register(row, Resin(varp))
        }
    }

    private class Resin(private val varp: String) : OmnishopCurrencyAdapter {
        override fun ProtectedAccess.count(currencyRow: Int): Int = vars[varp]

        override fun ProtectedAccess.charge(currencyRow: Int, amount: Int) {
            vars[varp] = (vars[varp] - amount).coerceAtLeast(0)
        }
    }

    private companion object {
        val CURRENCIES =
            mapOf(
                "dbrow.omnishop_currency_mixology_mox" to "varp.mixology_mox_points",
                "dbrow.omnishop_currency_mixology_aga" to "varp.mixology_aga_points",
                "dbrow.omnishop_currency_mixology_lye" to "varp.mixology_lye_points",
            )
    }
}
