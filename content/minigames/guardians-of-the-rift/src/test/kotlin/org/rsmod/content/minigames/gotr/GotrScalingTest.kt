package org.rsmod.content.minigames.gotr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GotrScalingTest {
    @Test
    fun `charge target scales linearly through twenty players`() {
        assertEquals(500, Gotr.chargeTargetFor(1))
        assertEquals(1000, Gotr.chargeTargetFor(2))
        assertEquals(10000, Gotr.chargeTargetFor(20))
    }

    @Test
    fun `each player after twenty adds two hundred stones worth of charge`() {
        assertEquals(10400, Gotr.chargeTargetFor(21))
        assertEquals(14000, Gotr.chargeTargetFor(30))
        assertEquals(500, Gotr.chargeTargetFor(0))
    }
}
