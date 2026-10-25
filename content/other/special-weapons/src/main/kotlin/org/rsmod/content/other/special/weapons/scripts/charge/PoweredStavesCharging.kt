package org.rsmod.content.other.special.weapons.scripts.charge

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.refs.params
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Charge/Check/Uncharge/Swap interactions for powered staves. */
class PoweredStavesCharging
@Inject
constructor(private val charges: ObjChargeManager, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        // Tridents: Check op3 on all forms, Uncharge op4 on charged forms only (uncharged
        // toxic tridents have "Dismantle" there instead); all charge via rune use-on.
        for (staff in SEAS_CHARGED) {
            onOpHeld3(staff) { checkCharges(it.inventory[it.slot], it.type) }
            onOpHeld4(staff) { uncharge(it.inventory, it.slot, SEAS) }
        }
        for (staff in SEAS_UNCHARGED) {
            onOpHeld3(staff) { checkCharges(it.inventory[it.slot], it.type) }
        }
        for (staff in SEAS_CHARGED + SEAS_UNCHARGED) {
            for (rune in SEAS_RUNES) {
                onOpHeldU(rune, staff) { chargeStaff(inv, it.secondSlot, SEAS, TRIDENT_MAX) }
            }
        }
        for (staff in SEAS_E_CHARGED) {
            onOpHeld3(staff) { checkCharges(it.inventory[it.slot], it.type) }
            onOpHeld4(staff) { uncharge(it.inventory, it.slot, SEAS) }
        }
        onOpHeld3("obj.tots_i_uncharged") { checkCharges(it.inventory[it.slot], it.type) }
        for (staff in SEAS_E_CHARGED + listOf("obj.tots_i_uncharged")) {
            for (rune in SEAS_RUNES) {
                onOpHeldU(rune, staff) { chargeStaff(inv, it.secondSlot, SEAS, TRIDENT_E_MAX) }
            }
        }
        for (staff in SWAMP_CHARGED) {
            onOpHeld3(staff) { checkCharges(it.inventory[it.slot], it.type) }
            onOpHeld4(staff) { uncharge(it.inventory, it.slot, SWAMP) }
        }
        onOpHeld3("obj.toxic_tots_uncharged") { checkCharges(it.inventory[it.slot], it.type) }
        for (staff in SWAMP_CHARGED + listOf("obj.toxic_tots_uncharged")) {
            for (rune in SWAMP_RUNES) {
                onOpHeldU(rune, staff) { chargeStaff(inv, it.secondSlot, SWAMP, TRIDENT_MAX) }
            }
        }
        for (staff in SWAMP_E_CHARGED) {
            onOpHeld3(staff) { checkCharges(it.inventory[it.slot], it.type) }
            onOpHeld4(staff) { uncharge(it.inventory, it.slot, SWAMP) }
        }
        onOpHeld3("obj.toxic_tots_i_uncharged") {
            checkCharges(it.inventory[it.slot], it.type)
        }
        for (staff in SWAMP_E_CHARGED + listOf("obj.toxic_tots_i_uncharged")) {
            for (rune in SWAMP_RUNES) {
                onOpHeldU(rune, staff) { chargeStaff(inv, it.secondSlot, SWAMP, TRIDENT_E_MAX) }
            }
        }

        // Sanguinesti (normal + holy): Charge op3 on uncharged; Check op3 / Charge op4 /
        // Uncharge op5 on charged.
        for (staff in SANG_CHARGED) {
            onOpHeld3(staff) { checkCharges(it.inventory[it.slot], it.type) }
            onOpHeld4(staff) { chargeStaff(inv, it.slot, SANG, SANG_MAX) }
            onOpHeld5(staff) { uncharge(it.inventory, it.slot, SANG) }
            onOpHeldU("obj.bloodrune", staff) {
                chargeStaff(inv, it.secondSlot, SANG, SANG_MAX)
            }
        }
        for (staff in SANG_UNCHARGED) {
            onOpHeld3(staff) { chargeStaff(inv, it.slot, SANG, SANG_MAX) }
            onOpHeldU("obj.bloodrune", staff) {
                chargeStaff(inv, it.secondSlot, SANG, SANG_MAX)
            }
        }

        // Warped sceptre: Charge op4 on both forms; Check op3 / Uncharge op5 on charged.
        onOpHeld3("obj.warped_sceptre") { checkCharges(it.inventory[it.slot], it.type) }
        onOpHeld4("obj.warped_sceptre") { chargeStaff(inv, it.slot, WARPED, WARPED_MAX) }
        onOpHeld5("obj.warped_sceptre") { uncharge(it.inventory, it.slot, WARPED) }
        onOpHeld4("obj.warped_sceptre_uncharged") {
            chargeStaff(inv, it.slot, WARPED, WARPED_MAX)
        }
        for (rune in WARPED_RUNES) {
            onOpHeldU(rune, "obj.warped_sceptre") {
                chargeStaff(inv, it.secondSlot, WARPED, WARPED_MAX)
            }
            onOpHeldU(rune, "obj.warped_sceptre_uncharged") {
                chargeStaff(inv, it.secondSlot, WARPED, WARPED_MAX)
            }
        }

        // Wilderness sceptres: ether charges via shard use-on; Check op3, Swap op4,
        // Uncharge op5 on charged forms.
        for (staff in WILD_SCEPTRES_CHARGED) {
            onOpHeld3(staff) { checkEther(it.inventory[it.slot]) }
            onOpHeld4(staff) { swap(it.inventory, it.slot) }
            onOpHeld5(staff) { uncharge(it.inventory, it.slot, ETHER) }
            onOpHeldU("obj.wild_cave_shard", staff) {
                chargeStaff(inv, it.secondSlot, ETHER, ETHER_MAX)
            }
        }
        for (staff in WILD_SCEPTRES_UNCHARGED) {
            onOpHeld4(staff) { swap(it.inventory, it.slot) }
            onOpHeldU("obj.wild_cave_shard", staff) {
                chargeStaff(inv, it.secondSlot, ETHER, ETHER_MAX)
            }
        }

        // Bone staff: 1 chaos rune per charge; Uncharge op4 refunds them.
        onOpHeld3("obj.rat_bone_staff") { checkCharges(it.inventory[it.slot], it.type) }
        onOpHeld4("obj.rat_bone_staff") { uncharge(it.inventory, it.slot, BONE) }
        onOpHeldU("obj.chaosrune", "obj.rat_bone_staff") {
            chargeStaff(inv, it.secondSlot, BONE, BONE_MAX)
        }

        // Eye of Ayak: two mutually exclusive charge pools - demon tears or runes
        // (2 death + 1 chaos per charge). Check op3 / Uncharge op4 on charged;
        // Charge op3 on uncharged.
        onOpHeld3("obj.eye_of_ayak") { checkAyak(it.inventory[it.slot]) }
        onOpHeld4("obj.eye_of_ayak") { unchargeAyak(it.inventory, it.slot) }
        onOpHeld3("obj.eye_of_ayak_uncharged") { chargeAyak(inv, it.slot) }
        onOpHeldU("obj.demon_tear", "obj.eye_of_ayak") {
            chargeAyakPool(inv, it.secondSlot, AYAK_TEAR)
        }
        onOpHeldU("obj.demon_tear", "obj.eye_of_ayak_uncharged") {
            chargeAyakPool(inv, it.secondSlot, AYAK_TEAR)
        }
        for (rune in AYAK_RUNES) {
            onOpHeldU(rune, "obj.eye_of_ayak") {
                chargeAyakPool(inv, it.secondSlot, AYAK_RUNE)
            }
            onOpHeldU(rune, "obj.eye_of_ayak_uncharged") {
                chargeAyakPool(inv, it.secondSlot, AYAK_RUNE)
            }
        }

        // Equipped "Check" (worn op 2) mirrors the held Check.
        for (staff in CHECKABLE_WORN) {
            onOpWorn2(staff) { checkCharges(player.righthand, null) }
        }
        for (staff in WILD_SCEPTRES_CHARGED) {
            onOpWorn2(staff) { checkEther(player.righthand) }
        }
        onOpWorn2("obj.eye_of_ayak") { checkAyak(player.righthand) }
    }

    private fun ProtectedAccess.checkCharges(obj: InvObj?, type: dev.openrune.types.ItemServerType?) {
        val varobj = objVarobj(obj) ?: return
        var count = charges.getCharges(obj, varobj)
        // A "(full)" trident stores an implicit full charge pool.
        if (obj.isType("obj.tots") && count == 0) {
            count = TRIDENT_MAX
        }
        val name = type?.name ?: obj?.let { getInvObj(it).name } ?: "staff"
        mes("Your $name has $count charges.")
    }

    private fun ProtectedAccess.checkEther(obj: InvObj?) {
        val total = charges.getCharges(obj, "varobj.wilderness_ether_charges")
        val usable = (total - ETHER_DEPOSIT).coerceAtLeast(0)
        mes("Your sceptre has $usable charges remaining.")
    }

    private fun ProtectedAccess.checkAyak(obj: InvObj?) {
        val tears = charges.getCharges(obj, "varobj.eye_of_ayak_tear_charges")
        val runes = charges.getCharges(obj, "varobj.eye_of_ayak_rune_charges")
        mes("Your Eye of Ayak has ${tears + runes} charges.")
    }

    /** The uncharged Eye's Charge op picks between runes and tears based on inventory. */
    private suspend fun ProtectedAccess.chargeAyak(inventory: Inventory, staffSlot: Int) {
        val canRunes = invTotal(inv, "obj.chaosrune") >= 1 && invTotal(inv, "obj.deathrune") >= 2
        val canTears = invTotal(inv, "obj.demon_tear") >= 1
        when {
            canRunes && canTears -> {
                val useRunes =
                    choice2(
                        "Runes.",
                        true,
                        "Demon tears.",
                        false,
                        title = "Charge the Eye of Ayak with:",
                    )
                chargeAyakPool(inventory, staffSlot, if (useRunes) AYAK_RUNE else AYAK_TEAR)
            }
            canRunes -> chargeAyakPool(inventory, staffSlot, AYAK_RUNE)
            canTears -> chargeAyakPool(inventory, staffSlot, AYAK_TEAR)
            else ->
                mes(
                    "You need at least one chaos rune and two death runes or " +
                        "one demon tear in order to charge the Eye of Ayak."
                )
        }
    }

    suspend fun ProtectedAccess.chargeAyakPool(
        inventory: Inventory,
        staffSlot: Int,
        recipe: ChargeRecipe,
    ) {
        val staff = inventory[staffSlot] ?: return
        val tears = charges.getCharges(staff, "varobj.eye_of_ayak_tear_charges")
        val runes = charges.getCharges(staff, "varobj.eye_of_ayak_rune_charges")
        if (recipe == AYAK_TEAR && runes > 0) {
            mes(
                "The Eye of Ayak is charged with runes, and thus can only be charged " +
                    "with more runes. You must fully uncharge it to use demon tears."
            )
            return
        }
        if (recipe == AYAK_RUNE && tears > 0) {
            mes(
                "The Eye of Ayak is charged with demon tears, and thus can only be " +
                    "charged with more demon tears. You must fully uncharge it to use runes."
            )
            return
        }
        chargeStaff(inventory, staffSlot, recipe, AYAK_MAX)
    }

    private suspend fun ProtectedAccess.unchargeAyak(inventory: Inventory, slot: Int) {
        val obj = inventory[slot] ?: return
        val tears = charges.getCharges(obj, "varobj.eye_of_ayak_tear_charges")
        val runes = charges.getCharges(obj, "varobj.eye_of_ayak_rune_charges")
        val recipe = if (tears > 0) AYAK_TEAR else AYAK_RUNE
        uncharge(inventory, slot, recipe)
    }

    private fun objVarobj(obj: InvObj?): String? =
        when {
            obj == null -> null
            obj.isType("obj.sanguinesti_staff") || obj.isType("obj.sanguinesti_staff_or") ->
                "varobj.sanguinesti_charges"
            obj.isType("obj.warped_sceptre") -> "varobj.warped_sceptre_charges"
            obj.isType("obj.rat_bone_staff") -> "varobj.rat_bone_charges"
            else -> "varobj.trident_charges"
        }

    /** Swap between the powered and autocast "(a)" sceptre forms, preserving charges. */
    fun ProtectedAccess.swap(inventory: Inventory, slot: Int) {
        val obj = inventory[slot] ?: return
        val counterpart = SWAP_PAIRS.entries.firstOrNull { obj.isType(it.key) }?.value ?: return
        inventory[slot] = InvObj(counterpart, vars = obj.vars)
    }

    /** Consumes the recipe's materials for each requested charge, then applies them. */
    suspend fun ProtectedAccess.chargeStaff(
        inventory: Inventory,
        staffSlot: Int,
        recipe: ChargeRecipe,
        maxCharges: Int,
    ) {
        val staff = inventory[staffSlot] ?: return
        // The "(full)" trident stores its pool implicitly: an empty var means full.
        val current =
            charges.getCharges(staff, recipe.varobj).let {
                if (staff.isType("obj.tots") && it == 0) TRIDENT_MAX else it
            }
        if (current >= maxCharges) {
            mes("Your ${recipe.name} is already fully charged.")
            return
        }

        val affordable = recipe.cost.minOf { (type, per) -> invTotal(inv, type) / per }
        if (affordable <= 0) {
            mes("You don't have the required runes to charge your ${recipe.name}.")
            return
        }

        val applicable = min(maxCharges - current, affordable)
        val requested =
            min(countDialog("How many charges do you want to apply? (Up to $applicable)"), applicable)
        if (requested == 0) {
            return
        }

        if (!(inventory[staffSlot]?.let { s -> recipe.matches.any { s.isType(it) } } == true)) {
            return
        }
        val result = charges.addCharges(inventory, staffSlot, requested, recipe.varobj, maxCharges)
        if (result !is ObjChargeManager.Charge.Success) {
            return
        }
        for ((type, per) in recipe.cost) {
            if (invDel(inv, type, requested * per).failure) {
                return
            }
        }
        // A seas trident filled to capacity converts to the tradeable "(full)" variant.
        if (recipe == SEAS && staff.isType("obj.tots_charged") && result.total == TRIDENT_MAX) {
            inventory[staffSlot] = InvObj("obj.tots")
        }
        objbox(recipe.displayObj, 400, "You apply $requested charges to your ${recipe.name}.")
    }

    /** Uncharge: converts the staff to its uncharged form and refunds the stored materials. */
    suspend fun ProtectedAccess.uncharge(
        inventory: Inventory,
        slot: Int,
        recipe: ChargeRecipe,
    ) {
        val obj = inventory[slot] ?: return
        val full = obj.isType("obj.tots")
        val stored = charges.getCharges(obj, recipe.varobj)
        val current = if (full && stored == 0) TRIDENT_MAX else stored

        if (current == 0) {
            charges.removeAllCharges(inventory, slot, recipe.varobj)
            return
        }

        val confirmation =
            choice2("Proceed.", true, "Cancel.", false, title = "Uncharge your ${recipe.name}?")
        if (!confirmation) {
            return
        }

        if (full) {
            // The full trident's charge pool is implicit, so `removeAllCharges` would see `0`.
            val uncharged = getInvObj(obj).paramOrNull(params.uncharged_variant)
            if (uncharged != null) {
                inventory[slot] = InvObj(uncharged, vars = obj.vars)
            }
        } else {
            charges.removeAllCharges(inventory, slot, recipe.varobj)
        }

        for ((type, per) in recipe.cost) {
            invAddOrDrop(objRepo, type, current * per)
        }
        mes("You uncharge your ${recipe.name}.")
    }

    data class ChargeRecipe(
        val name: String,
        val displayObj: String,
        val varobj: String,
        val cost: List<Pair<String, Int>>,
        val matches: List<String>,
    )

    companion object {
        const val TRIDENT_MAX = 2_500
        const val TRIDENT_E_MAX = 20_000
        const val SANG_MAX = 20_000
        const val WARPED_MAX = 20_000
        const val ETHER_MAX = 17_000
        const val ETHER_DEPOSIT = 1_000
        const val BONE_MAX = 20_000
        const val AYAK_MAX = 50_000

        private val SEAS_CHARGED = listOf("obj.tots", "obj.tots_charged")
        private val SEAS_UNCHARGED = listOf("obj.tots_uncharged")
        private val SEAS_E_CHARGED = listOf("obj.tots_i_charged")
        private val SWAMP_CHARGED = listOf("obj.toxic_tots_charged")
        private val SWAMP_E_CHARGED = listOf("obj.toxic_tots_i_charged")
        private val SANG_CHARGED = listOf("obj.sanguinesti_staff", "obj.sanguinesti_staff_or")
        private val SANG_UNCHARGED =
            listOf("obj.sanguinesti_staff_uncharged", "obj.sanguinesti_staff_uncharged_or")

        private val WILD_SCEPTRES_CHARGED =
            listOf(
                "obj.wild_cave_sceptre_charged",
                "obj.wild_cave_sceptre_charged_recol",
                "obj.wild_cave_accursed_charged",
                "obj.wild_cave_accursed_charged_recol",
            )
        private val WILD_SCEPTRES_UNCHARGED =
            listOf(
                "obj.wild_cave_sceptre_uncharged",
                "obj.wild_cave_sceptre_uncharged_recol",
                "obj.wild_cave_accursed_uncharged",
                "obj.wild_cave_accursed_uncharged_recol",
            )

        val SWAP_PAIRS =
            mapOf(
                "obj.wild_cave_sceptre_charged" to "obj.wild_cave_sceptre_charged_recol",
                "obj.wild_cave_sceptre_charged_recol" to "obj.wild_cave_sceptre_charged",
                "obj.wild_cave_sceptre_uncharged" to "obj.wild_cave_sceptre_uncharged_recol",
                "obj.wild_cave_sceptre_uncharged_recol" to "obj.wild_cave_sceptre_uncharged",
                "obj.wild_cave_accursed_charged" to "obj.wild_cave_accursed_charged_recol",
                "obj.wild_cave_accursed_charged_recol" to "obj.wild_cave_accursed_charged",
                "obj.wild_cave_accursed_uncharged" to "obj.wild_cave_accursed_uncharged_recol",
                "obj.wild_cave_accursed_uncharged_recol" to "obj.wild_cave_accursed_uncharged",
            )

        private val CHECKABLE_WORN =
            listOf(
                "obj.tots",
                "obj.tots_charged",
                "obj.tots_i_charged",
                "obj.toxic_tots_charged",
                "obj.toxic_tots_i_charged",
                "obj.sanguinesti_staff",
                "obj.sanguinesti_staff_or",
                "obj.warped_sceptre",
                "obj.rat_bone_staff",
            )

        private val SEAS_RUNES =
            listOf("obj.deathrune", "obj.chaosrune", "obj.firerune", "obj.coins")
        private val SWAMP_RUNES =
            listOf("obj.deathrune", "obj.chaosrune", "obj.firerune", "obj.snakeboss_scale")
        private val WARPED_RUNES = listOf("obj.chaosrune", "obj.earthrune")
        private val AYAK_RUNES = listOf("obj.deathrune", "obj.chaosrune")

        val SEAS =
            ChargeRecipe(
                name = "trident",
                displayObj = "obj.tots_charged",
                varobj = "varobj.trident_charges",
                cost =
                    listOf(
                        "obj.deathrune" to 1,
                        "obj.chaosrune" to 1,
                        "obj.firerune" to 5,
                        "obj.coins" to 10,
                    ),
                matches =
                    SEAS_CHARGED + SEAS_UNCHARGED + SEAS_E_CHARGED + "obj.tots_i_uncharged",
            )

        val SWAMP =
            ChargeRecipe(
                name = "trident",
                displayObj = "obj.toxic_tots_charged",
                varobj = "varobj.trident_charges",
                cost =
                    listOf(
                        "obj.deathrune" to 1,
                        "obj.chaosrune" to 1,
                        "obj.firerune" to 5,
                        "obj.snakeboss_scale" to 1,
                    ),
                matches =
                    listOf(
                        "obj.toxic_tots_charged",
                        "obj.toxic_tots_uncharged",
                        "obj.toxic_tots_i_charged",
                        "obj.toxic_tots_i_uncharged",
                    ),
            )

        val SANG =
            ChargeRecipe(
                name = "Sanguinesti staff",
                displayObj = "obj.sanguinesti_staff",
                varobj = "varobj.sanguinesti_charges",
                cost = listOf("obj.bloodrune" to 3),
                matches = SANG_CHARGED + SANG_UNCHARGED,
            )

        val WARPED =
            ChargeRecipe(
                name = "warped sceptre",
                displayObj = "obj.warped_sceptre",
                varobj = "varobj.warped_sceptre_charges",
                cost = listOf("obj.chaosrune" to 2, "obj.earthrune" to 5),
                matches = listOf("obj.warped_sceptre", "obj.warped_sceptre_uncharged"),
            )

        val ETHER =
            ChargeRecipe(
                name = "sceptre",
                displayObj = "obj.wild_cave_sceptre_charged",
                varobj = "varobj.wilderness_ether_charges",
                cost = listOf("obj.wild_cave_shard" to 1),
                matches = WILD_SCEPTRES_CHARGED + WILD_SCEPTRES_UNCHARGED,
            )

        val BONE =
            ChargeRecipe(
                name = "bone staff",
                displayObj = "obj.rat_bone_staff",
                varobj = "varobj.rat_bone_charges",
                cost = listOf("obj.chaosrune" to 1),
                matches = listOf("obj.rat_bone_staff"),
            )

        val AYAK_RUNE =
            ChargeRecipe(
                name = "Eye of Ayak",
                displayObj = "obj.eye_of_ayak",
                varobj = "varobj.eye_of_ayak_rune_charges",
                cost = listOf("obj.deathrune" to 2, "obj.chaosrune" to 1),
                matches = listOf("obj.eye_of_ayak", "obj.eye_of_ayak_uncharged"),
            )

        val AYAK_TEAR =
            ChargeRecipe(
                name = "Eye of Ayak",
                displayObj = "obj.eye_of_ayak",
                varobj = "varobj.eye_of_ayak_tear_charges",
                cost = listOf("obj.demon_tear" to 1),
                matches = listOf("obj.eye_of_ayak", "obj.eye_of_ayak_uncharged"),
            )
    }
}
