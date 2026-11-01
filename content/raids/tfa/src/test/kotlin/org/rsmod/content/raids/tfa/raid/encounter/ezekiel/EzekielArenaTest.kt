package org.rsmod.content.raids.tfa.raid.encounter.ezekiel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class EzekielArenaTest {
    private val origin = CoordGrid(EzekielArena.SPAWN_X, EzekielArena.SPAWN_Z, 0)

    @Test
    fun `west lane is the straight row from its fence slot across the arena`() {
        val lane = EzekielArena.laneTiles(origin, 1)
        assertEquals(EzekielArena.swordTile(origin, 1), lane.first())
        assertTrue(lane.all { it.z == origin.z })
        assertEquals(origin.x - 7, lane.minOf { it.x })
        assertEquals(origin.x + 9, lane.maxOf { it.x })
        assertTrue(lane.any { EzekielArena.inFootprint(origin, it) })
    }

    @Test
    fun `north lane is the straight column down through the footprint`() {
        val lane = EzekielArena.laneTiles(origin, 3)
        assertEquals(EzekielArena.swordTile(origin, 3), lane.first())
        assertTrue(lane.all { it.x == origin.x })
        assertTrue(lane.any { EzekielArena.inFootprint(origin, it) })
    }

    @Test
    fun `corner lanes reach the footprint on the diagonal`() {
        for (slot in intArrayOf(0, 2, 4, 6)) {
            val lane = EzekielArena.laneTiles(origin, slot)
            assertEquals(EzekielArena.swordTile(origin, slot), lane.first())
            assertTrue(lane.any { EzekielArena.inFootprint(origin, it) }, "slot $slot lane")
        }
    }

    @Test
    fun `every fence slot sits inside the arena`() {
        for (slot in 0 until EzekielArena.SWORD_SLOTS) {
            assertTrue(EzekielArena.inArena(origin, EzekielArena.swordTile(origin, slot)))
        }
    }

    @Test
    fun `enrage lanes all cross the origin footprint`() {
        for (index in EzekielArena.ENRAGE_LANES.indices) {
            val lane = EzekielArena.enrageLane(origin, index)
            assertTrue(lane.any { EzekielArena.inFootprint(origin, it) })
        }
    }

    @Test
    fun `cleave spares the tiles opposite the tank`() {
        val tank = origin.translate(1, -1)
        val cleave = EzekielArena.cleaveTiles(origin, tank).toSet()
        assertTrue(origin.translate(1, -1) in cleave)
        assertFalse(origin.translate(1, 3) in cleave)
        assertFalse(origin.translate(1, 0) in cleave)
        assertFalse(origin.translate(1, -3) in cleave)
    }

    @Test
    fun `cleave from the east spares the west side`() {
        val tank = origin.translate(4, 1)
        val cleave = EzekielArena.cleaveTiles(origin, tank).toSet()
        assertTrue(origin.translate(4, 1) in cleave)
        assertFalse(origin.translate(-1, 1) in cleave)
    }

    @Test
    fun `cross tiles are the plus through the footprint without the footprint`() {
        val cross = EzekielArena.crossTiles(origin).toSet()
        assertTrue(origin.translate(1, 6) in cross)
        assertTrue(origin.translate(5, 1) in cross)
        assertFalse(origin.translate(1, 1) in cross)
        assertFalse(origin.translate(-5, -6) in cross)
        assertFalse(cross.any { EzekielArena.inFootprint(origin, it) })
    }

    @Test
    fun `footprint distance is zero inside and grows outward`() {
        assertEquals(0, EzekielArena.footprintDistance(origin, origin))
        assertEquals(0, EzekielArena.footprintDistance(origin, origin.translate(2, 2)))
        assertEquals(1, EzekielArena.footprintDistance(origin, origin.translate(-1, 0)))
        assertEquals(3, EzekielArena.footprintDistance(origin, origin.translate(5, 0)))
    }

    @Test
    fun `centre tile is the middle of the footprint`() {
        assertEquals(origin.translate(1, 1), EzekielArena.centreTile(origin))
    }
}
