package org.rsmod.content.minigames.nightmarezone

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.minigames.nightmarezone.NightmareZoneRuns.Companion.KEY
import org.rsmod.content.minigames.nightmarezone.NightmareZoneRuns.Companion.WAVE_CHECK_TICKS
import org.rsmod.content.minigames.nightmarezone.NightmareZoneRuns.Companion.WAVE_TIMER
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NightmareZoneScript
@Inject
constructor(
    private val runs: NightmareZoneRuns,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.nzone_host") {
            if (runs.sessionFor(player)?.key == KEY) {
                with(runs) { leave() }
            } else {
                with(runs) { enter() }
            }
        }
        // Loc ops are keyed by type id globally, so this single binding doubles as the
        // exit door for inferno's copy of the same region - any active session leaves.
        onProtectedEvent<LocEvents.Op1>(VENT_DOOR_ID) {
            val session = manager.sessionForPlayer(player) ?: return@onProtectedEvent
            withInstanceLeaveTransition(InstanceEnterTransition()) {
                val exit = manager.leave(player, session, clock.cycle)
                telejump(exit, TeleportType.Exempt)
            }
        }

        onPlayerSoftTimer(WAVE_TIMER) {
            val session = runs.sessionFor(player)
            if (session == null) {
                player.clearSoftTimer(WAVE_TIMER)
                return@onPlayerSoftTimer
            }
            val run = runs.runFor(player) ?: return@onPlayerSoftTimer
            run.npcs.removeAll { it.hitpoints <= 0 }
            run.npcs.forEach {
                if (it.mode != NpcMode.OpPlayer2 && it.mode != NpcMode.ApPlayer2) {
                    runs.engage(it, player)
                }
            }
            runs.topUp(player, session.id)
            player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
        }

        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) {
            player.clearSoftTimer(WAVE_TIMER)
            val run = runs.remove(instanceId)
            if (run != null) {
                player.mes("The dream ends. Nightmare Zone points: ${run.points}.")
            }
        }
    }

    private companion object {
        const val VENT_DOOR_ID = 30266
    }
}

internal class NightmareZoneKillHook
@Inject
constructor(private val runs: NightmareZoneRuns, private val manager: InstanceManager) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val session = manager.sessionForPlayer(context.hero) ?: return
        if (session.key != KEY) return
        runs.awardKill(context.hero, session.id, context.npc)
    }
}

internal class NightmareZoneRespawnHook
@Inject
constructor(private val instances: InstanceManager, private val runs: NightmareZoneRuns) :
    PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        if (instances.sessionForPlayer(player)?.key != KEY) return null
        runs.reset(player)
        return NightmareZoneRuns.CITY_EXIT
    }
}

class NightmareZoneModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(NightmareZoneKillHook::class.java)
        addSetBinding<PlayerRespawnHook>(NightmareZoneRespawnHook::class.java)
    }
}
