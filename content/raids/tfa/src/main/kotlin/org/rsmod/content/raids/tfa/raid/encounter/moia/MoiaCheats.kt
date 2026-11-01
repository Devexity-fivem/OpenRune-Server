package org.rsmod.content.raids.tfa.raid.encounter.moia

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MoiaCheats
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val moia: MoiaEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfamoia") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Moia instance and teleport in"
            cheat { toMoia() }
        }
        onCommand("moia") {
            requiredRights = Rights.ADMINISTRATOR
            desc = USAGE
            cheat { moiaCommand() }
        }
    }

    private fun Cheat.moiaCommand() {
        val fight = moia.at(player.coords)
        if (fight == null) {
            player.mes("You aren't in a Moia room.")
            return
        }
        when (args.firstOrNull()) {
            "start" -> fight.ready(player)
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) player.mes("Usage: ::moia hp <percent>")
                else applied(fight.setHealthPercent(percent))
            }
            "binders" -> fight.run(player, "begin")
            "cough" -> fight.run(player, "cough")
            "enrage" -> fight.run(player, "enrage_start")
            "debug" -> fight.debugState().forEach(player::mes)
            "leave" -> leave()
            else -> player.mes("Usage: $USAGE")
        }
    }

    private fun Cheat.applied(done: Boolean) {
        player.mes(if (done) "Done." else "Moia isn't in a fight. Use ::moia start first.")
    }

    private fun Cheat.leave() {
        val session = manager.sessionForPlayer(player)
        if (session == null) {
            player.mes("You aren't in an instance.")
            return
        }
        val exit = manager.leave(player, session, clock.cycle)
        launcher.launch(player, busyText = "Close your dialog first.") {
            telejump(exit, TeleportType.Exempt)
        }
    }

    private fun Cheat.toMoia() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = moia.open(player)) {
                is MoiaEncounter.Opened.Failed -> mes(opened.reason)
                is MoiaEncounter.Opened.Room -> enter(opened)
            }
        }
    }

    private fun ProtectedAccess.enter(room: MoiaEncounter.Opened.Room) {
        telejump(room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, room.fight.session, clock.cycle)
    }

    private companion object {
        const val USAGE = "Moia testing: start | hp <pct> | binders | cough | enrage | debug | leave"
    }
}
