package org.rsmod.content.minigames.tithefarm

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.interfaces.omnishop.OmnishopCurrencyAdapter
import org.rsmod.content.interfaces.omnishop.OmnishopCurrencyAdapters
import org.rsmod.content.interfaces.omnishop.OmnishopFulfillment
import org.rsmod.content.interfaces.omnishop.OmnishopFulfillments
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Wires the Tithe reward shop's varbit-backed currency and non-item stock fulfilment. */
class TitheShop : PluginScript() {
    override fun ScriptContext.startup() {
        OmnishopCurrencyAdapters.register(CURRENCY_ROW, TithePoints)
        OmnishopFulfillments.register(AUTOWEED_ROW, AutoWeed)
        OmnishopFulfillments.register(AUTOWEED_TOGGLE_ROW, AutoWeedToggle)
        OmnishopFulfillments.register(CAN_RECHARGE_ROW, CanRecharge)
    }

    private object TithePoints : OmnishopCurrencyAdapter {
        override fun ProtectedAccess.count(currencyRow: Int): Int = vars[VB_POINTS]

        override fun ProtectedAccess.charge(currencyRow: Int, amount: Int) {
            vars[VB_POINTS] = (vars[VB_POINTS] - amount).coerceAtLeast(0)
        }
    }

    private object AutoWeed : OmnishopFulfillment {
        override fun ProtectedAccess.fulfill(quantity: Int): Boolean {
            if (vars[VB_AUTOWEED] != 0) {
                mes("You have already purchased the auto-weed feature.")
                return false
            }
            vars[VB_AUTOWEED] = 1
            mes("You unlock the auto-weed feature for your farming patches.")
            return true
        }
    }

    private object AutoWeedToggle : OmnishopFulfillment {
        override fun ProtectedAccess.fulfill(quantity: Int): Boolean {
            if (vars[VB_AUTOWEED] == 0) {
                mes("You need to purchase the auto-weed feature first.")
                return false
            }
            vars[VB_AUTOWEED] = if (vars[VB_AUTOWEED] == 1) 2 else 1
            val state = if (vars[VB_AUTOWEED] == 1) "enabled" else "disabled"
            mes("Auto-weed is now $state.")
            return true
        }
    }

    private object CanRecharge : OmnishopFulfillment {
        override fun ProtectedAccess.fulfill(quantity: Int): Boolean {
            if (inv.count(ZEAH_CAN) == 0) {
                mes("You don't have a watering can that needs refilling.")
                return false
            }
            vars[VB_CAN_CHARGES] = CAN_CHARGES_MAX
            mes("Farmer Gricoller refills your watering can.")
            return true
        }
    }

    private companion object {
        const val CURRENCY_ROW = "dbrow.omnishop_currency_tithe_points"
        const val AUTOWEED_ROW = "dbrow.tithe_shop_autoweed"
        const val AUTOWEED_TOGGLE_ROW = "dbrow.tithe_shop_autoweed_toggle"
        const val CAN_RECHARGE_ROW = "dbrow.tithe_shop_watering_can_recharge"
        const val VB_POINTS = "varbit.hosidius_tithe_rewardpoints"
        const val VB_AUTOWEED = "varbit.farming_blockweeds"
        const val VB_CAN_CHARGES = "varbit.zeah_wateringcan_charges"
        const val ZEAH_CAN = "obj.zeah_wateringcan"
        const val CAN_CHARGES_MAX = 1000
    }
}
