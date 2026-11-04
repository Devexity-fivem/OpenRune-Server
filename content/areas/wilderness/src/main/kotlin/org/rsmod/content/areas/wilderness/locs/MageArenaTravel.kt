package org.rsmod.content.areas.wilderness.locs

import dev.openrune.ServerCacheManager
import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mage Arena travel. The hut lever teleports to the Mage Arena bank (blocked by Tele Block
 * despite being above level 20 Wilderness), the arena-side lever returns outside, the bank's
 * sparkling pool leads to the god-cape chamber and back, and the hut ladder drops into the
 * cellar beside the chamber. Arena entry is through the mystical barrier scan locs, which
 * require Mage Arena I and forbid weapons/armour.
 */
class MageArenaTravel : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.magearena_lever_in") { pullLever(BANK) }
        onOpLoc1("loc.magearena_lever_out") { pullLever(ARENA_EXIT) }
        onOpLoc1("loc.magearena_lever_to_cellar") { pullLeverFrom(it.loc.coords, true) }
        onOpLoc1("loc.magearena_lever_from_cellar") { pullLeverFrom(it.loc.coords, false) }

        onOpLoc1("loc.magearena_ladder_to_cellar") { climbFrom(it.loc.coords, true) }
        onOpLoc1("loc.magearena_ladder_from_cellar") { climbFrom(it.loc.coords, false) }

        // waterportal1 is the chamber pool; waterportal2 is the bank pool.
        onOpLoc1("loc.magearena_waterportal1") { stepIntoPool(BANK) }
        onOpLoc1("loc.magearena_waterportal2") { stepIntoPool(CHAMBER) }

        onOpLoc1("loc.magearena_scan") { passBarrier(it.loc.coords) }
    }

    private suspend fun ProtectedAccess.pullLever(dest: CoordGrid) {
        arriveDelay()
        anim(LEVER_SEQ)
        mes("You pull the lever...")
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.pullLeverFrom(loc: CoordGrid, toCellar: Boolean) {
        pullLever(loc.translateCellar(toCellar))
    }

    private fun CoordGrid.translateCellar(toCellar: Boolean): CoordGrid =
        if (toCellar) translate(CELLAR_DX, CELLAR_DZ, 0) else translate(-CELLAR_DX, -CELLAR_DZ, 0)

    private suspend fun ProtectedAccess.climbFrom(loc: CoordGrid, down: Boolean) {
        arriveDelay()
        anim(LADDER_SEQ)
        delay(1)
        telejump(loc.translateCellar(down), TeleportType.Movement)
        mes(if (down) "You climb down the ladder." else "You climb up the ladder.")
    }

    private suspend fun ProtectedAccess.stepIntoPool(dest: CoordGrid) {
        arriveDelay()
        mes("You step into the pool.")
        anim(POOL_SEQ, delay = POOL_ANIM_DELAY)
        if (!mageArenaComplete(player)) {
            delay(2)
            mes("Your boots get wet.")
            return
        }
        delay(POOL_TICKS)
        anim(TELE_DISAPPEAR_SEQ)
        spotanim(POOL_SPOT, delay = POOL_SPOT_DELAY)
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.passBarrier(loc: CoordGrid) {
        arriveDelay()
        if (player.coords.z > loc.z) {
            if (!mageArenaComplete(player)) {
                mes("You cannot enter without the permission of Kolodion.")
                return
            }
            if (carriesWeaponsOrArmour()) {
                mes("You cannot enter the arena while carrying weapons or armour.")
                return
            }
            telejump(loc.translate(0, -1, 0), TeleportType.Exempt)
        } else {
            telejump(loc.translate(0, 1, 0), TeleportType.Exempt)
        }
        mes("You pass through the mystical barrier.")
    }

    // The Mage Arena I miniquest is not implemented yet; under the default assume-completed
    // quest policy this passes, and it auto-gates once the quest exists.
    private fun mageArenaComplete(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, MAGE_ARENA_QUEST)

    private fun ProtectedAccess.carriesWeaponsOrArmour(): Boolean =
        inv.any { it != null && it.isWeaponOrArmour() } ||
            worn.any { it != null && it.isWeaponOrArmour() }

    private fun InvObj.isWeaponOrArmour(): Boolean {
        val type = ServerCacheManager.getItem(id) ?: return false
        if (type.wearpos1 == -1 || type.wearpos1 in ALLOWED_SLOTS) {
            return false
        }
        return COMBAT_BONUSES.any { (type.paramOrNull(it) ?: 0) != 0 }
    }

    private companion object {
        private const val LEVER_SEQ = "seq.human_pull_magearena_lever"
        private const val LADDER_SEQ = "seq.human_reachforladder"
        private const val POOL_SEQ = "seq.human_spot_jump"
        private const val TELE_DISAPPEAR_SEQ = "seq.human_smokepuff_tele_disappear"
        private const val POOL_SPOT = "spotanim.watersplash"
        private const val POOL_ANIM_DELAY = 5
        private const val POOL_SPOT_DELAY = 20
        private const val POOL_TICKS = 2
        private const val MAGE_ARENA_QUEST = "quest_magearena1"

        // The hut lever and the chamber pool's return destination.
        private val BANK = CoordGrid(2509, 4689, 0)

        // The bank pool leads to the god-cape chamber.
        private val CHAMBER = CoordGrid(2542, 4718, 0)

        // The arena-side lever returns to just outside the hut.
        private val ARENA_EXIT = CoordGrid(3105, 3955, 0)

        // The cellar sits 549 tiles west and 756 north of the surface (LostCity movecoord).
        private const val CELLAR_DX = -549
        private const val CELLAR_DZ = 756

        // Combat gear is blocked; amulets, capes, boots and rings are fine.
        private val ALLOWED_SLOTS =
            setOf(Wearpos.Front.slot, Wearpos.Back.slot, Wearpos.Feet.slot, Wearpos.Ring.slot)

        private val COMBAT_BONUSES =
            listOf(
                params.attack_stab,
                params.attack_slash,
                params.attack_crush,
                params.attack_magic,
                params.attack_ranged,
                params.defence_stab,
                params.defence_slash,
                params.defence_crush,
                params.defence_magic,
                params.defence_ranged,
                params.melee_strength,
                params.ranged_strength,
                params.magic_damage,
            )
    }
}
