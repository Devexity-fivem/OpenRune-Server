package org.rsmod.content.bosses.nex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.flag.CollisionFlag

class NexDashTest {
    private val sw = CoordGrid(2924, 5203)

    private fun field(): CollisionFlagMap =
        CollisionFlagMap().apply {
            for (x in 2900..2950) {
                for (z in 5185..5230) {
                    allocateIfAbsent(x, z, 0)
                }
            }
        }

    private fun northWall(collision: CollisionFlagMap, z: Int) {
        for (x in 2915..2935) {
            collision.add(x, z, 0, CollisionFlag.WALL_SOUTH)
        }
    }

    @Test
    fun `open floor slides the full distance`() {
        assertEquals(
            sw.translate(0, 8),
            Nex.dashDestination(sw, 0, 1, 3, field(), CollisionStrategy.Normal),
        )
    }

    @Test
    fun `a wall stops the slide at its base`() {
        val collision = field().apply { northWall(this, 5208) }
        assertEquals(
            sw.translate(0, 2),
            Nex.dashDestination(sw, 0, 1, 3, collision, CollisionStrategy.Normal),
        )
    }

    @Test
    fun `wall-interior pocket tiles do not swallow her`() {
        val collision = field().apply { northWall(this, 5208) }
        // The pocket behind the wall is allocated and carries no occupancy
        // flags: a landing-box probe would accept it, sequential stepping
        // refuses to cross the wall.
        assertEquals(0, collision[2924, 5209, 0])
        assertEquals(
            sw.translate(0, 2),
            Nex.dashDestination(sw, 0, 1, 3, collision, CollisionStrategy.Normal),
        )
    }

    @Test
    fun `occupied tiles stop the slide`() {
        val collision = field()
        for (x in 2920..2930) {
            collision.add(x, 5210, 0, CollisionFlag.BLOCK_WALK)
        }
        assertEquals(
            sw.translate(0, 4),
            Nex.dashDestination(sw, 0, 1, 3, collision, CollisionStrategy.Normal),
        )
    }

    @Test
    fun `npc-occupied tiles stop the slide`() {
        val collision = field()
        for (x in 2920..2930) {
            collision.add(x, 5208, 0, CollisionFlag.BLOCK_NPCS)
        }
        assertEquals(
            sw.translate(0, 2),
            Nex.dashDestination(sw, 0, 1, 3, collision, CollisionStrategy.Normal),
        )
    }

    @Test
    fun `players do not block the slide`() {
        val collision = field()
        for (x in 2920..2930) {
            collision.add(x, 5208, 0, CollisionFlag.BLOCK_PLAYERS)
        }
        assertEquals(
            sw.translate(0, 8),
            Nex.dashDestination(sw, 0, 1, 3, collision, CollisionStrategy.Normal),
        )
    }

    @Test
    fun `a diagonal slide stops when either axis is walled`() {
        val collision = field().apply { northWall(this, 5208) }
        assertEquals(
            sw.translate(2, 2),
            Nex.dashDestination(sw, 1, 1, 3, collision, CollisionStrategy.Normal),
        )
    }

    @Test
    fun `backed against the wall she does not move`() {
        val collision = field().apply { northWall(this, 5206) }
        assertEquals(
            sw,
            Nex.dashDestination(sw, 0, 1, 3, collision, CollisionStrategy.Normal),
        )
    }
}
