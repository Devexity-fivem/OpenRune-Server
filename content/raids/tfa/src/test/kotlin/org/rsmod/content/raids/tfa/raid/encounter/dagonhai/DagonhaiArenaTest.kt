package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class DagonhaiArenaTest {
    private val origin = CoordGrid(DagonhaiArena.SPAWN_X, DagonhaiArena.SPAWN_Z, 0)

    @Test
    fun `cauldron wanders the scorch patch`() {
        val home = DagonhaiArena.cauldronHome(origin)
        assertEquals(CoordGrid(4065, 5599, 0), home)
        val seen = HashSet<CoordGrid>()
        for (slot in 0 until DagonhaiArena.CAULDRON_SLOTS) {
            val tile = DagonhaiArena.cauldronTile(origin, slot)
            assertTrue(tile.x in 4063..4068, "cauldron tile $tile outside scorch x-range")
            assertTrue(tile.z in 5597..5601, "cauldron tile $tile outside scorch z-range")
            assertTrue(seen.add(tile), "duplicate cauldron tile $tile")
        }
    }

    @Test
    fun `monks stand in the north and south arms`() {
        val north = DagonhaiArena.monkTile(origin, DagonhaiArena.SLOT_NORTH)
        val south = DagonhaiArena.monkTile(origin, DagonhaiArena.SLOT_SOUTH)
        assertEquals(CoordGrid(4065, 5607, 0), north)
        assertEquals(CoordGrid(4065, 5591, 0), south)
        assertNotEquals(north, south)
    }

    @Test
    fun `tome lanes run straight from each monk to the cauldron`() {
        val north = DagonhaiArena.tomeLane(origin, DagonhaiArena.SLOT_NORTH)
        val south = DagonhaiArena.tomeLane(origin, DagonhaiArena.SLOT_SOUTH)
        assertTrue(north.isNotEmpty())
        assertTrue(south.isNotEmpty())
        assertTrue(north.all { it.x == 4065 })
        assertTrue(south.all { it.x == 4065 })
        assertEquals(5606, north.first().z)
        assertEquals(5600, north.last().z)
        assertEquals(5592, south.first().z)
        assertEquals(5598, south.last().z)
        assertTrue(north.none { it == DagonhaiArena.cauldronHome(origin) })
        assertTrue(south.none { it == DagonhaiArena.cauldronHome(origin) })
    }

    @Test
    fun `entry lands inside the chamber east wall`() {
        val entry = DagonhaiArena.arenaEntry(origin)
        assertEquals(CoordGrid(4077, 5599, 0), entry)
        assertTrue(DagonhaiArena.inFightArea(origin, entry))
        assertTrue(DagonhaiArena.inRoom(origin, entry))
    }

    @Test
    fun `fight area covers chamber and both monk arms`() {
        assertTrue(DagonhaiArena.inFightArea(origin, CoordGrid(4065, 5591, 0)))
        assertTrue(DagonhaiArena.inFightArea(origin, CoordGrid(4065, 5607, 0)))
        assertTrue(DagonhaiArena.inFightArea(origin, CoordGrid(4077, 5599, 0)))
        assertTrue(DagonhaiArena.inFightArea(origin, CoordGrid(4049, 5597, 0)))
        assertFalse(DagonhaiArena.inFightArea(origin, CoordGrid(4090, 5599, 0)))
        assertFalse(DagonhaiArena.inFightArea(origin, CoordGrid(4065, 5620, 0)))
    }

    @Test
    fun `room bounds follow the copied mapsquare`() {
        assertTrue(DagonhaiArena.inRoom(origin, origin))
        assertTrue(DagonhaiArena.inRoom(origin, CoordGrid(4065, 5613, 0)))
        assertTrue(DagonhaiArena.inRoom(origin, CoordGrid(4065, 5584, 0)))
        assertFalse(DagonhaiArena.inRoom(origin, CoordGrid(3900, 5599, 0)))
    }

    @Test
    fun `rift tiles spread across the room`() {
        val tiles =
            (0 until DagonhaiArena.RIFT_SLOTS).map { DagonhaiArena.riftTile(origin, it) }.toSet()
        assertEquals(DagonhaiArena.RIFT_SLOTS, tiles.size)
        for (tile in tiles) assertTrue(DagonhaiArena.inFightArea(origin, tile))
    }
}
