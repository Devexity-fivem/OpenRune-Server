package org.rsmod.content.bosses.worldbosses

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

// Locks in the op surface MoonsExtensions relies on: the unlit brazier's
// Light op drives relighting, while the ice block has no ops — its break is
// timed, not click-driven.
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PmoonLocOpsTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `unlit brazier has a Light op and ice block has none`() {
        val brazier =
            checkNotNull(
                ServerCacheManager.getObject(
                    "loc.pmoon_boss_brazier_unlit".asRSCM(RSCMType.LOC)
                )
            )
        assertEquals("Light", brazier.actions.ops?.firstOrNull()?.text)

        val iceBlock =
            checkNotNull(
                ServerCacheManager.getObject("loc.pmoon_boss_ice_block".asRSCM(RSCMType.LOC))
            )
        assertTrue(iceBlock.actions.ops.isNullOrEmpty())
    }
}
