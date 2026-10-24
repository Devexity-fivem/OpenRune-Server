package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onApLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Gnome Stronghold course. Working: log balance, obstacle pipe.
// TODO(next pass): obstical_net2/3 climbs and tree branch + balancing rope
// need the level-1 loc dump (course platform is elevated).
class GnomeAgilityCourse @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onApLoc1("loc.gnome_log_balance1") { crossLog() }
        onApLoc1("loc.log_balance2") { crossLog() }
        onApLoc1("loc.log_balance3") { crossLog() }
        onApLoc1("loc.obstical_pipe3_1") { squeezePipe(southEnd = true) }
        onApLoc1("loc.obstical_pipe3_2") { squeezePipe(southEnd = false) }
    }

    private suspend fun ProtectedAccess.crossLog() {
        if (player.agilityLvl < REQUIRED_LVL) {
            mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
            return
        }
        val north = coords.z < LOG_SOUTH_Z
        val dest = if (north) CoordGrid(2474, 3436, 0) else CoordGrid(2474, 3430, 0)
        exactMove(coords, dest, delay1 = 40, delay2 = 80, dir = if (north) 0 else 1024)
        anim("seq.human_spot_jump")
        delay(4)
        statAdvance("stat.agility", LOG_XP)
    }

    private suspend fun ProtectedAccess.squeezePipe(southEnd: Boolean) {
        if (player.agilityLvl < REQUIRED_LVL) {
            mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
            return
        }
        val dest = if (southEnd) PIPE_NORTH else PIPE_SOUTH
        anim("seq.human_pipesqueeze")
        delay(3)
        exactMove(coords, dest, delay1 = 30, delay2 = 60, dir = if (southEnd) 0 else 1024)
        anim("seq.human_pipeunsqueeze")
        delay(2)
        statAdvance("stat.agility", PIPE_XP)
    }

    private companion object {
        const val REQUIRED_LVL = 1
        const val LOG_XP = 7.5
        const val PIPE_XP = 7.5
        const val LOG_SOUTH_Z = 3430
        val PIPE_SOUTH = CoordGrid(2478, 3430, 0)
        val PIPE_NORTH = CoordGrid(2478, 3440, 0)
    }
}
