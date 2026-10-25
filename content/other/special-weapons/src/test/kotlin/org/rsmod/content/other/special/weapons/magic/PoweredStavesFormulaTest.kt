package org.rsmod.content.other.special.weapons.magic

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.ACCURSED
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.BONE_STAFF
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.EYE_OF_AYAK
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.SANGUINESTI
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.SEAS_TRIDENT
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.STARTER_STAFF
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.SWAMP_TRIDENT
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.THAMMARON
import org.rsmod.content.other.special.weapons.magic.PoweredStaves.Companion.WARPED_SCEPTRE

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PoweredStavesFormulaTest {
    @Test
    fun `trident of the seas follows floor(M over 3) minus 5`() {
        assertEquals(28, SEAS_TRIDENT.maxHit(99))
        assertEquals(11, SEAS_TRIDENT.maxHit(50))
        assertEquals(28, SEAS_TRIDENT.maxHit(100))
        assertEquals(30, SEAS_TRIDENT.maxHit(105))
        assertEquals(31, SEAS_TRIDENT.maxHit(108))
    }

    @Test
    fun `trident of the swamp adds three over seas`() {
        for (lvl in 1..110) {
            assertEquals(SEAS_TRIDENT.maxHit(lvl) + 3, SWAMP_TRIDENT.maxHit(lvl), "lvl=$lvl")
        }
    }

    @Test
    fun `sanguinesti adds four over seas`() {
        for (lvl in 1..110) {
            assertEquals(SEAS_TRIDENT.maxHit(lvl) + 4, SANGUINESTI.maxHit(lvl), "lvl=$lvl")
        }
    }

    @Test
    fun `warped sceptre follows floor(8M plus 96) over 37`() {
        // Wiki anchors: starts at 16 for the lvl-62 wield req, peaks at 24 for lvl 99.
        assertEquals(16, WARPED_SCEPTRE.maxHit(62))
        assertEquals(17, WARPED_SCEPTRE.maxHit(70))
        assertEquals(19, WARPED_SCEPTRE.maxHit(78))
        assertEquals(22, WARPED_SCEPTRE.maxHit(94))
        assertEquals(24, WARPED_SCEPTRE.maxHit(99))
    }

    @Test
    fun `wilderness sceptres follow their own floors`() {
        // Thammaron's: floor(M/3) - 8 -> lvl 99 = 25, lvl 60 = 12.
        assertEquals(25, THAMMARON.maxHit(99))
        assertEquals(12, THAMMARON.maxHit(60))
        // Accursed: floor(M/3) - 6 -> lvl 99 = 27, lvl 60 = 14.
        assertEquals(27, ACCURSED.maxHit(99))
        assertEquals(14, ACCURSED.maxHit(60))
    }

    @Test
    fun `bone staff is floor(M over 3) plus 5 with a flat +10 vs rats`() {
        assertEquals(38, BONE_STAFF.maxHit(99))
        assertEquals(21, BONE_STAFF.maxHit(50))
        assertEquals(10, BONE_STAFF.flatBonus)
    }

    @Test
    fun `eye of ayak matches accursed floor`() {
        for (lvl in 1..110) {
            assertEquals(ACCURSED.maxHit(lvl), EYE_OF_AYAK.maxHit(lvl), "lvl=$lvl")
        }
        assertEquals(3, EYE_OF_AYAK.attackRate)
    }

    @Test
    fun `starter staff has fixed max hit of 8`() {
        assertEquals(8, STARTER_STAFF.maxHit(1))
        assertEquals(8, STARTER_STAFF.maxHit(99))
        assertEquals(5, STARTER_STAFF.attackRate)
    }

    @Test
    fun `wilderness sceptres are flagged for the wilderness passive`() {
        assertEquals(true, THAMMARON.wildernessSceptre)
        assertEquals(true, ACCURSED.wildernessSceptre)
        assertEquals(1_000, THAMMARON.activationDeposit)
        assertEquals(1_000, ACCURSED.activationDeposit)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
