package dev.openrune.types

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemServerTypeTest {
    @Test
    fun `equipable when iop2 wields`() {
        val type = item(wearpos = 3, ops = mutableListOf(null, "Wield"))
        assertTrue(type.isEquipable)
        assertTrue(type.hasWearableOp(2))
        assertFalse(type.hasWearableOp(1))
    }

    @Test
    fun `equipable when iop1 wears`() {
        // Ring of shadows: iop1=Wear, iop2=Teleport.
        val type = item(wearpos = 12, ops = mutableListOf("Wear", "Teleport"))
        assertTrue(type.isEquipable)
        assertTrue(type.hasWearableOp(1))
        assertFalse(type.hasWearableOp(2))
    }

    @Test
    fun `equipable when iop2 is Equip`() {
        // Rada's blessings wear via iop2=Equip.
        val type = item(wearpos = 13, ops = mutableListOf(null, "Equip"))
        assertTrue(type.isEquipable)
        assertTrue(type.hasWearableOp(2))
    }

    @Test
    fun `equipable when iop4 wears`() {
        // Shayzien supply sets wear via iop4=Wear.
        val type = item(wearpos = 4, ops = mutableListOf(null, null, null, "Wear"))
        assertTrue(type.isEquipable)
        assertTrue(type.hasWearableOp(4))
        assertFalse(type.hasWearableOp(2))
    }

    @Test
    fun `not equipable without wearpos`() {
        val type = item(wearpos = -1, ops = mutableListOf(null, "Wear"))
        assertFalse(type.isEquipable)
    }

    @Test
    fun `not equipable without wear verb`() {
        val type = item(wearpos = 3, ops = mutableListOf("Use", "Teleport"))
        assertFalse(type.isEquipable)
        assertFalse(type.hasWearableOp(1))
        assertFalse(type.hasWearableOp(2))
    }

    private fun item(wearpos: Int, ops: MutableList<String?>): ItemServerType =
        ItemServerType(wearpos1 = wearpos, interfaceOptions = ops)
}
