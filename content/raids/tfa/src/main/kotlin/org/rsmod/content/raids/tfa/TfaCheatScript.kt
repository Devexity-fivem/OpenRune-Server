package org.rsmod.content.raids.tfa

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onCommand
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusEncounter
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class TfaCheatScript
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val ocellus: OcellusEncounter,
    private val rayCast: RayCastValidator,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("tfaocellus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reset Ocellus and teleport in front of the arena entrance"
            cheat { toOcellus() }
        }
        onCommand("ocellus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Ocellus testing: start | shield | p3 | hp <percent> | debug [on|off] | los | flags"
            cheat { ocellusCommand() }
        }
    }

    private fun Cheat.ocellusCommand() {
        when (args.firstOrNull()) {
            "start" -> {
                ocellus.ready(player)
            }
            "shield" -> report(ocellus.setHealthPercent(0), "Shield dropped to 1 hp.")
            "debug" ->
                when (args.getOrNull(1)) {
                    "on" -> {
                        ocellus.debug.enable(player)
                        player.mes("Ocellus debug tracing on (also logged by the server).")
                    }
                    "off" -> {
                        ocellus.debug.disable()
                        player.mes("Ocellus debug tracing off.")
                    }
                    else -> ocellus.debug.snapshot().forEach { player.mes(it) }
                }
            "los" -> stoneSight()
            "flags" -> flagDump()
            "p3" ->
                player.mes(
                    if (ocellus.skipToPhaseThree()) "Skipping to phase 3."
                    else "Ocellus isn't in phase 2. Use ::ocellus start, then ::ocellus shield."
                )
            "hp" -> {
                val percent = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                if (percent == null) {
                    player.mes("Usage: ::ocellus hp <percent>")
                    return
                }
                report(ocellus.setHealthPercent(percent), "Boss health set to $percent%.")
            }
            else -> player.mes("Usage: ::ocellus start | shield | p3 | hp <percent> | debug [on|off]")
        }
    }

    /** Dumps the collision flags on the player's tile and 1 to 3 tiles away along each axis. */
    private fun Cheat.flagDump() {
        fun describe(x: Int, z: Int): String {
            val flags = collision[x, z, player.coords.level]
            val names = FLAG_NAMES.filter { flags and it.value != 0 }.keys.joinToString(",")
            return "0x${flags.toUInt().toString(16)}[$names]"
        }
        val here = player.coords
        player.mes("here $here ${describe(here.x, here.z)}")
        for ((label, dx, dz) in listOf(Triple("N", 0, 1), Triple("S", 0, -1), Triple("E", 1, 0), Triple("W", -1, 0))) {
            val line = (1..3).joinToString("  ") { "$it=${describe(here.x + dx * it, here.z + dz * it)}" }
            player.mes("$label $line")
        }
    }

    /** Reports, for every stone, the distance and line of sight the stone click is gated on. */
    private fun Cheat.stoneSight() {
        val centre = OcellusEncounter.CENTRE
        val stones =
            listOf(4, 7, 10).flatMap { d ->
                listOf(
                    centre.translate(0, d),
                    centre.translate(0, -d),
                    centre.translate(d, 0),
                    centre.translate(-d, 0),
                )
            }
        for (stone in stones) {
            val distance = player.coords.chebyshevDistance(stone)
            val sight = rayCast.hasLineOfSight(player.coords, stone, extraFlag = CollisionFlag.BLOCK_PLAYERS)
            player.mes("stone $stone distance=$distance los=$sight")
        }
    }

    private fun Cheat.report(applied: Boolean, message: String) {
        player.mes(if (applied) message else "Ocellus isn't in a fight. Use ::ocellus start first.")
    }

    private companion object {
        val FLAG_NAMES =
            linkedMapOf(
                "LOC" to CollisionFlag.LOC,
                "LOC_PROJ" to CollisionFlag.LOC_PROJ_BLOCKER,
                "GROUND_DECOR" to CollisionFlag.GROUND_DECOR,
                "BLOCK_NPCS" to CollisionFlag.BLOCK_NPCS,
                "BLOCK_PLAYERS" to CollisionFlag.BLOCK_PLAYERS,
                "BLOCK_WALK" to CollisionFlag.BLOCK_WALK,
                "LOC_ROUTE" to CollisionFlag.LOC_ROUTE_BLOCKER,
                "WALL_N" to CollisionFlag.WALL_NORTH,
                "WALL_E" to CollisionFlag.WALL_EAST,
                "WALL_S" to CollisionFlag.WALL_SOUTH,
                "WALL_W" to CollisionFlag.WALL_WEST,
                "PROJ_N" to CollisionFlag.WALL_NORTH_PROJ_BLOCKER,
                "PROJ_E" to CollisionFlag.WALL_EAST_PROJ_BLOCKER,
                "PROJ_S" to CollisionFlag.WALL_SOUTH_PROJ_BLOCKER,
                "PROJ_W" to CollisionFlag.WALL_WEST_PROJ_BLOCKER,
            )
    }

    private fun Cheat.toOcellus() {
        ocellus.reset()
        launcher.launch(player, busyText = "Close your dialog first.") {
            telejump(OcellusEncounter.ENTRANCE, TeleportType.Exempt)
        }
    }
}
