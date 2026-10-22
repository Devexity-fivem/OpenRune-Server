package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionPlayer

/**
 * Switchable tracing for the fight (`::ocellus debug`). It records every boss combat tick and,
 * while enabled, reports each change of phase, interaction or quadrant plus the tick the combat
 * ticks stop, along with what the npc interaction processor would be checking at that moment.
 */
internal class OcellusDebug(
    private val deps: BossDeps,
    private val boss: () -> Npc?,
    private val quadrant: () -> String,
) {
    private var watcher: Player? = null
    private var lastCombatTick = NEVER
    private var combatTicks = 0
    private var lastKey = ""
    private var reportedStall = false

    val enabled: Boolean
        get() = watcher != null

    fun noteCombatTick() {
        lastCombatTick = deps.mapClock.cycle
        combatTicks++
        reportedStall = false
    }

    fun reset() {
        lastCombatTick = NEVER
        combatTicks = 0
        lastKey = ""
        reportedStall = false
    }

    fun enable(player: Player) {
        watcher = player
        lastKey = ""
        schedule()
    }

    fun disable() {
        watcher = null
    }

    fun snapshot(): List<String> {
        val npc = boss() ?: return listOf("No Ocellus npc.")
        if (!npc.isSlotAssigned) return listOf("Ocellus npc is not in the world.")
        val encounter = deps.encounterRegistry.of(npc)
        val interaction = npc.interaction
        val target = (interaction as? InteractionPlayer)?.target
        val players = OcellusArena.players(npc, deps.playerList)
        val ago = if (lastCombatTick == NEVER) "never" else "${deps.mapClock.cycle - lastCombatTick} ticks ago"
        val lines =
            mutableListOf(
                "type=${npc.visType.id} phase=${encounter.currentPhaseName} " +
                    "hp=${npc.hitpoints}/${npc.baseHitpointsLvl} quadrant=${quadrant()}",
                "combat ticks: $combatTicks total, last $ago",
                "interaction=${interaction?.javaClass?.simpleName ?: "none"} " +
                    "target=${target?.displayName ?: "-"} mode=${npc.mode} busy=${npc.isBusy}",
                "maxRange=${npc.visType.maxRange} attackRange=${npc.attackRange} " +
                    "spawn=${npc.spawnCoords} at=${npc.coords}",
            )
        for (player in players) {
            val distance = npc.spawnCoords.chebyshevDistance(player.coords)
            val verdict = if (distance > npc.visType.maxRange) "OUT OF MAX RANGE" else "ok"
            lines += "  ${player.displayName} ${player.coords} spawnDistance=$distance $verdict"
        }
        if (players.isEmpty()) lines += "  no players in the arena"
        return lines
    }

    private fun schedule() {
        deps.worldQueues.add(1) { if (enabled) tick().also { schedule() } }
    }

    private fun tick() {
        val npc = boss()?.takeIf { it.isSlotAssigned } ?: return
        val cycle = deps.mapClock.cycle
        val encounter = deps.encounterRegistry.of(npc)
        val key = "${npc.visType.id}|${encounter.currentPhaseName}|${npc.interaction != null}|${quadrant()}"
        val stalled =
            lastCombatTick != NEVER && cycle - lastCombatTick >= STALL_TICKS && !reportedStall
        val reason =
            when {
                key != lastKey -> "state changed"
                stalled -> "no combat tick for $STALL_TICKS ticks"
                cycle % HEARTBEAT == 0 -> "heartbeat"
                else -> return
            }
        if (stalled) reportedStall = true
        lastKey = key
        report("[$cycle] $reason")
        snapshot().forEach(::report)
    }

    private fun report(line: String) {
        logger.info { "[ocellus-debug] $line" }
        watcher?.mes(line)
    }

    private companion object {
        val logger = InlineLogger()
        const val NEVER = -1
        const val STALL_TICKS = 3
        const val HEARTBEAT = 25
    }
}
