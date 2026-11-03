package org.rsmod.content.other.dizanasquiver

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.config.refs.params
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DizanaQuiverScript @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (symbol in SLOT_QUIVERS) {
            if (ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ)) == null) continue
            onOpHeld2(symbol) { open() }
            onOpWorn2(symbol) { open() }
        }
        for (symbol in UNCHARGED_QUIVERS) {
            if (ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ)) == null) continue
            onOpHeld3(symbol) { uncharge(it.inventory, it.slot) }
            onOpWorn3(symbol) { unchargeWorn(it.slot, it.obj) }
            onOpHeld4(symbol) { empty() }
            onOpWorn4(symbol) { empty() }
        }
        for (symbol in UNCHARGEABLE_QUIVERS) {
            if (ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ)) == null) continue
            onOpHeld3(symbol) { uncharge(it.inventory, it.slot) }
            onOpWorn3(symbol) { unchargeWorn(it.slot, it.obj) }
            onOpHeld4(symbol) { empty() }
            onOpWorn4(symbol) { empty() }
        }
        for (symbol in INFINITE_QUIVERS) {
            if (ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ)) == null) continue
            onOpHeld3(symbol) { empty() }
            onOpWorn3(symbol) { empty() }
        }
    }

    /**
     * "Open" acts as the worn-tab "Fill" op when the quiver is worn and the ammo slot holds
     * quiver-compatible ammunition; otherwise it reports the stored stack.
     */
    private fun ProtectedAccess.open() {
        val ammoInv = RangedAmmunition.quiverAmmoInv(player)
        val stored = ammoInv[0]
        val storedType = getOrNull(stored)
        val wornAmmo = player.quiver
        val wornType = getOrNull(wornAmmo)

        if (wornType == null) {
            reportContents(storedType, stored?.count ?: 0)
            return
        }
        if (!RangedAmmunition.isQuiverAmmoType(wornType)) {
            player.mes("You can only store arrows or bolts in the quiver.")
            return
        }
        if (storedType == null) {
            ammoInv[0] = InvObj(wornType, wornAmmo?.count ?: 0)
            player.quiver = null
            player.mes("You store your ammunition in the quiver.")
        } else if (storedType.id == wornType.id) {
            ammoInv[0] = InvObj(storedType, (stored?.count ?: 0) + (wornAmmo?.count ?: 0))
            player.quiver = null
            player.mes("You store your ammunition in the quiver.")
        } else {
            ammoInv[0] = InvObj(wornType, wornAmmo?.count ?: 0)
            player.quiver = InvObj(storedType, stored?.count ?: 0)
            player.mes("You swap the quiver's contents with your worn ammunition.")
        }
        RangedAmmunition.syncQuiverClient(player)
    }

    private fun ProtectedAccess.empty() {
        val ammoInv = RangedAmmunition.quiverAmmoInvOrNull(player)
        val stored = ammoInv?.get(0)
        val storedType = getOrNull(stored)
        if (storedType == null) {
            player.mes("The quiver is empty.")
            return
        }
        player.invAddOrDrop(
            objRepo,
            RSCM.getReverseMapping(RSCMType.OBJ, storedType.id),
            stored?.count ?: 0,
        )
        ammoInv?.set(0, null)
        player.mes("You empty the quiver's ammunition.")
        RangedAmmunition.syncQuiverClient(player)
    }

    private fun ProtectedAccess.uncharge(inventory: Inventory, slot: Int) {
        val quiverObj = inventory[slot] ?: return
        val type = getInvObj(quiverObj)
        val uncharged = type.paramOrNull(params.uncharged_variant)
        if (uncharged == null) {
            player.mes("You can't uncharge this.")
            return
        }
        inventory[slot] = InvObj(uncharged, 1, quiverObj.vars)
        player.mes("You uncharge your quiver.")
        RangedAmmunition.syncQuiverClient(player)
    }

    private fun ProtectedAccess.unchargeWorn(slot: Int, quiverObj: InvObj) {
        val type = getInvObj(quiverObj)
        val uncharged = type.paramOrNull(params.uncharged_variant)
        if (uncharged == null) {
            player.mes("You can't uncharge this.")
            return
        }
        player.worn[slot] = InvObj(uncharged, 1, quiverObj.vars)
        player.mes("You uncharge your quiver.")
        RangedAmmunition.syncQuiverClient(player)
    }

    private fun ProtectedAccess.reportContents(type: ItemServerType?, count: Int) {
        if (type == null || count <= 0) {
            player.mes("The quiver is empty.")
        } else {
            player.mes("Your quiver holds ${count}x ${type.name}.")
        }
    }

    private companion object {
        val UNCHARGED_QUIVERS =
            listOf("obj.dizanas_quiver_uncharged", "obj.dizanas_quiver_uncharged_trouver")

        val UNCHARGEABLE_QUIVERS =
            listOf("obj.dizanas_quiver_charged", "obj.dizanas_quiver_charged_trouver")

        val INFINITE_QUIVERS =
            listOf("obj.dizanas_quiver_infinite", "obj.dizanas_quiver_infinite_trouver")

        val SLOT_QUIVERS = UNCHARGED_QUIVERS + UNCHARGEABLE_QUIVERS + INFINITE_QUIVERS
    }
}
