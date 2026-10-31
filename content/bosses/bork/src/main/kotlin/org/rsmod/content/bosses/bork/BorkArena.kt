package org.rsmod.content.bosses.bork

import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * Geometry of the Bork arena. The instance copies the Chasm of Tears guardian floor
 * (region 12948, level 2), so every coordinate below is expressed as an offset from the
 * player entry tile — the same numbers work in every instance placement.
 *
 * The Smorks' cannon cart sits dead centre: it blocks Bork's stomp beats, which is why
 * tiles in its shadow are the safe spots during the stomp cycle.
 */
internal object BorkArena {
    const val ENTRANCE_REGION = 12948
    const val ENTRANCE_LEVEL = 2

    val ENTRANCE_LOC_TILE = CoordGrid(3223, 9516, ENTRANCE_LEVEL)
    val EXIT_RETURN_TILE = CoordGrid(3224, 9516, ENTRANCE_LEVEL)

    /** Offsets from the instance entry tile. */
    const val SMORK_DX = 2
    const val SMORK_DZ = -6
    const val BORK_DX = 2
    const val BORK_DZ = -13

    fun smorkTile(enter: CoordGrid): CoordGrid = enter.translate(SMORK_DX, SMORK_DZ)

    fun borkTile(enter: CoordGrid): CoordGrid = enter.translate(BORK_DX, BORK_DZ)

    fun exitLocTile(enter: CoordGrid): CoordGrid = enter.translate(0, 1)

    /** The small beat: the tile ring immediately surrounding Bork's footprint. */
    fun beatTiles(npc: Npc): List<CoordGrid> = ring(npc, inner = 0, outer = 1)

    /**
     * The strong beat Bork fires when his target stands outside melee range: the same
     * shockwave pushed three tiles further out in every direction.
     */
    fun bigBeatTiles(npc: Npc): List<CoordGrid> = ring(npc, inner = 0, outer = 3)

    private fun ring(npc: Npc, inner: Int, outer: Int): List<CoordGrid> {
        val sw = npc.coords
        val s = npc.size
        val tiles = ArrayList<CoordGrid>()
        for (dx in -outer until s + outer) {
            for (dz in -outer until s + outer) {
                val inside = dx >= -inner && dx < s + inner && dz >= -inner && dz < s + inner
                if (!inside) {
                    tiles += sw.translate(dx, dz)
                }
            }
        }
        return tiles
    }

    const val CART_SHADOW = 1
}
