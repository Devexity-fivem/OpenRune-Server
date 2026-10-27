package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ancient Prison access: the frozen key assembled from the four gods' pieces
 * opens the Frozen Door permanently (varp.nex_perm), the outer prison door is a
 * normal gate, and the inner door to the safe room costs 40 Zarosian essence
 * (varbit.godwars_counter_zaros) per entry.
 */
class NexPrison @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        for ((first, second) in KEY_PIECE_PAIRS) {
            onOpHeldU(first, second) { combineFrozenKey() }
        }
        for (loc in FROZEN_DOOR_LOCS) {
            onOpLoc1(loc) { openFrozenDoor(FROZEN_DOOR_Z) }
        }
        onOpLoc1(OUTER_DOOR) { crossZDoor(OUTER_DOOR_Z) }
        onOpLoc1(INNER_DOOR) { openInnerDoor() }
    }

    private fun ProtectedAccess.combineFrozenKey() {
        if (KEY_PIECES.any { player.inv.count(it) <= 0 }) return
        for (piece in KEY_PIECES) invDel(player.inv, piece, 1)
        invAdd(player.inv, FROZEN_KEY, 1)
        mes("You combine the four pieces into a frozen key.")
    }

    private fun ProtectedAccess.openFrozenDoor(doorZ: Int) {
        if (player.vars[UNLOCK_VARP] == 0) {
            if (player.inv.count(FROZEN_KEY) <= 0) {
                mes("The door is frozen solid. It won't budge.")
                return
            }
            invDel(player.inv, FROZEN_KEY, 1)
            VarPlayerIntMapSetter.set(player, UNLOCK_VARP, 1)
            mes("The frozen key fits perfectly. The door thaws and swings open.")
        }
        telejump(if (player.coords.z > doorZ) southOf(player.coords.x, doorZ) else northOf(player.coords.x, doorZ))
    }

    private fun ProtectedAccess.crossZDoor(doorZ: Int) {
        telejump(if (player.coords.z > doorZ) southOf(player.coords.x, doorZ) else northOf(player.coords.x, doorZ))
    }

    private fun ProtectedAccess.openInnerDoor() {
        // Exiting the safe room back to the prison floor is free; essence is
        // only charged heading east into the safe room.
        if (player.coords.x >= INNER_DOOR_X) {
            telejump(CoordGrid(INNER_DOOR_X - 1, INNER_DOOR_Z, 0))
            return
        }
        val essence = player.vars[ESSENCE_VARBIT]
        if (essence < REQUIRED_ESSENCE) {
            mes(
                "The door is sealed shut by ancient magic. You need $REQUIRED_ESSENCE " +
                    "Zarosian essence to pass."
            )
            return
        }
        VarPlayerIntMapSetter.set(player, ESSENCE_VARBIT, essence - REQUIRED_ESSENCE)
        telejump(CoordGrid(INNER_DOOR_X + 1, INNER_DOOR_Z, 0))
    }

    private fun southOf(x: Int, doorZ: Int): CoordGrid = CoordGrid(x, doorZ - 1, 0)

    private fun northOf(x: Int, doorZ: Int): CoordGrid = CoordGrid(x, doorZ + 1, 0)

    private companion object {
        private const val FROZEN_KEY = "obj.nex_frozen_key"
        private const val UNLOCK_VARP = "varp.nex_perm"
        private const val ESSENCE_VARBIT = "varbit.godwars_counter_zaros"
        private const val REQUIRED_ESSENCE = 40

        private const val FROZEN_DOOR_Z = 5228
        private const val OUTER_DOOR_Z = 5219
        private const val INNER_DOOR_X = 2899
        private const val INNER_DOOR_Z = 5203

        private val FROZEN_DOOR_LOCS =
            listOf(
                "loc.nex_frozen_door_inner_1",
                "loc.nex_frozen_door_inner_2",
                "loc.nex_frozen_door_outer_1",
                "loc.nex_frozen_door_outer_2",
            )

        private const val OUTER_DOOR = "loc.nex_outer_prison_door"
        private const val INNER_DOOR = "loc.nex_inner_prison_door"

        private val KEY_PIECES =
            listOf(
                "obj.nex_frozen_key_armadyl",
                "obj.nex_frozen_key_bandos",
                "obj.nex_frozen_key_saradomin",
                "obj.nex_frozen_key_zamorak",
            )

        private val KEY_PIECE_PAIRS =
            KEY_PIECES.flatMapIndexed { i, a -> KEY_PIECES.drop(i + 1).map { b -> a to b } }
    }
}
