package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

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

class DagonhaiCheats
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val dagon: DagonhaiEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfadagon") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a fresh Dagon'hai instance and teleport in"
            cheat { toDagon() }
        }
        onCommand("dagon") {
            requiredRights = Rights.ADMINISTRATOR
            desc = USAGE
            cheat { dagonCommand() }
        }
    }

    private fun Cheat.dagonCommand() {
        val fight = dagon.at(player.coords)
        if (fight == null) {
            player.mes("You aren't in a Dagon'hai room.")
            return
        }
        when (args.firstOrNull()) {
            "start" -> fight.ready(player)
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) player.mes("Usage: ::dagon hp <percent>")
                else applied(fight.setHealthPercent(percent))
            }
            "ritual" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) player.mes("Usage: ::dagon ritual <percent>")
                else applied(fight.setRitual(percent))
            }
            "channel" -> applied(fight.forceChannel(args.getOrNull(1)?.toIntOrNull() ?: 0))
            "break" -> applied(fight.forceBreak())
            "debug" -> fight.debugState().forEach(player::mes)
            "leave" -> leave()
            else -> player.mes("Usage: $USAGE")
        }
    }

    private fun Cheat.applied(done: Boolean) {
        player.mes(if (done) "Done." else "The Dagon'hai aren't in a fight. Use ::dagon start first.")
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

    private fun Cheat.toDagon() {
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, clock.cycle) }
        launcher.launch(player, busyText = "Close your dialog first.") {
            when (val opened = dagon.open(player)) {
                is DagonhaiEncounter.Opened.Failed -> mes(opened.reason)
                is DagonhaiEncounter.Opened.Room -> enter(opened)
            }
        }
    }

    private fun ProtectedAccess.enter(room: DagonhaiEncounter.Opened.Room) {
        telejump(room.enter, TeleportType.Exempt)
        manager.finalizeEntry(player, room.fight.session, clock.cycle)
    }

    private companion object {
        const val USAGE =
            "Dagon'hai testing: start | hp <pct> | ritual <pct> | channel <elem> | break | debug | leave"
    }
}
