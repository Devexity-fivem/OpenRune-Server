package org.rsmod.content.skills.hunter

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PuroPuroInterfaces
@Inject
constructor(private val objRepo: ObjRepository, private val puro: PuroPuroManager) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc3(ELNOCK) { openExchange(it.npc) }
        onOpNpc4(ELNOCK) { openStorage() }
        onOpNpc5(ELNOCK) { quickWithdraw() }

        onIfModalButton(EXCHANGE_ROWS) { selectExchange(it.comsub) }
        onIfModalButton(EXCHANGE_CONFIRM) { confirmExchange() }

        for ((component, slot) in STORAGE_MAIN_ROWS) {
            onIfModalButton(component) { storageOp(it.op, slot, withdraw = true) }
        }
        onIfModalButton(STORAGE_WITHDRAW_ALL) {
            if (it.op == IfButtonOp.Op1) withdrawAll()
        }
        for ((component, slot) in STORAGE_SIDE_ROWS) {
            onIfModalButton(component) { storageOp(it.op, slot, withdraw = false) }
        }
        onIfModalButton(STORAGE_STORE_ALL) {
            if (it.op == IfButtonOp.Op1) storeAll()
        }
        for ((component, mode) in STORAGE_QTY) {
            onIfModalButton(component) {
                if (it.op == IfButtonOp.Op1) vars[QTY_VARBIT] = mode
            }
        }

        onOpHeld3(BUTTERFLY_NET) { openTracker() }
        onOpHeld3(MAGIC_NET) { openTracker() }
        onOpHeld1(IMPLING_SCROLL) { openTracker() }

        onOpHeld1(JAR_GENERATOR) { generateJar(IMPLING_JAR, IMPLING_JAR_COST) }
        onOpHeld3(JAR_GENERATOR) { generateJar(BUTTERFLY_JAR, BUTTERFLY_JAR_COST) }
        onOpHeld4(JAR_GENERATOR) { checkGenerator() }
    }

    private fun ProtectedAccess.openExchange(npc: Npc) {
        faceEntitySquare(npc)
        vars[ELNEX_VARBIT] = 0
        ifOpenMainModal(EXCHANGE_INTERFACE)
        ifSetEvents(EXCHANGE_ROWS, 0..19, IfEvent.Op1)
        ifSetEvents(EXCHANGE_CONFIRM, ALL_SUBS, IfEvent.Op1)
    }

    private fun ProtectedAccess.selectExchange(comsub: Int) {
        if (comsub % 3 == 0) {
            vars[ELNEX_VARBIT] = comsub / 3 + 1
        }
    }

    private fun ProtectedAccess.confirmExchange() {
        val product = vars[ELNEX_VARBIT]
        val (item, amount, costs) = PRODUCTS.getOrNull(product - 1) ?: return
        if (costs.isEmpty()) {
            val jar = JARRED_IMPLINGS.firstOrNull { inv.count(it) > 0 }
            if (jar == null) {
                mes("You don't have any jarred implings to trade.")
                return
            }
            invDel(inv, jar, 1)
        } else {
            if (costs.any { (jar, qty) -> inv.count(jar) < qty }) {
                mes("You don't have the right combination of jarred implings for that.")
                return
            }
            costs.forEach { (jar, qty) -> invDel(inv, jar, qty) }
        }
        player.invAddOrDrop(objRepo, item, amount)
        mes("Elnock Inquisitor takes your jarred implings and hands you your reward.")
    }

    private fun ProtectedAccess.openStorage() {
        ifOpenMainSidePair(STORAGE_MAIN, STORAGE_SIDE)
        for ((component, _) in STORAGE_MAIN_ROWS) {
            ifSetEvents(
                component,
                ALL_SUBS,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
                IfEvent.Op9,
                IfEvent.Op10,
            )
        }
        ifSetEvents(STORAGE_WITHDRAW_ALL, ALL_SUBS, IfEvent.Op1, IfEvent.Op10)
        for ((component, _) in STORAGE_SIDE_ROWS) {
            ifSetEvents(
                component,
                ALL_SUBS,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
                IfEvent.Op10,
            )
        }
        ifSetEvents(STORAGE_STORE_ALL, ALL_SUBS, IfEvent.Op1, IfEvent.Op10)
        for ((component, _) in STORAGE_QTY) {
            ifSetEvents(component, ALL_SUBS, IfEvent.Op1)
        }
        vars[QTY_VARBIT] = 0
    }

    private suspend fun ProtectedAccess.storageOp(op: IfButtonOp, slot: Int, withdraw: Boolean) {
        if (op.slot !in 1..4) return
        val single = if (withdraw) slot != SLOT_JAR else carriedCount(slot) <= 1
        val amount =
            when (val action = resolveAction(op.slot, single)) {
                QTY_X -> countDialog()
                QTY_ALL -> Int.MAX_VALUE
                else -> action
            }
        if (amount <= 0) return
        if (withdraw) {
            withdrawStored(slot, amount)
        } else {
            storeCarried(slot, amount)
        }
    }

    private fun ProtectedAccess.resolveAction(opSlot: Int, single: Boolean): Int {
        if (single) return 1
        val selected = QTY_SELECTED[vars[QTY_VARBIT].coerceIn(0, 3)]
        if (opSlot == 1) return selected
        return QTY_FALLBACK.filter { it != selected }.getOrElse(opSlot - 2) { 0 }
    }

    private fun ProtectedAccess.carriedCount(slot: Int): Int =
        when (slot) {
            SLOT_NET -> inv.count(BUTTERFLY_NET) + inv.count(MAGIC_NET)
            SLOT_REPELLENT -> inv.count(IMP_REPELLENT)
            else -> inv.count(IMPLING_JAR)
        }

    private fun ProtectedAccess.withdrawStored(slot: Int, amount: Int) {
        when (slot) {
            SLOT_NET -> {
                val stored = vars[STORED_NET_VARBIT]
                if (stored == 0) return
                vars[STORED_NET_VARBIT] = 0
                val net = if (stored == 2) MAGIC_NET else BUTTERFLY_NET
                player.invAddOrDrop(objRepo, net, 1)
            }
            SLOT_REPELLENT -> {
                if (vars[STORED_REPELLENT_VARBIT] == 0) return
                vars[STORED_REPELLENT_VARBIT] = 0
                player.invAddOrDrop(objRepo, IMP_REPELLENT, 1)
            }
            SLOT_JAR -> {
                val stored = vars[STORED_JARS_VARBIT]
                if (stored == 0) return
                val take = amount.coerceAtMost(stored).coerceAtMost(inv.freeSpace())
                if (take <= 0) {
                    mes("You don't have enough inventory space.")
                    return
                }
                vars[STORED_JARS_VARBIT] = stored - take
                player.invAddOrDrop(objRepo, IMPLING_JAR, take)
            }
        }
    }

    private fun ProtectedAccess.storeCarried(slot: Int, amount: Int) {
        when (slot) {
            SLOT_NET -> {
                if (vars[STORED_NET_VARBIT] != 0) {
                    mes("Elnock is already holding a net for you.")
                    return
                }
                val magic = inv.count(MAGIC_NET) > 0
                val carried = inv.count(BUTTERFLY_NET) + inv.count(MAGIC_NET)
                if (carried == 0) {
                    mes("You aren't carrying a butterfly net.")
                    return
                }
                if (magic) {
                    invDel(inv, MAGIC_NET, 1)
                    vars[STORED_NET_VARBIT] = 2
                } else {
                    invDel(inv, BUTTERFLY_NET, 1)
                    vars[STORED_NET_VARBIT] = 1
                }
            }
            SLOT_REPELLENT -> {
                if (vars[STORED_REPELLENT_VARBIT] != 0) {
                    mes("Elnock is already holding some repellent for you.")
                    return
                }
                if (inv.count(IMP_REPELLENT) == 0) {
                    mes("You aren't carrying any imp repellent.")
                    return
                }
                invDel(inv, IMP_REPELLENT, 1)
                vars[STORED_REPELLENT_VARBIT] = 1
            }
            SLOT_JAR -> {
                val stored = vars[STORED_JARS_VARBIT]
                val room = JAR_CAPACITY - stored
                if (room <= 0) {
                    mes("Elnock can't store any more jars for you.")
                    return
                }
                val carried = inv.count(IMPLING_JAR)
                if (carried == 0) {
                    mes("You aren't carrying any empty impling jars.")
                    return
                }
                val put = amount.coerceAtMost(carried).coerceAtMost(room)
                invDel(inv, IMPLING_JAR, put)
                vars[STORED_JARS_VARBIT] = stored + put
            }
        }
    }

    private fun ProtectedAccess.withdrawAll() {
        withdrawStored(SLOT_NET, 1)
        withdrawStored(SLOT_REPELLENT, 1)
        withdrawStored(SLOT_JAR, Int.MAX_VALUE)
    }

    private fun ProtectedAccess.storeAll() {
        storeCarried(SLOT_NET, 1)
        storeCarried(SLOT_REPELLENT, 1)
        storeCarried(SLOT_JAR, Int.MAX_VALUE)
    }

    private fun ProtectedAccess.quickWithdraw() {
        withdrawAll()
    }

    private fun ProtectedAccess.openTracker() {
        ifOpenMainModal(TRACKER_INTERFACE)
        val surface = trackerString(maze = false)
        val maze = trackerString(maze = true)
        runClientScript(
            TRACKER_BUILD_SCRIPT,
            *TRACKER_COMPONENTS,
            surface,
            maze,
        )
    }

    private fun ProtectedAccess.trackerString(maze: Boolean): String =
        (0 until IMPLING_COUNT)
            .joinToString("|") { index ->
                val count = puro.trackerCount(player, index, maze)
                if (count > 0) "<col=ff0000>$count</col>" else "<col=ffff00>0</col>"
            }

    private fun ProtectedAccess.generateJar(jar: String, charges: Int) {
        val slot = (0 until inv.size).firstOrNull { inv[it]?.isType(JAR_GENERATOR) == true }
        val generator = slot?.let { inv[it] } ?: return
        val stored = if (generator.vars > 0) generator.vars else GENERATOR_CAPACITY
        if (stored < charges) {
            mes("Your jar generator doesn't have enough charges left.")
            return
        }
        val left = stored - charges
        if (left <= 0) {
            inv[slot] = null
            mes("Your jar generator falls apart, out of charges.")
        } else {
            inv[slot] = generator.copy(vars = left)
        }
        player.invAddOrDrop(objRepo, jar, 1)
    }

    private fun ProtectedAccess.checkGenerator() {
        val generator = inv.firstOrNull { it?.isType(JAR_GENERATOR) == true } ?: return
        val stored = if (generator.vars > 0) generator.vars else GENERATOR_CAPACITY
        mes("Your jar generator has $stored charges left.")
    }

    companion object {
        const val ELNOCK = "npc.ii_elnock"

        const val EXCHANGE_INTERFACE = "interface.ii_elnocks_exchange"
        const val EXCHANGE_ROWS = "component.ii_elnocks_exchange:shop_frame"
        const val EXCHANGE_CONFIRM = "component.ii_elnocks_exchange:confirm"
        const val ELNEX_VARBIT = "varbit.ii_elnex"

        const val STORAGE_MAIN = "interface.ii_elnock_storage"
        const val STORAGE_SIDE = "interface.ii_elnock_storage_side"
        const val STORAGE_WITHDRAW_ALL = "component.ii_elnock_storage:withdrawal_all"
        const val STORAGE_STORE_ALL = "component.ii_elnock_storage_side:store_all"
        const val QTY_VARBIT = "varbit.ii_elnock_storage_selectedquantity"

        const val STORED_NET_VARBIT = "varbit.ii_stored_net"
        const val STORED_REPELLENT_VARBIT = "varbit.ii_stored_repellent"
        const val STORED_JARS_VARBIT = "varbit.ii_stored_impling_jars"

        const val TRACKER_INTERFACE = "interface.ii_tracker"
        const val TRACKER_BUILD_SCRIPT = 1327
        const val IMPLING_COUNT = 12

        const val SLOT_NET = 0
        const val SLOT_REPELLENT = 1
        const val SLOT_JAR = 2

        val ALL_SUBS = -1..-1

        const val BUTTERFLY_NET = "obj.hunting_butterfly_net"
        const val MAGIC_NET = "obj.ii_magic_butterfly_net"
        const val IMPLING_JAR = "obj.ii_impling_jar"
        const val IMP_REPELLENT = "obj.ii_imp_repellent"
        const val IMPLING_SCROLL = "obj.ii_impling_scroll"
        const val JAR_GENERATOR = "obj.ii_jar_generator"
        const val BUTTERFLY_JAR = "obj.butterfly_jar"
        const val COINS = "obj.coins"

        const val JAR_CAPACITY = 1000
        const val GENERATOR_CAPACITY = 100
        const val IMPLING_JAR_COST = 3
        const val BUTTERFLY_JAR_COST = 1

        val STORAGE_MAIN_ROWS =
            listOf(
                "component.ii_elnock_storage:net" to SLOT_NET,
                "component.ii_elnock_storage:repellent" to SLOT_REPELLENT,
                "component.ii_elnock_storage:jar" to SLOT_JAR,
            )
        val STORAGE_SIDE_ROWS =
            listOf(
                "component.ii_elnock_storage_side:net" to SLOT_NET,
                "component.ii_elnock_storage_side:repellent" to SLOT_REPELLENT,
                "component.ii_elnock_storage_side:jar" to SLOT_JAR,
            )
        val STORAGE_QTY =
            listOf(
                "component.ii_elnock_storage:quantity_1" to 0,
                "component.ii_elnock_storage:quantity_5" to 1,
                "component.ii_elnock_storage:quantity_all" to 2,
                "component.ii_elnock_storage:quantity_x" to 3,
            )

        const val QTY_X = -1
        const val QTY_ALL = -2

        val QTY_SELECTED = intArrayOf(1, 5, QTY_ALL, QTY_X)
        val QTY_FALLBACK = listOf(1, 5, QTY_X, QTY_ALL)

        val JARRED_IMPLINGS = (1..12).map { "obj.ii_captured_impling_$it" }

        data class Product(val item: String, val amount: Int, val costs: List<Pair<String, Int>>)

        val PRODUCTS =
            listOf(
                Product(
                    IMP_REPELLENT,
                    1,
                    listOf(
                        "obj.ii_captured_impling_1" to 3,
                        "obj.ii_captured_impling_2" to 2,
                        "obj.ii_captured_impling_3" to 1,
                    ),
                ),
                Product(
                    MAGIC_NET,
                    1,
                    listOf(
                        "obj.ii_captured_impling_3" to 3,
                        "obj.ii_captured_impling_4" to 2,
                        "obj.ii_captured_impling_5" to 1,
                    ),
                ),
                Product(
                    JAR_GENERATOR,
                    1,
                    listOf(
                        "obj.ii_captured_impling_5" to 3,
                        "obj.ii_captured_impling_6" to 2,
                        "obj.ii_captured_impling_7" to 1,
                    ),
                ),
                Product(IMPLING_JAR, 3, emptyList()),
            )

        val TRACKER_COMPONENTS =
            arrayOf(
                "component.ii_tracker:surface_grid".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:surface_models".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:surface_text".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:surface_scrollbar".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:puro_grid".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:puro_models".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:puro_text".asRSCM(RSCMType.COMPONENT),
                "component.ii_tracker:puro_scrollbar".asRSCM(RSCMType.COMPONENT),
            )
    }
}
