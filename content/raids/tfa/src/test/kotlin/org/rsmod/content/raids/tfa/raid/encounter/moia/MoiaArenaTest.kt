package org.rsmod.content.raids.tfa.raid.encounter.moia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class MoiaArenaTest {
    private val origin = CoordGrid(MoiaArena.SPAWN_X, MoiaArena.SPAWN_Z, 0)

    @Test
    fun `hovers form the four sides of the ring`() {
        val south = MoiaArena.hoverSpawn(origin, MoiaArena.SIDE_SOUTH)
        val north = MoiaArena.hoverSpawn(origin, MoiaArena.SIDE_NORTH)
        val west = MoiaArena.hoverSpawn(origin, MoiaArena.SIDE_WEST)
        val east = MoiaArena.hoverSpawn(origin, MoiaArena.SIDE_EAST)
        assertEquals(origin, south)
        assertTrue(north.z > origin.z && west.x < origin.x && east.x > origin.x)
        // each hover centre sits one side off the ring centre
        val centre = MoiaArena.centreTile(origin)
        for (spawn in listOf(south, north, west, east)) {
            val mid = spawn.translate(1, 1)
            assertTrue(
                kotlin.math.abs(mid.x - centre.x) + kotlin.math.abs(mid.z - centre.z) > 0,
                "hover at $spawn",
            )
        }
    }

    @Test
    fun `binder lattice is the 3x3 grid around the centre tile`() {
        val centre = MoiaArena.centreTile(origin)
        for (slot in 0 until MoiaArena.BINDER_SLOTS) {
            val tile = MoiaArena.binderTile(origin, slot)
            assertTrue(
                kotlin.math.abs(tile.x - centre.x) <= 2 &&
                    kotlin.math.abs(tile.z - centre.z) <= 2,
                "slot $slot at $tile",
            )
        }
        assertEquals(centre, MoiaArena.binderTile(origin, MoiaArena.CENTRE_SLOT))
    }

    @Test
    fun `west slot is the initial gap in the ring`() {
        val west = MoiaArena.binderTile(origin, MoiaArena.WEST_SLOT)
        val centre = MoiaArena.centreTile(origin)
        assertTrue(west.x < centre.x)
    }

    @Test
    fun `perimeter cycle visits each outer slot exactly once`() {
        assertEquals(8, MoiaArena.PERIMETER.size)
        assertEquals(MoiaArena.BINDER_SLOTS - 1, MoiaArena.PERIMETER.toSet().size)
        assertFalse(MoiaArena.PERIMETER.contains(MoiaArena.CENTRE_SLOT))
    }

    @Test
    fun `binder tiles and hover spawns stay inside the arena`() {
        for (slot in 0 until MoiaArena.BINDER_SLOTS) {
            assertTrue(
                MoiaArena.contains(origin, MoiaArena.binderTile(origin, slot)),
                "slot $slot",
            )
        }
        for (side in 0..3) {
            assertTrue(
                MoiaArena.inCourtyard(origin, MoiaArena.hoverSpawn(origin, side)),
                "side $side",
            )
        }
        assertTrue(MoiaArena.contains(origin, MoiaArena.arenaEntry(origin)))
        assertTrue(MoiaArena.contains(origin, MoiaArena.enrageSpawn(origin)))
    }

    @Test
    fun `enrage spawn footprint covers the centre lattice tile`() {
        val spawn = MoiaArena.enrageSpawn(origin)
        val centre = MoiaArena.centreTile(origin)
        assertTrue(
            centre.x in spawn.x until spawn.x + MoiaArena.FOOTPRINT &&
                centre.z in spawn.z until spawn.z + MoiaArena.FOOTPRINT,
        )
    }

    @Test
    fun `entry sits east of the fence ring`() {
        val entry = MoiaArena.arenaEntry(origin)
        assertTrue(entry.x > MoiaArena.binderTile(origin, MoiaArena.CENTRE_SLOT).x)
    }

    @Test
    fun `footprint distance is chebyshev around the hover centre`() {
        assertEquals(0, MoiaArena.footprintDistance(origin, origin.translate(1, 1)))
        assertEquals(3, MoiaArena.footprintDistance(origin, origin.translate(-2, 1)))
        assertEquals(5, MoiaArena.footprintDistance(origin, origin.translate(1, -4)))
    }

    @Test
    fun `infection damage escalates then caps`() {
        assertTrue(MoiaArena.infectDamage(0) < MoiaArena.infectDamage(3))
        assertEquals(20, MoiaArena.infectDamage(5))
        assertEquals(20, MoiaArena.infectDamage(11))
    }
}
