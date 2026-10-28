package org.rsmod.content.minigames.fightcave

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
import org.rsmod.content.minigames.fightcave.FightCaveRuns.Companion.KEY
import org.rsmod.content.minigames.fightcave.FightCaveRuns.Companion.WAVE_CHECK_TICKS
import org.rsmod.content.minigames.fightcave.FightCaveRuns.Companion.WAVE_TIMER
import dev.openrune.types.NpcMode
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FightCaveScript @Inject constructor(private val runs: FightCaveRuns) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.tzhaar_fightcave_wall_entrance") { with(runs) { enter() } }
        onOpLoc1("loc.tzhaar_fightcave_wall_exit") { with(runs) { leave() } }

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
            if (!run.completed && run.npcs.isEmpty() && run.wave > 0) {
                if (run.wave < FightCaveWaves.LAST_WAVE) {
                    runs.spawnWave(player, session.id, run.wave + 1)
                }
            }
            if (run.wave == FightCaveWaves.LAST_WAVE && !run.healersSpawned) {
                val jad = run.npcs.firstOrNull { it.type.internalName == JAD_NAME }
                if (jad != null && jad.hitpoints * 2 <= jad.type.hitpoints) {
                    runs.spawnHealers(player, session.id)
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
        const val JAD_NAME = "tzhaar_fightcave_swarm_boss"
    }
}

internal class FightCaveKillHook
@Inject
constructor(private val runs: FightCaveRuns, private val objRepo: ObjRepository) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val run = runs.runFor(context.hero) ?: return
        when (context.npc.type.internalName) {
            TZ_KEK_NAME -> {
                val session = runs.sessionFor(context.hero) ?: return
                repeat(2) { index ->
                    runs.spawn(
                            session.id,
                            FightCaveWaves.TZ_KEK_MINI,
                            context.npc.coords,
                            index,
                            context.hero,
                            radiusMin = 1,
                            radiusMax = 3,
                        )
                        ?.let { run.npcs.add(it) }
                }
            }
            JAD_NAME -> {
                run.completed = true
                context.hero.invAddOrDrop(objRepo, "obj.firecape")
                context.hero.mes("You have defeated TzTok-Jad!")
            }
        }
    }

    private companion object {
        const val TZ_KEK_NAME = "tzhaar_fightcave_swarm_2a"
        const val JAD_NAME = "tzhaar_fightcave_swarm_boss"
    }
}

internal class FightCaveRespawnHook
@Inject
constructor(private val instances: InstanceManager, private val runs: FightCaveRuns) :
    PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        if (instances.sessionForPlayer(player)?.key != KEY) return null
        runs.reset(player)
        return FightCaveRuns.CITY_EXIT
    }
}

class FightCaveModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(FightCaveKillHook::class.java)
        addSetBinding<PlayerRespawnHook>(FightCaveRespawnHook::class.java)
    }
}
