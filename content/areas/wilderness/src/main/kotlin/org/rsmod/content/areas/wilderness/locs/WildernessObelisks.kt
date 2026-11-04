package org.rsmod.content.areas.wilderness.locs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Wilderness obelisks teleport every player standing in the 3x3 grid at their centre to another
 * obelisk. Touching an obelisk arms it for [ACTIVATE_DELAY] cycles (the pillars swap to their
 * glowing variant) and the first player to activate it decides the destination: "Activate" picks a
 * random site (players can even land back at the same obelisk), while "Teleport to Destination"
 * uses that player's configured destination, settable via "Set Destination" once the Hard
 * Wilderness Diary is complete. Obelisks work at any Wilderness level, but players under Tele
 * Block are left behind.
 */
class WildernessObelisks
@Inject
constructor(
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
    private val locRepo: LocRepository,
) : PluginScript() {

    private data class Obelisk(val symbol: String, val name: String, val center: CoordGrid)

    private data class Activation(val destIndex: Int, val fireAt: Int)

    private val obelisks =
        listOf(
            Obelisk("loc.wilderness_portal_stone_0", "level 44", CoordGrid(2980, 3866, 0)),
            Obelisk("loc.wilderness_portal_stone_1", "level 27", CoordGrid(3035, 3732, 0)),
            Obelisk("loc.wilderness_portal_stone_2", "level 35", CoordGrid(3106, 3794, 0)),
            Obelisk("loc.wilderness_portal_stone_3", "level 13", CoordGrid(3156, 3620, 0)),
            Obelisk("loc.wilderness_portal_stone_4", "level 19", CoordGrid(3227, 3667, 0)),
            Obelisk("loc.wilderness_portal_stone_5", "level 50", CoordGrid(3304, 3914, 0)),
        )

    private val armed = mutableMapOf<Int, Activation>()

    override fun ScriptContext.startup() {
        for ((index, obelisk) in obelisks.withIndex()) {
            onOpLoc1(obelisk.symbol) { activate(index, useDestination = false) }
            onOpLoc2(obelisk.symbol) { activate(index, useDestination = true) }
            onOpLoc3(obelisk.symbol) { setDestination(index) }
        }
        onPlayerSoftTimer(ACTIVATE_TIMER) { player.fireDueActivations() }
    }

    private suspend fun ProtectedAccess.activate(index: Int, useDestination: Boolean) {
        arriveDelay()
        val now = player.currentMapClock
        val existing = armed[index]
        if (existing != null && existing.fireAt > now) {
            return
        }
        val stored = storedDestination(index)
        val destIndex =
            if (useDestination && stored != null) stored else obelisks.indices.random()
        armed[index] = Activation(destIndex, now + ACTIVATE_DELAY)
        softTimer(ACTIVATE_TIMER, ACTIVATE_DELAY)
        glowPillars(obelisks[index])
    }

    private fun glowPillars(site: Obelisk) {
        val pillarId = site.symbol.asRSCM(RSCMType.LOC)
        for ((dx, dz) in PILLAR_OFFSETS) {
            val tile = site.center.translate(dx, dz)
            for (pillar in locRepo.findAll(tile)) {
                if (pillar.id == pillarId) {
                    locRepo.add(tile, GLOWING_PILLAR, ACTIVATE_DELAY + 1, pillar.angle, pillar.shape)
                }
            }
        }
    }

    private fun Player.fireDueActivations() {
        val now = currentMapClock
        val due = armed.filterValues { it.fireAt <= now }
        armed.keys.removeAll(due.keys)
        for ((index, activation) in due) {
            fire(obelisks[index], activation)
        }
    }

    private fun fire(site: Obelisk, activation: Activation) {
        val dest = obelisks[activation.destIndex].center
        for (other in playerList) {
            if (other.coords.chebyshevDistance(site.center) > PLATFORM_RADIUS) {
                continue
            }
            if (other.vars[TELEBLOCK_VARBIT] > 0) {
                continue
            }
            val landing =
                CoordGrid(
                    dest.x + Random.nextInt(-LANDING_SPREAD, LANDING_SPREAD + 1),
                    dest.z + Random.nextInt(-LANDING_SPREAD, LANDING_SPREAD + 1),
                    0,
                )
            other.spotanim(FIRE_SPOT, height = FIRE_SPOT_HEIGHT)
            PathingEntityCommon.telejump(other, collision, landing)
            other.mes("Ancient magic teleports you somewhere in the Wilderness!")
        }
    }

    private suspend fun ProtectedAccess.setDestination(index: Int) {
        arriveDelay()
        if (vars[DIARY_VARBIT] == 0) {
            mesbox("You must complete the Hard Wilderness Diary to set an obelisk's destination.")
            return
        }
        val options = obelisks.filterIndexed { i, _ -> i != index }
        val pick =
            choice5(
                "${options[0].name} Wilderness",
                0,
                "${options[1].name} Wilderness",
                1,
                "${options[2].name} Wilderness",
                2,
                "${options[3].name} Wilderness",
                3,
                "${options[4].name} Wilderness",
                4,
                title = "Set teleport destination",
            )
        val dest = options[pick]
        storeDestination(index, obelisks.indexOf(dest))
        mes("The obelisk's destination is now the Wilderness obelisk in ${dest.name} Wilderness.")
    }

    private fun ProtectedAccess.storedDestination(index: Int): Int? {
        val stored = (vars[DEST_VARP] shr (index * BITS_PER_SITE)) and SITE_MASK
        return if (stored != 0) stored - 1 else null
    }

    private fun ProtectedAccess.storeDestination(index: Int, dest: Int) {
        val shift = index * BITS_PER_SITE
        val raw = vars[DEST_VARP]
        vars[DEST_VARP] = (raw and (SITE_MASK shl shift).inv()) or ((dest + 1) shl shift)
    }

    private fun CoordGrid.chebyshevDistance(other: CoordGrid): Int =
        maxOf(abs(x - other.x), abs(z - other.z))

    private companion object {
        private const val GLOWING_PILLAR = "loc.wilderness_portal_stone_glowing"
        private const val FIRE_SPOT = "spotanim.fx_obelisk01_glow01"
        private const val FIRE_SPOT_HEIGHT = 90
        private const val ACTIVATE_TIMER = "timer.wild_obelisk_activate"
        private const val ACTIVATE_DELAY = 8
        private const val PLATFORM_RADIUS = 1
        private const val LANDING_SPREAD = 1
        private const val DEST_VARP = "varp.wild_obelisk_dest"
        private const val DIARY_VARBIT = "varbit.wilderness_diary_hard_complete"
        private const val TELEBLOCK_VARBIT = "varbit.teleblock_cycles"
        private const val BITS_PER_SITE = 3
        private const val SITE_MASK = 0b111
        private val PILLAR_OFFSETS = listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2)
    }
}
