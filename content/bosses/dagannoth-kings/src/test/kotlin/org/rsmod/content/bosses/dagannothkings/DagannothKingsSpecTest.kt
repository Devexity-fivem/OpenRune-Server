package org.rsmod.content.bosses.dagannothkings

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class DagannothKingsSpecTest {
    @Test
    fun `specs build and validate`() {
        assertDoesNotThrow { dagannothPrimeSpec() }
        assertDoesNotThrow { dagannothSupremeSpec() }
        assertDoesNotThrow { dagannothRexSpec() }
        assertDoesNotThrow { spinolypSpec() }
    }
}
