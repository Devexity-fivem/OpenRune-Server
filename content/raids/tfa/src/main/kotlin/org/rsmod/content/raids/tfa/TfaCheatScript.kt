package org.rsmod.content.raids.tfa

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
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
        onCommand("ocellus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Ocellus testing: start | shield | hp <percent>"
            cheat { ocellusCommand() }
        }
    }

    private fun Cheat.ocellusCommand() {
        when (args.firstOrNull()) {
            "start" -> {
                ocellus.ready(player)
            }
            "shield" -> report(ocellus.setHealthPercent(0), "Shield dropped to 1 hp.")
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) {
                    player.mes("Usage: ::ocellus hp <percent>")
                    return
                }
                report(ocellus.setHealthPercent(percent), "Boss health set to $percent%.")
            }
            else -> player.mes("Usage: ::ocellus start | shield | hp <percent>")
        }
    }

    private fun Cheat.report(applied: Boolean, message: String) {
        player.mes(if (applied) message else "Ocellus isn't in a fight. Use ::ocellus start first.")
    }

    private fun Cheat.toOcellus() {
        ocellus.reset()
        launcher.launch(player, busyText = "Close your dialog first.") {
            telejump(OcellusEncounter.ENTRANCE, TeleportType.Exempt)
        }
    }
}
