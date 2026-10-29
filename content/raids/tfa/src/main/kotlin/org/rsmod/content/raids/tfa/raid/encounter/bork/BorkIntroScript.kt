package org.rsmod.content.raids.tfa.raid.encounter.bork

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BorkIntroScript @Inject constructor(private val encounter: BorkEncounter) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.tfa_boss_teleporter") { touch() }
    }

    private suspend fun ProtectedAccess.touch() {
        val fight = encounter.at(player.coords) ?: return
        if (fight.elapsedTicks != null) {
            mes("Your party has already started the challenge. You can't join them now.")
            return
        }
        val begin = choice2("Yes.", true, "No.", false, title = "Begin the challenge?")
        if (!begin) return
        telejump(fight.arenaEntry(), TeleportType.Exempt)
        fight.ready(player)
    }
}
