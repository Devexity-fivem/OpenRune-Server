package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import kotlin.math.atan2
import kotlin.math.roundToInt
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The shadow slash: a band as wide as the boss sweeping out from it in one of eight directions,
 * clipped to the floor of the quadrant it stands in. Diagonals sweep a corner "L" of three tiles
 * per step. The spec binds every step's band at [AIM], a tick before the slash starts, while the
 * boss is pinned facing that way until [RELEASE], so players can see where it will land and move
 * out of the way.
 */
internal object OcellusSlash {
    const val AIM = "ocellus.slash_aim"
    const val RELEASE = "ocellus.slash_release"
    const val STEPS = 13
    const val STEP_DELAY = 2

    private const val FACING_DISTANCE = 5
    private const val SOUTH = 6
    private val DIRECTIONS =
        listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

    fun register(deps: BossDeps) {
        deps.extensionRegistry.register(AIM) { aim(it.npc, it.target) }
        deps.extensionRegistry.register(RELEASE) { it.npc.clearFacingLock() }
    }

    fun band(npc: Npc, target: Player, step: Int): List<CoordGrid> {
        val (dx, dz) = DIRECTIONS[octant(npc, target)]
        val sw = npc.coords
        val far = npc.size - 1
        val tiles =
            when {
                dz == 0 -> {
                    val x = if (dx > 0) sw.x + far + step else sw.x - step
                    (0..far).map { CoordGrid(x, sw.z + it, sw.level) }
                }
                dx == 0 -> {
                    val z = if (dz > 0) sw.z + far + step else sw.z - step
                    (0..far).map { CoordGrid(sw.x + it, z, sw.level) }
                }
                else -> {
                    val cx = if (dx > 0) sw.x + far else sw.x
                    val cz = if (dz > 0) sw.z + far else sw.z
                    listOf(
                        CoordGrid(cx + dx * step, cz + dz * step, sw.level),
                        CoordGrid(cx + dx * (step - 1), cz + dz * step, sw.level),
                        CoordGrid(cx + dx * step, cz + dz * (step - 1), sw.level),
                    )
                }
            }
        return tiles.filter { OcellusArena.sameQuadrantFloor(npc, it) }
    }

    private fun aim(npc: Npc, target: Player) {
        val (dx, dz) = DIRECTIONS[octant(npc, target)]
        npc.lockFacing(centre(npc).translate(dx * FACING_DISTANCE, dz * FACING_DISTANCE))
    }

    private fun octant(npc: Npc, target: Player): Int {
        val centre = centre(npc)
        val dx = target.coords.x - centre.x
        val dz = target.coords.z - centre.z
        if (dx == 0 && dz == 0) return SOUTH
        val turns = (atan2(dz.toDouble(), dx.toDouble()) / (Math.PI / 4)).roundToInt()
        return Math.floorMod(turns, DIRECTIONS.size)
    }

    private fun centre(npc: Npc): CoordGrid = npc.coords.translate(npc.size / 2, npc.size / 2)
}
