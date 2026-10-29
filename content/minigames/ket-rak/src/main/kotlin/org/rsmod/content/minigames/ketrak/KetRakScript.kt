package org.rsmod.content.minigames.ketrak

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.minigames.ketrak.KetRakRuns.Companion.HEALER
import org.rsmod.content.minigames.ketrak.KetRakRuns.Companion.JAD
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
constructor(
    private val runs: KetRakRuns,
    private val objRepo: ObjRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : PluginScript() {
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
                in 0..5 -> {
                    val challenge = choice + 1
                    if (lockedReason(player, challenge) != null) {
                        player.mes(lockedReason(player, challenge)!!)
                        return@onOpNpc1
                    }
                    with(runs) { enter(challenge) }
                }
                6 -> {
                    if (player.vars[COMPLETION_VARPS[5]] <= 0) {
                        player.mes("You must first prove yourself in all six challenges.")
                        return@onOpNpc1
                    }
                    with(runs) { enter(1, endless = true) }
                }
            }
        }

        onPlayerSoftTimer(WAVE_TIMER) {
            val session = runs.sessionFor(player)
            if (session == null) {
                player.clearSoftTimer(WAVE_TIMER)
                return@onPlayerSoftTimer
            }
            val run =
                runs.runFor(player)
                    ?: run {
                        player.clearSoftTimer(WAVE_TIMER)
                        return@onPlayerSoftTimer
                    }
            run.npcs.removeAll { it.hitpoints <= 0 }
            if (
                run.npcs.isNotEmpty() &&
                    run.npcs.none {
                        it.coords.mx == player.coords.mx && it.coords.mz == player.coords.mz &&
                            it.coords.chebyshevDistance(player.coords) <= FORFEIT_RANGE
                    }
            ) {
                player.clearSoftTimer(WAVE_TIMER)
                runs.remove(session.id)
                player.mes("You have left the challenge.")
                player.timer(KetRakRuns.EXIT_TIMER, 2)
                return@onPlayerSoftTimer
            }
            for (npc in run.npcs) {
                when (npc.type.internalName) {
                    JAD -> {
                        if (npc.mode != NpcMode.OpPlayer2 && npc.mode != NpcMode.ApPlayer2) {
                            runs.engage(npc, player)
                        }
                        runs.tickHealers(player, session.id, npc)
                    }
                    HEALER -> {
                        val jad = runs.jadFor(npc)
                        if (npc.mode == NpcMode.None && (jad == null || jad.hitpoints <= 0)) {
                            npc.opPlayer2(player, aiPlayerInteractions)
                        }
                    }
                }
            }
            if (!run.completed && run.npcs.isEmpty() && run.jadCount > 0) {
                if (run.endless) {
                    runs.topUp(player, session.id)
                } else {
                    recordCompletion(player, run.jadCount)
                    runs.complete(player, session.id, objRepo)
                    player.timer(KetRakRuns.EXIT_TIMER, EXIT_DELAY)
                }
            }
            player.softTimer(WAVE_TIMER, WAVE_CHECK_TICKS)
        }

        onPlayerTimer(KetRakRuns.EXIT_TIMER) {
            player.clearTimer(KetRakRuns.EXIT_TIMER)
            with(runs) { leave() }
        }

        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) {
            player.clearSoftTimer(WAVE_TIMER)
            runs.remove(instanceId)
        }
    }

    private fun lockedReason(player: Player, challenge: Int): String? {
        if (challenge > 1 && player.vars[COMPLETION_VARPS[challenge - 2]] <= 0) {
            return "You must first defeat ${challenge - 1} JalTok-Jad${if (challenge > 2) "s" else ""}."
        }
        if (challenge >= 3 && player.vars["varp.total_zuk_kills"] <= 0) {
            return "You must first defeat TzKal-Zuk in the Inferno."
        }
        return null
    }

    private fun recordCompletion(player: Player, jadCount: Int) {
        val varp = COMPLETION_VARPS.getOrNull(jadCount - 1) ?: return
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] + 1)
    }

    private companion object {
        private const val FORFEIT_RANGE = 60
        private const val EXIT_DELAY = 4
        private val COMPLETION_VARPS =
            arrayOf(
                "varp.jad_challenge_1_completions",
                "varp.jad_challenge_2_completions",
                "varp.jad_challenge_3_completions",
                "varp.jad_challenge_4_completions",
                "varp.jad_challenge_5_completions",
                "varp.jad_challenge_6_completions",
            )
    }
}

internal class KetRakKillHook @Inject constructor(private val runs: KetRakRuns) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val run = runs.runFor(context.hero) ?: return
        if (context.npc.type.internalName == KetRakRuns.JAD) run.kills++
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
