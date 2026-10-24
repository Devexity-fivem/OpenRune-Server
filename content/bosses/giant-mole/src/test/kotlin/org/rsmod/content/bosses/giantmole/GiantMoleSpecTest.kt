package org.rsmod.content.bosses.giantmole

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class GiantMoleSpecTest {
    @Test
    fun `spec builds and validates`() {
        assertDoesNotThrow { giantMoleSpec() }
    }
}
