package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stepping stones over the gap left by phase 3's collapse. Crossing takes a hop onto each
 * stone along the arm, then a hop off the far end onto the other side.
 */
class OcellusStones : PluginScript() {
    override fun ScriptContext.startup() {
        for (stone in STONES) onApLoc1(stone) { apJump(it.loc) }
    }

    private suspend fun ProtectedAccess.apJump(stone: BoundLocInfo) {
        if (isWithinApRange(stone, distance = STONE_AP_RANGE)) jump(stone.coords)
    }

    private suspend fun ProtectedAccess.jump(stone: CoordGrid) {
        val centre = OcellusEncounter.CENTRE
        val alongZ = stone.x == centre.x
        val side = if (alongZ) (coords.x - centre.x).sign else (coords.z - centre.z).sign
        if (side == 0) return
        val outward = if (alongZ) (stone.z - centre.z).sign else (stone.x - centre.x).sign
        val stones = generateSequence(stone) { next(it, outward, alongZ, centre) }.toList()
        for (tile in stones) {
            hop(tile)
            delay(HOP_TICKS)
        }
        val last = stones.last()
        hop(if (alongZ) CoordGrid(centre.x - side, last.z, last.level) else CoordGrid(last.x, centre.z - side, last.level))
        delay(HOP_TICKS)
    }

    private fun next(from: CoordGrid, outward: Int, alongZ: Boolean, centre: CoordGrid): CoordGrid? {
        val tile =
            if (alongZ) from.translate(0, outward * STONE_SPACING)
            else from.translate(outward * STONE_SPACING, 0)
        val offset = if (alongZ) abs(tile.z - centre.z) else abs(tile.x - centre.x)
        return tile.takeIf { offset <= FARTHEST_STONE }
    }

    private fun ProtectedAccess.hop(to: CoordGrid) {
        exactMove(coords, to, delay1 = HOP_START_CYCLES, delay2 = HOP_END_CYCLES, dir = direction(coords, to))
        anim("seq.human_steppingstonejump", delay = HOP_ANIM_DELAY)
        soundSynth(JUMP_SOUND, delay = HOP_SOUND_DELAY)
    }

    private fun direction(from: CoordGrid, to: CoordGrid): Int =
        when {
            to.x > from.x -> FACE_EAST
            to.x < from.x -> FACE_WEST
            to.z > from.z -> FACE_NORTH
            else -> FACE_SOUTH
        }

    private companion object {
        val STONES =
            listOf(
                "loc.tfa_ocellus_platform_jump_middle",
                "loc.tfa_ocellus_platform_jump_outer_east",
                "loc.tfa_ocellus_platform_jump_outer_north",
                "loc.tfa_ocellus_platform_jump_outer_south",
                "loc.tfa_ocellus_platform_jump_outer_west",
            )

        const val STONE_AP_RANGE = 3
        const val STONE_SPACING = 3
        const val FARTHEST_STONE = 10

        const val HOP_START_CYCLES = 47
        const val HOP_END_CYCLES = 59
        const val HOP_ANIM_DELAY = 19
        const val HOP_SOUND_DELAY = 59
        const val HOP_TICKS = 3

        const val FACE_SOUTH = 0
        const val FACE_WEST = 512
        const val FACE_NORTH = 1024
        const val FACE_EAST = 1536

        const val JUMP_SOUND = "synth.varrock_fence_jump"
    }
}
