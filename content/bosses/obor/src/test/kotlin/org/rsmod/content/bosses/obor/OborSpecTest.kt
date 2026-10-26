package org.rsmod.content.bosses.obor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class OborSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.hillgiant_boss".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.agilityarena_player_spikedback".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.myarm_rock_roc_travel".asRSCM(RSCMType.SPOTANIM) }
    }
}
