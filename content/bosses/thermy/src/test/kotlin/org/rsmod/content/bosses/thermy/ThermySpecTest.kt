package org.rsmod.content.bosses.thermy

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class ThermySpecTest {
    @Test
    fun `spec builds and validates`() {
        assertDoesNotThrow { thermonuclearSmokeDevilSpec() }
    }
}
