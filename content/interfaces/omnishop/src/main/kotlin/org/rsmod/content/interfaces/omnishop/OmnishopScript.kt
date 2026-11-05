package org.rsmod.content.interfaces.omnishop

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfScriptArgs
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class OmnishopScript : PluginScript() {
    override fun ScriptContext.startup() {
        onIfClose("interface.omnishop_main") { player.omnishopLastShop = -1 }
        onIfScriptTrigger<InfoArgs>("component.omnishop_main:trigger_request_info") {
            sendInfo(it.shop, it.index)
        }
        onIfScriptTrigger<BuyArgs>("component.omnishop_main:trigger_buy") {
            buy(it.shop, it.index, it.option)
        }
        onIfScriptTrigger<ExamineArgs>("component.omnishop_main:trigger_examine") {
            val obj = ServerCacheManager.getItem(it.obj) ?: return@onIfScriptTrigger
            mes(obj.examine)
        }
        onIfModalButton("component.omnishop_side:items") { button ->
            val obj = button.obj ?: return@onIfModalButton
            when (button.op) {
                IfButtonOp.Op2 -> sell(obj, 1)
                IfButtonOp.Op3 -> sell(obj, 5)
                IfButtonOp.Op4 -> sell(obj, 10)
                IfButtonOp.Op5 -> sell(obj, 50)
                IfButtonOp.Op10 -> mes(obj.examine)
                else -> Unit
            }
        }
    }

    private fun ProtectedAccess.sendInfo(shop: Int, index: Int) {
        if (shop != player.omnishopLastShop) return
        val stock = OmnishopStock.find(shop, index) ?: return
        player.omnishopSelectedId = index
        runClientScript(INFO_UPDATE.asRSCM(RSCMType.CLIENTSCRIPT), shop, -1, index, stock.description(), 1, 0, 1)
    }

    internal fun ProtectedAccess.buy(shop: Int, index: Int, option: Int) {
        if (shop != player.omnishopLastShop) return
        val stock = OmnishopStock.find(shop, index) ?: return
        if (!stock.buyable) return
        val requested = stock.buyQuantity(option) ?: return
        val costs = stock.buyCosts()

        val fulfillment = OmnishopFulfillments.find(stock.stockRowId)
        if (fulfillment != null) {
            for ((currency, price) in costs) {
                if (price > 0 && currencyCount(currency) < price) {
                    mes("You don't have enough ${currency.pluralName}.")
                    return
                }
            }
            if (with(fulfillment) { fulfill(1) }) {
                for ((currency, price) in costs) {
                    if (price > 0) chargeCurrency(currency, price)
                }
            }
            return
        }

        var quantity = minOf(requested, stock.maxPurchase / stock.multiplier)
        val payments = mutableListOf<OmnishopPayment>()
        val paymentCurrencies = mutableListOf<OmnishopCurrency>()
        val adapterCosts = mutableListOf<Pair<OmnishopCurrency, Long>>()
        for ((currency, price) in costs) {
            if (price == 0L) continue
            if (OmnishopCurrencyAdapters.adapterFor(currency.row) != null) {
                adapterCosts += currency to price
            } else {
                payments += OmnishopPayment(currency.objs, price)
                paymentCurrencies += currency
            }
        }
        for ((i, payment) in payments.withIndex()) {
            if (payment.balance(player) < payment.price) {
                mes("You don't have enough ${paymentCurrencies[i].pluralName}.")
                return
            }
        }
        for ((currency, price) in adapterCosts) {
            val balance = adapterCount(currency)
            if (balance < price) {
                mes("You don't have enough ${currency.pluralName}.")
                return
            }
            quantity = minOf(quantity, (balance / price).toInt())
        }
        val bought =
            buyOmnishopStock(
                player,
                payments,
                stock.objFor(player),
                stock.multiplier,
                quantity,
                cert = stock.soldNoted,
            )
        if (bought == 0) {
            mes("You don't have enough inventory space.")
            return
        }
        for ((currency, price) in adapterCosts) {
            adapterCharge(currency, price * bought)
        }
        redraw()
    }

    internal fun ProtectedAccess.sell(obj: ItemServerType, requested: Int) {
        val shop = player.omnishopLastShop
        val stock = OmnishopStock.all(shop).firstOrNull { it.obj.id == obj.id }
        if (stock == null || !stock.sellable) {
            mes("You can't sell this item to this shop.")
            return
        }
        val quantity = minOf(requested, inv.count(obj.internalName))
        if (quantity == 0) return
        val costs = stock.sellCosts()
        if (costs.any { (currency, price) -> currency.objs.isEmpty() || price * quantity > Int.MAX_VALUE }) return
        val result =
            player.invTransaction(inv, autoCommit = false) {
                val inventory = select(inv)
                delete(inventory, obj.id, quantity)
                for ((currency, price) in costs) {
                    if (price > 0) add(inventory, currency.objs.first().id, (price * quantity).toInt())
                }
            }
        if (result.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        result.commitAll()
        redraw()
    }

    private fun ProtectedAccess.redraw() {
        val redraw = "varp.omnishop_redraw"
        VarPlayerIntMapSetter.set(player, redraw, player.vars[redraw] xor 1)
    }

    private fun ProtectedAccess.currencyCount(currency: OmnishopCurrency): Long =
        OmnishopCurrencyAdapters.adapterFor(currency.row)?.let { adapter ->
            adapter.run { count(currency.row).toLong() }
        }
            ?: currency.objs.sumOf { inv.physicalCount(it.internalName).toLong() }

    private fun ProtectedAccess.chargeCurrency(currency: OmnishopCurrency, amount: Long) {
        OmnishopCurrencyAdapters.adapterFor(currency.row)?.let { adapter ->
            adapter.run { charge(currency.row, amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
            return
        }
        var remaining = amount
        for (obj in currency.objs) {
            if (remaining == 0L) return
            val take = minOf(remaining, inv.physicalCount(obj.internalName).toLong()).toInt()
            if (take > 0 && invDel(inv, obj.internalName, take).success) remaining -= take
        }
    }

    private fun ProtectedAccess.adapterCount(currency: OmnishopCurrency): Long {
        val adapter = OmnishopCurrencyAdapters.adapterFor(currency.row) ?: return 0L
        return adapter.run { count(currency.row).toLong() }
    }

    private fun ProtectedAccess.adapterCharge(currency: OmnishopCurrency, amount: Long) {
        OmnishopCurrencyAdapters.adapterFor(currency.row)?.run {
            charge(currency.row, amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
    }

    internal data class InfoArgs(val shop: Int, val index: Int) : IfScriptArgs

    internal data class BuyArgs(val shop: Int, val index: Int, val option: Int) : IfScriptArgs

    internal data class ExamineArgs(val obj: Int) : IfScriptArgs

    private companion object {
        const val INFO_UPDATE = "clientscript.omnishop_info_update"
    }
}
