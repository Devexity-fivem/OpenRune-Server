package org.rsmod.content.minigames.inferno

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.minigames.inferno.InfernoRuns.Companion.KEY
import org.rsmod.content.minigames.inferno.InfernoRuns.Companion.WAVE_CHECK_TICKS
import org.rsmod.content.minigames.inferno.InfernoRuns.Companion.WAVE_TIMER
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class InfernoScript @Inject constructor(private val runs: InfernoRuns) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.inferno_entrance") {
            if (runs.sessionFor(player)?.key == KEY) {
                with(runs) { leave() }
            } else {
                with(runs) { enter() }
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
            if (!run.completed && run.npcs.isEmpty() && run.wave > 0) {
                if (run.wave < InfernoWaves.LAST_WAVE) {
                    runs.spawnWave(player, session.id, run.wave + 1)
                }
            }
            if (run.wave == InfernoWaves.TRIPLE_JAD_WAVE && !run.jadHealersSpawned) {
                val hurt =
                    run.npcs.any {
                        it.type.internalName == JAD_NAME &&
                            it.hitpoints * 2 <= it.type.hitpoints
                    }
                if (hurt) runs.spawnJadHealers(player, session.id)
            }
            if (run.wave == InfernoWaves.LAST_WAVE && !run.zukHealersSpawned) {
                val zuk = run.npcs.firstOrNull { it.type.internalName == ZUK_NAME }
                if (zuk != null && zuk.hitpoints * 2 <= zuk.type.hitpoints) {
                    runs.spawnZukHealers(player, session.id)
                }
            }
            player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
        }

        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) {
            player.clearSoftTimer(WAVE_TIMER)
            runs.remove(instanceId)
        }
    }

    private companion object {
        const val JAD_NAME = "inferno_jad"
        const val ZUK_NAME = "inferno_tzkalzuk_placeholder"
    }
}

internal class InfernoKillHook
@Inject
constructor(private val runs: InfernoRuns, private val objRepo: ObjRepository) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val run = runs.runFor(context.hero) ?: return
        if (context.npc.type.internalName == ZUK_NAME) {
            run.completed = true
            context.hero.invAddOrDrop(objRepo, "obj.infernal_cape")
            context.hero.mes("You have defeated TzKal-Zuk!")
        }
    }

    private companion object {
        const val ZUK_NAME = "inferno_tzkalzuk_placeholder"
    }
}

internal class InfernoRespawnHook
@Inject
constructor(private val instances: InstanceManager, private val runs: InfernoRuns) :
    PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        if (instances.sessionForPlayer(player)?.key != KEY) return null
        runs.reset(player)
        return InfernoRuns.CITY_EXIT
    }
}

class InfernoModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(InfernoKillHook::class.java)
        addSetBinding<PlayerRespawnHook>(InfernoRespawnHook::class.java)
    }
}
