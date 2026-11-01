package org.rsmod.content.minigames.mess

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.events.EventBus
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TheMess
@Inject
constructor(
    private val eventBus: EventBus,
    private val xpMods: XpModifiers,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {

    private data class Dish(val obj: String, val level: Int, val baseXp: Int)

    override fun ScriptContext.startup() {
        onArea(AREA) { openHud() }
        onAreaExit(AREA) { player.ifCloseOverlay(HUD, eventBus) }
        onPlayerLogin {
            if (inMess(player.coords)) {
                protectedAccess.launch(player) { openHud() }
            }
        }

        onOpLoc1(FOOD_CUPBOARD) { takeFromCupboard(FOOD_ITEMS) }
        onOpLoc1(UTENSIL_CUPBOARD) { takeFromCupboard(UTENSIL_ITEMS) }
        onOpLoc1(MEAT_TABLE) { invAdd(inv, RAW_MEAT, 1) }
        onOpLoc2(MEAT_TABLE) { takeMeat() }
        RETURNABLE.forEach { item ->
            onOpLocU(FOOD_CUPBOARD, item) { returnItem(item) }
            onOpLocU(UTENSIL_CUPBOARD, item) { returnItem(item) }
        }

        onOpHeldU("obj.bowl_water", FLOUR) { mixDough() }
        onOpHeldU(PASTRY_DOUGH, PIE_DISH) { combine(PASTRY_DOUGH, PIE_DISH, PIE_SHELL) }
        onOpHeldU(COOKED_MEAT, PIE_SHELL) { combine(COOKED_MEAT, PIE_SHELL, UNCOOKED_PIE) }
        onOpHeldU(COOKED_MEAT, "obj.bowl_water") {
            combine(COOKED_MEAT, "obj.bowl_water", MEAT_WATER)
        }
        onOpHeldU(POTATO, MEAT_WATER) { combine(POTATO, MEAT_WATER, UNCOOKED_STEW) }
        onOpHeldU(TOMATO, PIZZA_BASE) { combine(TOMATO, PIZZA_BASE, INCOMPLETE_PIZZA) }
        onOpHeldU(CHEESE, INCOMPLETE_PIZZA) { combine(CHEESE, INCOMPLETE_PIZZA, UNCOOKED_PIZZA) }
        onOpHeldU(PINEAPPLE_CHUNKS, PLAIN_PIZZA) {
            combine(PINEAPPLE_CHUNKS, PLAIN_PIZZA, PINEAPPLE_PIZZA)
        }
        onOpHeldU("obj.knife", PINEAPPLE) { slicePineapple() }

        DISHES.forEach { dish ->
            onOpLocU(BUFFET_TABLE, dish.obj) { serveDish(dish) }
        }
        onOpLoc1(BUFFET_TABLE) { serve() }
    }

    @OptIn(UncheckedType::class)
    private fun ProtectedAccess.openHud() {
        val apprec = inv(APPRECIATION_INV)
        if (DISHES.all { apprec.count(it.obj) == 0 }) {
            DISHES.forEachIndexed { i, dish ->
                apprec[i] = InvObj(dish.obj.asRSCM(RSCMType.OBJ), MAX_APPRECIATION)
            }
        }
        invTransmit(apprec)
        player.ifOpenOverlay(HUD, HUD_TARGET, eventBus)
    }

    private fun inMess(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in AREA_X && coords.z in AREA_Z

    private suspend fun ProtectedAccess.takeFromCupboard(items: List<String>) {
        openSkillMulti(
            SkillMultiConfig(
                actionType = SkillingActionType.TAKE,
                verb = "take",
                entries = items.map { SkillMultiEntry(it) },
                maxCountProvider = { inv, _ -> inv.freeSpace() },
            )
        ) { selection ->
            invAdd(inv, selection.entry.internal, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.takeMeat() {
        val amount = countDialog().coerceAtMost(inv.freeSpace())
        if (amount > 0) {
            invAdd(inv, RAW_MEAT, amount)
        }
    }

    private fun ProtectedAccess.returnItem(item: String) {
        if (inv.count(item) > 0) {
            invDel(inv, item, Int.MAX_VALUE)
            mes("You return the items to the cupboard.")
        }
    }

    private suspend fun ProtectedAccess.mixDough() {
        val materials = listOf(Material("obj.bowl_water"), Material(FLOUR))
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = listOf(
                    SkillMultiEntry(PASTRY_DOUGH, materials),
                    SkillMultiEntry(PIZZA_BASE, materials),
                ),
            )
        ) { selection ->
            repeat(selection.amount) {
                if (inv.count(FLOUR) == 0 || inv.count("obj.bowl_water") == 0) return@repeat
                invDel(inv, FLOUR, 1)
                invDel(inv, "obj.bowl_water", 1)
                invAdd(inv, selection.entry.internal, 1)
                invAdd(inv, "obj.bowl_empty", 1)
            }
            mes("You mix the flour and water together.")
        }
    }

    private fun ProtectedAccess.combine(a: String, b: String, result: String) {
        invDel(inv, a, 1)
        invDel(inv, b, 1)
        invAdd(inv, result, 1)
    }

    private fun ProtectedAccess.slicePineapple() {
        invDel(inv, PINEAPPLE, 1)
        invAdd(inv, PINEAPPLE_CHUNKS, 1)
        mes("You slice the pineapple into chunks.")
    }

    private suspend fun ProtectedAccess.serve() {
        val held = DISHES.filter { inv.count(it.obj) > 0 }
        when {
            held.isEmpty() -> mesbox("You aren't carrying any food to serve.")
            held.size == 1 -> serveDish(held.first())
            else ->
                openSkillMulti(
                    SkillMultiConfig(
                        actionType = SkillingActionType.GIVE,
                        verb = "serve",
                        entries = held.map { SkillMultiEntry(it.obj) },
                    )
                ) { selection ->
                    serveDish(held.first { it.obj == selection.entry.internal })
                }
        }
    }

    private fun ProtectedAccess.serveDish(dish: Dish) {
        if (player.cookingLvl < dish.level) {
            mes("You need a Cooking level of ${dish.level} to serve this.")
            return
        }
        if (inv.count(dish.obj) == 0) return
        val apprec = inv(APPRECIATION_INV)
        val xp = dish.baseXp * apprec.count(dish.obj) / MAX_APPRECIATION
        invDel(inv, dish.obj, 1)
        statAdvance("stat.cooking", xp.toDouble() * xpMods.get(player, "stat.cooking"))
        shiftAppreciation(apprec, dish)
        anim(SERVE_ANIM)
        spam("You serve ${dishName(dish.obj)} to the soldiers.")
    }

    @OptIn(UncheckedType::class)
    private fun ProtectedAccess.shiftAppreciation(apprec: org.rsmod.game.inv.Inventory, served: Dish) {
        DISHES.forEachIndexed { i, dish ->
            val delta = if (dish === served) -SERVE_DROP else SERVE_RAISE
            apprec[i] = InvObj(
                dish.obj.asRSCM(RSCMType.OBJ),
                (apprec.count(dish.obj) + delta).coerceIn(0, MAX_APPRECIATION),
            )
        }
        invTransmit(apprec)
    }

    private fun dishName(obj: String): String =
        obj.removePrefix("obj.hosidius_servery_").replace('_', ' ')

    private companion object {
        const val AREA = "area.mess_hall"
        const val HUD = "interface.hosidius_servery_hud"
        const val HUD_TARGET = "component.toplevel_osrs_stretch:overlay_hud"
        const val APPRECIATION_INV = "inv.skill_guide_cooking_wine"
        const val SERVE_ANIM = "seq.human_pickuptable"

        const val FOOD_CUPBOARD = "loc.hosidius_servery_cupboard_food"
        const val UTENSIL_CUPBOARD = "loc.hosidius_servery_cupboard_utensils"
        const val MEAT_TABLE = "loc.hosidius_servery_meat"
        const val BUFFET_TABLE = "loc.hosidius_servery_deposit"

        const val RAW_MEAT = "obj.hosidius_servery_raw_meat"
        const val COOKED_MEAT = "obj.hosidius_servery_cooked_meat"
        const val FLOUR = "obj.hosidius_servery_pot_flour"
        const val POTATO = "obj.hosidius_servery_potato"
        const val TOMATO = "obj.hosidius_servery_tomato"
        const val CHEESE = "obj.hosidius_servery_cheese"
        const val PINEAPPLE = "obj.hosidius_servery_pineapple"
        const val PINEAPPLE_CHUNKS = "obj.hosidius_servery_pineapple_chunks"
        const val PIE_DISH = "obj.hosidius_servery_piedish"
        const val PASTRY_DOUGH = "obj.hosidius_servery_pastry_dough"
        const val PIE_SHELL = "obj.hosidius_servery_pie_shell"
        const val PIZZA_BASE = "obj.hosidius_servery_pizza_base"
        const val UNCOOKED_PIE = "obj.hosidius_servery_uncooked_meat_pie"
        const val MEAT_WATER = "obj.hosidius_servery_meatwater"
        const val UNCOOKED_STEW = "obj.hosidius_servery_uncooked_stew"
        const val INCOMPLETE_PIZZA = "obj.hosidius_servery_incomplete_pizza"
        const val UNCOOKED_PIZZA = "obj.hosidius_servery_uncooked_pizza"
        const val MEAT_PIE = "obj.hosidius_servery_meat_pie"
        const val STEW = "obj.hosidius_servery_stew"
        const val PLAIN_PIZZA = "obj.hosidius_servery_plain_pizza"
        const val PINEAPPLE_PIZZA = "obj.hosidius_servery_pineapple_pizza"

        val DISHES = listOf(
            Dish(MEAT_PIE, 20, 160),
            Dish(STEW, 25, 168),
            Dish(PINEAPPLE_PIZZA, 65, 369),
        )

        val FOOD_ITEMS = listOf(FLOUR, POTATO, TOMATO, CHEESE, PINEAPPLE)
        val UTENSIL_ITEMS = listOf(PIE_DISH, "obj.bowl_empty", "obj.knife")
        val RETURNABLE = FOOD_ITEMS + UTENSIL_ITEMS + listOf(
            RAW_MEAT, COOKED_MEAT, PASTRY_DOUGH, PIE_SHELL, PIZZA_BASE,
            UNCOOKED_PIE, MEAT_WATER, UNCOOKED_STEW, INCOMPLETE_PIZZA,
            UNCOOKED_PIZZA, PLAIN_PIZZA, PINEAPPLE_CHUNKS, "obj.bowl_water",
        )

        const val MAX_APPRECIATION = 1000
        const val SERVE_DROP = 60
        const val SERVE_RAISE = 30

        val AREA_X = 1634..1670
        val AREA_Z = 3613..3636
    }
}
