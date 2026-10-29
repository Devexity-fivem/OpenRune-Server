package org.rsmod.content.minigames.ketrak

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.minigames.ketrak.KetRakRuns.Companion.JAD_NAME
import org.rsmod.content.minigames.ketrak.KetRakRuns.Companion.KEY
import org.rsmod.content.minigames.ketrak.KetRakRuns.Companion.WAVE_CHECK_TICKS
import org.rsmod.content.minigames.ketrak.KetRakRuns.Companion.WAVE_TIMER
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KetRakScript
@Inject
constructor(private val runs: KetRakRuns, private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.jad_challenge_master") {
            if (runs.sessionFor(player) != null) {
                return@onOpNpc1
            }
            val choice =
                menu(
                    "TzHaar-Ket-Rak's Challenges",
                    "Challenge 1: One JalTok-Jad",
                    "Challenge 2: Two JalTok-Jads",
                    "Challenge 3: Three JalTok-Jads",
                    "Challenge 4: Four JalTok-Jads",
                    "Challenge 5: Five JalTok-Jads",
                    "Challenge 6: Six JalTok-Jads",
                    "Final Challenge: Endless JalTok-Jads",
                )
            when (choice) {
                in 0..5 -> with(runs) { enter(choice + 1) }
                6 -> with(runs) { enter(1, endless = true) }
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
            run.npcs
                .filter {
                    it.type.internalName == JAD_NAME &&
                        it.hitpoints * 2 <= it.type.hitpoints &&
                        it !in run.healedJads
                }
                .forEach { runs.spawnHealers(player, session.id, it) }
            if (!run.completed && run.npcs.isEmpty() && run.jadCount > 0) {
                if (run.endless) {
                    runs.topUp(player, session.id)
                } else {
                    runs.complete(player, session.id, objRepo)
                }
            }
            player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
        }

        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) {
            player.clearSoftTimer(WAVE_TIMER)
            runs.remove(instanceId)
        }
    }
}

internal class KetRakKillHook @Inject constructor(private val runs: KetRakRuns) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val run = runs.runFor(context.hero) ?: return
        if (context.npc.type.internalName == JAD_NAME) run.kills++
    }

    private companion object {
        const val JAD_NAME = "jad_challenge_jad"
    }
}

internal class KetRakRespawnHook
@Inject
constructor(private val instances: InstanceManager, private val runs: KetRakRuns) :
    PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        if (instances.sessionForPlayer(player)?.key != KEY) return null
        runs.reset(player)
        return KetRakRuns.CITY_EXIT
    }
}

class KetRakModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(KetRakKillHook::class.java)
        addSetBinding<PlayerRespawnHook>(KetRakRespawnHook::class.java)
    }
}
