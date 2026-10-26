package org.rsmod.content.bosses.chaoselemental

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class ChaosElementalSpecTest {
    @Test
    fun `spec builds and validates`() {
        assertDoesNotThrow { chaosElementalSpec() }
    }
}
