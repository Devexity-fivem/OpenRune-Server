package org.rsmod.content.areas.wilderness.locs

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Ardougne and Edgeville levers teleport players to the Deserted Keep in deep Wilderness;
 * the keep lever returns to Ardougne (or Edgeville for players whose diary has swapped it).
 * Unlike teleports these work at any Wilderness level, but Tele Block still stops them.
 */
class WildernessLevers : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.wildinlever") { pullLever(KEEP, warn = true) }
        onOpLoc1("loc.edgeville_wildy_lever") { pullLever(KEEP, warn = true) }
        onOpLoc1("loc.wildoutlever") { pullLever(ARDOUGNE) }
        onOpLoc1("loc.wildoutlever_diary") { pullLever(ARDOUGNE) }
        onOpLoc2("loc.wildoutlever_diary") { pullLever(EDGEVILLE) }
        onOpLoc2("loc.wildoutlever_diary_swapped") { pullLever(ARDOUGNE) }
        onOpLoc1("loc.wildoutlever_diary_swapped") { pullLever(EDGEVILLE) }
    }

    private suspend fun ProtectedAccess.pullLever(dest: CoordGrid, warn: Boolean = false) {
        arriveDelay()
        if (warn && vars[WARN_VARBIT] == 0) {
            mesbox(
                "Warning: The lever teleports you deep into the Wilderness.<br>" +
                    "Beware of player killers and dangerous monsters."
            )
        }
        anim(LEVER_SEQ)
        mes("You pull the lever...")
        delay(1)
        telejump(dest, TeleportType.Exempt)
        if (dest == KEEP) {
            mes("...and find yourself in the Deserted Keep.")
        } else {
            mes("...and find yourself back at the lever's base.")
        }
    }

    private companion object {
        private const val LEVER_SEQ = "seq.human_leverdown"
        private const val WARN_VARBIT = "varbit.wilderness_lever_blockwarning_standard"
        private val KEEP = CoordGrid(3154, 3924, 0)
        private val ARDOUGNE = CoordGrid(2562, 3311, 0)
        private val EDGEVILLE = CoordGrid(3090, 3475, 0)
    }
}
