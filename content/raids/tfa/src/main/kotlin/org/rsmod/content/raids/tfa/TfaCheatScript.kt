package org.rsmod.content.raids.tfa

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusEncounter
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TfaCheatScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val ocellus: OcellusEncounter,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfaocellus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reset Ocellus and teleport in front of the arena entrance"
            cheat { toOcellus() }
        }
    }

    private fun Cheat.toOcellus() {
        ocellus.reset()
        launcher.launch(player, busyText = "Close your dialog first.") {
            telejump(OcellusEncounter.ENTRANCE, TeleportType.Exempt)
        }
    }
}
